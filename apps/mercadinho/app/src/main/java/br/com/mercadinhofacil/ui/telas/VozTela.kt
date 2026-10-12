package br.com.mercadinhofacil.ui.telas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.SeletorForma
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.voz.Assistente
import br.com.mercadinhofacil.voz.Ouvinte
import br.com.mercadinhofacil.voz.Resposta
import kotlinx.coroutines.launch

private data class Fala(val deQuem: Boolean, val texto: String, val abrir: String? = null, val rotulo: String? = null)

/** Conversa com o assistente: fala ou digita, ele responde em texto e em voz. */
@Composable
fun TelaVoz(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val ouvinte = remember { Ouvinte(context) }
    val conversa = remember { mutableStateListOf(Fala(false, "Olá! Fale o que aconteceu ou o que quer saber. Diga \"ajuda\" para ver exemplos.")) }
    var ouvindo by remember { mutableStateOf(false) }
    var parcial by remember { mutableStateOf("") }
    var nivel by remember { mutableFloatStateOf(0f) }
    var digitado by remember { mutableStateOf("") }
    var pensando by remember { mutableStateOf(false) }
    var proposta by remember { mutableStateOf<Resposta.Confirmar?>(null) }
    var forma by remember { mutableStateOf<FormaPagamento?>(null) }
    val lista = rememberLazyListState()

    DisposableEffect(Unit) { onDispose { ouvinte.parar(); app.falante.calar() } }
    LaunchedEffect(conversa.size, proposta) { if (conversa.isNotEmpty()) lista.animateScrollToItem(conversa.size - 1) }

    fun responder(texto: String) {
        app.falante.calar()
        if (app.prefs.responderFalando) app.falante.falar(texto)
    }

    fun enviar(frase: String) {
        if (frase.isBlank()) return
        proposta = null
        conversa += Fala(true, frase)
        pensando = true
        escopo.launch {
            when (val r = app.assistente.responder(frase)) {
                is Resposta.Informacao -> {
                    conversa += Fala(false, r.texto, r.abrir, r.rotuloAbrir)
                    responder(r.texto)
                }
                is Resposta.Confirmar -> {
                    proposta = r
                    forma = r.formaInicial
                    responder(r.titulo.removeSuffix("?") + ", total ${r.total?.let { Dinheiro.formatar(it) } ?: ""}. Confere?")
                }
            }
            pensando = false
        }
    }

    fun ouvir() {
        app.falante.calar()
        if (!ouvinte.disponivel()) {
            conversa += Fala(false, "Este celular não tem reconhecimento de voz. Digite o comando na caixa abaixo.")
            return
        }
        ouvindo = true
        parcial = ""
        ouvinte.ouvir(
            aoParcial = { parcial = it },
            aoTerminar = { ouvindo = false; parcial = ""; enviar(it) },
            aoErro = { ouvindo = false; parcial = ""; conversa += Fala(false, it) },
            aoNivel = { nivel = it },
        )
    }

    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ouvir() else conversa += Fala(false, "Sem permissão do microfone não consigo ouvir. Você pode digitar o comando.")
    }

    fun tocarMicrofone() {
        if (ouvindo) { ouvinte.parar(); ouvindo = false; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ouvir()
        else permissao.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ouvir()
    }

    Tela("Falar com o assistente", { nav.voltar() }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = lista,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(conversa) { f -> Balao(f) { rota -> nav.ir(rota) } }
                proposta?.let { p ->
                    item {
                        CartaoConfirmacao(
                            p, forma, { forma = it },
                            aoConfirmar = {
                                proposta = null
                                pensando = true
                                escopo.launch {
                                    val r = try { p.executar(forma) } catch (e: Exception) {
                                        br.com.mercadinhofacil.voz.Execucao("Não deu certo: ${e.message}")
                                    }
                                    conversa += Fala(false, r.resposta, r.abrir, if (r.abrir?.startsWith("comprovante") == true) "Ver comprovante" else "Abrir")
                                    responder(r.resposta)
                                    pensando = false
                                }
                            },
                            aoCancelar = {
                                proposta = null
                                conversa += Fala(false, "Tudo bem, não registrei nada.")
                                responder("Tudo bem, não registrei nada.")
                            },
                        )
                    }
                }
                if (ouvindo && parcial.isNotBlank()) item { Balao(Fala(true, "$parcial…")) {} }
            }
            if (pensando) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val pulso by animateFloatAsState(if (ouvindo) 1f + (nivel.coerceIn(0f, 10f) / 40f) else 1f, label = "pulso")
                FilledIconButton(
                    onClick = { tocarMicrofone() },
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulso),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (ouvindo) Vermelho else Laranja),
                ) {
                    Icon(if (ouvindo) Icons.Filled.Stop else Icons.Filled.Mic, if (ouvindo) "Parar" else "Falar", Modifier.size(52.dp), tint = Color.White)
                }
                Text(if (ouvindo) "Pode falar… estou ouvindo" else "Toque no microfone para falar", fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = digitado,
                        onValueChange = { digitado = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ou digite aqui…") },
                        singleLine = true,
                    )
                    IconButton(onClick = { enviar(digitado); digitado = "" }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", Modifier.size(32.dp)) }
                }
                TextButton(onClick = { conversa += Fala(false, Assistente.AJUDA) }) { Text("O que posso falar?", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun Balao(f: Fala, abrir: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (f.deQuem) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (f.deQuem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    f.texto, fontSize = 19.sp, lineHeight = 26.sp,
                    color = if (f.deQuem) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (f.abrir != null) {
                    TextButton(onClick = { abrir(f.abrir) }) { Text(f.rotulo ?: "Abrir", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CartaoConfirmacao(
    p: Resposta.Confirmar,
    forma: FormaPagamento?,
    aoMudarForma: (FormaPagamento) -> Unit,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1), contentColor = Color(0xFF3E2723)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Laranja),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            p.linhas.forEach { Text("• $it", fontSize = 18.sp) }
            p.total?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x22E8710A), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text("Total", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(Dinheiro.formatar(it), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (p.pedirForma) {
                Text("Forma de pagamento:", fontSize = 17.sp)
                SeletorForma(forma, aoMudarForma, p.permitirFiado)
            }
            Spacer(Modifier.size(4.dp))
            BotaoPrincipal("Sim, confirmar", Icons.Filled.Check, aoTocar = aoConfirmar)
            Row {
                TextButton(onClick = aoCancelar, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Close, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Não, cancelar", fontSize = 18.sp)
                }
            }
        }
    }
}
