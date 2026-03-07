package nl.codeinfinity.coreassistant

import android.content.Intent
import android.speech.RecognitionService

class AssistantRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Handle starting STT
    }

    override fun onCancel(listener: Callback?) {
        // Handle STT cancel
    }

    override fun onStopListening(listener: Callback?) {
        // Handle stopping STT
    }
}
