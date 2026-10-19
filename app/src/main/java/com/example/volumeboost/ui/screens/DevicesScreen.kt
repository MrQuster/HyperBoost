package com.example.volumeboost.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volumeboost.R
import com.example.volumeboost.data.DeviceProfile
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.DevicesViewModel

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val profiles by viewModel.profiles.collectAsState()

    var isBtTipExpanded by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var newCustomName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacingLarge, vertical = tokens.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingLarge)
    ) {
        Text(
            text = stringResource(R.string.devices_title),
            color = tokens.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Сворачиваемая карточка-подсказка по Bluetooth Absolute Volume
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBtTipExpanded = !isBtTipExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = tokens.accentColor)
                        Text(
                            text = stringResource(R.string.bt_abs_vol_title),
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (isBtTipExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = tokens.textSecondary
                    )
                }

                AnimatedVisibility(visible = isBtTipExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.bt_abs_vol_desc),
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        GlassButton(
                            text = stringResource(R.string.open_dev_options),
                            icon = Icons.Default.DeveloperMode,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Список карточек устройств с защитой от наложения текста на кнопки
        profiles.forEach { profile ->
            key(profile.id) {
                val typeRu = when (profile.type) {
                    "BLUETOOTH" -> "Bluetooth"
                    "USB" -> "USB-ЦАП"
                    "WIRED" -> "Проводные"
                    else -> "Динамик"
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(tokens.accentColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when (profile.type) {
                                    "BLUETOOTH" -> Icons.Default.Headphones
                                    "USB", "WIRED" -> Icons.Default.Cable
                                    else -> Icons.Default.Speaker
                                }
                                Icon(icon, contentDescription = null, tint = tokens.accentColor)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Текстовый блок с weight(1f) и maxLines = 1
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (profile.isPinned) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = tokens.accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Text(
                                        text = profile.customName,
                                        color = tokens.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$typeRu • Усиление: ${profile.boostPercent}%",
                                    color = tokens.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Фиксированный блок кнопок действий справа
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.togglePin(profile.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = stringResource(R.string.device_pin),
                                        tint = if (profile.isPinned) tokens.accentColor else tokens.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingProfile = profile
                                        newCustomName = profile.customName
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.device_rename),
                                        tint = tokens.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (profile.id != "SPEAKER") {
                                    IconButton(
                                        onClick = { viewModel.deleteProfile(profile.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.device_delete),
                                            tint = tokens.dangerColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.device_autostart),
                                color = tokens.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(end = 12.dp)
                            )
                            GlassSwitch(
                                checked = profile.autoStartOnConnect,
                                onCheckedChange = { viewModel.toggleAutoStart(profile.id) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(tokens.screenBottomPadding))
    }

    if (editingProfile != null) {
        GlassDialog(
            title = stringResource(R.string.device_rename),
            onDismiss = { editingProfile = null },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                editingProfile?.let { viewModel.renameProfile(it.id, newCustomName) }
                editingProfile = null
            }
        ) {
            OutlinedTextField(
                value = newCustomName,
                onValueChange = { newCustomName = it },
                singleLine = true,
                label = { Text("Имя устройства") }
            )
        }
    }
}
