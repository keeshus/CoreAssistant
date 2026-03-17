package nl.codeinfinity.coreassistant

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ModelUtils {
    private const val TAG = "ModelUtils"

    fun copyModelsFromAssets(context: Context) {
        val targetDir = File(context.getExternalFilesDir(null), "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        // Since models is set as srcDir in build.gradle, its contents (stt, tts) are at the asset root
        copyAssetFolder(context, "tts", targetDir)
        copyAssetFolder(context, "stt", targetDir)
        copyAssetFolder(context, "vad", targetDir)
    }

    private fun copyAssetFolder(context: Context, assetPath: String, targetDir: File) {
        try {
            val assets = context.assets.list(assetPath) ?: return
            if (assets.isEmpty()) {
                // It's a file
                copyAssetFile(context, assetPath, File(targetDir, assetPath))
            } else {
                // It's a folder
                val folder = File(targetDir, assetPath)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                for (asset in assets) {
                    copyAssetFolder(context, "$assetPath/$asset", targetDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy assets from $assetPath", e)
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        if (targetFile.exists()) return // Skip if already exists

        try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Copied asset: $assetPath to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset file $assetPath", e)
        }
    }
}
