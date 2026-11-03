package com.hydrarsw.controlremoto.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrarsw.controlremoto.core.SavedTv
import com.hydrarsw.controlremoto.core.TvKind
import com.hydrarsw.controlremoto.discovery.FoundTv

val Graphite = Color(0xFF15171A)
val Panel = Color(0xFF23262B)
val PanelHigh = Color(0xFF30343A)
val Paper = Color(0xFFF4F1EA)
val Muted = Color(0xFFA7ABB2)
val Amber = Color(0xFFFFB547)
val Danger = Color(0xFFFF6B5E)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Graphite,
    background = Graphite,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    error = Danger,
)

@Composable
fun ControlRemotoApp(viewModel: AppViewModel) {
    MaterialTheme(colorScheme = Colors) {
        val screen by viewModel.screen.collectAsStateWithLifecycle()
        Surface(color = Graphite, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                UpdateBanner(viewModel)
                BackHandler(enabled = screen != Screen.Home) { viewModel.back() }
                when (val current = screen) {
                    Screen.Home -> HomeScreen(viewModel)
                    Screen.IrBrands -> IrBrandsScreen(viewModel)
                    is Screen.Pairing -> PairingScreen(viewModel, current.found)
                    is Screen.Remote -> RemoteScreen(viewModel, current.tvId)
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(viewModel: AppViewModel) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val state = update) {
        UpdateState.Idle -> Unit
        is UpdateState.Installing -> Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
            Text("Actualizando la app…", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.NeedsPermission -> BannerRow(
            text = "Hay una versión nueva. Autoriza a la app a instalar actualizaciones.",
            action = "Autorizar",
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            viewModel.checkForUpdate()
        }
        is UpdateState.Failed -> BannerRow("No se pudo actualizar.", "Reintentar") {
            viewModel.startUpdate(state.release)
        }
    }
}

@Composable
private fun BannerRow(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        trailing()
    }
}

@Composable
private fun HomeScreen(viewModel: AppViewModel) {
    val tvs by viewModel.tvs.collectAsStateWithLifecycle()
    val found by viewModel.found.collectAsStateWithLifecycle()
    var addingManually by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.onForeground() }
    val newlyFound = found.filter { candidate ->
        tvs.none { it.host == candidate.host && it.kind == candidate.kind }
    }

    ScreenTitle("Control Remoto") {
        IconButton(onClick = viewModel::refreshDiscovery) {
            Icon(Icons.Filled.Refresh, contentDescription = "Buscar de nuevo")
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tvs.isNotEmpty()) {
            item { SectionLabel("Tus televisiones") }
            items(tvs, key = { it.id }) { tv ->
                TvRow(
                    icon = if (tv.kind == TvKind.INFRARED) Icons.Filled.SettingsRemote else Icons.Filled.Tv,
                    title = tv.name,
                    subtitle = tv.kind.label,
                    onClick = { viewModel.openRemote(tv) },
                    trailing = {
                        IconButton(onClick = { viewModel.removeTv(tv) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${tv.name}", tint = Muted)
                        }
                    },
                )
            }
        }
        item { SectionLabel("Encontradas en tu Wi-Fi") }
        if (newlyFound.isEmpty()) {
            item {
                Text(
                    "Buscando… La TV y el celular deben estar en la misma red Wi-Fi.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(newlyFound, key = { "${it.host}-${it.kind}" }) { candidate ->
            TvRow(
                icon = Icons.Filled.Wifi,
                title = candidate.name,
                subtitle = "${candidate.kind.label} · ${candidate.host}",
                onClick = { viewModel.addFound(candidate) },
            )
        }
        item { SectionLabel("Agregar manualmente") }
        item {
            TvRow(
                icon = Icons.Filled.Add,
                title = "Agregar por dirección IP",
                subtitle = "Si tu TV con Wi-Fi no aparece",
                onClick = { addingManually = true },
            )
        }
        item {
            TvRow(
                icon = Icons.Filled.SettingsRemote,
                title = "Agregar por infrarrojo",
                subtitle = if (viewModel.hasIrEmitter) {
                    "Para TV sin Wi-Fi"
                } else {
                    "Este celular no tiene emisor infrarrojo"
                },
                enabled = viewModel.hasIrEmitter,
                onClick = viewModel::showIrBrands,
            )
        }
    }
    if (addingManually) {
        ManualTvDialog(
            onDismiss = { addingManually = false },
            onConfirm = { host, kind ->
                addingManually = false
                viewModel.addFound(FoundTv(kind.label, host, kind))
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(top = 12.dp, bottom = 2.dp),
        color = Amber,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun TvRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (enabled) Paper else Muted)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) Paper else Muted, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTvDialog(onDismiss: () -> Unit, onConfirm: (String, TvKind) -> Unit) {
    var host by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TvKind.ANDROID_TV) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Dirección IP") },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = kind.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        TvKind.entries.filter { it != TvKind.INFRARED }.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    kind = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(host, kind) }, enabled = host.isNotEmpty()) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PairingScreen(viewModel: AppViewModel, found: FoundTv) {
    val state by viewModel.pairing.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    ScreenTitle("Emparejar", onBack = viewModel::back)
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(found.name, style = MaterialTheme.typography.titleMedium)
        when (val current = state) {
            PairingState.Requesting, PairingState.Checking -> CircularProgressIndicator()
            is PairingState.Failed -> {
                Text(current.message, color = Danger)
                Button(onClick = { viewModel.addFound(found) }) { Text("Intentar de nuevo") }
            }
            PairingState.EnterCode, PairingState.WrongCode -> {
                Text("Escribe el código de 6 caracteres que aparece en la TV.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    },
                    label = { Text("Código") },
                    singleLine = true,
                    isError = current == PairingState.WrongCode,
                    supportingText = {
                        if (current == PairingState.WrongCode) Text("Ese código no coincide.")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                    ),
                )
                Button(
                    onClick = { viewModel.submitPairingCode(found, code) },
                    enabled = code.length == 6,
                ) { Text("Emparejar") }
            }
        }
    }
}

@Composable
private fun IrBrandsScreen(viewModel: AppViewModel) {
    ScreenTitle("Elige la marca", onBack = viewModel::back)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Apunta el celular a la TV y toca Probar. Si se enciende o apaga, toca Usar.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        viewModel.irBrands.forEach { brand ->
            Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(brand.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    OutlinedButton(
                        onClick = { viewModel.testIrPower(brand) },
                        border = BorderStroke(1.dp, PanelHigh),
                    ) { Text("Probar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.addIr(brand) }) { Text("Usar") }
                }
            }
        }
        Box(Modifier.size(1.dp))
        Text(
            "Contiene irdb de Simon Peter y colaboradores, usado con permiso. " +
                "Licencia e información para contribuir: https://github.com/probonopd/irdb",
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun RemoteHeader(tv: SavedTv?, viewModel: AppViewModel) {
    ScreenTitle(tv?.name ?: "Control", onBack = viewModel::back)
}
