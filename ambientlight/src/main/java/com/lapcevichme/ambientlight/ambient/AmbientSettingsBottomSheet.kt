package com.lapcevichme.ambientlight.ambient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lapcevichme.ambientlight.compose.AmbientLightState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import java.util.Locale
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lapcevichme.ambientlight.R
private val ChevronDownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChevronDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.White)) {
        moveTo(7.41f, 8.59f)
        lineTo(12f, 13.17f)
        lineTo(16.59f, 8.59f)
        lineTo(18f, 10f)
        lineTo(12f, 16f)
        lineTo(6f, 10f)
        close()
    }.build()
}

/**
 * Styling and color palette for the ambient light settings bottom sheet.
 * Defaults to authentic YouTube dark mode styling (monochromatic white & neutral grays).
 */
data class AmbientSheetColors(
    val accentColor: Color = Color.White,
    val containerColor: Color = Color(0xFF1E1E1E),
    val contentColor: Color = Color.White,
    val secondaryContentColor: Color = Color(0xFFAAAAAA),
    val inactiveTrackColor: Color = Color(0x33FFFFFF),
    val cardBackgroundColor: Color = Color(0x18FFFFFF),
    val cardBorderColor: Color = Color(0x22FFFFFF),
    val statusActiveColor: Color = Color(0xFF81C784),
    val switchCheckedThumbColor: Color = Color(0xFF1E1E1E),
    val switchCheckedTrackColor: Color = Color.White,
    val switchCheckedBorderColor: Color = Color.Transparent,
    val switchUncheckedThumbColor: Color = Color(0xFFAAAAAA),
    val switchUncheckedTrackColor: Color = Color(0x28FFFFFF),
    val switchUncheckedBorderColor: Color = Color(0x33FFFFFF)
) {
    companion object {
        val Default = AmbientSheetColors()

        /**
         * Dynamic palette pulling accents from the active Material 3 theme (Material You on Android 12+).
         */
        @Composable
        fun material3(
            accentColor: Color = MaterialTheme.colorScheme.primary,
            containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor: Color = MaterialTheme.colorScheme.onSurface,
            secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
        ) = AmbientSheetColors(
            accentColor = accentColor,
            containerColor = containerColor,
            contentColor = contentColor,
            secondaryContentColor = secondaryContentColor,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * Overload of [AmbientSettingsBottomSheet] taking an [AmbientLightState] directly.
 */
@Composable
fun AmbientSettingsBottomSheet(
    state: AmbientLightState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AmbientSheetColors = AmbientSheetColors.Default
) {
    AmbientSettingsBottomSheet(
        onDismissRequest = onDismissRequest,
        isEnabled = state.isEnabled,
        onEnabledChange = { state.isEnabled = it },
        blurRadius = state.blurRadius,
        onBlurRadiusChange = { state.blurRadius = it },
        brightness = state.brightness,
        onBrightnessChange = { state.brightness = it },
        vibrance = state.vibrance,
        onVibranceChange = { state.vibrance = it },
        spreadLimit = state.spreadLimit,
        onSpreadLimitChange = { state.spreadLimit = it },
        edgeSize = state.edgeSize,
        onEdgeSizeChange = { state.edgeSize = it },
        flickerReduction = state.flickerReduction,
        onFlickerReductionChange = { state.flickerReduction = it },
        contrast = state.contrast,
        onContrastChange = { state.contrast = it },
        directionTop = state.directionTop,
        onDirectionTopChange = { state.directionTop = it },
        directionBottom = state.directionBottom,
        onDirectionBottomChange = { state.directionBottom = it },
        directionLeft = state.directionLeft,
        onDirectionLeftChange = { state.directionLeft = it },
        directionRight = state.directionRight,
        onDirectionRightChange = { state.directionRight = it },
        detectLetterbox = state.detectLetterbox,
        onDetectLetterboxChange = { state.detectLetterbox = it },
        fadeStart = state.fadeStart,
        onFadeStartChange = { state.fadeStart = it },
        fadeCurve = state.fadeCurve,
        onFadeCurveChange = { state.fadeCurve = it },
        oledMode = state.isOledMode,
        onOledModeChange = { state.isOledMode = it },
        onResetDefaults = { state.resetToDefaults() },
        modifier = modifier,
        colors = colors
    )
}

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
    colors: AmbientSheetColors = AmbientSheetColors.Default,
    // Advanced Wessel Kroos parameters
    contrast: Float = 1.0f,
    onContrastChange: (Float) -> Unit = {},
    directionTop: Boolean = true,
    onDirectionTopChange: (Boolean) -> Unit = {},
    directionBottom: Boolean = true,
    onDirectionBottomChange: (Boolean) -> Unit = {},
    directionLeft: Boolean = true,
    onDirectionLeftChange: (Boolean) -> Unit = {},
    directionRight: Boolean = true,
    onDirectionRightChange: (Boolean) -> Unit = {},
    detectLetterbox: Boolean = true,
    onDetectLetterboxChange: (Boolean) -> Unit = {},
    fadeStart: Float = 0.15f,
    onFadeStartChange: (Float) -> Unit = {},
    fadeCurve: Float = 35f,
    onFadeCurveChange: (Float) -> Unit = {},
    oledMode: Boolean = false,
    onOledModeChange: (Boolean) -> Unit = {},
    onResetDefaults: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
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
            contrast = contrast,
            onContrastChange = onContrastChange,
            directionTop = directionTop,
            onDirectionTopChange = onDirectionTopChange,
            directionBottom = directionBottom,
            onDirectionBottomChange = onDirectionBottomChange,
            directionLeft = directionLeft,
            onDirectionLeftChange = onDirectionLeftChange,
            directionRight = directionRight,
            onDirectionRightChange = onDirectionRightChange,
            detectLetterbox = detectLetterbox,
            onDetectLetterboxChange = onDetectLetterboxChange,
            fadeStart = fadeStart,
            onFadeStartChange = onFadeStartChange,
            fadeCurve = fadeCurve,
            onFadeCurveChange = onFadeCurveChange,
            oledMode = oledMode,
            onOledModeChange = onOledModeChange,
            colors = colors,
            onClose = onDismissRequest,
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
    colors: AmbientSheetColors = AmbientSheetColors.Default,
    // Advanced Wessel Kroos parameters
    contrast: Float = 1.0f,
    onContrastChange: (Float) -> Unit = {},
    directionTop: Boolean = true,
    onDirectionTopChange: (Boolean) -> Unit = {},
    directionBottom: Boolean = true,
    onDirectionBottomChange: (Boolean) -> Unit = {},
    directionLeft: Boolean = true,
    onDirectionLeftChange: (Boolean) -> Unit = {},
    directionRight: Boolean = true,
    onDirectionRightChange: (Boolean) -> Unit = {},
    detectLetterbox: Boolean = true,
    onDetectLetterboxChange: (Boolean) -> Unit = {},
    fadeStart: Float = 0.15f,
    onFadeStartChange: (Float) -> Unit = {},
    fadeCurve: Float = 35f,
    onFadeCurveChange: (Float) -> Unit = {},
    oledMode: Boolean = false,
    onOledModeChange: (Boolean) -> Unit = {},
    onClose: (() -> Unit)? = null,
    onResetDefaults: (() -> Unit)? = null
) {
    var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }

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
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = colors.accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ambient_title),
                    color = colors.contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ambient_subtitle),
                    color = colors.secondaryContentColor,
                    fontSize = 12.sp
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.ambient_close),
                        tint = colors.contentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Ambient Toggle Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.cardBackgroundColor,
            border = BorderStroke(1.dp, colors.cardBorderColor),
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
                        text = stringResource(R.string.ambient_enable),
                        color = colors.contentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isEnabled) stringResource(R.string.ambient_status_active) else stringResource(R.string.ambient_status_disabled),
                        color = if (isEnabled) colors.statusActiveColor else colors.secondaryContentColor,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.switchCheckedThumbColor,
                        checkedTrackColor = colors.switchCheckedTrackColor,
                        checkedBorderColor = colors.switchCheckedBorderColor,
                        uncheckedThumbColor = colors.switchUncheckedThumbColor,
                        uncheckedTrackColor = colors.switchUncheckedTrackColor,
                        uncheckedBorderColor = colors.switchUncheckedBorderColor
                    )
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
                    text = stringResource(R.string.ambient_section_basic),
                    color = colors.secondaryContentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Blur Radius
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_blur),
                    valueText = "${blurRadius.toInt()} dp",
                    value = blurRadius,
                    onValueChange = onBlurRadiusChange,
                    valueRange = 0f..120f,
                    colors = colors
                )

                // Brightness
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_brightness),
                    valueText = "${(brightness * 100).toInt()}%",
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.2f..2.0f,
                    colors = colors
                )

                // Vibrance
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_vibrance),
                    valueText = "${(vibrance * 100).toInt()}%",
                    value = vibrance,
                    onValueChange = onVibranceChange,
                    valueRange = 0.5f..2.5f,
                    colors = colors
                )

                // Spread Limit
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_spread_limit),
                    valueText = "${String.format(Locale.US, "%.2f", spreadLimit)}x",
                    value = spreadLimit,
                    onValueChange = onSpreadLimitChange,
                    valueRange = 0.5f..2.5f,
                    colors = colors
                )

                // Edge Size
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_edge_size),
                    valueText = "${(edgeSize * 100).toInt()}%",
                    value = edgeSize,
                    onValueChange = onEdgeSizeChange,
                    valueRange = 0.05f..0.40f,
                    colors = colors
                )

                // Flicker Reduction (Smoothing)
                AmbientSliderItem(
                    label = stringResource(R.string.ambient_flicker_reduction),
                    valueText = "$flickerReduction%",
                    value = flickerReduction.toFloat(),
                    onValueChange = { onFlickerReductionChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = colors
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ADVANCED SETTINGS ACCORDION
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.cardBackgroundColor,
                    border = BorderStroke(1.dp, if (isAdvancedExpanded) colors.accentColor.copy(alpha = 0.4f) else colors.cardBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isAdvancedExpanded) colors.accentColor else colors.secondaryContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.ambient_section_advanced),
                                    color = colors.contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.ambient_section_advanced_desc),
                                    color = colors.secondaryContentColor,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = ChevronDownIcon,
                            contentDescription = null,
                            tint = colors.secondaryContentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (isAdvancedExpanded) 180f else 0f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isAdvancedExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        // Section: DIRECTIONS
                        Text(
                            text = stringResource(R.string.ambient_section_directions),
                            color = colors.secondaryContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DirectionChip(stringResource(R.string.ambient_direction_top), directionTop, onDirectionTopChange, colors, Modifier.weight(1f))
                            DirectionChip(stringResource(R.string.ambient_direction_bottom), directionBottom, onDirectionBottomChange, colors, Modifier.weight(1f))
                            DirectionChip(stringResource(R.string.ambient_direction_left), directionLeft, onDirectionLeftChange, colors, Modifier.weight(1f))
                            DirectionChip(stringResource(R.string.ambient_direction_right), directionRight, onDirectionRightChange, colors, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section: CONTRAST & OLED
                        Text(
                            text = stringResource(R.string.ambient_section_filters),
                            color = colors.secondaryContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        AmbientSliderItem(
                            label = stringResource(R.string.ambient_contrast),
                            valueText = "${(contrast * 100).toInt()}%",
                            value = contrast,
                            onValueChange = onContrastChange,
                            valueRange = 0.5f..1.5f,
                            colors = colors
                        )

                        AmbientSwitchItem(
                            title = stringResource(R.string.ambient_oled_mode),
                            description = stringResource(R.string.ambient_oled_mode_desc),
                            checked = oledMode,
                            onCheckedChange = onOledModeChange,
                            colors = colors
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section: BLACK BARS / LETTERBOX
                        Text(
                            text = stringResource(R.string.ambient_section_bars),
                            color = colors.secondaryContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        AmbientSwitchItem(
                            title = stringResource(R.string.ambient_letterbox),
                            description = stringResource(R.string.ambient_letterbox_desc),
                            checked = detectLetterbox,
                            onCheckedChange = onDetectLetterboxChange,
                            colors = colors
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section: SPREAD FADE CURVE
                        Text(
                            text = stringResource(R.string.ambient_section_fade),
                            color = colors.secondaryContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        AmbientSliderItem(
                            label = stringResource(R.string.ambient_fade_start),
                            valueText = "${(fadeStart * 100).toInt()}%",
                            value = fadeStart,
                            onValueChange = onFadeStartChange,
                            valueRange = 0.0f..0.40f,
                            colors = colors
                        )

                        AmbientSliderItem(
                            label = stringResource(R.string.ambient_fade_curve),
                            valueText = "${fadeCurve.toInt()}",
                            value = fadeCurve,
                            onValueChange = onFadeCurveChange,
                            valueRange = 10f..70f,
                            colors = colors
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset to defaults
        if (onResetDefaults != null) {
            TextButton(
                onClick = onResetDefaults,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.ambient_reset_defaults), color = colors.secondaryContentColor, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DirectionChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AmbientSheetColors,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (checked) colors.accentColor.copy(alpha = 0.16f) else colors.cardBackgroundColor,
        border = BorderStroke(1.dp, if (checked) colors.accentColor else colors.cardBorderColor),
        modifier = modifier.clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (checked) colors.accentColor else colors.secondaryContentColor,
                fontSize = 12.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun AmbientSwitchItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AmbientSheetColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = colors.contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.secondaryContentColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.switchCheckedThumbColor,
                checkedTrackColor = colors.switchCheckedTrackColor,
                checkedBorderColor = colors.switchCheckedBorderColor,
                uncheckedThumbColor = colors.switchUncheckedThumbColor,
                uncheckedTrackColor = colors.switchUncheckedTrackColor,
                uncheckedBorderColor = colors.switchUncheckedBorderColor
            )
        )
    }
}

@Composable
private fun AmbientSliderItem(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: AmbientSheetColors,
    steps: Int = 0
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.contentColor.copy(alpha = 0.85f), fontSize = 13.sp)
            Text(valueText, color = colors.accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentColor,
                activeTrackColor = colors.accentColor,
                inactiveTrackColor = colors.inactiveTrackColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
    }
}
