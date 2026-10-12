package br.com.mercadinhofacil.dados

import androidx.room.withTransaction
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.ia.SituacaoCliente
import br.com.mercadinhofacil.core.ia.SituacaoProduto
import br.com.mercadinhofacil.core.relatorio.DespesaRegistro
import br.com.mercadinhofacil.core.relatorio.ItemVendido
import br.com.mercadinhofacil.core.relatorio.Relatorio
import br.com.mercadinhofacil.core.relatorio.Resultado
import br.com.mercadinhofacil.core.relatorio.VendaRegistro
import br.com.mercadinhofacil.core.voz.FormaPagamento
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Um item no carrinho da tela de venda. */
data class ItemCarrinho(
    val produtoId: Long?,
    val nome: String,
    val unidade: String,
    val quantidade: Double,
    val precoUnitario: Long,
    val custoUnitario: Long,
) {
    val total: Long get() = Dinheiro.multiplicar(precoUnitario, quantidade)
}

data class FluxoDeCaixa(
    val entradasVendas: Long,
    val recebimentosFiado: Long,
    val outrasEntradas: Long,
    val despesas: Long,
    val compras: Long,
    val retiradas: Long,
    val ajustes: Long,
) {
    val entradas: Long get() = entradasVendas + recebimentosFiado + outrasEntradas
    val saidas: Long get() = despesas + compras + retiradas
    val saldo: Long get() = entradas - saidas + ajustes
}

