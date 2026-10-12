package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.dados.Conta
import br.com.mercadinhofacil.dados.Movimento
import br.com.mercadinhofacil.dados.TipoConta
import br.com.mercadinhofacil.dados.TipoMovimento
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.CampoDinheiro
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Confirmacao
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LinhaValor
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Verde
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.dataHora
import kotlinx.coroutines.launch

@Composable
fun TelaContas(nav: Nav) {
    val app = LocalApp.current
    val escopo = rememberCoroutineScope()
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(null)
    var nova by remember { mutableStateOf(false) }

    Tela("Caixa e contas", { nav.voltar() }) { padding ->
        val lista = contas
        if (lista == null) { Carregando(); return@Tela }
        Coluna(padding) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Dinheiro total do negócio", fontSize = 16.sp)
                    Text(Dinheiro.formatar(lista.sumOf { it.saldo }), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            lista.forEach { c ->
                Card(onClick = { nav.ir("conta/${c.conta.id}") }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.conta.nome, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(Dinheiro.formatar(c.saldo), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (c.saldo < 0) Vermelho else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Secao("Lançar")
            BotaoPrincipal("Despesa (conta, aluguel, luz…)", Icons.Filled.MoneyOff, cor = Vermelho) { nav.ir("lancamento/${TipoMovimento.DESPESA}") }
            BotaoSecundario("Transferir entre contas", Icons.Filled.SwapHoriz) { nav.ir("transferencia") }
            BotaoSecundario("Colocar dinheiro no caixa (troco, aporte)", Icons.Filled.ArrowDownward) { nav.ir("lancamento/${TipoMovimento.ENTRADA}") }
            BotaoSecundario("Retirada do dono", Icons.Filled.ArrowUpward) { nav.ir("lancamento/${TipoMovimento.RETIRADA}") }
            BotaoSecundario("Fechar o caixa (gaveta)", Icons.Filled.Lock) {
                lista.firstOrNull { it.conta.tipo == TipoConta.DINHEIRO }?.let { nav.ir("fechamento/${it.conta.id}") }
            }
            TextButton(onClick = { nova = true }) { Icon(Icons.Filled.Add, null); Text("Criar outra conta (banco, poupança…)", fontSize = 17.sp) }
        }
    }

    if (nova) {
        DialogoConta(null, aoFechar = { nova = false }) { nome ->
            nova = false
            escopo.launch { app.repositorio.salvarConta(Conta(nome = nome, tipo = TipoConta.OUTRA, ordem = 10)) }
        }
    }
}

@Composable
private fun DialogoConta(atual: String?, aoFechar: () -> Unit, aoSalvar: (String) -> Unit) {
    var nome by remember { mutableStateOf(atual.orEmpty()) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (atual == null) "Nova conta" else "Renomear conta") },
        text = { Campo(nome, { nome = it }, "Nome da conta", dica = "ex.: Banco do Brasil, Poupança") },
        confirmButton = { TextButton(onClick = { if (nome.isNotBlank()) aoSalvar(nome.trim()) }) { Text("Salvar", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar", fontSize = 18.sp) } },
    )
}

@Composable
fun LinhaMovimento(m: Movimento, aoDesfazer: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                m.descricao.ifBlank { TipoMovimento.rotulo(m.tipo) }, fontSize = 17.sp,
                textDecoration = if (m.cancelado) TextDecoration.LineThrough else null,
            )
            Text("${TipoMovimento.rotulo(m.tipo)} · ${m.data.dataHora()}" + if (m.cancelado) " · cancelado" else "", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            (if (m.entrada) "+" else "-") + Dinheiro.formatar(m.valor),
            fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = if (m.cancelado) MaterialTheme.colorScheme.outline else if (m.entrada) Verde else Vermelho,
            textDecoration = if (m.cancelado) TextDecoration.LineThrough else null,
        )
        if (aoDesfazer != null && !m.cancelado && m.vendaId == null) {
            IconButton(onClick = aoDesfazer) { Icon(Icons.Filled.Undo, "Desfazer", tint = MaterialTheme.colorScheme.outline) }
        }
    }
}

@Composable
fun TelaConta(nav: Nav, contaId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(null)
    val movimentos by remember { app.repositorio.dao.movimentosDaConta(contaId) }.collectAsState(emptyList())
    val fechamentos by remember { app.repositorio.dao.fechamentos(contaId) }.collectAsState(emptyList())
    var desfazer by remember { mutableStateOf<Movimento?>(null) }
    var renomear by remember { mutableStateOf(false) }
    val conta = contas?.firstOrNull { it.conta.id == contaId }

    Tela(conta?.conta?.nome ?: "Conta", { nav.voltar() }, acoes = { IconButton(onClick = { renomear = true }) { Icon(Icons.Filled.Edit, "Renomear") } }) { padding ->
        if (conta == null) { Carregando(); return@Tela }
        Coluna(padding) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Saldo", fontSize = 16.sp)
                    Text(Dinheiro.formatar(conta.saldo), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (conta.saldo < 0) Vermelho else MaterialTheme.colorScheme.primary)
                }
            }
            BotaoPrincipal("Fechar caixa / conferir saldo", Icons.Filled.Lock) { nav.ir("fechamento/$contaId") }
            fechamentos.firstOrNull()?.let { f ->
                val dif = f.valorContado - f.saldoSistema
                Text(
                    "Último fechamento: ${f.data.dataHora()} — contado ${Dinheiro.formatar(f.valorContado)}" +
                        if (dif != 0L) " (diferença ${Dinheiro.formatar(dif)})" else " (bateu certinho)",
                    fontSize = 15.sp,
                )
            }
            Secao("Movimentações")
            if (movimentos.isEmpty()) Text("Nada lançado ainda.")
            movimentos.forEach { m ->
                LinhaMovimento(m) { desfazer = m }
                HorizontalDivider()
            }
            Text("Para desfazer uma venda, abra a venda e toque em \"Cancelar esta venda\".", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
        }
    }

    desfazer?.let { m ->
        Confirmacao(
            "Desfazer lançamento?",
            "${TipoMovimento.rotulo(m.tipo)}: ${m.descricao} — ${Dinheiro.formatar(m.valor)}" +
                if (m.tipo == TipoMovimento.TRANSFERENCIA) "\nAs duas pontas da transferência serão desfeitas." else "",
            "Desfazer", Laranja,
            aoConfirmar = {
                desfazer = null
                escopo.launch {
                    try { app.repositorio.cancelarMovimento(m.id); aviso(context, "Lançamento desfeito") } catch (e: Exception) { aviso(context, e.message ?: "Erro") }
                }
            },
            aoCancelar = { desfazer = null },
        )
    }

    if (renomear && conta != null) {
        DialogoConta(conta.conta.nome, aoFechar = { renomear = false }) { nome ->
            renomear = false
            escopo.launch { app.repositorio.salvarConta(conta.conta.copy(nome = nome)) }
        }
    }
}

