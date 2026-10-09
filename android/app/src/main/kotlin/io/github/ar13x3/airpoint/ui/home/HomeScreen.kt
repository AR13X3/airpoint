package io.github.ar13x3.airpoint.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.core.LinkPhase
import io.github.ar13x3.airpoint.core.LinkProblem
import io.github.ar13x3.airpoint.core.PcEndpoint
import io.github.ar13x3.airpoint.core.SessionState
import io.github.ar13x3.airpoint.core.SpenPhase
import io.github.ar13x3.airpoint.core.SpenProblem
import io.github.ar13x3.airpoint.core.UserSettings
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.common.screenScroll
import io.github.ar13x3.airpoint.ui.common.RoundIconButton
import io.github.ar13x3.airpoint.ui.common.rememberPermission
import io.github.ar13x3.airpoint.ui.components.AirButton
import io.github.ar13x3.airpoint.ui.components.AirCard
import io.github.ar13x3.airpoint.ui.components.ButtonStyle
import io.github.ar13x3.airpoint.ui.components.Chip
import io.github.ar13x3.airpoint.ui.components.IconTile
import io.github.ar13x3.airpoint.ui.components.LinkStage
import io.github.ar13x3.airpoint.ui.components.ListRow
import io.github.ar13x3.airpoint.ui.components.LivePad
import io.github.ar13x3.airpoint.ui.components.SectionLabel
import io.github.ar13x3.airpoint.ui.components.SessionButton
import io.github.ar13x3.airpoint.ui.components.SessionButtonState
import io.github.ar13x3.airpoint.ui.components.TuningSlider
import io.github.ar13x3.airpoint.ui.components.enterStagger
import io.github.ar13x3.airpoint.ui.components.toStage
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.riseTransition
import kotlinx.coroutines.delay

private enum class HeadlineAction { PairAgain, AllowNearby, ReconnectSpen, RetryPc }

private data class Headline(val title: String, val body: String, val action: HeadlineAction? = null, val warn: Boolean = false)

