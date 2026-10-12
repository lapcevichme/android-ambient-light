package com.lapcevichme.ambientlight.ambient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Reusable Bottom Sheet component for ambient light configuration.
 * Designed to be easily integrated into video players and extensions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSettingsBottomSheet(
    onDismissRequest: () -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    vibrance: Float,
    onVibranceChange: (Float) -> Unit,
    spreadLimit: Float,
    onSpreadLimitChange: (Float) -> Unit,
    edgeSize: Float,
    onEdgeSizeChange: (Float) -> Unit,
    flickerReduction: Int,
    onFlickerReductionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSwitchVideo: (() -> Unit)? = null,
    onPickGalleryVideo: (() -> Unit)? = null,
    onResetDefaults: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0x66FFFFFF)
            )
        },
        modifier = modifier
    ) {
        AmbientSettingsContent(
            isEnabled = isEnabled,
            onEnabledChange = onEnabledChange,
            blurRadius = blurRadius,
            onBlurRadiusChange = onBlurRadiusChange,
            brightness = brightness,
            onBrightnessChange = onBrightnessChange,
            vibrance = vibrance,
            onVibranceChange = onVibranceChange,
            spreadLimit = spreadLimit,
            onSpreadLimitChange = onSpreadLimitChange,
            edgeSize = edgeSize,
            onEdgeSizeChange = onEdgeSizeChange,
            flickerReduction = flickerReduction,
            onFlickerReductionChange = onFlickerReductionChange,
            onClose = onDismissRequest,
            onSwitchVideo = onSwitchVideo,
            onPickGalleryVideo = onPickGalleryVideo,
            onResetDefaults = onResetDefaults,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

/**
 * Embeddable settings content Composable. Can be hosted inside a BottomSheet,
 * Dialog, or a standalone Settings screen.
 */
@Composable
fun AmbientSettingsContent(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    vibrance: Float,
    onVibranceChange: (Float) -> Unit,
    spreadLimit: Float,
    onSpreadLimitChange: (Float) -> Unit,
    edgeSize: Float,
    onEdgeSizeChange: (Float) -> Unit,
    flickerReduction: Int,
    onFlickerReductionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onSwitchVideo: (() -> Unit)? = null,
    onPickGalleryVideo: (() -> Unit)? = null,
    onResetDefaults: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Фоновая подсветка",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ambient Light • Wessel Kroos Port",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Ambient Toggle Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x18FFFFFF),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Включить подсветку",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isEnabled) "Свечение активно" else "Эффект отключен",
                        color = if (isEnabled) Color(0xFF81C784) else Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }

        AnimatedVisibility(
            visible = isEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "ПАРАМЕТРЫ СВЕЧЕНИЯ",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Blur Radius
                AmbientSliderItem(
                    label = "Размытие лучей",
                    valueText = "${blurRadius.toInt()} dp",
                    value = blurRadius,
                    onValueChange = onBlurRadiusChange,
                    valueRange = 0f..120f
                )

                // Brightness
                AmbientSliderItem(
                    label = "Яркость",
                    valueText = "${(brightness * 100).toInt()}%",
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.2f..2.0f
                )

                // Vibrance
                AmbientSliderItem(
                    label = "Насыщенность (Vibrance)",
                    valueText = "${(vibrance * 100).toInt()}%",
                    value = vibrance,
                    onValueChange = onVibranceChange,
                    valueRange = 0.5f..2.5f
                )

                // Spread Limit
                AmbientSliderItem(
                    label = "Длина лучей (Spread Limit)",
                    valueText = "${String.format(Locale.US, "%.2f", spreadLimit)}x",
                    value = spreadLimit,
                    onValueChange = onSpreadLimitChange,
                    valueRange = 0.5f..2.5f
                )

                // Edge Size
                AmbientSliderItem(
                    label = "Ширина забора с кромки",
                    valueText = "${(edgeSize * 100).toInt()}%",
                    value = edgeSize,
                    onValueChange = onEdgeSizeChange,
                    valueRange = 0.05f..0.40f
                )

                // Flicker Reduction (Smoothing)
                AmbientSliderItem(
                    label = "Сглаживание мерцания (Flicker)",
                    valueText = "$flickerReduction%",
                    value = flickerReduction.toFloat(),
                    onValueChange = { onFlickerReductionChange(it.toInt()) },
                    valueRange = 0f..100f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Demo Actions (Switch video / Gallery picker / Reset)
        if (onSwitchVideo != null || onPickGalleryVideo != null || onResetDefaults != null) {
            Text(
                text = "ИСТОЧНИК ВИДЕО И ДЕМО",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (onPickGalleryVideo != null) {
                Button(
                    onClick = onPickGalleryVideo,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁 Выбрать видео из галереи", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onSwitchVideo != null) {
                OutlinedButton(
                    onClick = onSwitchVideo,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Сменить тестовое видео", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onResetDefaults != null) {
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Сбросить параметры по умолчанию", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AmbientSliderItem(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Text(valueText, color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
    }
}