fun Long.paraDataHora(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
fun LocalDateTime.paraMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun LocalDate.paraMillis(): Long = atStartOfDay().paraMillis()

/**
 * Todas as operações que mexem em dinheiro ou estoque passam por aqui, sempre
 * dentro de uma transação: ou grava tudo (venda + itens + estoque + caixa), ou
 * não grava nada.
 */
class Repositorio(private val banco: BancoDados) {

    val dao = banco.dao()

    private suspend fun registrar(acao: String, detalhe: String) {
        dao.inserir(Historico(acao = acao, detalhe = detalhe))
    }

    /** Cria Dinheiro, Pix e Cartão na primeira vez que o app abre. */
    suspend fun prepararContasPadrao() {
        if (dao.quantidadeContas() > 0) return
        dao.inserir(Conta(nome = "Dinheiro (gaveta)", tipo = TipoConta.DINHEIRO, ordem = 0))
        dao.inserir(Conta(nome = "Pix", tipo = TipoConta.PIX, ordem = 1))
        dao.inserir(Conta(nome = "Cartão (maquininha)", tipo = TipoConta.CARTAO, ordem = 2))
    }

    suspend fun contaParaForma(forma: FormaPagamento): Conta? = when (forma) {
        FormaPagamento.DINHEIRO -> dao.contaPorTipo(TipoConta.DINHEIRO)
        FormaPagamento.PIX -> dao.contaPorTipo(TipoConta.PIX)
        FormaPagamento.CARTAO -> dao.contaPorTipo(TipoConta.CARTAO)
        FormaPagamento.FIADO -> null
    } ?: dao.listaContas().firstOrNull()?.conta

    // ---------------------------------------------------------------- vendas

    /**
     * @param pixPendente para Pix: true = ainda não caiu, fica na lista de
     * pendentes até alguém confirmar.
     */
    suspend fun registrarVenda(
        itens: List<ItemCarrinho>,
        desconto: Long,
        forma: FormaPagamento,
        clienteId: Long?,
        valorRecebido: Long? = null,
        pixPendente: Boolean = false,
    ): Long = banco.withTransaction {
        require(itens.isNotEmpty()) { "A venda está vazia" }
        require(forma != FormaPagamento.FIADO || clienteId != null) { "Fiado precisa de um cliente" }
        val subtotal = itens.sumOf { it.total }
        val descontoReal = desconto.coerceIn(0, subtotal)
        val total = subtotal - descontoReal
        val status = when {
            forma == FormaPagamento.FIADO -> StatusVenda.FIADO
            forma == FormaPagamento.PIX && pixPendente -> StatusVenda.PENDENTE
            else -> StatusVenda.PAGA
        }
        val conta = contaParaForma(forma)
        val agora = System.currentTimeMillis()
        val id = dao.inserir(
            Venda(
                data = agora, subtotal = subtotal, desconto = descontoReal, total = total, forma = forma.name,
                contaId = conta?.id, clienteId = clienteId, status = status, valorRecebido = valorRecebido,
            ),
        )
        dao.inserirItens(itens.map { ItemVenda(0, id, it.produtoId, it.nome, it.unidade, it.quantidade, it.precoUnitario, it.custoUnitario) })
        for (item in itens) {
            val pid = item.produtoId ?: continue
            dao.somarEstoque(pid, -item.quantidade)
            dao.inserir(MovimentoEstoque(data = agora, produtoId = pid, quantidade = -item.quantidade, tipo = TipoEstoque.VENDA, custoUnitario = item.custoUnitario, vendaId = id))
        }
        if (status == StatusVenda.PAGA && conta != null) {
            dao.inserir(Movimento(data = agora, contaId = conta.id, tipo = TipoMovimento.VENDA, valor = total, entrada = true, descricao = "Venda nº $id", vendaId = id))
        }
        val cliente = clienteId?.let { dao.cliente(it)?.nome }
        registrar("Venda", "Venda nº $id: ${Dinheiro.formatar(total)} em ${forma.rotulo}" + (cliente?.let { " para $it" } ?: ""))
        id
    }

    suspend fun confirmarPix(vendaId: Long) = banco.withTransaction {
        val v = dao.vendaCompleta(vendaId)?.venda ?: return@withTransaction
        if (v.status != StatusVenda.PENDENTE) return@withTransaction
        val conta = dao.contaPorTipo(TipoConta.PIX) ?: contaParaForma(FormaPagamento.PIX) ?: return@withTransaction
        dao.atualizar(v.copy(status = StatusVenda.PAGA, contaId = conta.id))
        dao.inserir(Movimento(contaId = conta.id, tipo = TipoMovimento.VENDA, valor = v.total, entrada = true, descricao = "Venda nº ${v.id} (Pix confirmado)", vendaId = v.id))
        registrar("Pix confirmado", "Venda nº ${v.id}: ${Dinheiro.formatar(v.total)}")
    }

    /** Muda a forma de um Pix que não caiu (o cliente acabou pagando em dinheiro, ou ficou fiado). */
    suspend fun trocarFormaPendente(vendaId: Long, forma: FormaPagamento, clienteId: Long?) = banco.withTransaction {
        val v = dao.vendaCompleta(vendaId)?.venda ?: return@withTransaction
        if (v.status != StatusVenda.PENDENTE) return@withTransaction
        if (forma == FormaPagamento.FIADO) {
            dao.atualizar(v.copy(forma = forma.name, status = StatusVenda.FIADO, clienteId = clienteId ?: v.clienteId, contaId = null))
        } else {
            val conta = contaParaForma(forma) ?: return@withTransaction
            dao.atualizar(v.copy(forma = forma.name, status = StatusVenda.PAGA, contaId = conta.id))
            dao.inserir(Movimento(contaId = conta.id, tipo = TipoMovimento.VENDA, valor = v.total, entrada = true, descricao = "Venda nº ${v.id}", vendaId = v.id))
        }
        registrar("Forma de pagamento alterada", "Venda nº ${v.id}: Pix pendente virou ${forma.rotulo}")
    }

    /** Cancela a venda: devolve os produtos ao estoque e estorna o dinheiro da conta. */
    suspend fun cancelarVenda(vendaId: Long, motivo: String) = banco.withTransaction {
        val vc = dao.vendaCompleta(vendaId) ?: return@withTransaction
        if (vc.venda.status == StatusVenda.CANCELADA) return@withTransaction
        val agora = System.currentTimeMillis()
        dao.atualizar(vc.venda.copy(status = StatusVenda.CANCELADA, canceladaEm = agora, motivoCancelamento = motivo))
        for (item in vc.itens) {
            val pid = item.produtoId ?: continue
            dao.somarEstoque(pid, item.quantidade)
            dao.inserir(MovimentoEstoque(data = agora, produtoId = pid, quantidade = item.quantidade, tipo = TipoEstoque.CANCELAMENTO, custoUnitario = item.custoUnitario, vendaId = vendaId, observacao = "Venda nº $vendaId cancelada"))
        }
        dao.cancelarMovimentosDaVenda(vendaId)
        registrar("Venda cancelada", "Venda nº $vendaId (${Dinheiro.formatar(vc.venda.total)})" + if (motivo.isNotBlank()) " — $motivo" else "")
    }

    // ---------------------------------------------------------------- fiado

    suspend fun dividaDoCliente(clienteId: Long): Long =
        dao.fiadosDoCliente(clienteId).sumOf { it.total } - dao.totalRecebidoDoCliente(clienteId)

    suspend fun receberFiado(clienteId: Long, valor: Long, forma: FormaPagamento): Long = banco.withTransaction {
        require(valor > 0) { "Valor precisa ser maior que zero" }
        val conta = contaParaForma(if (forma == FormaPagamento.FIADO) FormaPagamento.DINHEIRO else forma)
            ?: throw IllegalStateException("Nenhuma conta cadastrada")
        val cliente = dao.cliente(clienteId) ?: throw IllegalStateException("Cliente não encontrado")
        val id = dao.inserir(
            Movimento(contaId = conta.id, tipo = TipoMovimento.RECEBIMENTO_FIADO, valor = valor, entrada = true, descricao = "Pagamento de ${cliente.nome}", clienteId = clienteId),
        )
        registrar("Fiado recebido", "${cliente.nome} pagou ${Dinheiro.formatar(valor)} em ${forma.rotulo}")
        id
    }

    /** Para o consultor: há quanto tempo está aberta a dívida mais antiga de cada cliente. */
    suspend fun situacaoClientes(): List<SituacaoCliente> = dao.listaClientes().filter { it.divida > 0 }.map { c ->
        var pago = dao.totalRecebidoDoCliente(c.cliente.id)
        var maisAntiga: LocalDate? = null
        for (v in dao.fiadosDoCliente(c.cliente.id)) {
            if (pago >= v.total) { pago -= v.total; continue }
            maisAntiga = v.data.paraDataHora().toLocalDate(); break
        }
        SituacaoCliente(c.cliente.nome, c.divida, maisAntiga)
    }

    // ---------------------------------------------------------------- contas

    suspend fun lancar(contaId: Long, tipo: String, valor: Long, entrada: Boolean, descricao: String, categoria: String = ""): Long = banco.withTransaction {
        require(valor > 0) { "Valor precisa ser maior que zero" }
        val id = dao.inserir(Movimento(contaId = contaId, tipo = tipo, valor = valor, entrada = entrada, descricao = descricao, categoria = categoria))
        val conta = dao.conta(contaId)?.nome ?: ""
        registrar(TipoMovimento.rotulo(tipo), "${descricao.ifBlank { TipoMovimento.rotulo(tipo) }}: ${Dinheiro.formatar(valor)} ($conta)")
        id
    }

    suspend fun transferir(origemId: Long, destinoId: Long, valor: Long, observacao: String = "") = banco.withTransaction {
        require(valor > 0) { "Valor precisa ser maior que zero" }
        require(origemId != destinoId) { "Escolha contas diferentes" }
        val origem = dao.conta(origemId) ?: return@withTransaction
        val destino = dao.conta(destinoId) ?: return@withTransaction
        val grupo = UUID.randomUUID().toString()
        val agora = System.currentTimeMillis()
        dao.inserir(Movimento(data = agora, contaId = origemId, tipo = TipoMovimento.TRANSFERENCIA, valor = valor, entrada = false, descricao = "Para ${destino.nome}" + if (observacao.isNotBlank()) " — $observacao" else "", grupo = grupo))
        dao.inserir(Movimento(data = agora, contaId = destinoId, tipo = TipoMovimento.TRANSFERENCIA, valor = valor, entrada = true, descricao = "De ${origem.nome}" + if (observacao.isNotBlank()) " — $observacao" else "", grupo = grupo))
        registrar("Transferência", "${Dinheiro.formatar(valor)} de ${origem.nome} para ${destino.nome}")
    }

    /** Desfaz um lançamento. Lançamentos de venda se desfazem cancelando a venda. */
    suspend fun cancelarMovimento(id: Long) = banco.withTransaction {
        val m = dao.movimento(id) ?: return@withTransaction
        if (m.cancelado) return@withTransaction
        require(m.vendaId == null) { "Para desfazer uma venda, cancele a venda" }
        if (m.grupo != null) dao.cancelarGrupo(m.grupo) else dao.cancelarMovimento(id)
        registrar("Lançamento cancelado", "${TipoMovimento.rotulo(m.tipo)}: ${m.descricao} (${Dinheiro.formatar(m.valor)})")
    }

    suspend fun fecharCaixa(contaId: Long, contado: Long, observacao: String): Long = banco.withTransaction {
        val conta = dao.listaContas().firstOrNull { it.conta.id == contaId } ?: throw IllegalStateException("Conta não encontrada")
        val diferenca = contado - conta.saldo
        if (diferenca != 0L) {
            dao.inserir(
                Movimento(
                    contaId = contaId, tipo = TipoMovimento.AJUSTE, valor = kotlin.math.abs(diferenca), entrada = diferenca > 0,
                    descricao = if (diferenca > 0) "Sobra no fechamento" else "Falta no fechamento",
                ),
            )
        }
        val id = dao.inserir(Fechamento(contaId = contaId, saldoSistema = conta.saldo, valorContado = contado, observacao = observacao))
        registrar(
            "Caixa fechado",
            "${conta.conta.nome}: sistema ${Dinheiro.formatar(conta.saldo)}, contado ${Dinheiro.formatar(contado)}" +
                if (diferenca != 0L) " (diferença ${Dinheiro.formatar(diferenca)})" else "",
        )
        id
    }

    suspend fun salvarConta(c: Conta) {
        if (c.id == 0L) {
            dao.inserir(c); registrar("Conta criada", c.nome)
        } else {
            dao.atualizar(c); registrar("Conta alterada", c.nome)
        }
    }

    // ---------------------------------------------------------------- produtos e estoque

    suspend fun salvarProduto(p: Produto): Long = banco.withTransaction {
        if (p.id == 0L) {
            val id = dao.inserir(p)
            if (p.estoque != 0.0) dao.inserir(MovimentoEstoque(produtoId = id, quantidade = p.estoque, tipo = TipoEstoque.AJUSTE, custoUnitario = p.custo, observacao = "Estoque inicial"))
            registrar("Produto cadastrado", "${p.nome}: ${Dinheiro.formatar(p.preco)}")
            id
        } else {
            val antes = dao.produto(p.id)
            dao.atualizar(p)
            if (antes != null) {
                val mudancas = mutableListOf<String>()
                if (antes.nome != p.nome) mudancas += "nome ${antes.nome} → ${p.nome}"
                if (antes.preco != p.preco) mudancas += "preço ${Dinheiro.formatar(antes.preco)} → ${Dinheiro.formatar(p.preco)}"
                if (antes.custo != p.custo) mudancas += "custo ${Dinheiro.formatar(antes.custo)} → ${Dinheiro.formatar(p.custo)}"
                if (antes.estoque != p.estoque) {
                    mudancas += "estoque ${Quantidade.formatar(antes.estoque)} → ${Quantidade.formatar(p.estoque)}"
                    dao.inserir(MovimentoEstoque(produtoId = p.id, quantidade = p.estoque - antes.estoque, tipo = TipoEstoque.AJUSTE, custoUnitario = p.custo, observacao = "Ajuste manual"))
                }
                if (antes.ativo && !p.ativo) mudancas += "removido"
                if (mudancas.isNotEmpty()) registrar("Produto alterado", "${p.nome}: ${mudancas.joinToString(", ")}")
            }
            p.id
        }
    }

    /**
     * Mercadoria que chegou. Atualiza o custo pela média ponderada e, se
     * [contaId] vier, tira o valor pago da conta como compra.
     */
    suspend fun entradaEstoque(produtoId: Long, quantidade: Double, custoUnitario: Long?, contaId: Long?, valorPago: Long?, observacao: String = "") = banco.withTransaction {
        require(quantidade > 0) { "Quantidade precisa ser maior que zero" }
        val p = dao.produto(produtoId) ?: return@withTransaction
        val custo = custoUnitario ?: valorPago?.let { Math.round(it / quantidade) } ?: p.custo
        val novoCusto = if (p.estoque > 0 && p.custo > 0) {
            Math.round((p.estoque * p.custo + quantidade * custo) / (p.estoque + quantidade))
        } else custo
        dao.atualizar(p.copy(estoque = p.estoque + quantidade, custo = novoCusto))
        dao.inserir(MovimentoEstoque(produtoId = produtoId, quantidade = quantidade, tipo = TipoEstoque.ENTRADA, custoUnitario = custo, observacao = observacao))
        val pago = valorPago ?: Dinheiro.multiplicar(custo, quantidade)
        if (contaId != null && pago > 0) {
            dao.inserir(Movimento(contaId = contaId, tipo = TipoMovimento.COMPRA, valor = pago, entrada = false, descricao = "Compra: ${Quantidade.formatar(quantidade)} ${p.nome}"))
        }
        registrar("Entrada de estoque", "${Quantidade.formatar(quantidade)} ${p.unidade} de ${p.nome} a ${Dinheiro.formatar(custo)}" + if (contaId != null) ", pago ${Dinheiro.formatar(pago)}" else "")
    }

    suspend fun registrarPerda(produtoId: Long, quantidade: Double, motivo: String) = banco.withTransaction {
        require(quantidade > 0) { "Quantidade precisa ser maior que zero" }
        val p = dao.produto(produtoId) ?: return@withTransaction
        dao.somarEstoque(produtoId, -quantidade)
        dao.inserir(MovimentoEstoque(produtoId = produtoId, quantidade = -quantidade, tipo = TipoEstoque.PERDA, custoUnitario = p.custo, observacao = motivo))
        registrar("Perda", "${Quantidade.formatar(quantidade)} ${p.unidade} de ${p.nome}" + (if (motivo.isNotBlank()) " ($motivo)" else "") + " — prejuízo de ${Dinheiro.formatar(Dinheiro.multiplicar(p.custo, quantidade))}")
    }

    suspend fun salvarCliente(c: Cliente): Long = if (c.id == 0L) {
        val id = dao.inserir(c); registrar("Cliente cadastrado", c.nome); id
    } else {
        dao.atualizar(c); registrar(if (c.ativo) "Cliente alterado" else "Cliente removido", c.nome); c.id
    }

    // ---------------------------------------------------------------- relatórios

    suspend fun resultado(inicio: LocalDateTime, fim: LocalDateTime): Resultado {
        val vendas = dao.vendasNoPeriodo(inicio.paraMillis(), fim.paraMillis()).map { vc ->
            VendaRegistro(
                id = vc.venda.id,
                dataHora = vc.venda.data.paraDataHora(),
                total = vc.venda.total,
                desconto = vc.venda.desconto,
                forma = runCatching { FormaPagamento.valueOf(vc.venda.forma) }.getOrDefault(FormaPagamento.DINHEIRO),
                itens = vc.itens.map { ItemVendido(it.produtoId, it.nome, it.quantidade, Dinheiro.multiplicar(it.precoUnitario, it.quantidade), Dinheiro.multiplicar(it.custoUnitario, it.quantidade)) },
            )
        }
        val despesas = dao.movimentosNoPeriodo(inicio.paraMillis(), fim.paraMillis())
            .filter { it.tipo == TipoMovimento.DESPESA }
            .map { DespesaRegistro(it.data.paraDataHora(), it.valor, it.categoria.ifBlank { it.descricao.ifBlank { "Outros" } }) }
        return Relatorio.calcular(inicio, fim, vendas, despesas)
    }

    suspend fun fluxo(inicio: LocalDateTime, fim: LocalDateTime): FluxoDeCaixa {
        val m = dao.movimentosNoPeriodo(inicio.paraMillis(), fim.paraMillis())
        fun soma(tipo: String, entrada: Boolean? = null) = m.filter { it.tipo == tipo && (entrada == null || it.entrada == entrada) }.sumOf { it.valor }
        return FluxoDeCaixa(
            entradasVendas = soma(TipoMovimento.VENDA),
            recebimentosFiado = soma(TipoMovimento.RECEBIMENTO_FIADO),
            outrasEntradas = soma(TipoMovimento.ENTRADA),
            despesas = soma(TipoMovimento.DESPESA),
            compras = soma(TipoMovimento.COMPRA),
            retiradas = soma(TipoMovimento.RETIRADA),
            ajustes = soma(TipoMovimento.AJUSTE, true) - soma(TipoMovimento.AJUSTE, false),
        )
    }

    suspend fun situacaoProdutos(hoje: LocalDate): List<SituacaoProduto> {
        val giro30 = dao.giro(hoje.minusDays(30).paraMillis()).associateBy { it.produtoId }
        val giroTotal = dao.giro(0).associateBy { it.produtoId }
        return dao.listaProdutos().map { p ->
            SituacaoProduto(
                id = p.id, nome = p.nome, estoque = p.estoque, estoqueMinimo = p.estoqueMinimo, preco = p.preco, custo = p.custo,
                unidade = p.unidade, vendido30Dias = giro30[p.id]?.quantidade ?: 0.0,
                ultimaVenda = giroTotal[p.id]?.ultimaVenda?.paraDataHora()?.toLocalDate(),
                cadastradoEm = p.criadoEm.paraDataHora().toLocalDate(),
            )
        }
    }
}
