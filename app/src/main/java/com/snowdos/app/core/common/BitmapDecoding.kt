package com.snowdos.app.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared URI/file bitmap decoding helpers with power-of-two subsampling and bounded LRU memory caching.
 */
object AppBitmapCache {
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 8L)
            .toInt()
            .coerceIn(8 * 1024, 32 * 1024)
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun evictAll() {
        cache.evictAll()
    }

    fun cacheKey(uriString: String, maxWidthPx: Int, maxHeightPx: Int): String =
        "$uriString#${maxWidthPx}x$maxHeightPx"
}

fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = 1
    if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) {
        return inSampleSize
    }
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

suspend fun loadSampledBitmapFromUri(
    context: Context,
    uriString: String,
    maxWidthPx: Int,
    maxHeightPx: Int
): Bitmap? {
    val key = AppBitmapCache.cacheKey(uriString, maxWidthPx, maxHeightPx)
    AppBitmapCache.get(key)?.let { cached ->
        if (!cached.isRecycled) return cached
    }

    val decoded = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeWithUri(context, uri, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = maxWidthPx,
                reqHeight = maxHeightPx
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decodeWithUri(context, uri, decodeOptions)
        }.getOrNull()
    }

    if (decoded != null) {
        AppBitmapCache.put(key, decoded)
    }
    return decoded
}

/**
 * Loads a bitmap from [uriString] bounded to at most [maxEdgePx] on its longest edge.
 * Uses `RGB_565` to keep Parcel/RemoteViews binder size compact well below the 1 MB limit.
 */
suspend fun loadBoundedBitmapFromUri(
    context: Context,
    uriString: String,
    maxEdgePx: Int
): Bitmap? {
    val safeMaxEdge = maxEdgePx.coerceAtLeast(1)
    val parsedUri = Uri.parse(uriString)
    val fileStamp = if (parsedUri.scheme == "file") {
        parsedUri.path?.let { java.io.File(it).lastModified() } ?: 0L
    } else {
        0L
    }
    val key = "${uriString}#bounded_${safeMaxEdge}_$fileStamp"
    AppBitmapCache.get(key)?.let { cached ->
        if (!cached.isRecycled) return cached
    }

    val bounded = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeWithUri(context, uri, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = safeMaxEdge,
                reqHeight = safeMaxEdge
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = decodeWithUri(context, uri, decodeOptions)
                ?: decodeWithUri(
                    context,
                    uri,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
                ?: return@runCatching null

            val longestEdge = maxOf(decoded.width, decoded.height)
            if (longestEdge <= safeMaxEdge) {
                decoded
            } else {
                val scale = safeMaxEdge.toFloat() / longestEdge.toFloat()
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled !== decoded) {
                    decoded.recycle()
                }
                scaled
            }
        }.getOrNull()
    }

    if (bounded != null) {
        AppBitmapCache.put(key, bounded)
    }
    return bounded
}

private fun decodeWithUri(
    context: Context,
    uri: Uri,
    options: BitmapFactory.Options
): Bitmap? = if (uri.scheme == "file") {
    uri.path?.let { BitmapFactory.decodeFile(it, options) }
} else {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}
