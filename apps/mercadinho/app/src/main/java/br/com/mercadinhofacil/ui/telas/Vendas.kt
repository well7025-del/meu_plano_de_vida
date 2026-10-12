package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.impressao.Comprovante
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.dados.StatusVenda
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.servicos.Documentos
import br.com.mercadinhofacil.servicos.Imagens
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LinhaValor
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vazio
import br.com.mercadinhofacil.ui.Verde
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.dataHora
import br.com.mercadinhofacil.ui.rememberImpressao
import kotlinx.coroutines.launch

fun corStatus(status: String): Color = when (status) {
    StatusVenda.PAGA -> Verde
    StatusVenda.PENDENTE -> Laranja
    StatusVenda.FIADO -> Color(0xFF8D6E63)
    else -> Vermelho
}

@Composable
fun TelaComprovante(nav: Nav, vendaId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val vc by remember { app.repositorio.dao.observarVenda(vendaId) }.collectAsState(null)
    var cliente by remember { mutableStateOf<br.com.mercadinhofacil.dados.Cliente?>(null) }
    var cancelar by remember { mutableStateOf(false) }
    var trocarForma by remember { mutableStateOf(false) }
    var confirmarPix by remember { mutableStateOf(false) }
    val imprimir = rememberImpressao { nav.ir("impressora") }

    LaunchedEffect(vc?.venda?.clienteId) { cliente = vc?.venda?.clienteId?.let { app.repositorio.dao.cliente(it) } }

    Tela("Venda nº $vendaId", { nav.voltar() }) { padding ->
        val atual = vc
        if (atual == null) { Carregando(); return@Tela }
        val v = atual.venda
        val dados = remember(atual, cliente) { Documentos.comprovante(app.prefs, atual, cliente?.nome) }
        Coluna(padding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.data.dataHora(), Modifier.weight(1f), fontSize = 17.sp)
                AssistChip(onClick = {}, label = { Text(Documentos.situacao(v.status), fontWeight = FontWeight.Bold, color = corStatus(v.status)) })
            }
            if (v.status == StatusVenda.PENDENTE) {
                val codigo = dados.pixCopiaECola
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Peça para o cliente apontar a câmera do app do banco:", fontSize = 18.sp, color = Color(0xFF3E2723))
                        if (codigo != null) {
                            val qr = remember(codigo) { Imagens.qrCode(codigo, 600).asImageBitmap() }
                            Image(qr, "QR Code Pix", Modifier.size(260.dp))
                            Text(Dinheiro.formatar(v.total), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                            BotaoSecundario("Copiar Pix Copia e Cola", Icons.Filled.ContentCopy) { Compartilhar.copiar(context, "Pix", codigo) }
                        } else {
                            Text("Cadastre a chave Pix em \"Receber Pix\" para gerar o QR Code.", color = Vermelho, fontSize = 17.sp)
                        }
                        BotaoPrincipal("O Pix caiu — confirmar", Icons.Filled.CheckCircle) { confirmarPix = true }
                        TextButton(onClick = { trocarForma = true }) { Text("Pagou de outro jeito", fontSize = 17.sp) }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text(
                    Comprovante.texto(dados, 32),
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            }
            if (v.status == StatusVenda.CANCELADA && v.motivoCancelamento.isNotBlank()) Text("Motivo do cancelamento: ${v.motivoCancelamento}", color = Vermelho)

            BotaoPrincipal("Enviar no WhatsApp", Icons.Filled.Share, cor = Color(0xFF25D366)) {
                escopo.launch {
                    val arquivo = Compartilhar.salvarImagem(context, Imagens.comprovante(dados), "comprovante-${v.id}.png")
                    Compartilhar.arquivos(context, listOf(arquivo), "image/png", if (dados.pixCopiaECola != null) dados.pixCopiaECola else null, "Enviar comprovante")
                }
            }
            val telefone = cliente?.telefone.orEmpty()
            if (telefone.isNotBlank()) {
                BotaoSecundario("Mandar texto para ${cliente?.nome}", Icons.Filled.Share) {
                    Compartilhar.textoPara(context, telefone, Comprovante.whatsapp(dados))
                }
            } else {
                BotaoSecundario("Enviar como texto", Icons.Filled.Share) { Compartilhar.texto(context, Comprovante.whatsapp(dados), "Enviar comprovante") }
            }
            BotaoSecundario("Imprimir", Icons.Filled.Print) { imprimir(Documentos.bytesImpressao(app.prefs, dados)) }
            BotaoPrincipal("Nova venda", Icons.Filled.AddShoppingCart) { nav.trocar("vender") }
            if (v.status != StatusVenda.CANCELADA) {
                TextButton(onClick = { cancelar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar esta venda", color = Vermelho, fontSize = 17.sp)
                }
            }
        }
    }

    if (confirmarPix) {
        br.com.mercadinhofacil.ui.Confirmacao(
            "O Pix caiu na conta?",
            "Confira no app do banco antes. O valor entra na conta Pix do caixa.",
            "Sim, caiu",
            aoConfirmar = { confirmarPix = false; escopo.launch { app.repositorio.confirmarPix(vendaId); aviso(context, "Pix confirmado!") } },
            aoCancelar = { confirmarPix = false },
        )
    }

    if (trocarForma) {
        AlertDialog(
            onDismissRequest = { trocarForma = false },
            title = { Text("Como o cliente pagou?") },
            text = {
                Column {
                    listOf(FormaPagamento.DINHEIRO, FormaPagamento.CARTAO).forEach { f ->
                        TextButton(onClick = {
                            trocarForma = false
                            escopo.launch { app.repositorio.trocarFormaPendente(vendaId, f, null) }
                        }) { Text(f.rotulo, fontSize = 20.sp) }
                    }
                    if (vc?.venda?.clienteId != null) {
                        TextButton(onClick = {
                            trocarForma = false
                            escopo.launch { app.repositorio.trocarFormaPendente(vendaId, FormaPagamento.FIADO, null) }
                        }) { Text("Ficou no fiado", fontSize = 20.sp) }
                    } else Text("Para pôr no fiado, cancele e refaça a venda escolhendo o cliente.", fontSize = 14.sp)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { trocarForma = false }) { Text("Voltar") } },
        )
    }

    if (cancelar) {
        var motivo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelar = false },
            title = { Text("Cancelar a venda nº $vendaId?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Os produtos voltam para o estoque e o dinheiro sai da conta. Fica registrado no histórico.", fontSize = 17.sp)
                    Campo(motivo, { motivo = it }, "Motivo (opcional)", dica = "ex.: lançado errado")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelar = false
                    escopo.launch { app.repositorio.cancelarVenda(vendaId, motivo); aviso(context, "Venda cancelada") }
                }) { Text("Cancelar venda", color = Vermelho, fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { cancelar = false }) { Text("Voltar", fontSize = 18.sp) } },
        )
    }
}

