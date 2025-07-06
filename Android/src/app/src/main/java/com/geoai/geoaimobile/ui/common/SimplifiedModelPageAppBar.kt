/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.geoai.geoaimobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MapsUgc
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geoai.geoaimobile.data.Model
import com.geoai.geoaimobile.data.Task

/**
 * Simplified app bar for the preloaded model.
 * Removes back button, model selection, and configuration options.
 * Only shows task type and optional reset button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedModelPageAppBar(
  task: Task,
  model: Model,
  inProgress: Boolean,
  modelPreparing: Boolean,
  modifier: Modifier = Modifier,
  isResettingSession: Boolean = false,
  onResetSessionClicked: (Model) -> Unit = {},
  canShowResetSessionButton: Boolean = false,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  
  CenterAlignedTopAppBar(
    title = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Task type
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            task.icon ?: ImageVector.vectorResource(task.iconVectorResourceId!!),
            tint = getTaskIconColor(task = task),
            modifier = Modifier.size(16.dp),
            contentDescription = "",
          )
          Text(
            task.type.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = getTaskIconColor(task = task),
          )
        }
      }
    },
    modifier = modifier,
    // No navigation icon (back button removed)
    navigationIcon = {},
    // Only show reset button if enabled
    actions = {
      if (canShowResetSessionButton) {
        if (isResettingSession) {
          CircularProgressIndicator(
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
          )
        } else {
          val enableResetButton = !modelPreparing
          IconButton(
            onClick = { onResetSessionClicked(model) },
            enabled = enableResetButton,
            modifier = Modifier.alpha(if (!enableResetButton) 0.5f else 1f),
          ) {
            Icon(
              imageVector = Icons.Rounded.MapsUgc,
              contentDescription = "New Chat",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    },
    scrollBehavior = scrollBehavior,
  )
}