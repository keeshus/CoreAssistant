package nl.codeinfinity.coreassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object DownloadManager {
    private const val TAG = "DownloadManager"

    suspend fun downloadAndExtractModels(context: Context, url: String, checksumUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(context.cacheDir, "models.tar.gz")
        val extractDir = File(context.getExternalFilesDir(null), "downloaded_models")
        try {
            downloadFile(url, targetFile, onProgress)
            
            // Verify checksum
            val expectedChecksum = downloadString(checksumUrl).trim().split("\\s+".toRegex())[0]
            val actualChecksum = calculateSha256(targetFile)
            
            if (!expectedChecksum.equals(actualChecksum, ignoreCase = true)) {
                Log.e(TAG, "Checksum mismatch! Expected: $expectedChecksum, Actual: $actualChecksum")
                targetFile.delete()
                return@withContext false
            }

            extractTarGz(targetFile, extractDir)
            targetFile.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/extract models", e)
            targetFile.delete()
            false
        }
    }

    private fun downloadFile(urlString: String, targetFile: File, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        val fileLength = connection.contentLength

        val input = BufferedInputStream(connection.inputStream)
        val output = FileOutputStream(targetFile)

        val data = ByteArray(8192)
        var total: Long = 0
        var count: Int
        while (input.read(data).also { count = it } != -1) {
            total += count
            if (fileLength > 0) {
                onProgress((total * 100f / fileLength) / 100f)
            }
            output.write(data, 0, count)
        }
        output.flush()
        output.close()
        input.close()
        connection.disconnect()
    }

    private fun downloadString(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return text
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesCount: Int
            while (fis.read(buffer).also { bytesCount = it } != -1) {
                digest.update(buffer, 0, bytesCount)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(tarGzFile.inputStream()))).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                val destFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (tarInput.read(buffer).also { len = it } != -1) {
                            out.write(buffer, 0, len)
                        }
                    }
                }
                entry = tarInput.nextTarEntry
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val destFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zipInput.read(buffer).also { len = it } != -1) {
                            out.write(buffer, 0, len)
                        }
                    }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
    }
}
