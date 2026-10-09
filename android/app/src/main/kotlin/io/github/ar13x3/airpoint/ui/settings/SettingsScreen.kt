package io.github.ar13x3.airpoint.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import io.github.ar13x3.airpoint.ui.theme.Radii
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.BuildConfig
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.core.PairedPc
import io.github.ar13x3.airpoint.core.ThemeMode
import io.github.ar13x3.airpoint.core.UserSettings
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.common.screenScroll
import io.github.ar13x3.airpoint.ui.common.ScreenHeader
import io.github.ar13x3.airpoint.ui.components.AirCard
import io.github.ar13x3.airpoint.ui.components.ListRow
import io.github.ar13x3.airpoint.ui.components.SectionLabel
import io.github.ar13x3.airpoint.ui.components.Segmented
import io.github.ar13x3.airpoint.ui.components.enterStagger
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AirpointApp
    val c = Air.colors
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(UserSettings())
    val paired by app.settings.pairedPcs.collectAsStateWithLifecycle(emptyList())
    var forgetting by remember { mutableStateOf<PairedPc?>(null) }
    var licenses by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .screenScroll()
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.settings_pointer), Modifier.enterStagger(0))
        AirCard(Modifier.fillMaxWidth().enterStagger(0), padding = 6.dp) {
            SwitchRow(
                stringResource(R.string.settings_edge_scroll), stringResource(R.string.settings_edge_scroll_body),
                settings.edgeScroll,
            ) { v -> scope.launch { app.settings.setEdgeScroll(v) } }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.outline)
            SwitchRow(
                stringResource(R.string.settings_double_press), stringResource(R.string.settings_double_press_body),
                settings.doublePressCenters,
            ) { v -> scope.launch { app.settings.setDoublePressCenters(v) } }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_appearance), Modifier.enterStagger(1))
        Segmented(
            ThemeMode.entries, settings.theme,
            onSelect = { mode -> scope.launch { app.settings.setTheme(mode) } },
            label = {
                when (it) {
                    ThemeMode.System -> stringResource(R.string.theme_system)
                    ThemeMode.Light -> stringResource(R.string.theme_light)
                    ThemeMode.Dark -> stringResource(R.string.theme_dark)
                }
            },
            icon = {
                when (it) {
                    ThemeMode.System -> AirIcons.Auto
                    ThemeMode.Light -> AirIcons.Sun
                    ThemeMode.Dark -> AirIcons.Moon
                }
            },
            modifier = Modifier.enterStagger(1),
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_computers), Modifier.enterStagger(2))
        AirCard(Modifier.fillMaxWidth().enterStagger(2), padding = 8.dp) {
            if (paired.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_computers),
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            paired.sortedByDescending { it.lastUsed }.forEach { pc ->
                ListRow(pc.name, subtitle = pc.host, icon = AirIcons.Monitor) {
                    TextButton({ forgetting = pc }) {
                        Text(stringResource(R.string.settings_forget), color = c.danger, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_about), Modifier.enterStagger(3))
        AirCard(Modifier.fillMaxWidth().enterStagger(3), padding = 8.dp) {
            ListRow(
                stringResource(R.string.settings_source), subtitle = stringResource(R.string.settings_source_body),
                icon = AirIcons.Code,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.REPO_URL.toUri())) },
            ) { Icon(AirIcons.ChevronRight, null, Modifier.size(20.dp), tint = c.textTertiary) }
            ListRow(stringResource(R.string.settings_licenses), icon = AirIcons.Info, onClick = { licenses = true }) {
                Icon(AirIcons.ChevronRight, null, Modifier.size(20.dp), tint = c.textTertiary)
            }
            ListRow(
                stringResource(R.string.settings_spen_source),
                subtitle = stringResource(if (app.controller.isSimulated) R.string.settings_spen_simulated else R.string.settings_spen_real),
                icon = AirIcons.Stylus,
            )
        }

        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LoopMark(Modifier.size(40.dp), color = c.textTertiary, dotColor = c.accent)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            Text(stringResource(R.string.settings_made_by), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        Spacer(Modifier.height(32.dp))
    }

    forgetting?.let { pc ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.settings_forget_title, pc.name)) },
            text = { Text(stringResource(R.string.settings_forget_body)) },
            confirmButton = {
                TextButton({ scope.launch { app.settings.forget(pc.id) }; forgetting = null }) {
                    Text(stringResource(R.string.settings_forget), color = c.danger)
                }
            },
            dismissButton = { TextButton({ forgetting = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = c.surface,
        )
    }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Text(
                    stringResource(R.string.licenses_body),
                    Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton({ licenses = false }) { Text(stringResource(R.string.done)) } },
            containerColor = c.surface,
        )
    }
}

/** A setting with a switch. The whole row toggles it, and TalkBack reads it as one switch. */
@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Air.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.md)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.surfaceSunken, uncheckedBorderColor = c.outlineStrong, uncheckedThumbColor = c.textTertiary,
            ),
        )
    }
}