@Composable
fun HomeScreen(
    onOpenPair: (startAfter: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    startRequested: Boolean,
    onStartHandled: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AirpointApp
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val activity = LocalActivity.current
    val c = Air.colors

    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val nearby = rememberPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("InlinedApi") // gated: rememberPermission ignores it below API 33
    val notifications = rememberPermission(Manifest.permission.POST_NOTIFICATIONS, minSdk = 33)

    val pc: PcEndpoint? = session.link.pc
        ?: paired.firstOrNull { it.id == settings.lastPcId }?.endpoint
        ?: paired.maxByOrNull { it.lastUsed }?.endpoint

    // Set when Start had to ask for Nearby devices first, so granting it carries straight on.
    var startAfterGrant by rememberSaveable { mutableStateOf(false) }

    fun start() {
        when {
            paired.isEmpty() -> onOpenPair(true)
            !nearby.granted -> {
                startAfterGrant = true
                nearby.request()
            }
            activity != null -> {
                startAfterGrant = false
                if (!notifications.granted) notifications.request()
                vm.start(activity)
            }
        }
    }

    LaunchedEffect(nearby.granted) {
        if (nearby.granted && startAfterGrant && !session.active) start()
    }
    LaunchedEffect(startRequested) {
        if (startRequested) {
            onStartHandled()
            start()
        }
    }
    // While pointing, Back sends Airpoint to the background instead of closing it (closing
    // the Activity would release the S Pen, which is bound through it).
    BackHandler(enabled = session.active) { activity?.moveTaskToBack(true) }

    val headline = headlineFor(session, pc?.name, paired.isEmpty(), settings)
    val buttonState = when {
        !session.active -> SessionButtonState.Start
        session.spen.phase == SpenPhase.Connecting || session.link.phase == LinkPhase.Connecting -> SessionButtonState.Connecting
        else -> SessionButtonState.Stop
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .screenScroll()
            .padding(horizontal = 20.dp),
    ) {
        TopBar(onOpenSettings, Modifier.enterStagger(0))
        Spacer(Modifier.height(12.dp))

        AirCard(Modifier.fillMaxWidth().enterStagger(1), padding = 18.dp) {
            LinkStage(
                session.toStage(), vm.pad,
                penLabel = stringResource(R.string.s_pen),
                phoneLabel = stringResource(R.string.phone),
                pcLabel = pc?.name ?: stringResource(R.string.computer),
            )
            Spacer(Modifier.height(20.dp))
            StatusHeadline(headline) { action ->
                when (action) {
                    HeadlineAction.PairAgain -> onOpenPair(false)
                    HeadlineAction.AllowNearby -> nearby.request()
                    HeadlineAction.ReconnectSpen -> if (nearby.granted) activity?.let(vm::retrySpen) else nearby.request()
                    HeadlineAction.RetryPc -> vm.retryPc()
                }
            }
            if (vm.isSimulated) {
                Spacer(Modifier.height(14.dp))
                Chip(stringResource(R.string.simulated_badge), c.accentText, c.accentSoft)
            }
        }

        Spacer(Modifier.height(16.dp))
        SessionButton(buttonState, { if (session.active) vm.stop() else start() }, Modifier.enterStagger(2))
        Spacer(Modifier.height(16.dp))

        PcCard(pc, connected = session.link.phase == LinkPhase.Connected, onClick = { onOpenPair(false) }, Modifier.enterStagger(3))

        AnimatedVisibility(
            session.active,
            enter = expandVertically(Motion.calm()) + fadeIn(),
            exit = shrinkVertically(Motion.calm()) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                LivePad(vm.pad, gain = 1.6f * vm.sensitivity / 25f)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.tuning), Modifier.enterStagger(4))
        AirCard(Modifier.fillMaxWidth().enterStagger(4)) {
            TuningSlider(
                stringResource(R.string.sensitivity), stringResource(R.string.sensitivity_hint),
                vm.sensitivity, vm::onSensitivity,
            )
            Spacer(Modifier.height(22.dp))
            TuningSlider(
                stringResource(R.string.smoothness), stringResource(R.string.smoothness_hint),
                vm.smoothness, vm::onSmoothness,
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.outline)
            Spacer(Modifier.height(6.dp))
            val canCenter = session.link.phase == LinkPhase.Connected
            ListRow(
                stringResource(R.string.action_center),
                icon = AirIcons.Target,
                iconTint = if (canCenter) c.accentText else c.textTertiary,
                iconBackground = if (canCenter) c.accentSoft else c.tile,
                onClick = if (canCenter) ({ vm.center() }) else null,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = Air.colors
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LoopMark(Modifier.size(34.dp), color = c.accent)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.app_name),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = c.text,
        )
        RoundIconButton(AirIcons.Tune, stringResource(R.string.settings), onOpenSettings)
    }
}

@Composable
private fun StatusHeadline(h: Headline, onAction: (HeadlineAction) -> Unit) {
    val c = Air.colors
    val titleColor by animateColorAsState(if (h.warn) c.warn else c.text, Motion.calm(), label = "title")
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        AnimatedContent(h.title, transitionSpec = { riseTransition() }, label = "title") { title ->
            Text(title, style = MaterialTheme.typography.headlineMedium, color = titleColor)
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(h.body, transitionSpec = { riseTransition() }, label = "body") { body ->
            Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
        }
        AnimatedVisibility(h.action != null, enter = expandVertically(Motion.calm()) + fadeIn(), exit = shrinkVertically(Motion.calm()) + fadeOut()) {
            val action = h.action ?: return@AnimatedVisibility
            Column {
                Spacer(Modifier.height(16.dp))
                AirButton(
                    text = when (action) {
                        HeadlineAction.PairAgain -> stringResource(R.string.action_pair_again)
                        HeadlineAction.AllowNearby -> stringResource(R.string.action_allow)
                        HeadlineAction.ReconnectSpen -> stringResource(R.string.action_reconnect_spen)
                        HeadlineAction.RetryPc -> stringResource(R.string.try_again)
                    },
                    onClick = { onAction(action) },
                    style = ButtonStyle.Tonal,
                    compact = true,
                    icon = when (action) {
                        HeadlineAction.PairAgain -> AirIcons.Monitor
                        HeadlineAction.AllowNearby -> AirIcons.Bluetooth
                        else -> AirIcons.Refresh
                    },
                )
            }
        }
    }
}

