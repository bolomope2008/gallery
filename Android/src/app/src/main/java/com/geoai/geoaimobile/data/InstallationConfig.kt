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

/**
 * Configuration for model installation.
 * Allows easy switching between debug (local file) and production (remote URL) sources.
 */
object InstallationConfig {
    /**
     * Source URL for the model file.
     * - Debug: Uses local file from app's external files directory
     * - Production: Will use remote URL from server
     */
    const val MODEL_SOURCE_URL = "file:///storage/emulated/0/Android/data/com.geoai.geoaimobile/files/gemma-3n-E2B-it-int4.task"
    
    // Production example (commented out for debug):
    // const val MODEL_SOURCE_URL = "https://your-server.com/models/gemma-3n-E2B-it-int4.task"
    
    /**
     * SharedPreferences key for tracking installation status
     */
    const val PREF_KEY_MODEL_INSTALLED = "model_installation_complete"
    const val PREF_KEY_INSTALLATION_TIMESTAMP = "model_installation_timestamp"
    const val PREF_KEY_INSTALLATION_VERSION = "model_installation_version"
    
    /**
     * Installation version - increment this to force re-installation on app update
     */
    const val INSTALLATION_VERSION = 1
    
    /**
     * Timeout for model download/copy operation (in milliseconds)
     */
    const val INSTALLATION_TIMEOUT_MS = 600000L // 10 minutes
}