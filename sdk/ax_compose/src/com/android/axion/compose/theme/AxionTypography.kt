/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalTextApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.compose.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun Context.fontFamilyFromConfig(normalKey: String, mediumKey: String): FontFamily? {
    val normal = getAndroidConfig(normalKey)
    val medium = getAndroidConfig(mediumKey)
    if (normal.isEmpty() || medium.isEmpty()) return null
    if (normal == "sans-serif" && medium == "sans-serif-medium") return null
    return FontFamily(
        Font(DeviceFontFamilyName(normal), FontWeight.Normal),
        Font(DeviceFontFamilyName(medium), FontWeight.Medium),
    )
}

private fun variableFont(name: String): FontFamily =
    FontFamily(Font(DeviceFontFamilyName(name)))

@SuppressLint("DiscouragedApi")
private fun Context.getAndroidConfig(configName: String): String {
    val configId = resources.getIdentifier(configName, "string", "android")
    return if (configId != 0) resources.getString(configId) else ""
}

private fun buildExpressiveTypography(context: Context): Typography {
    val brand = context.fontFamilyFromConfig("config_headlineFontFamily", "config_headlineFontFamilyMedium")
    val plain = context.fontFamilyFromConfig("config_bodyFontFamily", "config_bodyFontFamilyMedium")

    return Typography(
        displayLarge = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-large"), fontWeight = FontWeight.Normal,
            fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.2).sp, hyphens = Hyphens.Auto,
        ),
        displayLargeEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-large-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.2).sp, hyphens = Hyphens.Auto,
        ),
        displayMedium = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-medium"), fontWeight = FontWeight.Normal,
            fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        displayMediumEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-medium-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        displaySmall = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-small"), fontWeight = FontWeight.Normal,
            fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        displaySmallEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-display-small-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineLarge = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-large"), fontWeight = FontWeight.Normal,
            fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineLargeEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-large-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineMedium = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-medium"), fontWeight = FontWeight.Normal,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineMediumEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-medium-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineSmall = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-small"), fontWeight = FontWeight.Normal,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        headlineSmallEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-headline-small-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.0.sp, hyphens = Hyphens.Auto,
        ),
        titleLarge = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-large"), fontWeight = FontWeight.Normal,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.02.em, hyphens = Hyphens.Auto,
        ),
        titleLargeEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-large-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp, hyphens = Hyphens.Auto,
        ),
        titleMedium = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-medium"), fontWeight = FontWeight.Medium,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.02.em, hyphens = Hyphens.Auto,
        ),
        titleMediumEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-medium-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.02.em, hyphens = Hyphens.Auto,
        ),
        titleSmall = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-small"), fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.02.em, hyphens = Hyphens.Auto,
        ),
        titleSmallEmphasized = TextStyle(
            fontFamily = brand ?: variableFont("variable-title-small-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.02.em, hyphens = Hyphens.Auto,
        ),
        bodyLarge = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-large"), fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        bodyLargeEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-large-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        bodyMedium = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-medium"), fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        bodyMediumEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-medium-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        bodySmall = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-small"), fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        bodySmallEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-body-small-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelLarge = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-large"), fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelLargeEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-large-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelMedium = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-medium"), fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelMediumEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-medium-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelSmall = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-small"), fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
        labelSmallEmphasized = TextStyle(
            fontFamily = plain ?: variableFont("variable-label-small-emphasized"), fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.01.em, hyphens = Hyphens.Auto,
        ),
    )
}

@Composable
fun rememberAxionTypography(): Typography {
    val context = LocalContext.current
    return remember { buildExpressiveTypography(context) }
}
