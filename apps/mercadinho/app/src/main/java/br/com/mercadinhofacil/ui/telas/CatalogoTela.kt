package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.servicos.Catalogo
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.aviso
import kotlinx.coroutines.launch

@Composable
fun TelaCatalogo(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val produtos by remember { app.repositorio.dao.produtos() }.collectAsState(null)
    var titulo by remember { mutableStateOf(app.prefs.nomeExibicao) }
    var subtitulo by remember { mutableStateOf(listOf(app.prefs.lojaTelefone, app.prefs.lojaEndereco).filter { it.isNotBlank() }.joinToString(" · ")) }
    var rodape by remember { mutableStateOf("Peça pelo WhatsApp! Preços sujeitos a alteração.") }
    var descricao by remember { mutableStateOf(true) }
    var categoria by remember { mutableStateOf<String?>(null) }
    var selecionados by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var iniciado by remember { mutableStateOf(false) }
    var gerando by remember { mutableStateOf(false) }

    LaunchedEffect(produtos) {
        val l = produtos
        if (l != null && !iniciado) { selecionados = l.filter { it.noCatalogo && it.preco > 0 }.map { it.id }.toSet(); iniciado = true }
    }

    Tela("Catálogo digital", { nav.voltar() }) { padding ->
        val lista = produtos
        if (lista == null) { Carregando(); return@Tela }
        val escolhidos = lista.filter { it.id in selecionados && (categoria == null || it.categoria == categoria) }
        val opcoes = Catalogo.Opcoes(titulo.ifBlank { app.prefs.nomeExibicao }, subtitulo, rodape, descricao)
        fun gerar(acao: suspend () -> Unit) {
            if (escolhidos.isEmpty()) { aviso(context, "Escolha pelo menos um produto"); return }
            gerando = true
            escopo.launch {
                try { acao() } catch (e: Exception) { aviso(context, "Não consegui gerar: ${e.message}") }
                gerando = false
            }
        }
        Coluna(padding) {
            Text("O cliente abre no WhatsApp, sem instalar nada.", fontSize = 17.sp)
            if (gerando) LinearProgressIndicator(Modifier.fillMaxWidth())
            BotaoPrincipal("Enviar como imagens", Icons.Filled.Image) {
                gerar { Compartilhar.arquivos(context, Catalogo.imagens(context, escolhidos, opcoes), "image/png", null, "Enviar catálogo") }
            }
            BotaoSecundario("Enviar como PDF", Icons.Filled.PictureAsPdf) {
                gerar { Compartilhar.arquivos(context, listOf(Catalogo.pdf(context, escolhidos, opcoes)), "application/pdf", null, "Enviar catálogo") }
            }
            BotaoSecundario("Enviar como texto", Icons.Filled.TextSnippet) {
                gerar { Compartilhar.texto(context, Catalogo.texto(escolhidos, opcoes), "Enviar catálogo") }
            }
            Secao("Aparência")
            Campo(titulo, { titulo = it }, "Título")
            Campo(subtitulo, { subtitulo = it }, "Subtítulo (telefone, endereço, horário)")
            Campo(rodape, { rodape = it }, "Mensagem no rodapé")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar descrição dos produtos", Modifier.weight(1f), fontSize = 17.sp)
                Switch(descricao, { descricao = it })
            }
            Secao("Produtos (${escolhidos.size})")
            val categorias = lista.map { it.categoria }.filter { it.isNotBlank() }.distinct().sorted()
            if (categorias.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(categoria == null, { categoria = null }, label = { Text(maxLines = 1, softWrap = false, text = "Todas") })
                    categorias.forEach { c -> FilterChip(categoria == c, { categoria = c }, label = { Text(maxLines = 1, softWrap = false, text = c) }) }
                }
            }
            Row {
                TextButton(onClick = { selecionados = lista.map { it.id }.toSet() }) { Text("Marcar todos") }
                TextButton(onClick = { selecionados = emptySet() }) { Text("Desmarcar todos") }
            }
            lista.filter { categoria == null || it.categoria == categoria }.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selecionados = if (p.id in selecionados) selecionados - p.id else selecionados + p.id }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(p.id in selecionados, { selecionados = if (it) selecionados + p.id else selecionados - p.id })
                    FotoProduto(p.foto, 44)
                    Spacer(Modifier.width(10.dp))
                    Text(p.nome, Modifier.weight(1f), fontSize = 17.sp)
                    Text(Dinheiro.formatar(p.preco), fontSize = 17.sp)
                }
            }
        }
    }
}
