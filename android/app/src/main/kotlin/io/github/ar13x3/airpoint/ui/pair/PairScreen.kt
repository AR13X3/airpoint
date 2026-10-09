package io.github.ar13x3.airpoint.ui.pair

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.core.DiscoveredPc
import io.github.ar13x3.airpoint.core.PairedPc
import io.github.ar13x3.airpoint.net.DEFAULT_PORT
import io.github.ar13x3.airpoint.ui.common.screenScroll
import io.github.ar13x3.airpoint.ui.common.ScreenHeader
import io.github.ar13x3.airpoint.ui.components.AirButton
import io.github.ar13x3.airpoint.ui.components.AirCard
import io.github.ar13x3.airpoint.ui.components.ButtonStyle
import io.github.ar13x3.airpoint.ui.components.Chip
import io.github.ar13x3.airpoint.ui.components.IconTile
import io.github.ar13x3.airpoint.ui.components.ListRow
import io.github.ar13x3.airpoint.ui.components.PinField
import io.github.ar13x3.airpoint.ui.components.PinState
import io.github.ar13x3.airpoint.ui.components.Radar
import io.github.ar13x3.airpoint.ui.components.enterStagger
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.Radii
import io.github.ar13x3.airpoint.ui.theme.riseTransition
import kotlinx.coroutines.delay

@Composable
fun PairScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    val app = LocalContext.current.applicationContext as AirpointApp
    val vm: PairViewModel = viewModel { PairViewModel(app) }
    val found by vm.found.collectAsStateWithLifecycle()
    val paired by vm.paired.collectAsStateWithLifecycle()

    LaunchedEffect(vm) { vm.done.collect { onPaired() } }
    BackHandler(enabled = vm.step is PairStep.Pin) { vm.back() }

    Box(Modifier.fillMaxSize().background(Air.colors.background)) {
        AnimatedContent(
            vm.step,
            transitionSpec = {
                // Shared X axis: forward into the PIN, back out of it.
                val forward = targetState is PairStep.Pin
                (slideInHorizontally(Motion.offsetCalm) { if (forward) it / 4 else -it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally(Motion.offsetCalm) { if (forward) -it / 8 else it / 8 } + fadeOut())
            },
            contentKey = { it::class },
            label = "step",
        ) { step ->
            when (step) {
                PairStep.Choose -> ChooseStep(found, paired, onBack, vm::choose, vm::use, vm::manual)
                is PairStep.Pin -> PinStep(step, vm)
            }
        }
    }
}

