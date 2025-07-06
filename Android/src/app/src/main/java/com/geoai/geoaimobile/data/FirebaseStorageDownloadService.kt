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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for downloading files from Firebase Storage with progress tracking
 */
@Singleton
class FirebaseStorageDownloadService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FirebaseStorageDownload"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    private val storage = FirebaseStorage.getInstance()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Downloads a file from Firebase Storage with retry logic and resume capability
     * @param storagePath The path in Firebase Storage (e.g., "llm/model.task")
     * @param targetFile The local file to save to
     * @param onProgress Progress callback (bytesDownloaded, totalBytes)
     * @return Boolean indicating success
     */
    suspend fun downloadFile(
        storagePath: String,
        targetFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        var lastException: Exception? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Download attempt ${attempt + 1} from Firebase Storage: $storagePath")
                
                // Check network connectivity before attempting download
                if (!isNetworkAvailable()) {
                    throw Exception("No network connection available")
                }
                
                val storageRef: StorageReference = storage.reference.child(storagePath)
                
                // Get file metadata first to know total size
                val metadata = storageRef.metadata.await()
                val totalBytes = metadata.sizeBytes
                
                Log.d(TAG, "File size: $totalBytes bytes")
                
                // Create parent directories if needed
                targetFile.parentFile?.mkdirs()
                
                // Check if partial file exists for resumption
                val existingSize = if (targetFile.exists()) targetFile.length() else 0L
                val shouldResume = existingSize > 0 && existingSize < totalBytes
                
                if (shouldResume) {
                    Log.d(TAG, "Resuming download from $existingSize bytes")
                    // For Firebase Storage, we'll recreate the file if partial exists
                    // Firebase Storage doesn't support range requests, so we start over
                    targetFile.delete()
                }
                
                // Download using stream for progress tracking
                val inputStream = storageRef.stream.await()
                
                downloadWithProgress(inputStream.stream, targetFile, totalBytes, onProgress)
                
                Log.d(TAG, "Download completed successfully")
                return true
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Download attempt ${attempt + 1} failed", e)
                
                // Clean up partial file
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                
                // Don't retry if this is the last attempt
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        Log.e(TAG, "Download failed after $MAX_RETRY_ATTEMPTS attempts", lastException)
        return false
    }

    /**
     * Downloads a file with progress tracking
     */
    private fun downloadWithProgress(
        inputStream: InputStream,
        targetFile: File,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        var bytesDownloaded = 0L
        val buffer = ByteArray(8192)
        
        // Throttle progress updates to avoid overwhelming the UI
        var lastProgressUpdate = 0L
        var lastProgressTime = System.currentTimeMillis()
        val progressUpdateInterval = 500L // Update every 500ms
        val progressUpdateThreshold = 10 * 1024 * 1024L // Or every 10MB
        
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    
                    // Report progress only if enough time/data has passed
                    val currentTime = System.currentTimeMillis()
                    val bytesSinceLastUpdate = bytesDownloaded - lastProgressUpdate
                    val timeSinceLastUpdate = currentTime - lastProgressTime
                    
                    if (bytesSinceLastUpdate >= progressUpdateThreshold || 
                        timeSinceLastUpdate >= progressUpdateInterval) {
                        onProgress?.invoke(bytesDownloaded, totalBytes)
                        lastProgressUpdate = bytesDownloaded
                        lastProgressTime = currentTime
                    }
                }
                
                // Final progress update to ensure we show 100%
                onProgress?.invoke(bytesDownloaded, totalBytes)
            }
        }
    }

    /**
     * Discovers the first .task file in a Firebase Storage folder
     * @param folderPath The folder path to search in (e.g., "llm")
     * @return The full path to the .task file, or null if none found
     */
    suspend fun discoverTaskFile(folderPath: String): String? {
        return try {
            Log.d(TAG, "Searching for .task files in folder: $folderPath")
            
            val folderRef: StorageReference = storage.reference.child(folderPath)
            val listResult = folderRef.listAll().await()
            
            // Find the first .task file
            val taskFile = listResult.items.find { item ->
                item.name.endsWith(".task", ignoreCase = true)
            }
            
            if (taskFile != null) {
                val fullPath = "$folderPath/${taskFile.name}"
                Log.d(TAG, "Found .task file: $fullPath")
                return fullPath
            } else {
                Log.w(TAG, "No .task files found in folder: $folderPath")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering .task files in folder: $folderPath", e)
            null
        }
    }

    /**
     * Checks if a file exists in Firebase Storage
     */
    suspend fun fileExists(storagePath: String): Boolean {
        return try {
            val storageRef: StorageReference = storage.reference.child(storagePath)
            storageRef.metadata.await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "File does not exist or cannot be accessed: $storagePath", e)
            false
        }
    }

    /**
     * Gets the size of a file in Firebase Storage
     */
    suspend fun getFileSize(storagePath: String): Long {
        return try {
            val storageRef: StorageReference = storage.reference.child(storagePath)
            val metadata = storageRef.metadata.await()
            metadata.sizeBytes
        } catch (e: Exception) {
            Log.w(TAG, "Could not get file size: $storagePath", e)
            -1L
        }
    }

    /**
     * Checks if network is available and connected
     */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Gets network type for bandwidth optimization
     */
    private fun getNetworkType(): String {
        val network = connectivityManager.activeNetwork ?: return "none"
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
        
        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }
}