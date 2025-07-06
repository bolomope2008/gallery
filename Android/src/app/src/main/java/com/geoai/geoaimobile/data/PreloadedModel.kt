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

package com.geoai.geoaimobile.data

import android.content.Context
import java.io.File

/**
 * Configuration for the preloaded Gemma model.
 * This model is automatically loaded at app startup with fixed configuration.
 */
object PreloadedModel {
    const val MODEL_NAME = "gemma-3n-E2B-it-int4.task"
    const val MODEL_DISPLAY_NAME = "Gemma 3B"
    const val SOURCE_MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"
    
    // Fixed configuration values as per requirements
    const val MAX_TOKENS = 1024
    const val TOP_K = 40
    const val TOP_P = 0.90f
    const val TEMPERATURE = 1.0f
    
    // Model features
    const val SUPPORT_IMAGE = true
    const val SUPPORT_AUDIO = true
    
    // Accelerators
    val ACCELERATORS = listOf(Accelerator.CPU, Accelerator.GPU)
    val DEFAULT_ACCELERATOR = Accelerator.GPU
    
    /**
     * Creates a Model instance for the preloaded Gemma model
     */
    fun createModel(): Model {
        val configs = listOf(
            LabelConfig(key = ConfigKey.MAX_TOKENS, defaultValue = MAX_TOKENS.toString()),
            // These configs are displayed but not editable in the simplified UI
            LabelConfig(key = ConfigKey.TOPK, defaultValue = TOP_K.toString()),
            LabelConfig(key = ConfigKey.TOPP, defaultValue = TOP_P.toString()),
            LabelConfig(key = ConfigKey.TEMPERATURE, defaultValue = TEMPERATURE.toString()),
            LabelConfig(key = ConfigKey.ACCELERATOR, defaultValue = DEFAULT_ACCELERATOR.label)
        )
        
        val model = Model(
            name = MODEL_NAME,
            displayName = MODEL_DISPLAY_NAME,
            url = "", // No URL as it's preloaded
            configs = configs,
            sizeInBytes = 3136226711L, // Size from ls -la output
            downloadFileName = MODEL_NAME,
            showBenchmarkButton = false,
            showRunAgainButton = false,
            imported = false,
            llmSupportImage = SUPPORT_IMAGE,
            llmSupportAudio = SUPPORT_AUDIO,
            preloaded = true
        )
        
        // Set fixed config values
        model.configValues = mutableMapOf(
            ConfigKey.MAX_TOKENS.label to MAX_TOKENS,
            ConfigKey.TOPK.label to TOP_K,
            ConfigKey.TOPP.label to TOP_P,
            ConfigKey.TEMPERATURE.label to TEMPERATURE,
            ConfigKey.ACCELERATOR.label to DEFAULT_ACCELERATOR.label
        )
        
        model.preProcess()
        return model
    }
    
    /**
     * Gets the path where the model should be stored in app's external files directory
     */
    fun getModelPath(context: Context): String {
        val externalFilesDir = context.getExternalFilesDir(null)
        return File(externalFilesDir, MODEL_NAME).absolutePath
    }
    
    /**
     * Checks if the model file exists in the app's external files directory
     */
    fun isModelInstalled(context: Context): Boolean {
        return File(getModelPath(context)).exists()
    }
}