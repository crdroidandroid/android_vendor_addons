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

package com.android.axion.compose.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun SecureListPreference(
    key: String,
    title: String,
    summary: String,
    options: List<Pair<String, String>>,
    defaultValue: String,
    position: PreferencePosition = LocalPreferencePosition.current,
    dependencyKey: String? = null
) {
    val (value, setValue) = rememberSecureSettingStringState(key, defaultValue)
    val enabled = if (dependencyKey != null) {
        rememberSecureSettingBoolean(dependencyKey, true)
    } else {
        true
    }

    ListPreference(
        title = title,
        summary = summary,
        options = options,
        value = value,
        onValueChange = setValue,
        enabled = enabled,
        position = position
    )
}

@Composable
fun ListPreference(
    title: String,
    summary: String? = null,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second
    val displaySummary = selectedLabel ?: summary

    BasePreference(
        title = title,
        summary = displaySummary,
        enabled = enabled,
        position = position,
        modifier = modifier.clickable(enabled = enabled) { showDialog = true },
    )

    if (showDialog && enabled) {
        ListPreferenceDialog(
            title = title,
            options = options,
            selectedKey = value,
            onOptionSelected = { newValue ->
                onValueChange(newValue)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ListPreferenceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (key, label) ->
                    val selected = selectedKey == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onOptionSelected(key) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}
