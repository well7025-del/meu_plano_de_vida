package br.com.mercadinhofacil.voz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import br.com.mercadinhofacil.core.Dinheiro
import java.util.Locale

/** Pedido de reconhecimento em português, preferindo o modo sem internet. */
fun intencaoDeVoz(comTela: Boolean): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    // Dá tempo para a pessoa pensar antes de falar e no meio da frase.
    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    if (comTela) putExtra(RecognizerIntent.EXTRA_PROMPT, "Pode falar…")
    else putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
}

/**
 * Reconhecimento de fala dentro da tela do app.
 *
 * Alguns celulares (vários Xiaomi, por exemplo) devolvem "não entendi" no
 * mesmo instante em que o microfone abre, antes da pessoa falar. Quando o erro
 * chega cedo demais, o ouvinte simplesmente tenta de novo, sem mostrar nada.
 * Se o reconhecedor do aparelho não funcionar de jeito nenhum, avisa com
 * [aoFalhar] para a tela usar a janela de voz do Google.
 */
class Ouvinte(private val context: Context) {

    private var reconhecedor: SpeechRecognizer? = null
    private val principal = Handler(Looper.getMainLooper())

    fun disponivel(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * @param aoFalhar mensagem para a pessoa e se vale a pena usar a janela do Google.
     */
    fun ouvir(
        aoParcial: (String) -> Unit,
        aoTerminar: (String) -> Unit,
        aoFalhar: (mensagem: String, usarJanela: Boolean) -> Unit,
        aoNivel: (Float) -> Unit = {},
    ) {
        iniciar(aoParcial, aoTerminar, aoFalhar, aoNivel, tentativa = 1)
    }

    private fun iniciar(
        aoParcial: (String) -> Unit,
        aoTerminar: (String) -> Unit,
        aoFalhar: (String, Boolean) -> Unit,
        aoNivel: (Float) -> Unit,
        tentativa: Int,
    ) {
        parar()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            aoFalhar("O reconhecimento de voz deste celular não abriu.", true); return
        }
        reconhecedor = r
        val abriuEm = SystemClock.elapsedRealtime()
        var falou = false
        var ultimoParcial = ""

        r.setRecognitionListener(object : RecognitionListener {
            // Ignora o que chega de um reconhecedor antigo, já descartado.
            private fun atual() = reconhecedor === r

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { falou = true }
            override fun onRmsChanged(rmsdB: Float) { if (atual()) aoNivel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (!atual()) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { ultimoParcial = it; falou = true; aoParcial(it) }
            }

            override fun onResults(results: Bundle?) {
                if (!atual()) return
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    .orEmpty().ifBlank { ultimoParcial }
                parar()
                if (texto.isBlank()) aoFalhar("Não ouvi nada. Toque no microfone e fale de novo.", false) else aoTerminar(texto)
            }

            override fun onError(error: Int) {
                if (!atual()) return
                val decorrido = SystemClock.elapsedRealtime() - abriuEm
                // Se já tinha ouvido parte da frase, usa o que ouviu.
                if (ultimoParcial.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    parar(); aoTerminar(ultimoParcial); return
                }
                val erroPrecoce = !falou && decorrido < 3000 && error in setOf(
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                )
                if (erroPrecoce && tentativa < 3) {
                    parar()
                    principal.postDelayed({ iniciar(aoParcial, aoTerminar, aoFalhar, aoNivel, tentativa + 1) }, 350)
                    return
                }
                parar()
                when {
                    erroPrecoce -> aoFalhar("O microfone deste celular fecha sozinho. Vou usar a janela de voz do Google.", true)
                    error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> aoFalhar("Preciso da permissão do microfone.", false)
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        aoFalhar("Não entendi. Toque no microfone e fale de novo, com calma.", false)
                    error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_SERVER ->
                        aoFalhar(
                            "O reconhecimento de voz deste celular precisou de internet. Para usar sem internet, baixe o pacote " +
                                "\"Português (Brasil)\" offline nas configurações de voz do Google.",
                            false,
                        )
                    else -> aoFalhar("O reconhecimento de voz do celular deu erro (código $error). Vou usar a janela de voz do Google.", true)
                }
            }
        })
        try {
            r.startListening(intencaoDeVoz(comTela = false))
        } catch (e: Exception) {
            parar()
            aoFalhar("O reconhecimento de voz deste celular não abriu.", true)
        }
    }

    fun parar() {
        principal.removeCallbacksAndMessages(null)
        val r = reconhecedor ?: return
        reconhecedor = null
        runCatching { r.cancel() }
        runCatching { r.destroy() }
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