@Composable
fun TelaLancamento(nav: Nav, tipo: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(emptyList())
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var contaId by remember { mutableStateOf<Long?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    val entrada = tipo == TipoMovimento.ENTRADA
    val sugestoes = when (tipo) {
        TipoMovimento.DESPESA -> listOf("Luz", "Água", "Aluguel", "Internet", "Funcionário", "Gás", "Transporte", "Embalagens", "Impostos", "Manutenção")
        TipoMovimento.RETIRADA -> listOf("Pró-labore", "Despesa da casa")
        else -> listOf("Troco inicial", "Aporte do dono")
    }
    val conta = contas.firstOrNull { it.conta.id == contaId } ?: contas.firstOrNull()

    Tela(TipoMovimento.rotulo(tipo), { nav.voltar() }) { padding ->
        Coluna(padding) {
            if (tipo == TipoMovimento.RETIRADA) Text("Dinheiro que você tira do negócio para uso pessoal. Não entra como despesa no lucro.", fontSize = 16.sp)
            CampoDinheiro(valor, { valor = it }, "Valor")
            Campo(descricao, { descricao = it }, "Descrição")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sugestoes.forEach { s -> FilterChip(descricao == s, { descricao = s }, label = { Text(s, fontSize = 16.sp) }) }
            }
            Text(if (entrada) "Entra em qual conta?" else "Sai de qual conta?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                contas.forEach { c -> FilterChip(conta?.conta?.id == c.conta.id, { contaId = c.conta.id }, label = { Text("${c.conta.nome} (${Dinheiro.formatar(c.saldo)})", fontSize = 16.sp) }) }
            }
            BotaoPrincipal("Lançar", cor = if (entrada) Verde else Vermelho) {
                val v = Dinheiro.ler(valor)
                if (v == null || v <= 0) aviso(context, "Digite o valor") else if (conta == null) aviso(context, "Escolha a conta") else confirmar = true
            }
        }
    }

    if (confirmar && conta != null) {
        val v = Dinheiro.ler(valor) ?: 0
        Confirmacao(
            "Confirmar?",
            "${TipoMovimento.rotulo(tipo)}: ${descricao.ifBlank { "sem descrição" }}\n${Dinheiro.formatar(v)} ${if (entrada) "em" else "de"} ${conta.conta.nome}",
            aoConfirmar = {
                confirmar = false
                escopo.launch {
                    app.repositorio.lancar(conta.conta.id, tipo, v, entrada, descricao.trim().ifBlank { TipoMovimento.rotulo(tipo) }, descricao.trim())
                    aviso(context, "Lançado")
                    nav.voltar()
                }
            },
            aoCancelar = { confirmar = false },
        )
    }
}

