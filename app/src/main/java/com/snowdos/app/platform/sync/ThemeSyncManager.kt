package com.snowdos.app.platform.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.data.local.BackgroundImageStorage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.platform.icon.AppIconManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Coordinates bidirectional theme, custom-background, widget, and app-icon sync between
 * SnowDo's (`com.snowdos.app`) and SnowSplit (`com.snowsplit.app`).
 */
@Singleton
class ThemeSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val partnerAppDetector: PartnerAppDetector,
    private val backgroundImageStorage: BackgroundImageStorage,
    private val appIconManager: AppIconManager,
    private val widgetUpdateTrigger: WidgetUpdateTrigger,
    private val dispatcherProvider: DispatcherProvider
) : DefaultLifecycleObserver {

    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatcherProvider.io) }
    private val syncMutex = Mutex()
    private val started = AtomicBoolean(false)
    private val lastForegroundPullElapsedMs = AtomicLong(Long.MIN_VALUE)

    private var pushDebounceJob: Job? = null
    private var lastObservedFingerprint: AppearanceSyncFingerprint? = null

    @Volatile
    private var suppressedSyncedFingerprint: AppearanceSyncFingerprint? = null

    fun initialize() {
        if (!started.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        observeLocalAppearanceChanges()
        scope.launch {
            if (partnerAppDetector.refreshPartnerStatus()) {
                pullFromPartnerIfNewer()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        partnerAppDetector.isProcessInForeground = true
        refreshAndPullAsync()
    }

    override fun onResume(owner: LifecycleOwner) {
        partnerAppDetector.isProcessInForeground = true
        refreshAndPullAsync()
    }

    override fun onStop(owner: LifecycleOwner) {
        partnerAppDetector.isProcessInForeground = false
    }

    fun onCrossAppSyncEnabled() {
        scope.launch {
            val trusted = partnerAppDetector.refreshPartnerStatus()
            if (!trusted) return@launch
            val appliedRemote = pullFromPartnerIfNewer()
            if (!appliedRemote) {
                val current = settingsRepository.observeSettings().first()
                if (current.crossAppSyncEnabled && current.partnerAppConnected) {
                    pushLocalSnapshot(current)
                }
            }
        }
    }

    suspend fun buildOutgoingSnapshotJson(): String {
        val settings = settingsRepository.observeSettings().first()
        val resolvedHash = resolveCurrentBackgroundHash(settings)
        val (effectiveSyncId, effectiveRevision) = resolveEffectiveOutgoingMeta(settings)
        return buildPayloadFromSettings(
            settings = settings,
            syncId = effectiveSyncId,
            revision = effectiveRevision,
            backgroundHash = resolvedHash
        ).toJson()
    }

    suspend fun pullFromPartnerIfNewer(): Boolean = withContext(dispatcherProvider.io) {
        val current = settingsRepository.observeSettings().first()
        if (!current.crossAppSyncEnabled || !current.partnerAppConnected) {
            return@withContext false
        }
        val payloadJson = runCatching {
            context.contentResolver.query(
                PartnerAppConfig.PARTNER_APPEARANCE_URI,
                arrayOf(PartnerAppConfig.COLUMN_PAYLOAD_JSON),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(PartnerAppConfig.COLUMN_PAYLOAD_JSON)
                    if (colIdx >= 0) cursor.getString(colIdx) else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: return@withContext false

        val payload = SnowAppearanceSyncPayload.fromJson(payloadJson) ?: return@withContext false
        return@withContext applyIncomingPayload(payload)
    }

    suspend fun applyIncomingPayload(payload: SnowAppearanceSyncPayload): Boolean =
        withContext(dispatcherProvider.io) {
            syncMutex.withLock {
                val current = settingsRepository.observeSettings().first()
                val decision = ThemeSyncGuards.evaluateIncoming(
                    payload = payload,
                    ownPackage = PartnerAppConfig.OWN_PACKAGE,
                    expectedPartnerPackage = PartnerAppConfig.PARTNER_PACKAGE,
                    crossAppSyncEnabled = current.crossAppSyncEnabled,
                    partnerAppConnected = current.partnerAppConnected,
                    lastAppliedSyncId = current.lastAppliedSyncId,
                    lastSyncedRevision = current.lastSyncedRevision,
                    currentAppearance = current.appearance,
                    currentReduceLiveBlur = current.reduceLiveBlur,
                    localBackgroundHash = current.localBackgroundHash
                )

                if (decision !is SyncDecision.Apply) {
                    return@withLock false
                }

                pushDebounceJob?.cancel()
                pushDebounceJob = null

                var resolvedBgUri = current.appearance.backgroundImageUri
                var resolvedBgHash = current.localBackgroundHash

                when (decision.backgroundAction) {
                    BackgroundSyncAction.COPY_FROM_PARTNER -> {
                        val contentUri = payload.customBackgroundContentUri?.let(Uri::parse)
                        if (contentUri != null) {
                            val imported = backgroundImageStorage.importSyncedStream {
                                context.contentResolver.openInputStream(contentUri)
                            }.getOrNull()
                            if (imported != null) {
                                resolvedBgUri = imported.uri
                                resolvedBgHash = payload.customBackgroundHash ?: imported.sha256Hash
                            }
                        }
                    }
                    BackgroundSyncAction.CLEAR_LOCAL -> {
                        backgroundImageStorage.clearStoredImages()
                        resolvedBgUri = null
                        resolvedBgHash = null
                    }
                    BackgroundSyncAction.NO_CHANGE -> Unit
                }

                val targetAppearance = payload.toAppearanceConfig(resolvedBackgroundImageUri = resolvedBgUri)
                suppressedSyncedFingerprint = AppearanceSyncFingerprint.from(
                    appearance = targetAppearance,
                    reduceLiveBlur = payload.reduceLiveBlur,
                    localBackgroundHash = resolvedBgHash
                )

                settingsRepository.applySyncedAppearance(
                    appearance = targetAppearance,
                    reduceLiveBlur = payload.reduceLiveBlur,
                    syncRevision = payload.updatedAtEpochMillis,
                    syncId = payload.syncId,
                    backgroundHash = resolvedBgHash
                )

                appIconManager.onAppearanceSynced(isAppInForeground = partnerAppDetector.isProcessInForeground)
                widgetUpdateTrigger.updateAll()
                true
            }
        }

    private fun refreshAndPullAsync() {
        val nowMs = System.nanoTime() / 1_000_000L
        if (!tryAcquirePullSlot(lastForegroundPullElapsedMs, nowMs = nowMs)) {
            return
        }
        scope.launch {
            if (partnerAppDetector.refreshPartnerStatus()) {
                pullFromPartnerIfNewer()
            }
        }
    }

    private fun observeLocalAppearanceChanges() {
        scope.launch {
            settingsRepository.observeSettings().collect { settings ->
                val fingerprint = AppearanceSyncFingerprint.from(
                    appearance = settings.appearance,
                    reduceLiveBlur = settings.reduceLiveBlur,
                    localBackgroundHash = settings.localBackgroundHash
                )

                val previous = lastObservedFingerprint
                lastObservedFingerprint = fingerprint
                if (previous == null || previous == fingerprint) {
                    return@collect
                }

                if (suppressedSyncedFingerprint == fingerprint) {
                    suppressedSyncedFingerprint = null
                    return@collect
                }

                if (!settings.crossAppSyncEnabled || !settings.partnerAppConnected) {
                    return@collect
                }

                // Immediately bump local revision + syncId before the 400 ms push debounce so a
                // ContentProvider pull during a rapid app switch already sees the newer state.
                val immediateRevision = nextMonotonicRevision(settings)
                val immediateSyncId = UUID.randomUUID().toString()
                settingsRepository.recordLocalAppearanceModified(
                    modifiedAtMillis = immediateRevision,
                    syncId = immediateSyncId
                )

                pushDebounceJob?.cancel()
                pushDebounceJob = scope.launch {
                    delay(PUSH_DEBOUNCE_MS)
                    val latest = settingsRepository.observeSettings().first()
                    if (!latest.crossAppSyncEnabled || !latest.partnerAppConnected) return@launch
                    pushLocalSnapshot(latest)
                }
            }
        }
    }

    private suspend fun pushLocalSnapshot(settings: AppSettings) {
        syncMutex.withLock {
            val trusted = partnerAppDetector.refreshPartnerStatus()
            if (!trusted) return@withLock

            val current = settingsRepository.observeSettings().first()
            if (!current.crossAppSyncEnabled || !current.partnerAppConnected) return@withLock

            val resolvedHash = resolveCurrentBackgroundHash(current)
            val nextRevision = max(
                System.currentTimeMillis(),
                max(current.lastSyncedRevision, current.localModifiedAtMillis)
            )
            val syncId = current.lastPublishedSyncId.ifBlank { UUID.randomUUID().toString() }

            settingsRepository.markLocalAppearanceSynced(revision = nextRevision, syncId = syncId)
            val payload = buildPayloadFromSettings(
                settings = current,
                syncId = syncId,
                revision = nextRevision,
                backgroundHash = resolvedHash
            )

            val intent = Intent(PartnerAppConfig.SYNC_ACTION).apply {
                setPackage(PartnerAppConfig.PARTNER_PACKAGE)
                putExtra(PartnerAppConfig.EXTRA_PAYLOAD_JSON, payload.toJson())
            }
            runCatching {
                context.sendBroadcast(intent, PartnerAppConfig.SYNC_PERMISSION)
            }
        }
    }

    private suspend fun resolveCurrentBackgroundHash(settings: AppSettings): String? {
        val bgUri = settings.appearance.backgroundImageUri ?: return null
        val existing = settings.localBackgroundHash
        if (!existing.isNullOrBlank()) return existing

        val computed = backgroundImageStorage.computeHashForUri(bgUri)
        if (!computed.isNullOrBlank()) {
            settingsRepository.updateLocalBackgroundHash(computed)
        }
        return computed
    }

    private fun buildPayloadFromSettings(
        settings: AppSettings,
        syncId: String,
        revision: Long,
        backgroundHash: String?
    ): SnowAppearanceSyncPayload {
        val hasCustomBg = !settings.appearance.backgroundImageUri.isNullOrBlank()
        return SnowAppearanceSyncPayload(
            schemaVersion = SnowAppearanceSyncPayload.CURRENT_SCHEMA_VERSION,
            sourcePackage = PartnerAppConfig.OWN_PACKAGE,
            syncId = syncId,
            updatedAtEpochMillis = revision,
            themeMode = ThemeMode.PRESET.name,
            presetId = settings.appearance.effectivePresetId(),
            customAccentHex = settings.appearance.customAccentHex,
            gamingAccentHex = settings.appearance.gamingAccentHex,
            eightBitVariant = settings.appearance.eightBitVariant.name,
            glassOpacity = settings.appearance.glassOpacity,
            reduceLiveBlur = settings.reduceLiveBlur,
            hasCustomBackground = hasCustomBg,
            customBackgroundHash = if (hasCustomBg) backgroundHash else null,
            customBackgroundContentUri = if (hasCustomBg) PartnerAppConfig.OWN_BACKGROUND_CONTENT_URI else null,
            weatherStyle = settings.appearance.weatherStyle.name,
            autoSeasonalThemeEnabled = settings.appearance.autoSeasonalThemeEnabled
        )
    }

    private data class AppearanceSyncFingerprint(
        val mode: ThemeMode,
        val presetId: String,
        val customAccentHex: String,
        val gamingAccentHex: String,
        val eightBitVariantName: String,
        val weatherStyleName: String,
        val autoSeasonalThemeEnabled: Boolean,
        val backgroundImageUri: String?,
        val localBackgroundHash: String?,
        val glassOpacityBits: Int,
        val reduceLiveBlur: Boolean
    ) {
        companion object {
            fun from(
                appearance: ThemeAppearanceConfig,
                reduceLiveBlur: Boolean,
                localBackgroundHash: String?
            ): AppearanceSyncFingerprint = AppearanceSyncFingerprint(
                mode = appearance.mode,
                presetId = appearance.effectivePresetId(),
                customAccentHex = appearance.customAccentHex.uppercase(),
                gamingAccentHex = appearance.gamingAccentHex.uppercase(),
                eightBitVariantName = appearance.eightBitVariant.name,
                weatherStyleName = appearance.weatherStyle.name,
                autoSeasonalThemeEnabled = appearance.autoSeasonalThemeEnabled,
                backgroundImageUri = appearance.backgroundImageUri,
                localBackgroundHash = localBackgroundHash,
                glassOpacityBits = (appearance.glassOpacity * 1000f).toInt(),
                reduceLiveBlur = reduceLiveBlur
            )
        }
    }

    companion object {
        internal const val PUSH_DEBOUNCE_MS = 400L
        internal const val FOREGROUND_PULL_COOLDOWN_MS = 1_000L

        internal fun tryAcquirePullSlot(
            lastTriggerMs: AtomicLong,
            nowMs: Long,
            cooldownMs: Long = FOREGROUND_PULL_COOLDOWN_MS
        ): Boolean {
            val prev = lastTriggerMs.get()
            if (prev != Long.MIN_VALUE && (nowMs - prev) < cooldownMs) {
                return false
            }
            return lastTriggerMs.compareAndSet(prev, nowMs)
        }

        internal fun nextMonotonicRevision(
            settings: AppSettings,
            nowMillis: Long = System.currentTimeMillis()
        ): Long = max(nowMillis, max(settings.lastSyncedRevision, settings.localModifiedAtMillis) + 1L)

        internal fun resolveEffectiveOutgoingMeta(settings: AppSettings): Pair<String, Long> {
            val effectiveRevision = max(max(settings.lastSyncedRevision, settings.localModifiedAtMillis), 1L)
            val effectiveSyncId = settings.lastPublishedSyncId
                .ifBlank { settings.lastAppliedSyncId }
                .ifBlank { "state_$effectiveRevision" }
            return effectiveSyncId to effectiveRevision
        }
    }
}
