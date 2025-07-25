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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.geoai.geoaimobile.ui.common.MarkdownText

/**
 * Composable function to display warning message content within a chat.
 *
 * Supports markdown.
 */
@Composable
fun MessageBodyWarning(message: ChatMessageWarning) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.tertiaryContainer)
    ) {
      MarkdownText(
        text = message.content,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        smallFontSize = true,
      )
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyWarningPreview() {
//   GalleryTheme {
//     Row(modifier = Modifier.padding(16.dp)) {
//       MessageBodyWarning(message = ChatMessageWarning(content = "This is a warning"))
//     }
//   }
// }
