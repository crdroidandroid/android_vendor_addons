/*
 * Copyright (C) 2025 AxionOS
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
package com.android.axion.compose.about

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedDeviceIllustration(
    modifier: Modifier = Modifier,
    deviceName: String = "Pixel",
    version: String = "2.3",
    atomColor: Color? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "device_animation")
    
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    val primaryColor = atomColor ?: MaterialTheme.colorScheme.primary
    val tertiaryColor = if (atomColor != null) atomColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.tertiary
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-floatOffset).dp)
                .rotate(rotationAngle)
        ) {
            AnimatedAtom(
                modifier = Modifier.fillMaxSize(),
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor
            )
        }
    }
}

@Composable
private fun DeviceFrame(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    tertiaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    primaryContainer: Color,
    tertiaryContainer: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "screen_animation")
    
    val screenGradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "screen_gradient"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor,
                        surfaceColor.copy(alpha = 0.97f)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.8f),
                        tertiaryColor.copy(alpha = 0.6f),
                        primaryColor.copy(alpha = 0.8f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryContainer.copy(alpha = 0.2f + screenGradientOffset * 0.1f),
                            tertiaryContainer.copy(alpha = 0.15f),
                            primaryContainer.copy(alpha = 0.2f - screenGradientOffset * 0.05f)
                        ),
                        start = Offset(0f, screenGradientOffset * 400f),
                        end = Offset(400f, (1f - screenGradientOffset) * 400f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedAtom(
                    modifier = Modifier.size(65.dp),
                    primaryColor = primaryColor,
                    tertiaryColor = tertiaryColor
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                Text(
                    text = "AxionOS",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = onSurfaceColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun AnimatedAtom(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    tertiaryColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "atom")
    
    val orbit1Rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit1"
    )
    
    val orbit2Rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit2"
    )
    
    val orbit3Rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit3"
    )
    
    val nucleusPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nucleus"
    )
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val orbitRadius = size.minDimension / 2.5f
        val electronRadius = 3.dp.toPx()
        val nucleusRadius = 1.5.dp.toPx() * nucleusPulse
        
        val orbitStroke = 2.5.dp.toPx()
        drawOvalOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 0f, primaryColor.copy(alpha = 0.6f), orbitStroke)
        drawOvalOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 60f, tertiaryColor.copy(alpha = 0.6f), orbitStroke)
        drawOvalOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 120f, primaryColor.copy(alpha = 0.6f), orbitStroke)
        
        drawCircle(color = primaryColor, radius = nucleusRadius, center = Offset(centerX, centerY))
        
        drawElectronOnOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 0f, orbit1Rotation, electronRadius, tertiaryColor)
        drawElectronOnOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 60f, orbit2Rotation, electronRadius, primaryColor)
        drawElectronOnOrbit(centerX, centerY, orbitRadius, orbitRadius * 0.35f, 120f, orbit3Rotation, electronRadius, tertiaryColor)
    }
}

private fun DrawScope.drawOvalOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, color: Color, stroke: Float) {
    rotate(degrees = tilt, pivot = Offset(cX, cY)) {
        drawOval(color, Offset(cX - rX, cY - rY), Size(rX * 2, rY * 2), style = Stroke(stroke))
    }
}

private fun DrawScope.drawElectronOnOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, angle: Float, radius: Float, color: Color) {
    val rad = Math.toRadians(angle.toDouble())
    val tiltRad = Math.toRadians(tilt.toDouble())
    val x = rX * cos(rad).toFloat()
    val y = rY * sin(rad).toFloat()
    val rotX = x * cos(tiltRad).toFloat() - y * sin(tiltRad).toFloat()
    val rotY = x * sin(tiltRad).toFloat() + y * cos(tiltRad).toFloat()
    val eX = cX + rotX
    val eY = cY + rotY
    
    drawCircle(Brush.radialGradient(listOf(color.copy(0.6f), color.copy(0.1f), Color.Transparent), Offset(eX, eY), radius * 1.8f), radius * 1.5f, Offset(eX, eY))
    drawCircle(color, radius, Offset(eX, eY))
}

private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
