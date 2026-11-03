package com.hydrarsw.controlremoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrarsw.controlremoto.core.ConnectionState
import com.hydrarsw.controlremoto.core.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RemoteScreen(viewModel: AppViewModel, tvId: String) {
    val tvs by viewModel.tvs.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val supported by viewModel.supportedKeys.collectAsStateWithLifecycle()
    var showNumbers by remember { mutableStateOf(false) }
    val isReady = connection == ConnectionState.Ready

    fun enabled(key: RemoteKey) = key in supported && (isReady || key == RemoteKey.POWER)

    RemoteHeader(tvs.firstOrNull { it.id == tvId }, viewModel)
    StatusLine(connection, viewModel::retryConnection)

    @Composable
    fun Key(
        key: RemoteKey,
        label: String,
        icon: ImageVector? = null,
        modifier: Modifier = Modifier,
        size: Dp = 64.dp,
        repeat: Boolean = false,
        shape: Shape = CircleShape,
        tint: Color = Paper,
        text: String? = null,
    ) {
        PressKey(
            label = label,
            enabled = enabled(key),
            repeat = repeat,
            shape = shape,
            modifier = modifier.size(size),
            onPress = { viewModel.press(key) },
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint)
            } else {
                Text(text ?: label, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Key(RemoteKey.POWER, "Encender o apagar", Icons.Filled.PowerSettingsNew, tint = Danger)
            Key(RemoteKey.INPUT, "Cambiar entrada", Icons.AutoMirrored.Filled.Input)
            Key(RemoteKey.HOME, "Inicio", Icons.Filled.Home)
            Key(RemoteKey.MENU, "Menú", Icons.Filled.Menu)
        }

        Box(Modifier.size(260.dp).clip(CircleShape).background(Panel)) {
            Key(RemoteKey.UP, "Arriba", Icons.Filled.KeyboardArrowUp, Modifier.align(Alignment.TopCenter), 84.dp, true)
            Key(RemoteKey.DOWN, "Abajo", Icons.Filled.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter), 84.dp, true)
            Key(RemoteKey.LEFT, "Izquierda", Icons.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart), 84.dp, true)
            Key(RemoteKey.RIGHT, "Derecha", Icons.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd), 84.dp, true)
            Key(RemoteKey.OK, "Aceptar", modifier = Modifier.align(Alignment.Center), size = 92.dp, tint = Amber, text = "OK")
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rocker(
                title = "VOL",
                top = { Key(RemoteKey.VOL_UP, "Subir volumen", Icons.AutoMirrored.Filled.VolumeUp, repeat = true) },
                bottom = { Key(RemoteKey.VOL_DOWN, "Bajar volumen", Icons.AutoMirrored.Filled.VolumeDown, repeat = true) },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Key(RemoteKey.BACK, "Atrás", Icons.AutoMirrored.Filled.ArrowBack)
                Key(RemoteKey.MUTE, "Silenciar", Icons.AutoMirrored.Filled.VolumeOff)
            }
            Rocker(
                title = "CH",
                top = { Key(RemoteKey.CH_UP, "Canal siguiente", Icons.Filled.KeyboardArrowUp) },
                bottom = { Key(RemoteKey.CH_DOWN, "Canal anterior", Icons.Filled.KeyboardArrowDown) },
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Key(RemoteKey.REWIND, "Retroceder", Icons.Filled.FastRewind)
            Key(RemoteKey.PLAY_PAUSE, "Reproducir o pausar", Icons.Filled.PlayArrow)
            Key(RemoteKey.STOP, "Detener", Icons.Filled.Stop)
            Key(RemoteKey.FORWARD, "Adelantar", Icons.Filled.FastForward)
        }

        TextButton(onClick = { showNumbers = !showNumbers }) {
            Icon(Icons.Filled.Dialpad, contentDescription = null)
            Text(if (showNumbers) "  Ocultar números" else "  Mostrar números")
        }

        if (showNumbers) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { number ->
                        Key(RemoteKey.valueOf("NUM_$number"), "Número $number", text = "$number")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: ConnectionState, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (text, color) = when (state) {
            ConnectionState.Connecting -> "Conectando…" to Muted
            is ConnectionState.WaitingForTv -> state.message to Amber
            ConnectionState.Ready -> "Conectado" to Muted
            is ConnectionState.Failed -> state.message to Danger
        }
        Text(text, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodySmall)
        if (state is ConnectionState.Failed) TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun Rocker(title: String, top: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Column(
        Modifier.width(76.dp).background(Panel, RoundedCornerShape(38.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Text(title, Modifier.padding(vertical = 2.dp), color = Muted, style = MaterialTheme.typography.labelMedium)
        bottom()
    }
}

@Composable
private fun PressKey(
    label: String,
    enabled: Boolean,
    repeat: Boolean,
    shape: Shape,
    modifier: Modifier,
    onPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) PanelHigh else Color.Transparent)
            .alpha(if (enabled) 1f else 0.3f)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (enabled) {
                    onClick {
                        press()
                        true
                    }
                }
            }
            .pointerInput(enabled, repeat) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        press()
                        val repeater = if (repeat) {
                            scope.launch {
                                delay(REPEAT_DELAY_MS)
                                while (true) {
                                    press()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        } else {
                            null
                        }
                        tryAwaitRelease()
                        repeater?.cancel()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 170L
