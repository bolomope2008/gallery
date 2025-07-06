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

package com.geoai.geoaimobile.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.geoai.geoaimobile.ui.theme.GalleryTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geoai.geoaimobile.R
import com.geoai.geoaimobile.ui.theme.customColors

/**
 * Composable function to display a visual indicator for model initialization status.
 *
 * This function renders a row containing a circular progress indicator and a message indicating
 * that the model is currently initializing. It provides a visual cue to the user that the model is
 * in a loading state, with specific information about the backend (CPU/GPU) being used.
 */
@Composable
fun ModelInitializationStatusChip(
  backend: String = "",
  phase: String = "",
  progress: Int = 0,
  phaseDetail: String = ""
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(
      modifier =
        Modifier.padding(8.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
      Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Circular progress indicator with determinate progress and green color
        CircularProgressIndicator(
          progress = { if (progress > 0) progress / 100f else 0f },
          modifier = Modifier.size(14.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.customColors.successColor,
          trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Dynamic text message with progress and detailed phase info
        val message = when {
          phaseDetail.isNotEmpty() && progress > 0 -> "$phaseDetail ($progress%)"
          phaseDetail.isNotEmpty() -> phaseDetail
          backend == "GPU" && phase == "loading" -> "Loading model for GPU ($progress%)"
          backend == "CPU" && phase == "loading" -> "Loading model for CPU ($progress%)"
          backend == "GPU" && phase == "creating" -> "Creating GPU inference engine ($progress%)"
          backend == "CPU" && phase == "creating" -> "Creating CPU inference engine ($progress%)"
          backend == "GPU" && phase == "optimizing" -> "Optimizing for GPU acceleration ($progress%)"
          backend == "CPU" && phase == "optimizing" -> "Preparing CPU processing ($progress%)"
          backend.isNotEmpty() && progress > 0 -> "Initializing on $backend ($progress%)"
          backend.isNotEmpty() -> "Initializing on $backend"
          progress > 0 -> "Initializing AI model ($progress%)"
          else -> "Initializing AI model..."
        }
        
        Text(
          message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun ModelInitializationStatusPreview() {
//   GalleryTheme { ModelInitializationStatusChip() }
// }
