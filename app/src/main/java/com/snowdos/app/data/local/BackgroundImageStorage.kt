package com.snowdos.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snowdos.app.core.common.AppBitmapCache
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.core.common.calculateInSampleSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class BackgroundImageLoadException(message: String) : IllegalStateException(message)

data class ImportedBackgroundResult(
    val uri: String,
    val sha256Hash: String
)

/**
 * Persists selected background images inside internal app storage so they remain
 * accessible across restarts without relying on transient Photo Picker URI grants.
 */
@Singleton
class BackgroundImageStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) {
    private val backgroundsDir: File
        get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    suspend fun importFromUri(sourceUri: Uri): Result<String> = withContext(dispatcherProvider.io) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: throw BackgroundImageLoadException("Bild konnte nicht geöffnet werden")

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = MAX_DIMENSION_PX,
                reqHeight = MAX_DIMENSION_PX
            )
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw BackgroundImageLoadException("Bild konnte nicht dekodiert werden")

            try {
                clearStoredImages()
                val targetFile = File(backgroundsDir, "bg_${System.currentTimeMillis()}.jpg")
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                Uri.fromFile(targetFile).toString()
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Imports raw image bytes from a trusted synced ContentProvider stream without re-compressing
     * or buffering the entire payload in heap memory, preserving the exact SHA-256 hash across
     * SnowDo's and SnowSplit.
     */
    suspend fun importSyncedStream(
        openStream: () -> InputStream?
    ): Result<ImportedBackgroundResult> = withContext(dispatcherProvider.io) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val tempFile = File(context.cacheDir, "bg_sync_tmp_${System.nanoTime()}.tmp")
            try {
                openStream()?.use { rawInput ->
                    DigestInputStream(rawInput, digest).use { digestStream ->
                        FileOutputStream(tempFile).use { out ->
                            val buffer = ByteArray(STREAM_BUFFER_BYTES)
                            var total = 0L
                            while (true) {
                                val read = digestStream.read(buffer)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_SYNC_IMAGE_BYTES) {
                                    throw BackgroundImageLoadException("Sync-Hintergrundbild überschreitet Größenlimit")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                } ?: throw BackgroundImageLoadException("Sync-Hintergrundbild konnte nicht gelesen werden")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw BackgroundImageLoadException("Ungültiges Bildformat im Sync-Stream")
                }

                clearStoredImages()
                val targetFile = File(backgroundsDir, "bg_sync_${System.currentTimeMillis()}.jpg")
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.inputStream().use { input ->
                        FileOutputStream(targetFile).use { out ->
                            input.copyTo(out, STREAM_BUFFER_BYTES)
                        }
                    }
                    tempFile.delete()
                }
                ImportedBackgroundResult(
                    uri = Uri.fromFile(targetFile).toString(),
                    sha256Hash = formatSha256Hex(digest.digest())
                )
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    suspend fun computeHashForUri(uriString: String?): String? = withContext(dispatcherProvider.io) {
        val file = resolveInternalBackgroundFile(uriString) ?: return@withContext null
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            FileInputStream(file).use { input ->
                DigestInputStream(input, digest).use { digestStream ->
                    while (digestStream.read(buffer) >= 0) {
                        // DigestInputStream updates digest cleanly chunk-by-chunk without heap spikes
                    }
                }
            }
            formatSha256Hex(digest.digest())
        }.getOrNull()
    }

    fun openReadableFileDescriptor(uriString: String?): ParcelFileDescriptor? {
        val file = resolveInternalBackgroundFile(uriString) ?: return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }

    private fun resolveInternalBackgroundFile(uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null
        val parsed = Uri.parse(uriString)
        if (parsed.scheme != "file") return null
        val path = parsed.path ?: return null
        val candidate = File(path).canonicalFile
        val rootDir = backgroundsDir.canonicalFile
        if (!candidate.path.startsWith(rootDir.path + File.separator)) {
            return null
        }
        return candidate.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    suspend fun clearStoredImages() = withContext(dispatcherProvider.io) {
        backgroundsDir.listFiles()?.forEach { it.delete() }
        AppBitmapCache.evictAll()
    }

    companion object {
        private const val MAX_DIMENSION_PX = 2048
        private const val MAX_SYNC_IMAGE_BYTES = 16L * 1024L * 1024L
        private const val STREAM_BUFFER_BYTES = 8 * 1024

        internal fun computeSha256Hex(bytes: ByteArray): String =
            formatSha256Hex(MessageDigest.getInstance("SHA-256").digest(bytes))

        private fun formatSha256Hex(digestBytes: ByteArray): String =
            digestBytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
