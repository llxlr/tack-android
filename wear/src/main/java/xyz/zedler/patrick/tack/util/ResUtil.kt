/*
 * This file is part of Tack Android.
 *
 * Tack Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tack Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tack Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler
 */

package xyz.zedler.patrick.tack.util

import androidx.annotation.DrawableRes
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Icon

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AnimatedVectorDrawable(
  @DrawableRes resId: Int,
  description: String,
  color: Color,
  trigger: Boolean,
  modifier: Modifier = Modifier,
  animated: Boolean = true
) {
  val image = AnimatedImageVector.animatedVectorResource(resId)
  val painterForward = rememberAnimatedVectorPainter(
    animatedImageVector = image,
    atEnd = if (animated) trigger else false
  )
  val painterBackward = rememberAnimatedVectorPainter(
    animatedImageVector = image,
    atEnd = !trigger
  )
  Icon(
    painter = if (trigger || !animated) painterForward else painterBackward,
    contentDescription = description,
    tint = color,
    modifier = modifier
  )
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AnimatedVectorDrawable(
  @DrawableRes resId1: Int,
  @DrawableRes resId2: Int,
  description: String,
  color: Color,
  trigger: MutableState<Boolean>,
  modifier: Modifier = Modifier,
  animated: Boolean = true
) {
  val image1 = AnimatedImageVector.animatedVectorResource(resId1)
  val image2 = AnimatedImageVector.animatedVectorResource(resId2)
  val painterForward = rememberAnimatedVectorPainter(
    animatedImageVector = image1,
    atEnd = if (animated) trigger.value else true
  )
  val painterBackward = rememberAnimatedVectorPainter(
    animatedImageVector = image2,
    atEnd = if (animated) !trigger.value else true
  )
  Icon(
    painter = if (trigger.value) painterForward else painterBackward,
    contentDescription = description,
    tint = color,
    modifier = modifier
  )
}