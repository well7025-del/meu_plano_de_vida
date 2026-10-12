package br.com.mercadinhofacil.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import br.com.mercadinhofacil.servicos.Impressora
import kotlinx.coroutines.launch

/**
 * Devolve uma função "imprimir(bytes)" que pede a permissão do Bluetooth
 * quando precisa e avisa o resultado na tela.
 */
@Composable
fun rememberImpressao(aoPrecisarEscolher: () -> Unit): (ByteArray) -> Unit {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var pendente by remember { mutableStateOf<ByteArray?>(null) }

    fun enviar(bytes: ByteArray) {
        if (app.prefs.impressoraEndereco.isBlank()) {
            aviso(context, "Escolha a impressora primeiro.")
            aoPrecisarEscolher()
            return
        }
        aviso(context, "Imprimindo…")
        escopo.launch {
            val erro = Impressora.imprimir(context, app.prefs.impressoraEndereco, bytes)
            aviso(context, erro ?: "Impresso!")
        }
    }

    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val b = pendente
        pendente = null
        if (r.values.all { it } && b != null) enviar(b) else aviso(context, "Sem permissão do Bluetooth não dá para imprimir.")
    }

    return { bytes ->
        if (Impressora.temPermissao(context)) enviar(bytes)
        else {
            pendente = bytes
            permissao.launch(Impressora.permissoesNecessarias())
        }
    }
}
