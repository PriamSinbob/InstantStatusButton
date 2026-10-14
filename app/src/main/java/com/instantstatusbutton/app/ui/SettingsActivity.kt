package com.instantstatusbutton.app.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.instantstatusbutton.app.widget.ButtonWidgetProvider

/**
 * Launcher screen and widget configuration screen.
 * Opened by the system when a widget is added (APPWIDGET_CONFIGURE, with the widget id),
 * and from the launcher (no widget id) to manage accounts and edit existing widgets.
 */
class SettingsActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val configureId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        // If the user backs out of the configuration screen, Android discards the new widget.
        if (configureId != null) {
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configureId))
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3F51B5))) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsApp(configureId)
                }
            }
        }
    }

    @Composable
    private fun SettingsApp(configureId: Int?) {
        var editing by remember { mutableStateOf(configureId) }
        val id = editing
        if (id != null) {
            WidgetEditorScreen(
                widgetId = id,
                onSaved = {
                    ButtonWidgetProvider.update(this, AppWidgetManager.getInstance(this), id)
                    if (configureId != null) {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    } else {
                        editing = null
                    }
                },
                onCancel = { if (configureId != null) finish() else editing = null },
            )
        } else {
            HomeScreen(onEdit = { editing = it })
        }
    }
}
