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

package com.google.ai.edge.gallery.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.data.InstallationConfig
import com.google.ai.edge.gallery.data.PreloadedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.URL

/**
 * Worker responsible for installing the preloaded model during app's first run.
 * In debug mode, copies from local storage. In production, downloads from URL.
 */
class ModelInstallationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "ModelInstallationWorker"
        const val WORK_NAME = "model_installation"
        const val KEY_PROGRESS = "progress"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting model installation from: ${InstallationConfig.MODEL_SOURCE_URL}")
            
            // Get target file path
            val targetFile = File(PreloadedModel.getModelPath(applicationContext))
            
            // Check if already installed
            if (targetFile.exists()) {
                Log.d(TAG, "Model already exists at target location")
                return@withContext Result.success()
            }
            
            // Create parent directory if needed
            targetFile.parentFile?.mkdirs()
            
            // Install based on source type
            val success = if (InstallationConfig.MODEL_SOURCE_URL.startsWith("file://")) {
                installFromLocalFile(targetFile)
            } else {
                installFromRemoteUrl(targetFile)
            }
            
            if (success) {
                Log.d(TAG, "Model installation completed successfully")
                Result.success()
            } else {
                Log.e(TAG, "Model installation failed")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model installation error", e)
            Result.failure()
        }
    }
    
    /**
     * Installs model from local file system (debug mode)
     */
    private suspend fun installFromLocalFile(targetFile: File): Boolean {
        return try {
            val sourceUri = URI(InstallationConfig.MODEL_SOURCE_URL)
            val sourcePath = sourceUri.path ?: return false
            val sourceFile = File(sourcePath)
            
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found: $sourcePath")
                return false
            }
            
            Log.d(TAG, "Copying from local file: $sourcePath")
            
            var bytesCopied = 0L
            val totalBytes = sourceFile.length()
            
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        
                        // Report progress
                        val progress = ((bytesCopied * 100) / totalBytes).toInt()
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }
            
            Log.d(TAG, "Successfully copied $bytesCopied bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy from local file", e)
            // Clean up partial file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            false
        }
    }
    
    /**
     * Downloads model from remote URL (production mode)
     */
    private suspend fun installFromRemoteUrl(targetFile: File): Boolean {
        return try {
            val url = URL(InstallationConfig.MODEL_SOURCE_URL)
            Log.d(TAG, "Downloading from URL: $url")
            
            val connection = url.openConnection()
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = InstallationConfig.INSTALLATION_TIMEOUT_MS.toInt()
            
            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L
            
            connection.getInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        // Report progress
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                        }
                    }
                }
            }
            
            Log.d(TAG, "Successfully downloaded $bytesDownloaded bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from URL", e)
            // Clean up partial file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            false
        }
    }
    
    private fun workDataOf(vararg pairs: Pair<String, Any?>): androidx.work.Data {
        return androidx.work.Data.Builder().apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Double -> putDouble(key, value)
                }
            }
        }.build()
    }
}