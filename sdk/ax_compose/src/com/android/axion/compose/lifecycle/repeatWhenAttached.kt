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
package com.android.axion.compose.lifecycle

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@MainThread
fun View.repeatWhenAttached(
    block: suspend LifecycleOwner.(View) -> Unit
): DisposableHandle {
    val view = this
    val lifecycleCoroutineContext = Dispatchers.Main + EmptyCoroutineContext
    var lifecycleOwner: ViewLifecycleOwner? = null

    val onAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            lifecycleOwner?.onDestroy()
            lifecycleOwner = createLifecycleOwnerAndRun(view, lifecycleCoroutineContext, block)
        }

        override fun onViewDetachedFromWindow(v: View) {
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
        }
    }

    addOnAttachStateChangeListener(onAttachListener)
    if (view.isAttachedToWindow) {
        lifecycleOwner = createLifecycleOwnerAndRun(view, lifecycleCoroutineContext, block)
    }

    return DisposableHandle {
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        view.removeOnAttachStateChangeListener(onAttachListener)
    }
}

private fun createLifecycleOwnerAndRun(
    view: View,
    coroutineContext: CoroutineContext,
    block: suspend LifecycleOwner.(View) -> Unit
): ViewLifecycleOwner {
    return ViewLifecycleOwner(view).apply {
        onCreate()
        lifecycleScope.launch(context = coroutineContext) { block(view) }
    }
}

class ViewLifecycleOwner(private val view: View) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val windowVisibleListener =
        ViewTreeObserver.OnWindowVisibilityChangeListener { updateState() }
    private val windowFocusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { updateState() }

    private val savedStateRegistryOwner = object : SavedStateRegistryOwner {
        private val controller =
            SavedStateRegistryController.create(this).apply { performRestore(null) }

        override val savedStateRegistry = controller.savedStateRegistry
        override val lifecycle: Lifecycle
            get() = registry
    }

    private val viewModelStoreOwner = object :
        ViewModelStoreOwner,
        HasDefaultViewModelProviderFactory {

        override val viewModelStore: ViewModelStore = ViewModelStore()

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = ViewModelProvider.NewInstanceFactory()

        override val defaultViewModelCreationExtras: CreationExtras
            get() = CreationExtras.Empty
    }

    fun onCreate() {
        registry.currentState = Lifecycle.State.CREATED
        view.viewTreeObserver.addOnWindowVisibilityChangeListener(windowVisibleListener)
        view.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)

        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        view.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        updateState()
    }

    fun onDestroy() {
        view.viewTreeObserver.removeOnWindowVisibilityChangeListener(windowVisibleListener)
        view.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)

        view.setViewTreeLifecycleOwner(null)
        view.setViewTreeSavedStateRegistryOwner(null)
        view.setViewTreeViewModelStoreOwner(null)

        registry.currentState = Lifecycle.State.DESTROYED
    }

    override val lifecycle: Lifecycle
        get() = registry

    private fun updateState() {
        registry.currentState = when {
            view.windowVisibility != View.VISIBLE -> Lifecycle.State.CREATED
            !view.hasWindowFocus() -> Lifecycle.State.STARTED
            else -> Lifecycle.State.RESUMED
        }
    }
}
