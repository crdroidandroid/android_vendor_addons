/*
 * Copyright (C) 2025 AxionOS Project
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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.compose.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties


@Composable
fun SecureTimePreference(
    key: String,
    title: String,
    summary: String? = null,
    defaultValue: String = "0000",
    position: PreferencePosition = LocalPreferencePosition.current,
    dependencyKey: String? = null
) {
    val (value, setValue) = rememberSecureSettingStringState(key, defaultValue)
    val enabled = if (dependencyKey != null) {
        rememberSecureSettingBoolean(dependencyKey, true)
    } else {
        true
    }
    
    var showDialog by remember { mutableStateOf(false) }
    val formattedTime = formatTimeString(value)

    BasePreference(
        title = title,
        summary = summary,
        enabled = enabled,
        position = position,
        modifier = Modifier.clickable(enabled = enabled) { showDialog = true },
        widget = {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
            )
        },
    )
    
    if (showDialog && enabled) {
        TimePickerDialog(
            title = title,
            initialTime = value,
            onTimeSelected = { newValue ->
                setValue(newValue)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun TimePickerDialog(
    title: String,
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = initialTime.take(2).toIntOrNull() ?: 0
    val initialMinute = initialTime.takeLast(2).toIntOrNull() ?: 0
    
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(dialogWidth()),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hour = timePickerState.hour.toString().padStart(2, '0')
                    val minute = timePickerState.minute.toString().padStart(2, '0')
                    onTimeSelected("$hour$minute")
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

private fun formatTimeString(time: String): String {
    val hour = time.take(2).toIntOrNull() ?: 0
    val minute = time.takeLast(2).toIntOrNull() ?: 0
    return String.format("%02d:%02d", hour, minute)
}
