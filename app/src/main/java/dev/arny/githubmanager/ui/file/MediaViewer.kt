package dev.arny.githubmanager.ui.file

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import dev.arny.githubmanager.domain.FilePreviewKind
import dev.arny.githubmanager.domain.hexDump
import dev.arny.githubmanager.ui.formatAssetSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DECODE_EDGE = 2048
private const val HEX_PREVIEW_BYTES = 4_096

/** Raster or SVG preview with pinch-zoom, pan and double-tap reset. */
@Composable
fun ImageViewer(name: String, bytes: ByteArray, kind: FilePreviewKind, url: String?, size: Long) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, bytes, kind) {
        value = withContext(Dispatchers.Default) {
            if (kind == FilePreviewKind.VECTOR) renderSvg(bytes) else decodeBitmap(bytes)
        }
    }
    var scale by remember(bytes) { mutableFloatStateOf(1f) }
    var offset by remember(bytes) { mutableStateOf(Offset.Zero) }
    val loaded = bitmap
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        loaded?.let { "${it.width}×${it.height}" },
                        formatAssetSize(size),
                        if (kind == FilePreviewKind.VECTOR) "SVG" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (url != null) IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Icon(Icons.Default.OpenInBrowser, "Открыть на GitHub") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(bytes) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(bytes) {
                    detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                loaded == null -> CircularProgressIndicator()
                else -> Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y
                    )
                )
            }
        }
    }
}

/** Hex preview for files that cannot be rendered; keeps the whole file out of memory. */
@Composable
fun BinaryViewer(name: String, bytes: ByteArray, url: String?, size: Long) {
    val context = LocalContext.current
    // 16 bytes per row needs ~430dp; on phones use 8 so the ASCII column stays visible.
    val bytesPerRow = if (LocalConfiguration.current.screenWidthDp >= 430) 16 else 8
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("${formatAssetSize(size)} · двоичный файл",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (url != null) IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Icon(Icons.Default.OpenInBrowser, "Открыть на GitHub") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (bytes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Файл пуст или недоступен") }
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("Предпросмотр недоступен. Хекс-дамп первых ${minOf(bytes.size, HEX_PREVIEW_BYTES)} байт:",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(hexDump(bytes, HEX_PREVIEW_BYTES, bytesPerRow), softWrap = false,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp
                        ))
                }
            }
        }
    }
}

private fun decodeBitmap(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_DECODE_EDGE && bounds.outHeight / (sample * 2) >= MAX_DECODE_EDGE) {
        sample *= 2
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun renderSvg(bytes: ByteArray): Bitmap? = runCatching {
    val svg = SVG.getFromString(bytes.toString(Charsets.UTF_8))
    val width = svg.documentWidth.takeIf { it > 0 } ?: 512f
    val height = svg.documentHeight.takeIf { it > 0 } ?: 512f
    val factor = minOf(MAX_DECODE_EDGE / width, MAX_DECODE_EDGE / height).coerceIn(0.1f, 4f)
    val bitmap = Bitmap.createBitmap(
        (width * factor).toInt().coerceAtLeast(1),
        (height * factor).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    svg.setDocumentWidth(bitmap.width.toFloat())
    svg.setDocumentHeight(bitmap.height.toFloat())
    svg.renderToCanvas(Canvas(bitmap))
    bitmap
}.getOrNull()
