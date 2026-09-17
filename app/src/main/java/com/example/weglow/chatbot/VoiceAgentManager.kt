package com.example.weglow.chatbot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAgentManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val onRecognitionError: (String) -> Unit = {}
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceAgentManager)
                }
        }

        textToSpeech = TextToSpeech(context, this)
    }

    fun startListening() {
        val recognizer = speechRecognizer

        if (recognizer == null) {
            onRecognitionError(
                "Speech recognition isn't available on this device."
            )
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toString()
            )

            putExtra(
                RecognizerIntent.EXTRA_CALLING_PACKAGE,
                context.packageName
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
        }

        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsReady = true
        }
    }

    override fun onResults(results: Bundle?) {
        onListeningStateChanged(false)

        val matches =
            results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )

        if (!matches.isNullOrEmpty()) {
            onSpeechResult(matches[0])
        } else {
            onRecognitionError("Didn't catch that — try again.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onListeningStateChanged(true)
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        onListeningStateChanged(false)
    }

    override fun onError(error: Int) {
        onListeningStateChanged(false)

        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH ->
                "Didn't catch that — try again."

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Didn't hear anything — try again."

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Network issue — check your connection and try again."

            SpeechRecognizer.ERROR_AUDIO ->
                "Microphone error — try again."

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Still processing — wait a moment and try again."

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required for voice input."

            SpeechRecognizer.ERROR_CLIENT ->
                "Voice input was interrupted — try again."

            else ->
                "Voice input failed — try again."
        }

        onRecognitionError(message)
    }

    override fun onPartialResults(
        partialResults: Bundle?
    ) {}

    override fun onEvent(
        eventType: Int,
        params: Bundle?
    ) {}
}