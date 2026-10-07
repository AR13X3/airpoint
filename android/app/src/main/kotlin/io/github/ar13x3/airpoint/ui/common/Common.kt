package io.github.ar13x3.airpoint.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.ui.components.pressScale
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

class PermissionHandle(val granted: Boolean, val request: () -> Unit)

/**
 * Tracks a runtime permission, re-checking on resume (the user may have changed it in
 * Settings). Once Android stops showing the dialog, request() opens the app's settings page.
 */
@Composable
fun rememberPermission(permission: String, minSdk: Int = 0): PermissionHandle {
    if (Build.VERSION.SDK_INT < minSdk) return remember { PermissionHandle(true) {} }
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        val activity = context.findActivity()
        blocked = !ok && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    LifecycleResumeEffect(permission) {
        granted = check()
        onPauseOrDispose { }
    }
    return PermissionHandle(granted) {
        if (blocked) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            launcher.launch(permission)
        }
    }
}

/** A round 44 dp icon button with a hairline border. */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Air.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.outline, CircleShape)
            .clickable(interaction, ripple(color = c.text), role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = c.text)
    }
}

/** Back button plus an optional title, for secondary screens. */
@Composable
fun ScreenHeader(title: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(AirIcons.Back, stringResource(R.string.back), onBack)
        if (title != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = Air.colors.text,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * Body of a scrolling screen: content clips below the status bar (so it never collides with
 * the clock) but runs under the transparent gesture bar, with the bottom inset as padding.
 */
@Composable
fun Modifier.screenScroll(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    .verticalScroll(rememberScrollState())
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
