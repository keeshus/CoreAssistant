package nl.codeinfinity.coreassistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@android.annotation.SuppressLint("MissingPermission")
@Composable
fun VoiceRecorderHandler(
    isListening: Boolean,
    sherpaManager: SherpaManager,
    onVolumeUpdate: (Float) -> Unit,
    onTranscriptionComplete: (String) -> Unit,
    onError: (Exception) -> Unit,
    onStopListening: () -> Unit
) {
    LaunchedEffect(isListening) {
        if (isListening) {
            withContext(Dispatchers.IO) {
                try {
                    val sampleRate = 16000
                    val bufferSize = AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )

                    sherpaManager.resetVad()
                    audioRecord.startRecording()
                    val buffer = ShortArray(bufferSize)

                    while (isListening) {
                        val read = audioRecord.read(buffer, 0, bufferSize)
                        if (read > 0) {
                            var sumSq = 0f
                            val floats = FloatArray(read) {
                                val floatVal = buffer[it] / 32767.0f
                                sumSq += floatVal * floatVal
                                floatVal
                            }

                            val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                            onVolumeUpdate(rms)

                            val transcription = sherpaManager.processAudioAndTranscribe(floats)
                            if (!transcription.isNullOrEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onStopListening()
                                    onTranscriptionComplete(transcription)
                                }
                            }
                        }
                    }
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onStopListening()
                        onError(e)
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.AnimatedMicOverlay(
    isListening: Boolean,
    currentVolume: Float
) {
    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            val targetScale = 1f + (currentVolume * 10f).coerceIn(0f, 1f)
            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 100),
                label = "MicScale"
            )

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Listening",
                modifier = Modifier
                    .padding(32.dp)
                    .scale(animatedScale)
                    .size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
