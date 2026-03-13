package nl.codeinfinity.coreassistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.View

class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        // Return a simple empty view to satisfy the requirement,
        // as we immediately launch the main activity.
        return View(context)
    }

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        android.util.Log.d("AssistantSession", "onShow triggered, launching MainActivity into Voice Assistant Chat")
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_voice_assistant", true)
        }
        context.startActivity(intent)
        hide()
    }

    override fun onHide() {
        super.onHide()
        // Triggered when the assistant overlay is hidden
    }
}
