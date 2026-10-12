package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.pix.Pix
import br.com.mercadinhofacil.core.pix.TipoChave
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.servicos.Imagens
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.CampoDinheiro
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.dataHora
import kotlinx.coroutines.launch

@Composable
fun TelaPix(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val pendentes by remember { app.repositorio.dao.pixPendentes() }.collectAsState(emptyList())
    var titular by remember { mutableStateOf(app.prefs.pixTitular) }
    var banco by remember { mutableStateOf(app.prefs.pixBanco) }
    var chave by remember { mutableStateOf(app.prefs.pixChave) }
    var tipo by remember { mutableStateOf(app.prefs.pixTipo) }
    var cidade by remember { mutableStateOf(app.prefs.pixCidade) }
    var editando by remember { mutableStateOf(app.prefs.dadosPix == null) }

    Tela("Receber por Pix", { nav.voltar() }) { padding ->
        Coluna(padding) {
            if (pendentes.isNotEmpty()) {
                Secao("Esperando confirmação")
                Text("Confira no app do banco se o dinheiro caiu e toque na venda para confirmar.", fontSize = 16.sp)
                pendentes.forEach { v ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.ir("comprovante/${v.id}") },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1), contentColor = Color(0xFF3E2723)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Venda nº ${v.id}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(v.data.dataHora(), fontSize = 14.sp)
                            }
                            Text(Dinheiro.formatar(v.total), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Laranja)
                        }
                    }
                }
            }

            if (!editando && app.prefs.dadosPix != null) {
                BotaoPrincipal("Cobrar um valor (QR Code)", Icons.Filled.QrCode2) { nav.ir("cobranca") }
                val d = app.prefs.dadosPix!!
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Sua chave Pix", fontSize = 15.sp)
                        Text(d.chave, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${d.tipo.rotulo} · ${d.titular} · ${d.instituicao}", fontSize = 15.sp)
                    }
                }
                BotaoSecundario("Copiar a chave", Icons.Filled.ContentCopy) { Compartilhar.copiar(context, "Chave Pix", d.chave) }
                BotaoSecundario("Enviar dados do Pix no WhatsApp", Icons.Filled.Share) {
                    val codigo = runCatching { Pix.copiaECola(d, null) }.getOrNull()
                    Compartilhar.texto(context, Pix.mensagemCobranca(d, null, codigo, app.prefs.nomeExibicao), "Enviar Pix")
                }
                androidx.compose.material3.TextButton(onClick = { editando = true }) { Text("Alterar dados do Pix", fontSize = 17.sp) }
            } else {
                Secao("Seus dados do Pix")
                Text("Ficam só neste celular. Servem para gerar o QR Code e a mensagem de pagamento para o cliente.", fontSize = 15.sp)
                Campo(titular, { titular = it }, "Nome do titular da conta", dica = "como aparece no banco")
                Campo(banco, { banco = it }, "Banco / instituição", dica = "ex.: Nubank, Caixa, Mercado Pago")
                Text("Tipo de chave:", fontSize = 17.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoChave.entries.forEach { t -> FilterChip(tipo == t, { tipo = t }, label = { Text(t.rotulo, fontSize = 16.sp) }) }
                }
                Campo(chave, { chave = it; if (it.length > 5) tipo = Pix.detectarTipo(it) }, "Chave Pix")
                val chaveOk = chave.isBlank() || Pix.normalizarChave(chave, tipo) != null
                if (!chaveOk) Text("Essa chave não parece um(a) ${tipo.rotulo} válido(a). Confira.", color = Vermelho)
                Campo(cidade, { cidade = it }, "Cidade", dica = "ex.: Recife")
                BotaoPrincipal("Salvar", Icons.Filled.Save, habilitado = chaveOk && chave.isNotBlank() && titular.isNotBlank()) {
                    app.prefs.pixTitular = titular; app.prefs.pixBanco = banco; app.prefs.pixChave = chave
                    app.prefs.pixTipo = tipo; app.prefs.pixCidade = cidade
                    escopo.launch { app.repositorio.dao.inserir(br.com.mercadinhofacil.dados.Historico(acao = "Dados do Pix alterados", detalhe = "${tipo.rotulo}: $chave")) }
                    editando = false
                    aviso(context, "Dados do Pix salvos")
                }
            }
        }
    }
}

@Composable
fun TelaCobrancaPix(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    val dados = app.prefs.dadosPix
    val centavos = Dinheiro.ler(valor)?.takeIf { it > 0 }
    val codigo = remember(centavos, descricao, dados) {
        dados?.let { runCatching { Pix.copiaECola(it, centavos, "***", descricao.ifBlank { null }) }.getOrNull() }
    }

    Tela("Cobrar por Pix", { nav.voltar() }) { padding ->
        Coluna(padding) {
            if (dados == null) { Text("Cadastre sua chave Pix primeiro.", color = Vermelho, fontSize = 18.sp); return@Coluna }
            CampoDinheiro(valor, { valor = it }, "Valor (deixe vazio para o cliente digitar)")
            Campo(descricao, { descricao = it }, "Descrição (opcional)")
            HorizontalDivider()
            if (codigo == null) {
                Text("Não consegui gerar o código. Confira a chave Pix cadastrada.", color = Vermelho)
            } else {
                val qr = remember(codigo) { Imagens.qrCode(codigo, 700).asImageBitmap() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(qr, "QR Code Pix", Modifier.size(280.dp))
                    Text(centavos?.let { Dinheiro.formatar(it) } ?: "Valor livre", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${dados.titular} · ${dados.instituicao}", fontSize = 15.sp)
                }
                BotaoSecundario("Copiar Pix Copia e Cola", Icons.Filled.ContentCopy) { Compartilhar.copiar(context, "Pix", codigo) }
                BotaoPrincipal("Enviar no WhatsApp", Icons.Filled.Share, cor = Color(0xFF25D366)) {
                    val arquivo = Compartilhar.salvarImagem(context, Imagens.qrCode(codigo, 700), "pix.png")
                    Compartilhar.arquivos(context, listOf(arquivo), "image/png", Pix.mensagemCobranca(dados, centavos, codigo, app.prefs.nomeExibicao, descricao.ifBlank { null }), "Enviar Pix")
                }
                Text(
                    "Este QR é o mesmo que os bancos usam (padrão do Banco Central). Quando o dinheiro cair, confira no app do seu banco — o app não tem acesso à sua conta.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
