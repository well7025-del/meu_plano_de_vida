package br.com.mercadinhofacil.voz

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import br.com.mercadinhofacil.core.Dinheiro
import java.util.Locale

/**
 * Reconhecimento de fala do próprio Android, pedindo o modo no aparelho
 * (offline). Em celulares com o pacote de voz "Português (Brasil)" baixado,
 * nada do que é falado sai do celular.
 */
class Ouvinte(private val context: Context) {

    private var reconhecedor: SpeechRecognizer? = null

    fun disponivel(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun ouvir(
        aoParcial: (String) -> Unit,
        aoTerminar: (String) -> Unit,
        aoErro: (String) -> Unit,
        aoNivel: (Float) -> Unit = {},
    ) {
        parar()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = aoNivel(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(aoParcial)
            }

            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (texto.isBlank()) aoErro("Não ouvi nada. Toque no microfone e fale de novo.") else aoTerminar(texto)
            }

            override fun onError(error: Int) {
                aoErro(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi. Toque no microfone e fale de novo, com calma."
                        SpeechRecognizer.ERROR_AUDIO -> "Problema no microfone."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Preciso da permissão do microfone."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            "O reconhecimento de voz deste celular precisa de internet. Baixe o pacote de voz \"Português (Brasil)\" offline nas configurações do Google para usar sem internet."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O microfone está ocupado. Tente de novo."
                        else -> "Não consegui ouvir. Tente de novo ou digite o comando."
                    },
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        }
        r.startListening(intent)
    }

    fun parar() {
        reconhecedor?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        reconhecedor = null
    }
}

/** Fala as respostas em português, com a voz do próprio celular. */
class Falante(context: Context) {
    private var pronto = false
    private var pendente: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            pronto = true
            configurar()
            pendente?.let { falar(it) }
            pendente = null
        }
    }

    private fun configurar() {
        val br = Locale("pt", "BR")
        if (tts.isLanguageAvailable(br) >= TextToSpeech.LANG_AVAILABLE) tts.language = br
        tts.setSpeechRate(0.95f)
    }

    fun falar(texto: String) {
        // Asteriscos são negrito do WhatsApp; não devem ser lidos.
        val limpo = Regex("R\\$ ?[\\d.]+,\\d{2}").replace(texto) { m -> Dinheiro.ler(m.value)?.let { Dinheiro.porExtenso(it) } ?: m.value }
            .replace("*", "").replace("_", " ").replace("•", "")
        if (!pronto) { pendente = limpo; return }
        tts.speak(limpo, TextToSpeech.QUEUE_FLUSH, null, "resposta")
    }

    fun calar() {
        if (pronto) tts.stop()
    }

    fun encerrar() = tts.shutdown()
}
