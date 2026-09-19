package com.noobexon.xposedfakelocation.manager.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noobexon.xposedfakelocation.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.LruCache

private val bitmapCache = object : LruCache<String, Bitmap>(24) {
    override fun sizeOf(key: String, value: Bitmap): Int = 1
}

/**
 * Minimal remote image renderer (used only for the handful of contributor avatars on the About
 * screen). Replaces a full image-loading library: fetches on IO, decodes once, and keeps results
 * in a small in-process LruCache so re-scrolling and re-entering the screen do not refetch.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = bitmapCache.get(url), url) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = true
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.also { bitmapCache.put(url, it) }
            }
        }
    }

    val resolved = bitmap
    if (resolved != null) {
        Image(
            bitmap = resolved.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        ImagePlaceholder(modifier = modifier)
    }
}

@Composable
private fun ImagePlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MiuixTheme.colorScheme.surfaceVariant,
    ) {}
}

