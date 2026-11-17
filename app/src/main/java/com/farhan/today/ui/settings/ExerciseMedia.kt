package com.farhan.today.ui.settings

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Animasi gerakan dari `assets/exercise-media/<animation>` (WebP animasi 180×180).
 * API 28+: diputar lewat ImageDecoder/AnimatedImageDrawable. API 26–27: BitmapFactory
 * hanya membaca frame pertama, jadi tampil sebagai gambar diam.
 */
@Composable
internal fun ExerciseMedia(animation: String) {
    ExerciseImage(animation, Modifier.size(180.dp), play = true) {
        Text("Animasi gerakan tidak tersedia.")
    }
}

@Composable
internal fun ExerciseImage(
    animation: String,
    modifier: Modifier,
    play: Boolean = false,
    unavailable: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val path = "exercise-media/$animation"
    val result = produceState<Result<Drawable>?>(null, path, play) {
        value = null
        val drawable = withContext(Dispatchers.IO) {
            val decoded = if (Build.VERSION.SDK_INT >= 28) {
                try {
                    val source = ImageDecoder.createSource(context.assets, path)
                    if (play) ImageDecoder.decodeDrawable(source)
                    else BitmapDrawable(context.resources, ImageDecoder.decodeBitmap(source))
                } catch (_: IOException) {
                    null
                }
            } else null
            decoded ?: try {
                context.assets.open(path).use { input ->
                    BitmapFactory.decodeStream(input)?.let { BitmapDrawable(context.resources, it) }
                }
            } catch (_: IOException) {
                null
            }
        }
        value = drawable?.let { Result.success(it) }
            ?: Result.failure(IOException("Unable to decode exercise media"))
    }.value ?: return
    val media = result.getOrNull() ?: return unavailable()

    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        modifier = modifier,
        onRelease = { view ->
            if (Build.VERSION.SDK_INT >= 28) (view.drawable as? AnimatedImageDrawable)?.stop()
            view.setImageDrawable(null)
        },
        update = { view ->
            if (view.drawable !== media) {
                if (Build.VERSION.SDK_INT >= 28) (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(media)
                if (Build.VERSION.SDK_INT >= 28 && media is AnimatedImageDrawable) {
                    media.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    media.start()
                }
            }
        },
    )
}
