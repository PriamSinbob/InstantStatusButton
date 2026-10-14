package com.instantstatusbutton.app

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.instantstatusbutton.app.data.Network
import com.instantstatusbutton.app.data.SettingsRepository
import com.instantstatusbutton.app.data.WidgetSettings
import com.instantstatusbutton.app.location.LocationHelper
import com.instantstatusbutton.app.share.CardGenerator
import com.instantstatusbutton.app.share.Place
import com.instantstatusbutton.app.share.ShareLauncher
import com.instantstatusbutton.app.share.StatusComposer
import com.instantstatusbutton.app.work.Notifier
import com.instantstatusbutton.app.work.PostWorker
import kotlinx.coroutines.launch

/**
 * Invisible activity started by a widget tap.
 *
 * Running in a (transparent) activity means we are a foreground app, which is what lets us
 *  - read the location with only the foreground location permission (no ACCESS_BACKGROUND_LOCATION),
 *  - write the clipboard (Android 10+ blocks that from the background), and
 *  - start the Facebook / Instagram share screens (background activity starts are restricted).
 * X and LinkedIn posts are handed to WorkManager and need no UI.
 */
class TapActivity : ComponentActivity() {

    private var started = false
    private var shareInFlight = false
    private var chooserUsed = false
    private val shareQueue = ArrayDeque<Network>()
    private lateinit var settings: WidgetSettings
    private var shareText = ""
    private var shareImage: Uri? = null

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.none { it }) {
                Notifier.toast(this, "Location permission denied, posting without location")
            }
            run()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // The clipboard can only be written while we hold window focus, so start the flow once we have it.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !started) {
            started = true
            begin()
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Facebook / Instagram: open the next one, or close.
        if (shareInFlight) {
            shareInFlight = false
            launchNextShare()
        }
    }

    private fun begin() {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        settings = SettingsRepository(this).load(id)
        if (settings.networks.isEmpty()) {
            Notifier.toast(this, "No networks selected. Open the widget settings.")
            return finish()
        }
        if (settings.addLocation && !LocationHelper.hasPermission(this)) {
            permissionRequest.launch(LocationHelper.permissions)
        } else {
            run()
        }
    }

    private fun run() {
        lifecycleScope.launch {
            var place: Place? = null
            if (settings.addLocation) {
                Notifier.toast(this@TapActivity, "Getting location…")
                place = LocationHelper.currentPlace(this@TapActivity)
                if (place == null) Notifier.toast(this@TapActivity, "Could not get location, posting without it")
            }

            val status = settings.statusText
            if (Network.X in settings.networks) {
                PostWorker.enqueue(this@TapActivity, Network.X, StatusComposer.forX(status, place))
            }
            if (Network.LINKEDIN in settings.networks) {
                PostWorker.enqueue(this@TapActivity, Network.LINKEDIN, StatusComposer.full(status, place))
            }

            val manual = listOf(Network.FACEBOOK, Network.INSTAGRAM).filter { it in settings.networks }
            if (manual.isEmpty()) {
                Notifier.toast(this@TapActivity, "Posting…")
                return@launch finish()
            }

            shareText = StatusComposer.full(status, place)
            shareImage = CardGenerator.renderToShareUri(this@TapActivity, status, place?.name, settings.color)
            ShareLauncher.copyToClipboard(this@TapActivity, shareText)
            Notifier.toast(this@TapActivity, "Text copied. Paste it into the post.")
            shareQueue.addAll(manual)
            launchNextShare()
        }
    }

    private fun launchNextShare() {
        val network = shareQueue.removeFirstOrNull() ?: return finish()
        val image = shareImage ?: return finish()
        ShareLauncher.copyToClipboard(this, shareText)

        val direct = ShareLauncher.directIntent(this, network, image, shareText)
        val intent = direct ?: run {
            if (chooserUsed) return launchNextShare() // one system share sheet is enough
            chooserUsed = true
            Notifier.toast(this, "${network.title} app not found, using the share sheet")
            ShareLauncher.chooserIntent(image, shareText, "Share status")
        }
        try {
            shareInFlight = true
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            shareInFlight = false
            Notifier.toast(this, "Could not open ${network.title}")
            launchNextShare()
        }
    }
}