@Composable
fun TelaTransferencia(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(emptyList())
    var valor by remember { mutableStateOf("") }
    var origem by remember { mutableStateOf<Long?>(null) }
    var destino by remember { mutableStateOf<Long?>(null) }
    var obs by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }

    Tela("Transferir", { nav.voltar() }) { padding ->
        Coluna(padding) {
            Text("Ex.: depositou o dinheiro da gaveta no banco. Não conta como venda nem como despesa.", fontSize = 16.sp)
            CampoDinheiro(valor, { valor = it }, "Valor")
            Text("Sai de:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                contas.forEach { c -> FilterChip(origem == c.conta.id, { origem = c.conta.id }, label = { Text("${c.conta.nome} (${Dinheiro.formatar(c.saldo)})", fontSize = 16.sp) }) }
            }
            Text("Vai para:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                contas.filter { it.conta.id != origem }.forEach { c -> FilterChip(destino == c.conta.id, { destino = c.conta.id }, label = { Text(c.conta.nome, fontSize = 16.sp) }) }
            }
            Campo(obs, { obs = it }, "Observação (opcional)")
            BotaoPrincipal("Transferir", Icons.Filled.SwapHoriz) {
                val v = Dinheiro.ler(valor)
                when {
                    v == null || v <= 0 -> aviso(context, "Digite o valor")
                    origem == null || destino == null || origem == destino -> aviso(context, "Escolha as duas contas")
                    else -> confirmar = true
                }
            }
        }
    }

    if (confirmar) {
        val v = Dinheiro.ler(valor) ?: 0
        val o = contas.firstOrNull { it.conta.id == origem }?.conta?.nome
        val d = contas.firstOrNull { it.conta.id == destino }?.conta?.nome
        Confirmacao(
            "Confirmar transferência?", "${Dinheiro.formatar(v)}\nde $o\npara $d",
            aoConfirmar = {
                confirmar = false
                escopo.launch {
                    try {
                        app.repositorio.transferir(origem!!, destino!!, v, obs.trim()); aviso(context, "Transferido"); nav.voltar()
                    } catch (e: Exception) { aviso(context, e.message ?: "Erro") }
                }
            },
            aoCancelar = { confirmar = false },
        )
    }
}

@Composable
fun TelaFechamento(nav: Nav, contaId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(null)
    var contado by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }
    val conta = contas?.firstOrNull { it.conta.id == contaId }

    Tela("Fechar caixa", { nav.voltar() }) { padding ->
        if (conta == null) { Carregando(); return@Tela }
        val v = Dinheiro.ler(contado)
        Coluna(padding) {
            Text(conta.conta.nome, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LinhaValor("O sistema diz que tem", Dinheiro.formatar(conta.saldo), destaque = true)
            Text("Conte o dinheiro${if (conta.conta.tipo == TipoConta.DINHEIRO) " da gaveta" else " (veja no app do banco)"} e digite quanto tem de verdade:", fontSize = 17.sp)
            CampoDinheiro(contado, { contado = it }, "Valor contado")
            if (v != null) {
                val dif = v - conta.saldo
                when {
                    dif == 0L -> Text("Bateu certinho!", color = Verde, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    dif > 0 -> Text("Sobrando ${Dinheiro.formatar(dif)}", color = Laranja, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    else -> Text("Faltando ${Dinheiro.formatar(-dif)}", color = Vermelho, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (dif != 0L) Text("Ao fechar, o saldo é corrigido e a diferença fica registrada.", fontSize = 15.sp)
            }
            Campo(obs, { obs = it }, "Observação (opcional)")
            BotaoPrincipal("Fechar caixa", Icons.Filled.Lock, habilitado = v != null) { confirmar = true }
        }
    }

    if (confirmar && conta != null) {
        val v = Dinheiro.ler(contado) ?: 0
        Confirmacao(
            "Fechar o caixa?", "Contado: ${Dinheiro.formatar(v)}\nSistema: ${Dinheiro.formatar(conta.saldo)}",
            aoConfirmar = {
                confirmar = false
                escopo.launch { app.repositorio.fecharCaixa(contaId, v, obs.trim()); aviso(context, "Caixa fechado"); nav.voltar() }
            },
            aoCancelar = { confirmar = false },
        )
    }
}
