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

package xyz.zedler.patrick.tack.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun FadingEdgeRow(
  verticalAlignment: Alignment.Vertical,
  horizontalArrangement: Arrangement.HorizontalOrVertical,
  color: Color = MaterialTheme.colorScheme.surfaceContainer,
  modifier: Modifier,
  content: @Composable RowScope.() -> Unit
) {
  Box(modifier = modifier) {
    val scrollState = rememberScrollState()
    Row(
      verticalAlignment = verticalAlignment,
      horizontalArrangement = horizontalArrangement,
      modifier = Modifier
        .fillMaxSize()
        .horizontalScroll(scrollState)
    ) {
      content()
    }

    val gradientWidth = 8.dp.value
    val gradientColor = Color.Transparent
    Box(
      Modifier
        .matchParentSize()
        .drawBehind {
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(color, gradientColor),
              startX = 0f,
              endX = gradientWidth
            )
          )
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(gradientColor, color),
              startX = size.width - gradientWidth,
              endX = size.width
            )
          )
        }
    )
  }
}