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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun rememberSecureSettingString(key: String, defaultValue: String = ""): String {
    val state by rememberSettingString(key, SettingsType.SECURE, defaultValue)
    return state
}

@Composable
fun rememberSecureSettingStringState(
    key: String,
    defaultValue: String = "",
): Pair<String, (String) -> Unit> {
    val flow = rememberSettingsFlow(SettingsType.SECURE)
    val state by rememberSettingString(key, SettingsType.SECURE, defaultValue)
    val setter: (String) -> Unit = { flow.putString(key, it) }
    return state to setter
}

@Composable
fun rememberSecureSettingInt(key: String, defaultValue: Int = 0): Int {
    val state by rememberSettingInt(key, SettingsType.SECURE, defaultValue)
    return state
}

@Composable
fun rememberSecureSettingIntState(
    key: String,
    defaultValue: Int = 0,
): Pair<Int, (Int) -> Unit> {
    val flow = rememberSettingsFlow(SettingsType.SECURE)
    val state by rememberSettingInt(key, SettingsType.SECURE, defaultValue)
    val setter: (Int) -> Unit = { flow.putInt(key, it) }
    return state to setter
}

@Composable
fun rememberSecureSettingBoolean(key: String, defaultValue: Boolean = false): Boolean {
    val state by rememberSettingBoolean(key, SettingsType.SECURE, defaultValue)
    return state
}

@Composable
fun rememberSecureSettingBooleanState(
    key: String,
    defaultValue: Boolean = false,
): Pair<Boolean, (Boolean) -> Unit> {
    val flow = rememberSettingsFlow(SettingsType.SECURE)
    val state by rememberSettingBoolean(key, SettingsType.SECURE, defaultValue)
    val setter: (Boolean) -> Unit = { flow.putInt(key, if (it) 1 else 0) }
    return state to setter
}

@Composable
fun rememberSystemSettingString(key: String, defaultValue: String = ""): String {
    val state by rememberSettingString(key, SettingsType.SYSTEM, defaultValue)
    return state
}

@Composable
fun rememberSystemSettingIntState(
    key: String,
    defaultValue: Int = 0,
): Pair<Int, (Int) -> Unit> {
    val flow = rememberSettingsFlow(SettingsType.SYSTEM)
    val state by rememberSettingInt(key, SettingsType.SYSTEM, defaultValue)
    val setter: (Int) -> Unit = { flow.putInt(key, it) }
    return state to setter
}
