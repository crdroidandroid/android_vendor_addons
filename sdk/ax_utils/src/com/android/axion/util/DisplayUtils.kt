/*
 * Copyright 2025-2026 AxionOS
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

package com.android.axion.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import java.util.EnumSet
import kotlin.jvm.JvmSynthetic
import kotlin.math.max
import kotlin.math.min

class DisplayUtils private constructor() {
    enum class DisplayLayout(private val suffix: String?) {
        PHONE(null),
        TABLET_PORTRAIT("tablet_portrait"),
        TABLET_LANDSCAPE("tablet_landscape"),
        FOLDABLE_OUTER_PORTRAIT("foldable_outer_portrait"),
        FOLDABLE_OUTER_LANDSCAPE("foldable_outer_landscape"),
        FOLDABLE_INNER_PORTRAIT("foldable_inner_portrait"),
        FOLDABLE_INNER_LANDSCAPE("foldable_inner_landscape");

        fun getSettingName(base: String): String = suffix?.let { "${base}_$it" } ?: base
    }

    class DisplayLayoutTarget private constructor(
        val layout: DisplayLayout,
        displaySize: Point,
    ) {
        private val _displaySize = Point(displaySize)

        val displaySize: Point
            get() = Point(_displaySize)

        companion object {
            @JvmSynthetic
            internal fun create(layout: DisplayLayout, displaySize: Point): DisplayLayoutTarget =
                DisplayLayoutTarget(layout, displaySize)
        }
    }

    companion object {
        private const val DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED =
            "android.hardware.display.category.ALL_INCLUDING_DISABLED"
        private const val LARGE_SCREEN_MIN_DP = 600f

        @JvmStatic
        fun getInternalDisplays(context: Context): List<Display> {
            val displayManager =
                context.getSystemService(DisplayManager::class.java)
                    ?: return currentDisplayFallback(context)
            val internalDisplays =
                displayManager
                    .getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
                    .filter { it.isInternal }
            return if (internalDisplays.isEmpty()) {
                currentDisplayFallback(context)
            } else {
                internalDisplays
            }
        }

        @JvmStatic
        fun createStableDisplayContext(context: Context): Context {
            val stableContext = context.getApplicationContext() ?: context
            val display = getContextDisplay(context)
            return display?.let { stableContext.createDisplayContext(it) } ?: stableContext
        }

        @JvmStatic
        fun hasMultipleInternalDisplays(context: Context): Boolean =
            getInternalDisplays(context).size > 1

        @JvmStatic
        fun getLargestInternalDisplay(context: Context): Display? =
            getLargestInternalDisplay(getInternalDisplays(context))

        private fun getLargestInternalDisplay(displays: List<Display>): Display? {
            var largest: Display? = null
            var largestArea = -1L
            for (display in displays) {
                val area = getDisplayArea(display)
                if (area > largestArea) {
                    largest = display
                    largestArea = area
                }
            }
            return largest
        }

        @JvmStatic
        fun getSmallestInternalDisplay(context: Context): Display? {
            val displays = getInternalDisplays(context)
            var smallest: Display? = null
            var smallestArea = Long.MAX_VALUE
            for (display in displays) {
                val area = getDisplayArea(display)
                if (area < smallestArea) {
                    smallest = display
                    smallestArea = area
                }
            }
            return smallest
        }

        @JvmStatic
        fun getCurrentDisplayLayout(context: Context): DisplayLayout =
            getCurrentDisplayLayout(context, context.resources.configuration)

        @JvmStatic
        fun getCurrentDisplayLayout(
            context: Context,
            configuration: Configuration,
        ): DisplayLayout {
            val internalDisplays = getInternalDisplays(context)
            val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (internalDisplays.size > 1) {
                val current = getContextDisplay(context)
                val largest = getLargestInternalDisplay(internalDisplays)
                val inner =
                    current == null || largest == null || current.displayId == largest.displayId
                if (inner) {
                    return if (landscape) {
                        DisplayLayout.FOLDABLE_INNER_LANDSCAPE
                    } else {
                        DisplayLayout.FOLDABLE_INNER_PORTRAIT
                    }
                }
                return if (landscape) {
                    DisplayLayout.FOLDABLE_OUTER_LANDSCAPE
                } else {
                    DisplayLayout.FOLDABLE_OUTER_PORTRAIT
                }
            }
            if (!isLargeScreenDevice(context, internalDisplays)) {
                return DisplayLayout.PHONE
            }
            return if (landscape) DisplayLayout.TABLET_LANDSCAPE
            else DisplayLayout.TABLET_PORTRAIT
        }

        @JvmStatic
        fun getDisplayLayoutTargets(context: Context): List<DisplayLayoutTarget> {
            val displays = getInternalDisplays(context)
            if (displays.isEmpty()) {
                val metrics = context.resources.displayMetrics
                return listOf(
                    DisplayLayoutTarget.create(
                        DisplayLayout.PHONE,
                        Point(metrics.widthPixels, metrics.heightPixels),
                    )
                )
            }

            if (displays.size == 1) {
                val size = getRealSize(displays[0])
                if (!isLargeScreenDevice(context, displays)) {
                    return listOf(DisplayLayoutTarget.create(DisplayLayout.PHONE, size))
                }
                return createOrientationTargets(
                    DisplayLayout.TABLET_PORTRAIT,
                    DisplayLayout.TABLET_LANDSCAPE,
                    size,
                )
            }

            val targets = mutableListOf<DisplayLayoutTarget>()
            val addedLayouts = EnumSet.noneOf(DisplayLayout::class.java)
            val largest = getLargestInternalDisplay(displays)
            for (display in displays) {
                val inner = largest != null && display.displayId == largest.displayId
                val portrait =
                    if (inner) DisplayLayout.FOLDABLE_INNER_PORTRAIT
                    else DisplayLayout.FOLDABLE_OUTER_PORTRAIT
                val landscape =
                    if (inner) DisplayLayout.FOLDABLE_INNER_LANDSCAPE
                    else DisplayLayout.FOLDABLE_OUTER_LANDSCAPE
                for (target in createOrientationTargets(portrait, landscape, getRealSize(display))) {
                    if (addedLayouts.add(target.layout)) {
                        targets.add(target)
                    }
                }
            }
            return targets
        }

        @JvmStatic
        fun getRealSize(display: Display): Point {
            val size = Point()
            display.getRealSize(size)
            return size
        }

        @JvmStatic
        fun getLargestInternalDisplaySize(context: Context): Point {
            val display = getLargestInternalDisplay(context)
            if (display != null) {
                return getRealSize(display)
            }
            val metrics = context.resources.displayMetrics
            return Point(metrics.widthPixels, metrics.heightPixels)
        }

        private fun getDisplayArea(display: Display): Long {
            val size = getRealSize(display)
            return size.x.toLong() * size.y
        }

        @JvmStatic
        fun getInternalDisplaySizes(context: Context, includeRotatedSizes: Boolean): List<Point> {
            val sizes = mutableListOf<Point>()
            for (display in getInternalDisplays(context)) {
                val size = getRealSize(display)
                addUniqueSize(sizes, size)
                if (includeRotatedSizes) {
                    addUniqueSize(sizes, Point(size.y, size.x))
                }
            }
            return sizes
        }

        @JvmStatic
        fun isLargeScreenDevice(context: Context): Boolean =
            isLargeScreenDevice(context, getInternalDisplays(context))

        private fun isLargeScreenDevice(context: Context, displays: List<Display>): Boolean {
            val largest = getLargestInternalDisplay(displays)
            if (largest == null) {
                return context.resources.configuration.smallestScreenWidthDp >=
                    LARGE_SCREEN_MIN_DP
            }
            val size = getRealSize(largest)
            val smallestWidthDp =
                min(size.x, size.y) * 160f / context.resources.configuration.densityDpi
            return smallestWidthDp >= LARGE_SCREEN_MIN_DP
        }

        private fun createOrientationTargets(
            portraitLayout: DisplayLayout,
            landscapeLayout: DisplayLayout,
            size: Point,
        ): List<DisplayLayoutTarget> {
            val portrait = Point(min(size.x, size.y), max(size.x, size.y))
            val landscape = Point(portrait.y, portrait.x)
            return listOf(
                DisplayLayoutTarget.create(portraitLayout, portrait),
                DisplayLayoutTarget.create(landscapeLayout, landscape),
            )
        }

        private fun currentDisplayFallback(context: Context): List<Display> {
            val display = getContextDisplay(context)
            return display?.let { listOf(it) } ?: emptyList()
        }

        private fun getContextDisplay(context: Context): Display? {
            return try {
                context.getDisplay()
            } catch (e: UnsupportedOperationException) {
                val displayManager = context.getSystemService(DisplayManager::class.java)
                displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            }
        }

        private fun addUniqueSize(sizes: MutableList<Point>, size: Point) {
            if (!sizes.contains(size)) {
                sizes.add(size)
            }
        }
    }
}
