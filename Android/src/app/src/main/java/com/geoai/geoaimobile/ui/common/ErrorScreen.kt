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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.system.exitProcess

enum class ErrorType {
    MODEL_NOT_FOUND,
    MODEL_LOAD_FAILED,
    INSUFFICIENT_RESOURCES
}

@Composable
fun ErrorScreen(
    errorType: ErrorType,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    customMessage: String? = null
) {
    val (title, message) = when (errorType) {
        ErrorType.MODEL_NOT_FOUND -> Pair(
            "Model Not Found",
            "Critical model file not found. Please reinstall the application."
        )
        ErrorType.MODEL_LOAD_FAILED -> Pair(
            "Model Loading Failed", 
            "Failed to initialize AI model. Please try again."
        )
        ErrorType.INSUFFICIENT_RESOURCES -> Pair(
            "Insufficient Resources",
            "Failed to initialize AI model. Your device may not have enough resources."
        )
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Error,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = customMessage ?: message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                when (errorType) {
                    ErrorType.MODEL_NOT_FOUND -> {
                        Button(
                            onClick = { exitProcess(0) }
                        ) {
                            Text("Close App")
                        }
                    }
                    ErrorType.MODEL_LOAD_FAILED -> {
                        if (onRetry != null) {
                            Button(onClick = onRetry) {
                                Text("Retry")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { exitProcess(0) }
                        ) {
                            Text("Quit")
                        }
                    }
                    ErrorType.INSUFFICIENT_RESOURCES -> {
                        Button(
                            onClick = { exitProcess(0) }
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}