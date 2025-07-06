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
 * Allows easy switching between debug (local file) and production (Firebase Storage) sources.
 */
object InstallationConfig {
    /**
     * Build configuration - set to true for production, false for debug
     */
    private const val USE_FIREBASE_STORAGE = true // Using Firebase Storage for production
    
    /**
     * Firebase Storage configuration
     */
    private const val FIREBASE_STORAGE_BUCKET = "geoai-2e3da.firebasestorage.app"
    private const val FIREBASE_LLM_FOLDER = "llm" // Folder containing the .task model file
    
    /**
     * Local file path for debug mode (fallback filename)
     */
    private const val DEBUG_FILE_PATH = "file:///storage/emulated/0/Android/data/com.geoai.geoaimobile/files/model.task"
    
    /**
     * Source for the model file.
     * - Debug: Uses local file from app's external files directory
     * - Production: Uses Firebase Storage folder path (actual file will be discovered dynamically)
     */
    val MODEL_SOURCE = if (USE_FIREBASE_STORAGE) {
        "gs://$FIREBASE_STORAGE_BUCKET/$FIREBASE_LLM_FOLDER"
    } else {
        DEBUG_FILE_PATH
    }
    
    /**
     * Whether to use Firebase Storage for model download
     */
    val USE_FIREBASE = USE_FIREBASE_STORAGE
    
    /**
     * SharedPreferences key for tracking installation status
     */
    const val PREF_KEY_MODEL_INSTALLED = "model_installation_complete"
    const val PREF_KEY_INSTALLATION_TIMESTAMP = "model_installation_timestamp"
    const val PREF_KEY_INSTALLATION_VERSION = "model_installation_version"
    const val PREF_KEY_MODEL_FILENAME = "model_actual_filename" // Stores the actual filename from Firebase
    
    /**
     * Installation version - increment this to force re-installation on app update
     */
    const val INSTALLATION_VERSION = 1
    
    /**
     * Timeout for model download/copy operation (in milliseconds)
     */
    const val INSTALLATION_TIMEOUT_MS = 600000L // 10 minutes
    
    /**
     * Expected SHA-256 checksum of the model file (optional for integrity verification)
     * Set to null to skip checksum verification
     */
    val MODEL_CHECKSUM_SHA256: String? = null // Add your model's SHA-256 here for production
    
    /**
     * Whether to perform integrity verification
     */
    const val VERIFY_MODEL_INTEGRITY = false // Set to true for production
}