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

package com.android.axion.kotlin.settings

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

enum class SettingsType { SECURE, SYSTEM, GLOBAL }

class SettingsFlow @JvmOverloads constructor(
    private val contentResolver: ContentResolver,
    private val type: SettingsType,
    private val userId: Int = UserHandle.myUserId(),
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun getUri(key: String): Uri =
        when (type) {
            SettingsType.SECURE -> Settings.Secure.getUriFor(key)
            SettingsType.SYSTEM -> Settings.System.getUriFor(key)
            SettingsType.GLOBAL -> Settings.Global.getUriFor(key)
        }

    fun getString(key: String, default: String = ""): String =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.getStringForUser(
                    contentResolver,
                    key,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.getStringForUser(
                    contentResolver,
                    key,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.getString(contentResolver, key)
            } ?: default
        } catch (_: SecurityException) {
            default
        }

    fun putString(key: String, value: String): Boolean =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.putStringForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.putStringForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.putString(contentResolver, key, value)
            }
        } catch (_: SecurityException) {
            false
        }

    fun getInt(key: String, default: Int = 0): Int =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.getIntForUser(
                    contentResolver,
                    key,
                    default,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.getIntForUser(
                    contentResolver,
                    key,
                    default,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.getInt(contentResolver, key, default)
            }
        } catch (_: SecurityException) {
            default
        }

    fun putInt(key: String, value: Int): Boolean =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.putIntForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.putIntForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.putInt(contentResolver, key, value)
            }
        } catch (_: SecurityException) {
            false
        }

    fun getFloat(key: String, default: Float = 0f): Float =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.getFloatForUser(
                    contentResolver,
                    key,
                    default,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.getFloatForUser(
                    contentResolver,
                    key,
                    default,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.getFloat(contentResolver, key, default)
            }
        } catch (_: SecurityException) {
            default
        }

    fun putFloat(key: String, value: Float): Boolean =
        try {
            when (type) {
                SettingsType.SECURE -> Settings.Secure.putFloatForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.SYSTEM -> Settings.System.putFloatForUser(
                    contentResolver,
                    key,
                    value,
                    userId,
                )
                SettingsType.GLOBAL -> Settings.Global.putFloat(contentResolver, key, value)
            }
        } catch (_: SecurityException) {
            false
        }

    @JvmOverloads
    fun observe(key: String, emitInitial: Boolean = true): Flow<Unit> =
        callbackFlow {
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            try {
                contentResolver.registerContentObserver(getUri(key), false, observer, userId)
            } catch (_: SecurityException) {
                trySend(Unit)
                close()
                return@callbackFlow
            }
            if (emitInitial) {
                trySend(Unit)
            }
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.conflate()

    fun observeInt(key: String, default: Int = 0): Flow<Int> =
        observe(key).map { getInt(key, default) }

    fun observeString(key: String, default: String = ""): Flow<String> =
        observe(key).map { getString(key, default) }

    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        observeInt(key, if (default) 1 else 0).map { it != 0 }

    fun observeFloat(key: String, default: Float = 0f): Flow<Float> =
        observe(key).map { getFloat(key, default) }
}