@Composable
private fun ChooseStep(
    found: List<DiscoveredPc>,
    paired: List<PairedPc>,
    onBack: () -> Unit,
    onChoose: (DiscoveredPc) -> Unit,
    onUse: (PairedPc) -> Unit,
    onManual: (String, Int) -> Unit,
) {
    val c = Air.colors
    val offline = paired.filter { p -> found.none { it.id == p.id } }
    Column(
        Modifier
            .fillMaxSize()
            .screenScroll()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(stringResource(R.string.pair_title), onBack)
        Spacer(Modifier.height(8.dp))
        Radar(found, Modifier.fillMaxWidth(0.66f).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        AnimatedContent(found.size, transitionSpec = { riseTransition() }, label = "caption", modifier = Modifier.align(Alignment.CenterHorizontally)) { n ->
            Text(
                if (n == 0) stringResource(R.string.pair_scanning) else stringResource(R.string.pair_found, n),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))

        if (found.isNotEmpty() || offline.isNotEmpty()) {
            AirCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                found.forEach { pc ->
                    key(pc.id) {
                        val known = paired.any { it.id == pc.id }
                        ListRow(
                            pc.name, Modifier.enterStagger(0),
                            subtitle = pc.host, icon = AirIcons.Monitor,
                            iconTint = c.live, iconBackground = c.liveSoft,
                            onClick = { onChoose(pc) },
                        ) {
                            if (known) Chip(stringResource(R.string.pair_paired_badge), c.accentText, c.accentSoft)
                            Spacer(Modifier.width(6.dp))
                            Icon(AirIcons.ChevronRight, null, Modifier.size(20.dp), tint = c.textTertiary)
                        }
                    }
                }
                offline.forEach { pc ->
                    key(pc.id) {
                        ListRow(
                            pc.name, Modifier.graphicsLayer { alpha = 0.7f },
                            subtitle = stringResource(R.string.pair_offline), icon = AirIcons.Monitor,
                            iconTint = c.textTertiary, onClick = { onUse(pc) },
                        ) {
                            Chip(stringResource(R.string.pair_saved), c.textSecondary, c.surfaceRaised)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        HelpCard(onManual)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpCard(onManual: (String, Int) -> Unit) {
    val c = Air.colors
    var open by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.calm(), label = "chevron")
    AirCard(Modifier.fillMaxWidth(), padding = 8.dp, onClick = { open = !open }) {
        ListRow(stringResource(R.string.pair_help_title), icon = AirIcons.Info, iconTint = c.textSecondary) {
            Icon(AirIcons.ChevronDown, null, Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }, tint = c.textTertiary)
        }
        AnimatedVisibility(open, enter = expandVertically(Motion.calm()) + fadeIn(), exit = shrinkVertically(Motion.calm()) + fadeOut()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Tip(AirIcons.Monitor, stringResource(R.string.pair_help_running))
                Tip(AirIcons.Wifi, stringResource(R.string.pair_help_wifi))
                Tip(AirIcons.Shield, stringResource(R.string.pair_help_firewall))
                Spacer(Modifier.height(12.dp))
                AnimatedContent(manual, label = "manual") { showFields ->
                    if (!showFields) {
                        AirButton(stringResource(R.string.pair_manual), { manual = true }, style = ButtonStyle.Neutral, icon = AirIcons.Keyboard, compact = true)
                    } else {
                        ManualEntry(onManual)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    val c = Air.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.accentText)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Composable
private fun ManualEntry(onManual: (String, Int) -> Unit) {
    val c = Air.colors
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(DEFAULT_PORT.toString()) }
    val valid = host.isNotBlank() && port.toIntOrNull() in 1..65535
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.outlineStrong,
        focusedLabelColor = c.accentText, cursorColor = c.accent,
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                host, { host = it.trim() }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.pair_manual_host)) },
                placeholder = { Text("192.168.1.20") },
                singleLine = true, shape = Radii.md, colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(96.dp),
                label = { Text(stringResource(R.string.pair_manual_port)) },
                singleLine = true, shape = Radii.md, colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(12.dp))
        AirButton(
            stringResource(R.string.pair_manual_continue), { onManual(host, port.toInt()) },
            enabled = valid, compact = true, trailingIcon = AirIcons.ArrowRight,
        )
    }
}

@Composable
private fun PinStep(step: PairStep.Pin, vm: PairViewModel) {
    val c = Air.colors
    val success = vm.pinState == PinState.Success
    Column(
        Modifier
            .fillMaxSize()
            .screenScroll()
            .imePadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(null, vm::back)
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            success,
            transitionSpec = { (scaleIn(Motion.lively(), initialScale = 0.4f) + fadeIn()).togetherWith(fadeOut()) },
            label = "glyph",
        ) { done ->
            if (done) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(c.live), contentAlignment = Alignment.Center) {
                    Icon(AirIcons.Check, null, Modifier.size(40.dp), tint = if (c.isDark) c.background else Color.White)
                }
            } else {
                IconTile(AirIcons.Monitor, tint = c.accentText, background = c.accentSoft, size = 84.dp)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(success, transitionSpec = { riseTransition() }, label = "title") { done ->
            Text(
                if (done) stringResource(R.string.pin_success, step.endpoint.name) else stringResource(R.string.pin_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, color = c.text, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(success, transitionSpec = { riseTransition() }, label = "body") { done ->
            Text(
                if (done) stringResource(R.string.pin_success_body) else stringResource(R.string.pin_body, step.endpoint.name),
                style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        PinField(vm.pin, vm::onPin, vm.pinState)
        Spacer(Modifier.height(20.dp))
        val message = vm.message
        AnimatedContent(message, transitionSpec = { riseTransition() }, label = "message") { m ->
            val text = when (m) {
                null -> if (vm.pinState == PinState.Checking) stringResource(R.string.pin_checking) else ""
                PinMessage.Wrong -> stringResource(R.string.pin_wrong)
                is PinMessage.Locked -> stringResource(R.string.pin_locked, lockCountdown(m.until))
                PinMessage.Unreachable -> stringResource(R.string.pin_unreachable, step.endpoint.name)
                PinMessage.Incompatible -> stringResource(R.string.pin_incompatible, step.endpoint.name)
            }
            Text(
                text,
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                style = MaterialTheme.typography.bodyMedium,
                color = if (m == null) c.textSecondary else c.danger,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun lockCountdown(until: Long): Int {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(until) {
        while (now < until) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    return ((until - now + 999) / 1000).toInt().coerceAtLeast(0)
}
