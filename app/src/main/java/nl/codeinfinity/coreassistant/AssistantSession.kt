package nl.codeinfinity.coreassistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.View

class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        // Here you would normally inflate a standard Android View
        // or embed a ComposeView to use Jetpack Compose for the assistant overlay overlay.
        return super.onCreateContentView()
    }

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Triggered when the assistant is invoked (e.g., long-press power button/home button)
    }

    override fun onHide() {
        super.onHide()
        // Triggered when the assistant overlay is hidden
    }
}
