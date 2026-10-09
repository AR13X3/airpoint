package io.github.ar13x3.airpoint.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ar13x3.airpoint.BuildConfig
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.ui.brand.AnimatedLoopMark
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.common.rememberPermission
import io.github.ar13x3.airpoint.ui.components.AirButton
import io.github.ar13x3.airpoint.ui.components.AirCard
import io.github.ar13x3.airpoint.ui.components.ButtonStyle
import io.github.ar13x3.airpoint.ui.components.Chip
import io.github.ar13x3.airpoint.ui.components.IconTile
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.Numeric
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PAGES = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = Air.colors
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            // Read inside graphicsLayer (draw phase) so paging doesn't recompose every frame.
            fun offset() = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer { alpha = 1f - (offset().absoluteValue * 0.7f).coerceAtMost(1f) },
                verticalArrangement = Arrangement.Center,
            ) {
                // The art moves faster than the text, for depth.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .graphicsLayer { translationX = offset() * size.width * 0.45f },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> AnimatedLoopMark(Modifier.size(200.dp), replayKey = pager.settledPage == 0)
                        1 -> DesktopArt(visible = pager.settledPage == 1)
                        else -> PermissionsArt()
                    }
                }
                Spacer(Modifier.height(28.dp))
                when (page) {
                    0 -> PageText(stringResource(R.string.onb_welcome_title), stringResource(R.string.onb_welcome_body), big = true)
                    1 -> {
                        PageText(stringResource(R.string.onb_pc_title), stringResource(R.string.onb_pc_body))
                        Spacer(Modifier.height(20.dp))
                        ShareLinkButton()
                    }
                    else -> PermissionsPage()
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageDots(pager, Modifier.weight(1f))
            val last = pager.currentPage == PAGES - 1
            AnimatedContent(last, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut()) }, label = "cta") { isLast ->
                if (isLast) {
                    AirButton(stringResource(R.string.get_started), onDone, trailingIcon = AirIcons.ArrowRight)
                } else {
                    AirButton(stringResource(R.string.next), { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, trailingIcon = AirIcons.ArrowRight)
                }
            }
        }
    }
}

@Composable
private fun PageText(title: String, body: String, big: Boolean = false) {
    val c = Air.colors
    Text(
        title,
        Modifier.semantics { heading() },
        style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
        color = c.text,
    )
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    val c = Air.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            val active = pager.currentPage == i
            val width by animateDpAsState(if (active) 26.dp else 8.dp, Motion.lively(), label = "w$i")
            val color by animateColorAsState(if (active) c.accent else c.outlineStrong, Motion.calm(), label = "c$i")
            Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(color))
        }
    }
}

/** A tiny desktop whose tray icon pops a PIN bubble when the page settles. */
@Composable
private fun DesktopArt(visible: Boolean) {
    val c = Air.colors
    Box(Modifier.size(width = 280.dp, height = 200.dp)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = 280.dp, height = 176.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.surfaceSunken)
                .border(1.5.dp, c.outlineStrong, RoundedCornerShape(18.dp)),
        ) {
            // Taskbar with the Airpoint tray icon
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(c.surfaceRaised)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(c.outlineStrong))
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                LoopMark(Modifier.size(18.dp), color = c.text, dotColor = c.accent)
                Spacer(Modifier.width(10.dp))
                Text("9:41", style = Numeric.copy(fontSize = 11.sp), color = c.textSecondary)
            }
            AnimatedVisibility(
                visible,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 40.dp),
                enter = scaleIn(Motion.lively(), initialScale = 0.3f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 1f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface)
                        .border(1.dp, c.outline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("PAIRING PIN", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text("482 913", style = Numeric.copy(fontSize = 24.sp), color = c.accentText)
                }
            }
        }
        // Stand
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .size(width = 96.dp, height = 8.dp)
                .clip(CircleShape)
                .background(c.outlineStrong),
        )
    }
}

@Composable
private fun PermissionsArt() {
    val c = Air.colors
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(170.dp).clip(CircleShape).background(c.accentSoft))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(AirIcons.Bluetooth, tint = c.accentText, background = c.surface, size = 72.dp)
            IconTile(AirIcons.Bell, tint = c.accentText, background = c.surface, size = 72.dp)
        }
    }
}

@Composable
private fun ShareLinkButton() {
    val context = LocalContext.current
    val text = stringResource(R.string.onb_pc_share_text, BuildConfig.REPO_URL)
    AirButton(
        stringResource(R.string.onb_pc_share),
        {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        },
        style = ButtonStyle.Tonal,
        icon = AirIcons.Share,
        compact = true,
    )
}

@Composable
private fun PermissionsPage() {
    val nearby = rememberPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("InlinedApi") // gated: rememberPermission ignores it below API 33
    val notifications = rememberPermission(Manifest.permission.POST_NOTIFICATIONS, minSdk = 33)
    PageText(stringResource(R.string.onb_perm_title), stringResource(R.string.onb_perm_body))
    Spacer(Modifier.height(20.dp))
    AirCard(Modifier.fillMaxWidth(), padding = 6.dp) {
        PermissionRow(AirIcons.Bluetooth, stringResource(R.string.perm_nearby_title), stringResource(R.string.perm_nearby_body), nearby.granted, nearby.request)
        PermissionRow(AirIcons.Bell, stringResource(R.string.perm_notifications_title), stringResource(R.string.perm_notifications_body), notifications.granted, notifications.request)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String, granted: Boolean, request: () -> Unit) {
    val c = Air.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint = if (granted) c.live else c.accentText, background = if (granted) c.liveSoft else c.accentSoft)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            granted,
            transitionSpec = { (scaleIn(Motion.lively(), initialScale = 0.6f) + fadeIn()).togetherWith(scaleOut(targetScale = 0.8f) + fadeOut()) },
            label = "perm",
        ) { ok ->
            if (ok) {
                Chip(stringResource(R.string.perm_allowed), c.live, c.liveSoft) {
                    Icon(AirIcons.Check, null, Modifier.size(14.dp), tint = c.live)
                }
            } else {
                AirButton(stringResource(R.string.perm_allow), request, style = ButtonStyle.Primary, compact = true)
            }
        }
    }
}
