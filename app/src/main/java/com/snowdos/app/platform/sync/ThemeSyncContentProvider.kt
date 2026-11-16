package com.snowdos.app.platform.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snowdos.app.data.local.BackgroundImageStorage
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Signature-protected ContentProvider exposing SnowDo's active appearance snapshot (`/appearance`)
 * and internal background image stream (`/background`) to the verified SnowSplit partner app.
 *
 * Uses lazy [EntryPointAccessors] instead of `@AndroidEntryPoint` because `ContentProvider.onCreate()`
 * executes before `Application.onCreate()`.
 */
class ThemeSyncContentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncProviderEntryPoint {
        fun partnerAppDetector(): PartnerAppDetector
        fun themeSyncManager(): ThemeSyncManager
        fun settingsRepository(): SettingsRepository
        fun backgroundImageStorage(): BackgroundImageStorage
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(PartnerAppConfig.OWN_AUTHORITY, PartnerAppConfig.PATH_APPEARANCE, CODE_APPEARANCE)
        addURI(PartnerAppConfig.OWN_AUTHORITY, PartnerAppConfig.PATH_BACKGROUND, CODE_BACKGROUND)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val entryPoint = requireTrustedCaller()
        if (uriMatcher.match(uri) != CODE_APPEARANCE) {
            return null
        }

        val payloadJson = runBlocking(Dispatchers.IO) {
            entryPoint.themeSyncManager().buildOutgoingSnapshotJson()
        }
        return MatrixCursor(arrayOf(PartnerAppConfig.COLUMN_PAYLOAD_JSON), 1).apply {
            addRow(arrayOf(payloadJson))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val entryPoint = requireTrustedCaller()
        if (uriMatcher.match(uri) != CODE_BACKGROUND) {
            throw FileNotFoundException("Unsupported URI: $uri")
        }
        if (mode != "r") {
            throw SecurityException("Only read-only mode is permitted")
        }

        val backgroundUri = runBlocking(Dispatchers.IO) {
            entryPoint.settingsRepository().observeSettings().first().appearance.backgroundImageUri
        }
        return entryPoint.backgroundImageStorage().openReadableFileDescriptor(backgroundUri)
            ?: throw FileNotFoundException("No active custom background image")
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_APPEARANCE -> "vnd.android.cursor.item/vnd.${PartnerAppConfig.OWN_AUTHORITY}.appearance"
        CODE_BACKGROUND -> "image/jpeg"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun requireTrustedCaller(): SyncProviderEntryPoint {
        val appContext = context?.applicationContext
            ?: throw SecurityException("Provider context unavailable")
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            SyncProviderEntryPoint::class.java
        )
        val caller = callingPackage
        if (!entryPoint.partnerAppDetector().isTrustedPartnerPackage(caller)) {
            throw SecurityException("Untrusted callingPackage: $caller")
        }
        return entryPoint
    }

    private companion object {
        const val CODE_APPEARANCE = 1
        const val CODE_BACKGROUND = 2
    }
}
