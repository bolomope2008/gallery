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

package com.geoai.geoaimobile.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.geoai.geoaimobile.data.FileIntegrityVerifier
import com.geoai.geoaimobile.data.FirebaseStorageDownloadService
import com.geoai.geoaimobile.data.InstallationConfig
import com.geoai.geoaimobile.data.PreloadedModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.URL

/**
 * Data class for progress updates
 */
data class ProgressUpdate(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val downloadSpeed: Long = 0L // bytes per second
)

/**
 * Worker responsible for installing the preloaded model during app's first run.
 * In debug mode, copies from local storage. In production, downloads from remote server.
 */
class ModelInstallationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firebaseStorageService: FirebaseStorageDownloadService
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "ModelInstallationWorker"
        const val WORK_NAME = "model_installation"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_DOWNLOAD_PHASE = "download_phase"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting model installation from: ${InstallationConfig.MODEL_SOURCE}")
            
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
            val actualFile = if (InstallationConfig.USE_FIREBASE) {
                installFromFirebaseStorage(targetFile)
            } else if (InstallationConfig.MODEL_SOURCE.startsWith("file://")) {
                targetFile.takeIf { installFromLocalFile(targetFile) }
            } else {
                targetFile.takeIf { installFromRemoteUrl(targetFile) }
            }
            
            if (actualFile != null) {
                // Verify file integrity if enabled
                if (InstallationConfig.VERIFY_MODEL_INTEGRITY) {
                    val integrityValid = verifyModelIntegrity(actualFile)
                    if (!integrityValid) {
                        Log.e(TAG, "Model integrity verification failed")
                        // Clean up corrupted file
                        if (actualFile.exists()) {
                            actualFile.delete()
                        }
                        return@withContext Result.failure()
                    }
                }
                
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
     * Installs model from remote server (production mode)
     * @return The actual file that was downloaded, or null if failed
     */
    private suspend fun installFromFirebaseStorage(targetFile: File): File? {
        var actualTargetFile: File = targetFile // Initialize with original target
        
        return try {
            // Extract folder path from gs:// URL
            val storageUrl = InstallationConfig.MODEL_SOURCE
            if (!storageUrl.startsWith("gs://")) {
                Log.e(TAG, "Invalid Firebase Storage URL: $storageUrl")
                return null
            }
            
            // Parse gs://bucket/folder format
            val pathParts = storageUrl.removePrefix("gs://").split("/", limit = 2)
            if (pathParts.size < 2) {
                Log.e(TAG, "Invalid Firebase Storage URL format: $storageUrl")
                return null
            }
            
            val folderPath = pathParts[1] // This is the folder within the bucket
            Log.d(TAG, "Searching for .task files in Firebase Storage folder: $folderPath")
            
            // Set initial progress
            setProgress(workDataOf(
                KEY_DOWNLOAD_PHASE to "Discovering model...",
                KEY_PROGRESS to 0
            ))
            
            // Discover the .task file in the folder
            val actualModelPath = firebaseStorageService.discoverTaskFile(folderPath)
            if (actualModelPath == null) {
                Log.e(TAG, "No .task file found in server folder: $folderPath")
                setProgress(workDataOf(
                    KEY_DOWNLOAD_PHASE to "Model not found on server",
                    KEY_PROGRESS to -1
                ))
                return null
            }
            
            Log.d(TAG, "Found model file: $actualModelPath")
            
            // Extract filename and update PreloadedModel
            val modelFileName = actualModelPath.substringAfterLast("/")
            PreloadedModel.setActualModelName(modelFileName)
            Log.d(TAG, "Set model name to: $modelFileName")
            
            // Update target file path to use the actual filename
            actualTargetFile = File(targetFile.parent, modelFileName)
            Log.d(TAG, "Updated target file path to: ${actualTargetFile.absolutePath}")
            
            // Set download progress
            setProgress(workDataOf(
                KEY_DOWNLOAD_PHASE to "Connecting to server...",
                KEY_PROGRESS to 0
            ))
            
            // Create a channel for thread-safe progress updates
            val progressChannel = Channel<ProgressUpdate>(Channel.UNLIMITED)
            var downloadStartTime = System.currentTimeMillis()
            
            // Launch a coroutine to handle progress updates
            val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val progressJob = progressScope.launch {
                progressChannel.consumeAsFlow().collect { progress ->
                    val currentTime = System.currentTimeMillis()
                    val elapsedSeconds = (currentTime - downloadStartTime) / 1000.0
                    val downloadSpeed = if (elapsedSeconds > 0) {
                        (progress.bytesDownloaded / elapsedSeconds).toLong()
                    } else 0L
                    
                    val progressPercent = if (progress.totalBytes > 0) {
                        ((progress.bytesDownloaded * 100) / progress.totalBytes).toInt()
                    } else 0
                    
                    setProgress(workDataOf(
                        KEY_PROGRESS to progressPercent,
                        KEY_BYTES_DOWNLOADED to progress.bytesDownloaded,
                        KEY_TOTAL_BYTES to progress.totalBytes,
                        KEY_DOWNLOAD_PHASE to "Downloading AI model..."
                    ))
                }
            }
            
            // Set initial download progress
            setProgress(workDataOf(
                KEY_DOWNLOAD_PHASE to "Downloading AI model...",
                KEY_PROGRESS to 0
            ))
            
            downloadStartTime = System.currentTimeMillis()
            
            val success = firebaseStorageService.downloadFile(
                storagePath = actualModelPath,
                targetFile = actualTargetFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    // Send progress update through channel (thread-safe)
                    progressChannel.trySend(ProgressUpdate(bytesDownloaded, totalBytes))
                }
            )
            
            // Close the progress channel
            progressChannel.close()
            progressJob.cancel()
            
            if (success) {
                Log.d(TAG, "Successfully downloaded model from server")
                setProgress(workDataOf(
                    KEY_DOWNLOAD_PHASE to "Download completed!",
                    KEY_PROGRESS to 100,
                    KEY_BYTES_DOWNLOADED to actualTargetFile.length(),
                    KEY_TOTAL_BYTES to actualTargetFile.length()
                ))
                actualTargetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model from server", e)
            setProgress(workDataOf(
                KEY_DOWNLOAD_PHASE to "Download failed",
                KEY_PROGRESS to -1
            ))
            // Clean up partial file
            if (actualTargetFile.exists()) {
                actualTargetFile.delete()
            }
            null
        }
    }
    
    /**
     * Installs model from local file system (debug mode)
     */
    private suspend fun installFromLocalFile(targetFile: File): Boolean {
        return try {
            val sourceUri = URI(InstallationConfig.MODEL_SOURCE)
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
                        setProgress(workDataOf(
                            KEY_PROGRESS to progress,
                            KEY_BYTES_DOWNLOADED to bytesCopied,
                            KEY_TOTAL_BYTES to totalBytes,
                            KEY_DOWNLOAD_PHASE to "Installing AI model..."
                        ))
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
     * Downloads model from remote URL with resume capability
     */
    private suspend fun installFromRemoteUrl(targetFile: File): Boolean {
        return try {
            val url = URL(InstallationConfig.MODEL_SOURCE)
            Log.d(TAG, "Downloading from URL: $url")
            
            // Check if partial file exists for resumption
            val existingSize = if (targetFile.exists()) targetFile.length() else 0L
            Log.d(TAG, "Existing file size: $existingSize bytes")
            
            val connection = url.openConnection()
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = InstallationConfig.INSTALLATION_TIMEOUT_MS.toInt()
            
            // Add range header for resume if file exists
            if (existingSize > 0) {
                connection.setRequestProperty("Range", "bytes=$existingSize-")
                Log.d(TAG, "Requesting range: bytes=$existingSize-")
            }
            
            val contentLength = connection.contentLengthLong
            val totalBytes = if (existingSize > 0) existingSize + contentLength else contentLength
            var bytesDownloaded = existingSize
            
            connection.getInputStream().use { input ->
                FileOutputStream(targetFile, existingSize > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        // Report progress
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            setProgress(workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_BYTES_DOWNLOADED to bytesDownloaded,
                                KEY_TOTAL_BYTES to totalBytes,
                                KEY_DOWNLOAD_PHASE to "Downloading AI model..."
                            ))
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
    
    /**
     * Verifies the integrity of the downloaded model file
     */
    private suspend fun verifyModelIntegrity(targetFile: File): Boolean {
        return try {
            Log.d(TAG, "Starting model integrity verification")
            
            setProgress(workDataOf(
                KEY_DOWNLOAD_PHASE to "Verifying model integrity...",
                KEY_PROGRESS to -1  // Indeterminate during verification
            ))
            
            // Basic file validation first
            if (!FileIntegrityVerifier.validateFileBasics(targetFile)) {
                return false
            }
            
            // If checksum is provided, verify it
            val expectedChecksum = InstallationConfig.MODEL_CHECKSUM_SHA256
            if (expectedChecksum != null && expectedChecksum.isNotEmpty()) {
                Log.d(TAG, "Performing SHA-256 checksum verification")
                
                val checksumValid = FileIntegrityVerifier.verifyFileSHA256(
                    file = targetFile,
                    expectedChecksum = expectedChecksum
                )
                
                if (!checksumValid) {
                    Log.e(TAG, "Checksum verification failed")
                    return false
                }
            } else {
                Log.d(TAG, "No checksum provided, skipping checksum verification")
            }
            
            Log.d(TAG, "Model integrity verification passed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during integrity verification", e)
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

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): ModelInstallationWorker
    }
}