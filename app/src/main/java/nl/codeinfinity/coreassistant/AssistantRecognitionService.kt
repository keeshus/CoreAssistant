package nl.codeinfinity.coreassistant

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

class AssistantRecognitionService : RecognitionService() {
    private var recognitionJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var sherpaManager: SherpaManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        sherpaManager = SherpaManager(this)
        settingsManager = SettingsManager(this)
        
        val modelsDir = File(getExternalFilesDir(null), "models")
        
        // STT paths (Whisper)
        val sttDir = File(modelsDir, "stt")
        val encoderPath = File(sttDir, "small-encoder.int8.onnx").absolutePath
        val decoderPath = File(sttDir, "small-decoder.int8.onnx").absolutePath
        val tokensPath = File(sttDir, "small-tokens.txt").absolutePath
        
        // VAD path
        val vadModelPath = File(modelsDir, "vad/silero_vad.onnx").absolutePath
        
        if (File(encoderPath).exists()) {
            serviceScope.launch {
                val languagePref = settingsManager.sherpaLanguage.first()
                val whisperLang = languagePref.split("-").first().lowercase()
                sherpaManager.initStt(encoderPath, decoderPath, tokensPath, whisperLang)
            }
        }
        
        if (File(vadModelPath).exists()) {
            sherpaManager.initVad(vadModelPath)
        }
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        recognitionJob?.cancel()
        sherpaManager.resetVad()
        recognitionJob = serviceScope.launch {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            
            listener?.readyForSpeech(Bundle())
            audioRecord.startRecording()
            
            val buffer = ShortArray(bufferSize)
            
            // Simplified loop for background service
            while (isActive) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0) {
                    val floats = FloatArray(read) { buffer[it] / 32767.0f }
                    
                    val transcription = sherpaManager.processAudioAndTranscribe(floats)
                    if (!transcription.isNullOrEmpty()) {
                        val bundle = Bundle().apply {
                            putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(transcription))
                        }
                        listener?.results(bundle)
                        break
                    }
                }
            }
            
            // If the loop exits and we haven't broken out, try flushing
            if (!isActive) {
                val finalTranscription = sherpaManager.flushAndTranscribe()
                if (!finalTranscription.isNullOrEmpty()) {
                    val bundle = Bundle().apply {
                        putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(finalTranscription))
                    }
                    listener?.results(bundle)
                }
            }
            
            audioRecord.stop()
            audioRecord.release()
        }
    }

    override fun onCancel(listener: Callback?) {
        recognitionJob?.cancel()
        recognitionJob = null
    }

    override fun onStopListening(listener: Callback?) {
        // In a real implementation, this would trigger the final transcription
        recognitionJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognitionJob?.cancel()
    }
}
