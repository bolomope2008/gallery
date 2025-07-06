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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utility class for verifying file integrity using checksums
 */
object FileIntegrityVerifier {
    private const val TAG = "FileIntegrityVerifier"

    /**
     * Verifies the integrity of a file using SHA-256 checksum
     * @param file The file to verify
     * @param expectedChecksum The expected SHA-256 checksum (hex string)
     * @param onProgress Optional progress callback (bytesProcessed, totalBytes)
     * @return True if the file is valid, false otherwise
     */
    suspend fun verifyFileSHA256(
        file: File,
        expectedChecksum: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying file integrity: ${file.name}")
            Log.d(TAG, "Expected checksum: $expectedChecksum")
            
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: ${file.path}")
                return@withContext false
            }
            
            val actualChecksum = calculateSHA256(file, onProgress)
            Log.d(TAG, "Actual checksum: $actualChecksum")
            
            val isValid = actualChecksum.equals(expectedChecksum, ignoreCase = true)
            if (isValid) {
                Log.d(TAG, "File integrity verification passed")
            } else {
                Log.e(TAG, "File integrity verification failed! Checksums do not match.")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying file integrity", e)
            false
        }
    }

    /**
     * Calculates SHA-256 checksum of a file
     * @param file The file to calculate checksum for
     * @param onProgress Optional progress callback (bytesProcessed, totalBytes)
     * @return The SHA-256 checksum as a hex string
     */
    suspend fun calculateSHA256(
        file: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val totalBytes = file.length()
        var bytesProcessed = 0L
        
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesProcessed += bytesRead
                
                // Report progress
                onProgress?.invoke(bytesProcessed, totalBytes)
            }
        }
        
        // Convert bytes to hex string
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies file size matches expected size
     * @param file The file to check
     * @param expectedSize The expected file size in bytes
     * @return True if sizes match, false otherwise
     */
    fun verifyFileSize(file: File, expectedSize: Long): Boolean {
        val actualSize = file.length()
        val isValid = actualSize == expectedSize
        
        if (isValid) {
            Log.d(TAG, "File size verification passed: $actualSize bytes")
        } else {
            Log.e(TAG, "File size verification failed! Expected: $expectedSize, Actual: $actualSize")
        }
        
        return isValid
    }

    /**
     * Performs basic file validation checks
     * @param file The file to validate
     * @param minimumSize Minimum expected file size (default 1MB)
     * @return True if file passes basic validation
     */
    fun validateFileBasics(file: File, minimumSize: Long = 1024 * 1024): Boolean {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.path}")
            return false
        }
        
        if (!file.isFile()) {
            Log.e(TAG, "Path is not a file: ${file.path}")
            return false
        }
        
        if (!file.canRead()) {
            Log.e(TAG, "File is not readable: ${file.path}")
            return false
        }
        
        val fileSize = file.length()
        if (fileSize < minimumSize) {
            Log.e(TAG, "File is too small: $fileSize bytes (minimum: $minimumSize)")
            return false
        }
        
        if (fileSize == 0L) {
            Log.e(TAG, "File is empty: ${file.path}")
            return false
        }
        
        Log.d(TAG, "File basic validation passed: ${file.name} (${fileSize} bytes)")
        return true
    }
}