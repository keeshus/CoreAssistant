package nl.codeinfinity.coreassistant

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaManager(private val context: Context) {
    
    companion object {
        @Volatile private var tts: OfflineTts? = null
        @Volatile private var currentTtsModelPath: String? = null
        @Volatile private var stt: OfflineRecognizer? = null
        @Volatile private var vad: Vad? = null
    }

    fun isTtsReady(): Boolean {
        return tts != null
    }

    fun isSttReady(): Boolean {
        return stt != null
    }

    suspend fun initTts(modelPath: String, tokensPath: String, dataDir: String) = withContext(Dispatchers.IO) {
        if (tts != null && currentTtsModelPath == modelPath) return@withContext
        try {
            // If there's an existing tts with a different model, release it if possible, then clear it
            if (tts != null) {
                try {
                    tts?.release()
                } catch (e: Exception) {
                    // Ignore if release is not available
                }
                tts = null
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPath,
                        tokens = tokensPath,
                        dataDir = dataDir
                    )
                )
            )
            tts = OfflineTts(config = config)
            currentTtsModelPath = modelPath
            Log.d("SherpaManager", "TTS initialized successfully")
        } catch (e: Exception) {
            Log.e("SherpaManager", "Failed to init TTS", e)
        }
    }

    suspend fun initStt(encoderPath: String, decoderPath: String, tokensPath: String, language: String) = withContext(Dispatchers.IO) {
        if (stt != null) return@withContext
        try {
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoderPath,
                        decoder = decoderPath,
                        language = language
                    ),
                    tokens = tokensPath
                )
            )
            stt = OfflineRecognizer(config = config)
            Log.d("SherpaManager", "STT initialized successfully")
        } catch (e: Exception) {
            Log.e("SherpaManager", "Failed to init STT", e)
        }
    }

    fun initVad(modelPath: String) {
        if (vad != null) return
        try {
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelPath,
                    minSpeechDuration = 0.25f,
                    minSilenceDuration = 0.5f,
                    windowSize = 512,
                    threshold = 0.5f
                ),
                sampleRate = 16000,
                numThreads = 1
            )
            vad = Vad(config = config)
            Log.d("SherpaManager", "VAD initialized successfully")
        } catch (e: Exception) {
            Log.e("SherpaManager", "Failed to init VAD", e)
        }
    }

    fun resetVad() {
        vad?.clear()
        vad?.reset()
    }

    fun speak(text: String): GeneratedAudio? {
        return tts?.generate(text, 0)
    }

    fun transcribe(samples: FloatArray): String {
        val stream = stt?.createStream() ?: return ""
        stream.acceptWaveform(samples, 16000)
        stt?.decode(stream)
        val result = stt?.getResult(stream)
        val text = result?.text ?: ""
        stream.release()
        return text
    }

    suspend fun processAudioAndTranscribe(samples: FloatArray): String? {
        if (vad == null) {
            Log.e("SherpaManager", "VAD is null! Cannot process audio.")
            return null
        }
        vad?.acceptWaveform(samples)
        return getTranscribedText()
    }

    suspend fun flushAndTranscribe(): String? {
        vad?.flush()
        return getTranscribedText()
    }

    private suspend fun getTranscribedText(): String? {
        var fullText = ""
        while (vad?.empty() == false) {
            val segment = vad?.front() ?: break
            
            // Wait for STT to be loaded
            while (stt == null) {
                kotlinx.coroutines.delay(100)
            }
            
            val text = transcribe(segment.samples)
            if (text.isNotBlank()) {
                fullText += "$text "
            }
            vad?.pop()
        }
        return if (fullText.isNotBlank()) fullText.trim() else null
    }
}