@Composable
private fun PcCard(pc: PcEndpoint?, connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Air.colors
    val tileBg by animateColorAsState(if (connected) c.liveSoft else c.tile, Motion.calm(), label = "tile")
    val tileTint by animateColorAsState(if (connected) c.live else c.text, Motion.calm(), label = "tint")
    AirCard(modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (pc == null) AirIcons.Plus else AirIcons.Monitor, tint = tileTint, background = tileBg, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pc?.name ?: stringResource(R.string.pc_none_title),
                    style = MaterialTheme.typography.titleMedium, color = c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        pc == null -> stringResource(R.string.pc_none_body)
                        connected -> stringResource(R.string.pc_status_connected, pc.host)
                        else -> stringResource(R.string.pc_status_paired, pc.host)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (pc == null) R.string.action_pair_short else R.string.action_change), style = MaterialTheme.typography.titleSmall, color = c.accentText)
                Icon(AirIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.accentText)
            }
        }
    }
}

@Composable
private fun headlineFor(s: SessionState, pcName: String?, noPc: Boolean, settings: UserSettings): Headline {
    val pc = pcName ?: stringResource(R.string.computer)
    if (!s.active) {
        return if (noPc) {
            Headline(stringResource(R.string.home_setup_title), stringResource(R.string.home_setup_body))
        } else {
            Headline(stringResource(R.string.home_ready_title), stringResource(R.string.home_ready_body, pc))
        }
    }
    // Problems first, most actionable first.
    if (s.spen.phase == SpenPhase.Failed) {
        return when (s.spen.problem) {
            SpenProblem.PermissionDenied -> Headline(stringResource(R.string.home_spen_permission_title), stringResource(R.string.home_spen_permission_body), HeadlineAction.AllowNearby, true)
            SpenProblem.Unsupported -> Headline(stringResource(R.string.home_spen_unsupported_title), stringResource(R.string.home_spen_unsupported_body), null, true)
            SpenProblem.Lost -> Headline(stringResource(R.string.home_spen_lost_title), stringResource(R.string.home_spen_lost_body), HeadlineAction.ReconnectSpen, true)
            else -> Headline(stringResource(R.string.home_spen_failed_title), stringResource(R.string.home_spen_failed_body), HeadlineAction.ReconnectSpen, true)
        }
    }
    if (s.link.phase == LinkPhase.Failed) {
        return when (s.link.problem) {
            LinkProblem.Incompatible -> Headline(stringResource(R.string.home_incompatible_title), stringResource(R.string.home_incompatible_body, pc), HeadlineAction.RetryPc, true)
            LinkProblem.NotPaired -> if (noPc) {
                Headline(stringResource(R.string.home_setup_title), stringResource(R.string.home_setup_body))
            } else {
                Headline(stringResource(R.string.home_not_paired_title, pc), stringResource(R.string.home_not_paired_body), HeadlineAction.PairAgain, true)
            }
            else -> Headline(stringResource(R.string.home_reconnecting_title, pc), stringResource(R.string.home_reconnecting_now), HeadlineAction.RetryPc, true)
        }
    }
    if (s.link.phase == LinkPhase.Reconnecting) {
        val seconds = rememberCountdown(s.link.retryAt)
        val body = if (seconds > 0) stringResource(R.string.home_reconnecting_body, seconds) else stringResource(R.string.home_reconnecting_now)
        return Headline(stringResource(R.string.home_reconnecting_title, pc), body, HeadlineAction.RetryPc, true)
    }
    val penReady = s.spen.phase == SpenPhase.Ready
    val pcReady = s.link.phase == LinkPhase.Connected
    return when {
        penReady && pcReady -> Headline(
            stringResource(R.string.home_pointing_title, pc),
            stringResource(if (settings.doublePressCenters) R.string.home_pointing_body else R.string.home_pointing_body_no_center),
        )
        penReady -> Headline(stringResource(R.string.home_reaching_title, pc), stringResource(R.string.home_reaching_body))
        pcReady -> Headline(stringResource(R.string.home_waking_title), stringResource(R.string.home_waking_body, pc))
        else -> Headline(stringResource(R.string.home_connecting_title), stringResource(R.string.home_connecting_body, pc))
    }
}

/** Whole seconds until [deadline] (elapsedRealtime), ticking down. */
@Composable
private fun rememberCountdown(deadline: Long): Int {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (now >= deadline) break
            delay(250)
        }
    }
    return ((deadline - now + 999) / 1000).toInt().coerceAtLeast(0)
}
