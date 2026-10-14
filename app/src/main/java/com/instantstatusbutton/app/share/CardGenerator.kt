package com.instantstatusbutton.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.FileOutputStream

/** Renders the square (1080x1080) image card that is shared to Facebook / Instagram. */
object CardGenerator {
    private const val SIZE = 1080
    private const val MARGIN = 96

    fun render(status: String, placeName: String?, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val dark = ColorUtils.blendARGB(color, Color.BLACK, 0.35f)
        c.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), color, dark, Shader.TileMode.CLAMP)
        })
        val fg = if (ColorUtils.calculateLuminance(color) > 0.6) Color.parseColor("#1B1B1F") else Color.WHITE

        val hasPlace = !placeName.isNullOrBlank()
        val textAreaHeight = if (hasPlace) SIZE - 2 * MARGIN - 150 else SIZE - 2 * MARGIN
        val textWidth = SIZE - 2 * MARGIN

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = fg
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Largest font size at which the status fits the available box.
        var size = 150f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = layout(status, paint, textWidth)
            if (layout.height <= textAreaHeight || size <= 36f) break
            size -= 4f
        }
        if (layout.height > textAreaHeight) {
            layout = StaticLayout.Builder.obtain(status, 0, status.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines((textAreaHeight / paint.fontSpacing).toInt().coerceAtLeast(1))
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }

        c.save()
        c.translate(MARGIN.toFloat(), MARGIN + (textAreaHeight - layout.height) / 2f)
        layout.draw(c)
        c.restore()

        if (hasPlace) {
            val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = fg
                alpha = 230
                textSize = 52f
            }
            val line = StaticLayout.Builder.obtain("📍 $placeName", 0, placeName!!.length + 3, pp, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            c.save()
            c.translate(MARGIN.toFloat(), (SIZE - MARGIN - line.height).toFloat())
            line.draw(c)
            c.restore()
        }
        return bmp
    }

    /** Saves the card in the app cache and returns a content:// URI other apps can read. */
    fun renderToShareUri(context: Context, status: String, placeName: String?, color: Int): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // Unique name so receiving apps never show a stale cached image.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "status_${System.currentTimeMillis()}.png")
        val bmp = render(status, placeName, color)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun layout(text: String, paint: TextPaint, width: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
}
