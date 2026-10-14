package com.instantstatusbutton.app.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.instantstatusbutton.app.data.Network

/**
 * Facebook and Instagram do not allow posting to a personal profile through their APIs and both ignore
 * prefilled text, so we copy the text to the clipboard and hand them the image card via a share intent.
 */
object ShareLauncher {
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    fun packageFor(n: Network) = if (n == Network.FACEBOOK) FACEBOOK_PACKAGE else INSTAGRAM_PACKAGE

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Status", text))
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Intent targeting [network]'s app directly, or null if it is not installed (or cannot take images).
     */
    fun directIntent(context: Context, network: Network, image: Uri, text: String): Intent? {
        val pkg = packageFor(network)
        if (!isInstalled(context, pkg)) return null
        val intent = baseIntent(image, text).setPackage(pkg)
        if (intent.resolveActivity(context.packageManager) == null) return null
        context.grantUriPermission(pkg, image, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    /** System share sheet, used when the target app is missing. */
    fun chooserIntent(image: Uri, text: String, title: String): Intent =
        Intent.createChooser(baseIntent(image, text), title)

    private fun baseIntent(image: Uri, text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, image)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri("Status card", image)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
