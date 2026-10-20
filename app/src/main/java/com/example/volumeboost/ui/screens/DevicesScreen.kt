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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.volumeboost.R
import com.example.volumeboost.data.DeviceProfile
import com.example.volumeboost.ui.components.*
import com.example.volumeboost.ui.theme.LocalGlassTokens
import com.example.volumeboost.viewmodel.DevicesViewModel

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<DeviceProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var btTipExpanded by remember { mutableStateOf(false) }

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
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = tokens.minTouchTarget)
                        .clickable { btTipExpanded = !btTipExpanded },
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
                            fontSize = 14.sp
                        )
                    }
                    Icon(
                        imageVector = if (btTipExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = tokens.textSecondary
                    )
                }

                AnimatedVisibility(visible = btTipExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.bt_abs_vol_desc),
                            color = tokens.textSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                        GlassButton(
                            text = stringResource(R.string.open_dev_options),
                            icon = Icons.Default.DeveloperMode,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                }
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.devices_saved),
            color = tokens.accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        profiles.forEach { profile ->
            key(profile.id) {
                val icon = when (profile.type) {
                    "BLUETOOTH" -> Icons.Default.Headphones
                    "USB", "WIRED" -> Icons.Default.Cable
                    else -> Icons.Default.Speaker
                }
                val typeLocalized = when (profile.type) {
                    "BLUETOOTH" -> stringResource(R.string.dev_type_bt)
                    "USB" -> stringResource(R.string.dev_type_usb)
                    "WIRED" -> stringResource(R.string.dev_type_wired)
                    else -> stringResource(R.string.dev_type_speaker)
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(tokens.accentColor.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tokens.accentColor)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (profile.isPinned) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = tokens.accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = profile.customName,
                                        color = tokens.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.dev_boost_fmt, typeLocalized, profile.boostPercent),
                                    color = tokens.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.togglePin(profile.id) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = stringResource(R.string.device_pin),
                                        tint = if (profile.isPinned) tokens.accentColor else tokens.textSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        renameTarget = profile
                                        renameText = profile.customName
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.device_rename),
                                        tint = tokens.textSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                if (profile.id != "SPEAKER") {
                                    IconButton(
                                        onClick = { viewModel.deleteProfile(profile.id) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.device_delete),
                                            tint = tokens.dangerColor,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = tokens.minTouchTarget),
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

        Spacer(modifier = Modifier.height(136.dp))
    }

    renameTarget?.let { target ->
        GlassDialog(
            title = stringResource(R.string.device_rename),
            onDismiss = { renameTarget = null },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                viewModel.renameProfile(target.id, renameText)
                renameTarget = null
            }
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text(stringResource(R.string.device_name_hint), color = tokens.textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = tokens.textPrimary,
                    unfocusedTextColor = tokens.textPrimary,
                    focusedBorderColor = tokens.accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
