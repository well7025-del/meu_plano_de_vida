package br.com.mercadinhofacil.ui.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.Texto
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.dados.Cliente
import br.com.mercadinhofacil.dados.ItemCarrinho
import br.com.mercadinhofacil.dados.Produto
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.CampoDinheiro
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.LeitorCodigo
import br.com.mercadinhofacil.ui.LinhaValor
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.SeletorForma
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vazio
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.tratarErro
import kotlinx.coroutines.launch

/** Busca simples por nome, apelido ou código; tolera erro de digitação. */
fun filtrarProdutos(produtos: List<Produto>, busca: String): List<Produto> {
    val b = busca.trim()
    if (b.isEmpty()) return produtos
    val n = Texto.normalizar(b)
    return produtos
        .map { p ->
            val nomes = listOf(p.nome) + p.apelidos.split(',')
            val contem = nomes.any { Texto.normalizar(it).contains(n) } || p.codigoBarras == b
            p to if (contem) 2.0 else nomes.maxOf { Texto.semelhanca(b, it) }
        }
        .filter { it.second >= 0.5 }
        .sortedByDescending { it.second }
        .map { it.first }
}

@Composable
fun TelaVender(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val produtos by remember { app.repositorio.dao.produtos() }.collectAsState(emptyList())
    val carrinho = remember { mutableStateListOf<ItemCarrinho>() }
    var busca by remember { mutableStateOf("") }
    var mostrarLista by remember { mutableStateOf(false) }
    var lendo by remember { mutableStateOf(false) }
    var pesar by remember { mutableStateOf<Produto?>(null) }
    var editar by remember { mutableStateOf<Int?>(null) }
    var avulso by remember { mutableStateOf(false) }
    var pagando by remember { mutableStateOf(false) }
    var sairSemSalvar by remember { mutableStateOf(false) }

    fun adicionar(p: Produto, quantidade: Double = 1.0) {
        if (p.preco <= 0) { aviso(context, "${p.nome} está sem preço. Coloque o preço no cadastro."); return }
        val i = carrinho.indexOfFirst { it.produtoId == p.id }
        if (i >= 0) carrinho[i] = carrinho[i].copy(quantidade = carrinho[i].quantidade + quantidade)
        else carrinho += ItemCarrinho(p.id, p.nome, p.unidade, quantidade, p.preco, p.custo)
        if (p.estoque - quantidade < 0 && p.estoque <= 0) aviso(context, "Atenção: ${p.nome} está sem estoque no sistema.")
        busca = ""
        mostrarLista = false
    }

    fun escolher(p: Produto) {
        if (p.unidade == "kg" || p.unidade == "l") pesar = p else adicionar(p)
    }

    val total = carrinho.sumOf { it.total }

    BackHandler(enabled = pagando || carrinho.isNotEmpty()) {
        if (pagando) pagando = false else sairSemSalvar = true
    }

    if (pagando) {
        TelaPagamento(total, carrinho, aoVoltar = { pagando = false }) { desconto, forma, clienteId, recebido, pendente ->
            escopo.launch {
                try {
                    val id = app.repositorio.registrarVenda(carrinho.toList(), desconto, forma, clienteId, recebido, pendente)
                    carrinho.clear()
                    pagando = false
                    nav.trocar("comprovante/$id")
                } catch (e: Exception) {
                    app.tratarErro(context, e)
                }
            }
        }
        return
    }

    Tela(
        "Vender", { if (carrinho.isEmpty()) nav.voltar() else sairSemSalvar = true },
        rodape = {
            Surface(tonalElevation = 6.dp) {
                Column(Modifier.padding(16.dp)) {
                    LinhaValor("Total (${carrinho.size} ${if (carrinho.size == 1) "item" else "itens"})", Dinheiro.formatar(total), destaque = true, cor = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    BotaoPrincipal("Receber pagamento", Icons.Filled.Check, habilitado = carrinho.isNotEmpty()) { pagando = true }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Campo(
                    busca, { busca = it; mostrarLista = it.isNotBlank() }, "Buscar produto",
                    Modifier.weight(1f),
                    fim = { Icon(Icons.Filled.Search, null) },
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { lendo = true }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Filled.QrCodeScanner, "Ler código de barras", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { mostrarLista = !mostrarLista; busca = "" }) {
                    Icon(Icons.Filled.List, null); Spacer(Modifier.width(4.dp)); Text(if (mostrarLista) "Ver carrinho" else "Lista de produtos", fontSize = 16.sp)
                }
                TextButton(onClick = { avulso = true }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Item avulso", fontSize = 16.sp)
                }
            }
            HorizontalDivider()
            if (mostrarLista) {
                val lista = filtrarProdutos(produtos, busca)
                LazyColumn(Modifier.weight(1f)) {
                    if (lista.isEmpty()) item { Vazio("Nenhum produto encontrado. Cadastre em Produtos ou use \"Item avulso\".", Icons.Filled.Search) }
                    items(lista, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.nome, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Estoque: ${Quantidade.formatar(p.estoque)} ${p.unidade}") },
                            trailingContent = { Text(Dinheiro.formatar(p.preco) + if (p.unidade == "kg") "/kg" else "", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.clickable { escolher(p) },
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (carrinho.isEmpty()) item { Vazio("Busque o produto pelo nome, toque em \"Lista de produtos\" ou leia o código de barras.", Icons.Filled.ShoppingCart) }
                    itemsIndexed(carrinho) { i, item ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable { editar = i },
                                ) {
                                    Text(item.nome, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${Quantidade.formatar(item.quantidade)} ${item.unidade} × ${Dinheiro.formatar(item.precoUnitario)}", fontSize = 15.sp)
                                    Text(Dinheiro.formatar(item.total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    val q = item.quantidade - 1
                                    if (q <= 0) carrinho.removeAt(i) else carrinho[i] = item.copy(quantidade = q)
                                }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Remove, "Menos", Modifier.size(32.dp)) }
                                Text(Quantidade.formatar(item.quantidade), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { carrinho[i] = item.copy(quantidade = item.quantidade + 1) }, modifier = Modifier.size(52.dp)) {
                                    Icon(Icons.Filled.Add, "Mais", Modifier.size(32.dp))
                                }
                                IconButton(onClick = { carrinho.removeAt(i) }) { Icon(Icons.Filled.Delete, "Tirar", tint = Vermelho) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (lendo) {
        LeitorCodigo(aoLer = { codigo ->
            lendo = false
            escopo.launch {
                val p = app.repositorio.dao.produtoPorCodigo(codigo)
                if (p != null) escolher(p) else aviso(context, "Código $codigo não cadastrado. Cadastre o produto em Produtos.")
            }
        }, aoFechar = { lendo = false })
    }

    pesar?.let { p ->
        DialogoQuantidade("Quanto de ${p.nome}?", p.unidade, "") { q ->
            pesar = null
            if (q != null && q > 0) adicionar(p, q)
        }
    }

    editar?.let { i ->
        val item = carrinho.getOrNull(i)
        if (item == null) editar = null else {
            DialogoQuantidade("Quantidade de ${item.nome}", item.unidade, Quantidade.formatar(item.quantidade)) { q ->
                editar = null
                if (q != null) { if (q <= 0) carrinho.removeAt(i) else carrinho[i] = item.copy(quantidade = q) }
            }
        }
    }

    if (avulso) {
        DialogoAvulso(aoFechar = { avulso = false }) { nome, preco, q ->
            avulso = false
            carrinho += ItemCarrinho(null, nome, "un", q, preco, 0)
        }
    }

    if (sairSemSalvar) {
        AlertDialog(
            onDismissRequest = { sairSemSalvar = false },
            title = { Text("Sair sem registrar?") },
            text = { Text("Os itens do carrinho serão descartados.", fontSize = 18.sp) },
            confirmButton = { TextButton(onClick = { sairSemSalvar = false; carrinho.clear(); nav.voltar() }) { Text("Sair", color = Vermelho, fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { sairSemSalvar = false }) { Text("Continuar vendendo", fontSize = 18.sp) } },
        )
    }
}

@Composable
fun DialogoQuantidade(titulo: String, unidade: String, inicial: String, aoTerminar: (Double?) -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = { aoTerminar(null) },
        title = { Text(titulo) },
        text = {
            Column {
                Campo(texto, { texto = it }, "Quantidade ($unidade)", teclado = KeyboardType.Decimal, dica = if (unidade == "kg") "ex.: 0,350" else "ex.: 2")
                if (unidade == "kg") Text("Para 350 gramas, digite 0,350", Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { aoTerminar(Quantidade.ler(texto)) }) { Text("OK", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = { aoTerminar(null) }) { Text("Voltar", fontSize = 18.sp) } },
    )
}

@Composable
private fun DialogoAvulso(aoFechar: () -> Unit, aoAdicionar: (String, Long, Double) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf("") }
    var qtd by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Item avulso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(nome, { nome = it }, "Descrição", dica = "ex.: Bala, Gelo")
                CampoDinheiro(preco, { preco = it }, "Preço")
                Campo(qtd, { qtd = it }, "Quantidade", teclado = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Dinheiro.ler(preco)
                val q = Quantidade.ler(qtd)
                if (p != null && p > 0 && q != null && q > 0) aoAdicionar(nome.ifBlank { "Avulso" }, p, q)
            }) { Text("Adicionar", fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar", fontSize = 18.sp) } },
    )
}

/** Segunda etapa da venda: desconto, forma de pagamento, troco, cliente do fiado. */
@Composable
private fun TelaPagamento(
    subtotal: Long,
    itens: List<ItemCarrinho>,
    aoVoltar: () -> Unit,
    aoConfirmar: (desconto: Long, forma: FormaPagamento, clienteId: Long?, recebido: Long?, pixPendente: Boolean) -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val clientes by remember { app.repositorio.dao.clientes() }.collectAsState(emptyList())
    var forma by remember { mutableStateOf<FormaPagamento?>(null) }
    var descontoTexto by remember { mutableStateOf("") }
    var recebidoTexto by remember { mutableStateOf("") }
    var clienteId by remember { mutableStateOf<Long?>(null) }
    var buscaCliente by remember { mutableStateOf("") }
    var novoCliente by remember { mutableStateOf(false) }
    var pixRecebido by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }

    val desconto = (Dinheiro.ler(descontoTexto) ?: 0).coerceIn(0, subtotal)
    val total = subtotal - desconto
    val recebido = Dinheiro.ler(recebidoTexto)
    val troco = recebido?.let { it - total }
    val pronto = forma != null && (forma != FormaPagamento.FIADO || clienteId != null) &&
        (forma != FormaPagamento.DINHEIRO || recebido == null || recebido >= total)

    Tela("Pagamento", aoVoltar) { padding ->
        Coluna(padding) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    LinhaValor("Produtos (${itens.size})", Dinheiro.formatar(subtotal))
                    if (desconto > 0) LinhaValor("Desconto", "-" + Dinheiro.formatar(desconto), cor = Vermelho)
                    LinhaValor("Total a pagar", Dinheiro.formatar(total), destaque = true, cor = MaterialTheme.colorScheme.primary)
                }
            }
            CampoDinheiro(descontoTexto, { descontoTexto = it }, "Desconto (opcional)")
            Text("Como o cliente vai pagar?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SeletorForma(forma, { forma = it })

            when (forma) {
                FormaPagamento.DINHEIRO -> {
                    CampoDinheiro(recebidoTexto, { recebidoTexto = it }, "Valor recebido (para calcular o troco)")
                    if (troco != null) {
                        if (troco >= 0) LinhaValor("Troco", Dinheiro.formatar(troco), destaque = true, cor = MaterialTheme.colorScheme.primary)
                        else Text("Faltam ${Dinheiro.formatar(-troco)}", color = Vermelho, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                FormaPagamento.PIX -> {
                    Text("O Pix já caiu na conta?", fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pixRecebido = true }) {
                        RadioButton(pixRecebido, { pixRecebido = true }); Text("Sim, já recebi", fontSize = 18.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pixRecebido = false }) {
                        RadioButton(!pixRecebido, { pixRecebido = false }); Text("Ainda não: mostrar QR Code e esperar", fontSize = 18.sp)
                    }
                    if (app.prefs.dadosPix == null) Text("Cadastre sua chave Pix em \"Receber Pix\" para mostrar o QR Code.", color = Vermelho)
                }
                FormaPagamento.FIADO -> {
                    Text("Para qual cliente?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Campo(buscaCliente, { buscaCliente = it }, "Buscar cliente")
                    val filtrados = clientes.filter { buscaCliente.isBlank() || Texto.normalizar(it.cliente.nome).contains(Texto.normalizar(buscaCliente)) }
                    filtrados.take(8).forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { clienteId = c.cliente.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(clienteId == c.cliente.id, { clienteId = c.cliente.id })
                            Text(c.cliente.nome, Modifier.weight(1f), fontSize = 18.sp)
                            if (c.divida > 0) Text("deve ${Dinheiro.formatar(c.divida)}", color = Vermelho)
                        }
                        val limite = c.cliente.limite
                        if (clienteId == c.cliente.id && limite > 0 && c.divida + total > limite) {
                            Text("Passa do limite de ${Dinheiro.formatar(limite)} deste cliente.", color = Vermelho)
                        }
                    }
                    BotaoSecundario(if (buscaCliente.isNotBlank()) "Cadastrar \"${buscaCliente.trim()}\"" else "Cliente novo", Icons.Filled.PersonAdd) { novoCliente = true }
                }
                else -> {}
            }
            Spacer(Modifier.size(8.dp))
            BotaoPrincipal("Confirmar venda", Icons.Filled.Check, habilitado = pronto) {
                if (app.prefs.pedirConfirmacao) confirmar = true
                else aoConfirmar(desconto, forma!!, clienteId, recebido, forma == FormaPagamento.PIX && !pixRecebido)
            }
        }
    }

    if (confirmar && forma != null) {
        br.com.mercadinhofacil.ui.Confirmacao(
            "Confirmar venda?",
            "${Dinheiro.formatar(total)} em ${forma!!.rotulo}" +
                (clienteId?.let { id -> " para ${clientes.firstOrNull { it.cliente.id == id }?.cliente?.nome ?: ""}" } ?: "") +
                (troco?.takeIf { it > 0 && forma == FormaPagamento.DINHEIRO }?.let { "\nTroco: ${Dinheiro.formatar(it)}" } ?: ""),
            aoConfirmar = {
                confirmar = false
                aoConfirmar(desconto, forma!!, clienteId, recebido, forma == FormaPagamento.PIX && !pixRecebido)
            },
            aoCancelar = { confirmar = false },
        )
    }

    if (novoCliente) {
        DialogoNovoCliente(buscaCliente.trim(), aoFechar = { novoCliente = false }) { nome, telefone ->
            novoCliente = false
            escopo.launch {
                try {
                    clienteId = app.repositorio.salvarCliente(Cliente(nome = nome, telefone = telefone))
                    buscaCliente = ""
                    aviso(context, "$nome cadastrado")
                } catch (e: Exception) {
                    app.tratarErro(context, e)
                }
            }
        }
    }
}

@Composable
fun DialogoNovoCliente(nomeInicial: String, aoFechar: () -> Unit, aoSalvar: (String, String) -> Unit) {
    var nome by remember { mutableStateOf(nomeInicial) }
    var telefone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Cliente novo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(nome, { nome = it }, "Nome")
                Campo(telefone, { telefone = it }, "WhatsApp (opcional)", teclado = KeyboardType.Phone, dica = "(11) 98765-4321")
            }
        },
        confirmButton = { TextButton(onClick = { if (nome.isNotBlank()) aoSalvar(nome.trim(), telefone.trim()) }) { Text("Salvar", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar", fontSize = 18.sp) } },
    )
}