@Composable
fun TelaVendas(nav: Nav) {
    val app = LocalApp.current
    val vendas by remember { app.repositorio.dao.ultimasVendas(300) }.collectAsState(null)
    var filtro by remember { mutableStateOf<String?>(null) }
    Tela("Vendas feitas", { nav.voltar() }) { padding ->
        val lista = vendas
        if (lista == null) { Carregando(); return@Tela }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Todas", StatusVenda.PENDENTE to "Pix pendente", StatusVenda.FIADO to "Fiado", StatusVenda.CANCELADA to "Canceladas").forEach { (s, rotulo) ->
                    FilterChip(filtro == s, { filtro = s }, label = { Text(rotulo) })
                }
            }
            val filtradas = lista.filter { filtro == null || it.venda.status == filtro }
            LazyColumn(Modifier.weight(1f)) {
                if (filtradas.isEmpty()) item { Vazio("Nenhuma venda aqui ainda.", Icons.Filled.ReceiptLong) }
                items(filtradas, key = { it.venda.id }) { vc ->
                    val v = vc.venda
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.ir("comprovante/${v.id}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Nº ${v.id} · ${v.data.dataHora()}", fontSize = 15.sp)
                            Text(
                                vc.itens.joinToString { "${Quantidade.formatar(it.quantidade)} ${it.nome}" },
                                maxLines = 2, fontSize = 17.sp,
                            )
                            Text("${Documentos.rotuloForma(v.forma)} · ${Documentos.situacao(v.status)}", color = corStatus(v.status), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            Dinheiro.formatar(v.total), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = if (v.status == StatusVenda.CANCELADA) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
