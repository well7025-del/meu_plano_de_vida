package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Texto
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.dados.Cliente
import br.com.mercadinhofacil.dados.StatusVenda
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.servicos.Documentos
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.CampoDinheiro
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.SeletorForma
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vazio
import br.com.mercadinhofacil.ui.Verde
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.data
import br.com.mercadinhofacil.ui.dataHora
import kotlinx.coroutines.launch

@Composable
fun TelaClientes(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val clientes by remember { app.repositorio.dao.clientes() }.collectAsState(null)
    var busca by remember { mutableStateOf("") }
    var soDevendo by remember { mutableStateOf(false) }
    var novo by remember { mutableStateOf(false) }

    Tela(
        "Clientes e fiado", { nav.voltar() },
        flutuante = { ExtendedFloatingActionButton(onClick = { novo = true }, icon = { Icon(Icons.Filled.PersonAdd, null) }, text = { Text("Novo cliente", fontSize = 18.sp) }) },
    ) { padding ->
        val lista = clientes
        if (lista == null) { Carregando(); return@Tela }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val devendo = lista.filter { it.divida > 0 }
            Card(Modifier.padding(12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total a receber no fiado", fontSize = 16.sp, color = Color(0xFF5D4037))
                    Text(Dinheiro.formatar(devendo.sumOf { it.divida }), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBF360C))
                    Text("${devendo.size} ${if (devendo.size == 1) "cliente devendo" else "clientes devendo"}", color = Color(0xFF5D4037))
                }
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Campo(busca, { busca = it }, "Buscar cliente", fim = { Icon(Icons.Filled.Search, null) })
                FilterChip(soDevendo, { soDevendo = !soDevendo }, label = { Text("Só quem deve") })
            }
            val filtrados = lista.filter {
                (!soDevendo || it.divida > 0) && (busca.isBlank() || Texto.normalizar(it.cliente.nome).contains(Texto.normalizar(busca)))
            }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                if (filtrados.isEmpty()) item { Vazio(if (lista.isEmpty()) "Nenhum cliente ainda. Toque em \"Novo cliente\"." else "Nada encontrado.", Icons.Filled.People) }
                items(filtrados, key = { it.cliente.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.ir("cliente/${c.cliente.id}") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.cliente.nome, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            if (c.cliente.telefone.isNotBlank()) Text(c.cliente.telefone, color = MaterialTheme.colorScheme.outline)
                        }
                        if (c.divida > 0) Text("deve ${Dinheiro.formatar(c.divida)}", color = Vermelho, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        else Text("em dia", color = Verde)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (novo) {
        DialogoNovoCliente(busca, aoFechar = { novo = false }) { nome, telefone ->
            novo = false
            escopo.launch { app.repositorio.salvarCliente(Cliente(nome = nome, telefone = telefone)); aviso(context, "$nome cadastrado") }
        }
    }
}

private data class LinhaExtrato(val data: Long, val texto: String, val valor: Long, val debito: Boolean, val vendaId: Long?)

@Composable
fun TelaCliente(nav: Nav, clienteId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val dao = app.repositorio.dao
    val cliente by remember { dao.observarCliente(clienteId) }.collectAsState(null)
    val vendas by remember { dao.vendasDoCliente(clienteId) }.collectAsState(emptyList())
    val recebimentos by remember { dao.recebimentosDoCliente(clienteId) }.collectAsState(emptyList())
    var receber by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf(false) }

    val divida = vendas.filter { it.status == StatusVenda.FIADO }.sumOf { it.total } - recebimentos.filter { !it.cancelado }.sumOf { it.valor }

    Tela(cliente?.nome ?: "Cliente", { nav.voltar() }, acoes = {
        IconButton(onClick = { editar = true }) { Icon(Icons.Filled.Edit, "Editar") }
    }) { padding ->
        val c = cliente
        if (c == null) { Carregando(); return@Tela }
        Coluna(padding) {
            Card(colors = CardDefaults.cardColors(containerColor = if (divida > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(if (divida > 0) "Está devendo" else "Não deve nada", fontSize = 17.sp, color = Color.DarkGray)
                    Text(Dinheiro.formatar(divida.coerceAtLeast(0)), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (divida > 0) Vermelho else Verde)
                    if (divida < 0) Text("Tem crédito de ${Dinheiro.formatar(-divida)}", color = Verde)
                    if (c.limite > 0) Text("Limite de fiado: ${Dinheiro.formatar(c.limite)}", color = Color.DarkGray)
                }
            }
            if (divida > 0) {
                BotaoPrincipal("Receber pagamento", Icons.Filled.Payments) { receber = true }
                BotaoSecundario("Cobrar pelo WhatsApp (com Pix)", Icons.Filled.Send) {
                    val itens = vendas.filter { it.status == StatusVenda.FIADO }.take(10).joinToString("\n") { "• ${it.data.data()}: ${Dinheiro.formatar(it.total)}" }
                    val msg = Documentos.mensagemCobranca(context, app.prefs, c.nome.split(" ").first(), divida, "Compras no fiado:\n$itens")
                    if (c.telefone.isNotBlank()) Compartilhar.textoPara(context, c.telefone, msg) else Compartilhar.texto(context, msg, "Enviar cobrança")
                }
            }
            if (c.observacao.isNotBlank()) Text("Obs.: ${c.observacao}", fontSize = 16.sp)

            Secao("Histórico")
            val extrato = (
                vendas.map { v ->
                    LinhaExtrato(
                        v.data,
                        "Compra nº ${v.id} (${Documentos.rotuloForma(v.forma)})" + if (v.status == StatusVenda.CANCELADA) " — cancelada" else "",
                        v.total, v.status == StatusVenda.FIADO, v.id,
                    )
                } + recebimentos.map { m ->
                    LinhaExtrato(m.data, "Pagamento" + if (m.cancelado) " — cancelado" else "", m.valor, false, null)
                }
                ).sortedByDescending { it.data }
            if (extrato.isEmpty()) Text("Nenhuma compra ainda.")
            extrato.forEach { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = l.vendaId != null) { l.vendaId?.let { nav.ir("comprovante/$it") } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.texto, fontSize = 17.sp)
                        Text(l.data.dataHora(), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        (if (l.debito) "+" else "") + Dinheiro.formatar(l.valor),
                        color = if (l.debito) Vermelho else if (l.vendaId == null) Verde else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (receber) {
        var valor by remember { mutableStateOf(Dinheiro.formatar(divida, false)) }
        var forma by remember { mutableStateOf<FormaPagamento?>(FormaPagamento.DINHEIRO) }
        AlertDialog(
            onDismissRequest = { receber = false },
            title = { Text("Receber de ${cliente?.nome}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Deve ${Dinheiro.formatar(divida)}. Pode receber uma parte.", fontSize = 17.sp)
                    CampoDinheiro(valor, { valor = it }, "Valor recebido")
                    SeletorForma(forma, { forma = it }, permitirFiado = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Dinheiro.ler(valor)
                    if (v == null || v <= 0) { aviso(context, "Digite o valor"); return@TextButton }
                    receber = false
                    escopo.launch {
                        app.repositorio.receberFiado(clienteId, v, forma ?: FormaPagamento.DINHEIRO)
                        val resta = app.repositorio.dividaDoCliente(clienteId)
                        aviso(context, if (resta > 0) "Recebido. Ainda deve ${Dinheiro.formatar(resta)}" else "Recebido. Conta quitada!")
                    }
                }) { Text("Confirmar", fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { receber = false }) { Text("Voltar", fontSize = 18.sp) } },
        )
    }

    val c = cliente
    if (editar && c != null) {
        var nome by remember { mutableStateOf(c.nome) }
        var telefone by remember { mutableStateOf(c.telefone) }
        var obs by remember { mutableStateOf(c.observacao) }
        var limite by remember { mutableStateOf(if (c.limite > 0) Dinheiro.formatar(c.limite, false) else "") }
        var remover by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editar = false },
            title = { Text("Editar cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(nome, { nome = it }, "Nome")
                    Campo(telefone, { telefone = it }, "WhatsApp", teclado = KeyboardType.Phone)
                    CampoDinheiro(limite, { limite = it }, "Limite de fiado (opcional)")
                    Campo(obs, { obs = it }, "Observação", linhas = 2)
                    TextButton(onClick = { remover = true }) { Text("Remover cliente", color = Vermelho) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editar = false
                    escopo.launch {
                        app.repositorio.salvarCliente(c.copy(nome = nome.trim().ifBlank { c.nome }, telefone = telefone.trim(), observacao = obs.trim(), limite = Dinheiro.ler(limite) ?: 0))
                    }
                }) { Text("Salvar", fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { editar = false }) { Text("Voltar", fontSize = 18.sp) } },
        )
        if (remover) {
            br.com.mercadinhofacil.ui.Confirmacao(
                "Remover ${c.nome}?",
                if (divida > 0) "Atenção: ainda deve ${Dinheiro.formatar(divida)}. O histórico continua guardado." else "O histórico continua guardado.",
                "Remover", Vermelho,
                aoConfirmar = {
                    remover = false; editar = false
                    escopo.launch { app.repositorio.salvarCliente(c.copy(ativo = false)); nav.voltar() }
                },
                aoCancelar = { remover = false },
            )
        }
    }
}
