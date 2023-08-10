/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.utils

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun View.slideInToastFromTopForDuration(
    root: ViewGroup,
    lifecycleScope: LifecycleCoroutineScope,
    duration: Long = 5000
) {
    val view = this

    val transition: Transition = Slide(Gravity.TOP)
    transition.duration = 600
    transition.addTarget(view)

    TransitionManager.beginDelayedTransition(root, transition)
    view.visibility = View.VISIBLE

    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            delay(duration)
            withContext(Dispatchers.Main) {
                TransitionManager.beginDelayedTransition(root, transition)
                view.visibility = View.GONE
            }
        }
    }
}

fun View.slideInExtraActionsMenu(
    root: ViewGroup,
    visibility: Int
) {
    val transition: Transition = Slide(Gravity.BOTTOM)
    transition.duration = 600
    transition.addTarget(this)

    TransitionManager.beginDelayedTransition(root, transition)
    this.translationY = 0f
}
