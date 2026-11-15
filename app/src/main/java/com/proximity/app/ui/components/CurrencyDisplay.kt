// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.R
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.utils.CurrencyFormat
import java.math.BigDecimal

@Composable
fun BrandedHeaderLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    textColor: Color = LocalAppVisuals.current.textPrimary
) {
    val iconSize = if (compact) 28.dp else 32.dp
    val titleSize = if (compact) 20.sp else 24.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(ColorTokens.BrandTeal.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(id = R.string.cd_app_logo),
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.app_name),
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = textColor
        )
    }
}

@Composable
fun CurrencyDisplay(
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    digitFontSize: TextUnit = 24.sp,
    color: Color = LocalAppVisuals.current.textPrimary
) {
    val formattedDigits = CurrencyFormat.formatDigitsOnly(amount)
    val symbolSize = (digitFontSize.value * 0.82f).sp

    val annotated = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = symbolSize,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.82f),
                fontFamily = FontFamily.SansSerif
            )
        ) {
            append("\u20B9")
        }
        withStyle(
            SpanStyle(
                fontSize = digitFontSize,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.SansSerif
            )
        ) {
            append(formattedDigits)
        }
    }

    Text(text = annotated, modifier = modifier)
}

@Composable
fun CurrencyDisplay(
    amountStr: String?,
    modifier: Modifier = Modifier,
    digitFontSize: TextUnit = 24.sp,
    color: Color = LocalAppVisuals.current.textPrimary
) {
    val parsed = amountStr?.trim()?.replace(",", "")?.replace("\u20B9", "")?.toBigDecimalOrNull()
        ?: BigDecimal.ZERO
    CurrencyDisplay(
        amount = parsed,
        modifier = modifier,
        digitFontSize = digitFontSize,
        color = color
    )
}

@Composable
fun OperatorLogoBadge(
    carrierId: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val badgeSize: Dp = if (compact) 36.dp else 44.dp
    when (carrierId.trim().lowercase()) {
        "jio" -> {
            Box(
                modifier = modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(ColorTokens.CarrierJioFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.carrier_jio_badge),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = if (compact) 13.sp else 15.sp
                )
            }
        }
        "airtel" -> {
            Box(
                modifier = modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(ColorTokens.CarrierAirtelFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.carrier_airtel_badge),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 10.sp else 12.sp
                )
            }
        }
        "vodafone", "vi" -> {
            Box(
                modifier = modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, ColorTokens.CarrierLightBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.carrier_vi_badge),
                    color = ColorTokens.CarrierViText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 14.sp else 16.sp
                )
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, ColorTokens.CarrierLightBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.carrier_bsnl_badge),
                    color = ColorTokens.CarrierBsnlText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 10.sp else 11.sp
                )
            }
        }
    }
}
