package com.instantstatusbutton.app.ui

import android.appwidget.AppWidgetManager
import android.graphics.Color as AColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.instantstatusbutton.app.auth.OAuthManager
import com.instantstatusbutton.app.data.MAX_LABEL_LENGTH
import com.instantstatusbutton.app.data.Network
import com.instantstatusbutton.app.data.Provider
import com.instantstatusbutton.app.data.SecureStore
import com.instantstatusbutton.app.data.SettingsRepository
import com.instantstatusbutton.app.data.WidgetSettings
import com.instantstatusbutton.app.location.LocationHelper
import com.instantstatusbutton.app.widget.ButtonWidgetProvider
import android.widget.Toast

private val Palette = listOf(
    "#3F51B5", "#E53935", "#D81B60", "#8E24AA", "#1E88E5", "#00ACC1",
    "#43A047", "#7CB342", "#FDD835", "#FB8C00", "#6D4C41", "#212121",
)

@Composable
fun HomeScreen(onEdit: (Int) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val repo = remember { SettingsRepository(context) }
    val ids = remember(tick) { ButtonWidgetProvider.widgetIds(context).toList() }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Instant Status Button", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section("Your widgets") {
            if (ids.isEmpty()) {
                Text(
                    "No widgets yet. Long-press the home screen, choose Widgets, and add \"Instant Status Button\". " +
                        "Each widget has its own settings.",
                )
            }
            ids.forEach { id ->
                val s = remember(tick, id) { repo.load(id) }
                Card(Modifier.fillMaxWidth().clickable { onEdit(id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ButtonPreview(s.color, s.label, 56)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.statusText, maxLines = 2)
                            Text(
                                s.networks.joinToString { it.title }.ifEmpty { "No networks selected" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = { onEdit(id) }) { Text("Edit") }
                    }
                }
            }
        }

        AccountsSection(tick)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorScreen(widgetId: Int, onSaved: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val initial = remember(widgetId) { repo.load(widgetId) }
    var status by remember { mutableStateOf(initial.statusText) }
    var label by remember { mutableStateOf(initial.label) }
    var color by remember { mutableStateOf(initial.color) }
    var hex by remember { mutableStateOf(hexOf(initial.color)) }
    var addLocation by remember { mutableStateOf(initial.addLocation) }
    var networks by remember { mutableStateOf(initial.networks) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.none { it }) {
            Toast.makeText(context, "Location permission denied. Posts will not include a location.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Widget settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section("Button") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ButtonPreview(color, label, 96)
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
                    label = { Text("Button label") },
                    supportingText = { Text("${label.length}/$MAX_LABEL_LENGTH characters") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Button color", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEach { h ->
                    val c = AColor.parseColor(h)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == color) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(1.dp, Color.LightGray), CircleShape)
                            .clickable { color = c; hex = h },
                    )
                }
            }
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    parseHex(it)?.let { c -> color = c }
                },
                label = { Text("Custom color (#RRGGBB)") },
                isError = parseHex(hex) == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Status") {
            OutlinedTextField(
                value = status,
                onValueChange = { status = it },
                label = { Text("Status text") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Location") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add location")
                    Text(
                        "Reads your position when you tap and adds the place name and a Google Maps link.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = addLocation, onCheckedChange = {
                    addLocation = it
                    if (it && !LocationHelper.hasPermission(context)) locationPermission.launch(LocationHelper.permissions)
                })
            }
        }

        Section("Post to") {
            Network.entries.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        networks = if (n in networks) networks - n else networks + n
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = n in networks, onCheckedChange = {
                        networks = if (it) networks + n else networks - n
                    })
                    Column {
                        Text(n.title)
                        Text(
                            when (n) {
                                Network.X, Network.LINKEDIN -> "Posted automatically, no screen"
                                else -> "Opens the app with an image; paste the copied text"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        AccountsSection(tick)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val c = parseHex(hex) ?: color
                repo.save(widgetId, WidgetSettings(status.trim(), label.trim(), c, addLocation, networks))
                onSaved()
            }) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AccountsSection(refresh: Int) {
    Section("Accounts") {
        Text(
            "Create your own developer apps and enter their keys here (see the README). " +
                "Keys and tokens are stored encrypted on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
        AccountCard(Provider.X, refresh)
        AccountCard(Provider.LINKEDIN, refresh)
        Text(
            "Facebook and Instagram need no login: the app opens them with your status image and copies the text.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AccountCard(provider: Provider, refresh: Int) {
    val context = LocalContext.current
    val store = remember { SecureStore.get(context) }
    var clientId by remember { mutableStateOf(store.clientId(provider)) }
    var secret by remember { mutableStateOf(store.clientSecret(provider)) }
    var connectedTick by remember { mutableIntStateOf(0) }
    val connected = remember(refresh, connectedTick) { store.isConnected(provider) }
    val account = remember(refresh, connectedTick) { store.accountName(provider) }
    val name = OAuthManager.name(provider)

    fun persist() = store.setClientCredentials(provider, clientId, secret)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (connected) "Connected" + (account?.let { " as $it" } ?: "") else "Not connected",
                color = if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it; persist() },
                label = { Text("$name client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it; persist() },
                label = { Text(if (provider == Provider.X) "$name client secret (optional)" else "$name client secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Redirect URI to register:", style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(OAuthManager.redirectUri(provider), fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    persist()
                    OAuthManager.start(context, provider)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }) { Text(if (connected) "Reconnect" else "Connect $name") }
                if (connected) {
                    OutlinedButton(onClick = { OAuthManager.disconnect(context, provider); connectedTick++ }) {
                        Text("Log out")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ButtonPreview(color: Int, label: String, size: Int) {
    val light = androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.6
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (light) Color(0xFF1B1B1F) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 6).sp,
            maxLines = 1,
        )
    }
}

private fun hexOf(color: Int) = String.format("#%06X", 0xFFFFFF and color)

private fun parseHex(s: String): Int? = try {
    if (Regex("#[0-9a-fA-F]{6}").matches(s.trim())) AColor.parseColor(s.trim()) else null
} catch (_: IllegalArgumentException) { null }
