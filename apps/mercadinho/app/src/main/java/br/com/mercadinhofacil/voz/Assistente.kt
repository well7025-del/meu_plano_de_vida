package br.com.mercadinhofacil.voz

import br.com.mercadinhofacil.MercadinhoApp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.Texto
import br.com.mercadinhofacil.core.ia.Conselheiro
import br.com.mercadinhofacil.core.relatorio.Periodos
import br.com.mercadinhofacil.core.relatorio.Relatorio
import br.com.mercadinhofacil.core.voz.Comando
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.core.voz.Interpretador
import br.com.mercadinhofacil.core.voz.ItemFalado
import br.com.mercadinhofacil.core.voz.Periodo
import br.com.mercadinhofacil.core.voz.TipoConsulta
import br.com.mercadinhofacil.dados.Cliente
import br.com.mercadinhofacil.dados.ContaComSaldo
import br.com.mercadinhofacil.dados.ItemCarrinho
import br.com.mercadinhofacil.dados.Produto
import br.com.mercadinhofacil.dados.TipoMovimento
import br.com.mercadinhofacil.dados.paraDataHora
import java.time.LocalDate

data class Execucao(val resposta: String, val abrir: String? = null)

sealed class Resposta {
    /** Resposta pronta (consultas). [abrir] = tela sugerida para ver detalhes. */
    data class Informacao(val texto: String, val abrir: String? = null, val rotuloAbrir: String? = null) : Resposta()

    /** Operação que mexe em dinheiro/estoque: mostra o resumo e só grava se a pessoa confirmar. */
    data class Confirmar(
        val titulo: String,
        val linhas: List<String>,
        val total: Long?,
        val pedirForma: Boolean = false,
        val formaInicial: FormaPagamento? = null,
        val permitirFiado: Boolean = true,
        val executar: suspend (FormaPagamento?) -> Execucao,
    ) : Resposta()
}

/**
 * Transforma o que a pessoa falou em consulta respondida ou em operação
 * pronta para confirmar. É a "inteligência" do app: interpretação de frase,
 * busca aproximada de produto/cliente e análise do negócio, tudo local.
 */
class Assistente(private val app: MercadinhoApp) {

    private val repo get() = app.repositorio
    private val dao get() = repo.dao

    suspend fun responder(frase: String): Resposta = try {
        processar(Interpretador.interpretar(frase))
    } catch (e: Exception) {
        Resposta.Informacao("Tive um problema para fazer isso: ${e.message ?: "erro desconhecido"}.")
    }

    private fun nomesDe(p: Produto) = listOf(p.nome) + p.apelidos.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun acharProduto(nome: String): Produto? {
        val produtos = dao.listaProdutos()
        return Texto.melhor(nome, produtos) { nomesDe(it) }
    }

    private suspend fun acharCliente(nome: String): Cliente? {
        val clientes = dao.listaClientes().map { it.cliente }
        return Texto.melhor(nome, clientes, 0.55) { listOf(it.nome) }
    }

    private suspend fun acharConta(nome: String?): ContaComSaldo? {
        val contas = dao.listaContas()
        if (nome == null) return contas.firstOrNull { it.conta.tipo == "DINHEIRO" } ?: contas.firstOrNull()
        val porTipo = when (Texto.normalizar(nome)) {
            "pix", "banco" -> "PIX"
            "cartao", "maquininha", "maquina" -> "CARTAO"
            "dinheiro", "caixa", "gaveta", "especie" -> "DINHEIRO"
            else -> null
        }
        return porTipo?.let { t -> contas.firstOrNull { it.conta.tipo == t } }
            ?: Texto.melhor(nome, contas, 0.5) { listOf(it.conta.nome) }
    }

    private fun linhaItem(q: Double, unidade: String, nome: String, total: Long) =
        "${Quantidade.formatar(q)}${if (unidade != "un") " $unidade" else ""} × $nome — ${Dinheiro.formatar(total)}"

    private suspend fun processar(c: Comando): Resposta = when (c) {
        is Comando.Venda -> venda(c)
        is Comando.Compra -> compra(c)
        is Comando.Despesa -> despesa(c)
        is Comando.RecebimentoFiado -> recebimento(c)
        is Comando.Transferencia -> transferencia(c)
        is Comando.Perda -> perda(c)
        is Comando.Consulta -> consulta(c)
        Comando.CancelarUltimaVenda -> cancelarUltima()
        Comando.FecharCaixa -> Resposta.Informacao("Vou abrir o fechamento de caixa. Conte o dinheiro da gaveta e digite o valor.", "contas", "Fechar caixa")
        Comando.Ajuda -> Resposta.Informacao(AJUDA)
        is Comando.NaoEntendi -> Resposta.Informacao(
            "Não entendi \"${c.texto}\". Tente assim: \"vendi 2 arroz no pix\", \"gastei 50 reais de luz\", \"quanto vendi hoje\" ou diga \"ajuda\".",
        )
    }

    // ---------------------------------------------------------------- operações

    private suspend fun venda(c: Comando.Venda): Resposta {
        val carrinho = mutableListOf<ItemCarrinho>()
        val naoAchados = mutableListOf<String>()
        val semPreco = mutableListOf<String>()
        for (item in c.itens) {
            val p = acharProduto(item.nome)
            val precoFalado = item.precoUnitario ?: item.precoTotal?.let { Math.round(it / item.quantidade) }
            if (p == null && precoFalado == null) { naoAchados += item.nome; continue }
            val preco = precoFalado ?: p!!.preco
            if (preco <= 0) { semPreco += p?.nome ?: item.nome; continue }
            carrinho += ItemCarrinho(p?.id, p?.nome ?: Texto.capitalizar(item.nome), p?.unidade ?: "un", item.quantidade, preco, p?.custo ?: 0)
        }
        if (naoAchados.isNotEmpty()) {
            return Resposta.Informacao(
                "Não achei ${naoAchados.joinToString(" nem ")} nos produtos. Cadastre o produto, ou fale o preço junto, por exemplo: \"vendi 1 ${naoAchados.first()} a 5 reais\".",
                "produto/0", "Cadastrar produto",
            )
        }
        if (semPreco.isNotEmpty()) return Resposta.Informacao("${semPreco.joinToString()} está sem preço de venda. Coloque o preço no cadastro do produto.", "produtos", "Ver produtos")

        val subtotal = carrinho.sumOf { it.total }
        val desconto = (c.descontoValor ?: c.descontoPercentual?.let { Dinheiro.porcentagem(subtotal, it) } ?: 0).coerceIn(0, subtotal)
        var cliente: Cliente? = null
        var clienteNovo: String? = null
        if (c.cliente != null) {
            cliente = acharCliente(c.cliente)
            if (cliente == null) clienteNovo = Texto.capitalizar(c.cliente)
        }
        if (c.forma == FormaPagamento.FIADO && cliente == null && clienteNovo == null) {
            return Resposta.Informacao("Para quem é o fiado? Fale por exemplo: \"fiado para a Maria, 2 leite\".")
        }
        val linhas = carrinho.map { linhaItem(it.quantidade, it.unidade, it.nome, it.total) }.toMutableList()
        if (desconto > 0) linhas += "Desconto: -${Dinheiro.formatar(desconto)}"
        cliente?.let { linhas += "Cliente: ${it.nome}" }
        clienteNovo?.let { linhas += "Cliente novo: $it (será cadastrado)" }
        val total = subtotal - desconto
        return Resposta.Confirmar(
            titulo = "Registrar venda?",
            linhas = linhas,
            total = total,
            pedirForma = true,
            formaInicial = c.forma ?: FormaPagamento.DINHEIRO,
            permitirFiado = true,
        ) { forma ->
            val f = forma ?: FormaPagamento.DINHEIRO
            var clienteId = cliente?.id
            if (clienteId == null && clienteNovo != null && f == FormaPagamento.FIADO) {
                clienteId = repo.salvarCliente(Cliente(nome = clienteNovo))
            }
            if (f == FormaPagamento.FIADO && clienteId == null) {
                return@Confirmar Execucao("Fiado precisa de um cliente. Fale o nome junto: \"fiado para a Maria\".")
            }
            val id = repo.registrarVenda(carrinho, desconto, f, clienteId)
            val extra = if (f == FormaPagamento.FIADO) {
                " Agora ${cliente?.nome ?: clienteNovo} deve ${Dinheiro.formatar(repo.dividaDoCliente(clienteId!!))}."
            } else ""
            Execucao("Venda registrada: ${Dinheiro.formatar(total)} em ${f.rotulo}.$extra", "comprovante/$id")
        }
    }

    private suspend fun compra(c: Comando.Compra): Resposta {
        val resolvidos = mutableListOf<Pair<Produto, ItemFalado>>()
        val naoAchados = mutableListOf<String>()
        for (item in c.itens) {
            val p = acharProduto(item.nome)
            if (p == null) naoAchados += item.nome else resolvidos += p to item
        }
        if (naoAchados.isNotEmpty()) {
            return Resposta.Informacao("Não achei ${naoAchados.joinToString(" nem ")} nos produtos. Cadastre primeiro e depois registre a compra.", "produto/0", "Cadastrar produto")
        }
        val conta = if (c.valorTotal != null || resolvidos.any { it.second.precoUnitario != null || it.second.precoTotal != null }) acharConta(c.conta) else null
        // Valor de cada item: o que foi falado; se só veio o total, divide pelo custo atual de cada produto.
        val custos = resolvidos.map { (p, i) -> i.precoTotal ?: i.precoUnitario?.let { Dinheiro.multiplicar(it, i.quantidade) } }
        val totalFalado = c.valorTotal
        val pesos = resolvidos.map { (p, i) -> Dinheiro.multiplicar(p.custo.coerceAtLeast(1), i.quantidade) }
        val valores = resolvidos.indices.map { idx ->
            custos[idx] ?: totalFalado?.takeIf { custos.all { it == null } }?.let { Math.round(it.toDouble() * pesos[idx] / pesos.sum()) }
        }
        val linhas = resolvidos.mapIndexed { idx, (p, i) ->
            "+${Quantidade.formatar(i.quantidade)} ${p.unidade} ${p.nome}" + (valores[idx]?.let { " — ${Dinheiro.formatar(it)}" } ?: "")
        }.toMutableList()
        val total = valores.filterNotNull().sum().takeIf { it > 0 }
        if (total != null && conta != null) linhas += "Pago com: ${conta.conta.nome}"
        if (total == null) linhas += "Sem valor: só aumenta o estoque"
        return Resposta.Confirmar("Registrar entrada de mercadoria?", linhas, total) {
            resolvidos.forEachIndexed { idx, (p, i) ->
                repo.entradaEstoque(p.id, i.quantidade, null, if (valores[idx] != null) conta?.conta?.id else null, valores[idx], c.fornecedor ?: "")
            }
            Execucao("Estoque atualizado." + (total?.let { " Compra de ${Dinheiro.formatar(it)} lançada." } ?: ""), "produtos")
        }
    }

    private suspend fun despesa(c: Comando.Despesa): Resposta {
        val conta = acharConta(c.conta) ?: return Resposta.Informacao("Não há contas cadastradas.")
        return Resposta.Confirmar("Lançar despesa?", listOf(c.descricao, "Sai de: ${conta.conta.nome}"), c.valor) {
            repo.lancar(conta.conta.id, TipoMovimento.DESPESA, c.valor, false, c.descricao, c.descricao)
            Execucao("Despesa de ${Dinheiro.formatar(c.valor)} lançada em ${conta.conta.nome}.")
        }
    }

    private suspend fun recebimento(c: Comando.RecebimentoFiado): Resposta {
        val cliente = acharCliente(c.cliente)
            ?: return Resposta.Informacao("Não achei o cliente ${Texto.capitalizar(c.cliente)}.", "clientes", "Ver clientes")
        val divida = repo.dividaDoCliente(cliente.id)
        val valor = c.valor ?: divida
        if (valor <= 0) return Resposta.Informacao("${cliente.nome} não tem fiado em aberto.")
        val linhas = mutableListOf("Cliente: ${cliente.nome}", "Devia: ${Dinheiro.formatar(divida)}")
        if (valor > divida) linhas += "Atenção: valor maior que a dívida"
        linhas += "Fica devendo: ${Dinheiro.formatar((divida - valor).coerceAtLeast(0))}"
        return Resposta.Confirmar(
            "Receber pagamento de fiado?", linhas, valor,
            pedirForma = true, formaInicial = c.forma ?: FormaPagamento.DINHEIRO, permitirFiado = false,
        ) { forma ->
            repo.receberFiado(cliente.id, valor, forma ?: FormaPagamento.DINHEIRO)
            val resta = repo.dividaDoCliente(cliente.id)
            Execucao(
                "Recebido ${Dinheiro.formatar(valor)} de ${cliente.nome}. " +
                    if (resta > 0) "Ainda deve ${Dinheiro.formatar(resta)}." else "A conta está quitada.",
                "cliente/${cliente.id}",
            )
        }
    }

    private suspend fun transferencia(c: Comando.Transferencia): Resposta {
        val origem = acharConta(c.origem) ?: return Resposta.Informacao("Não achei a conta ${c.origem}.", "contas", "Ver contas")
        val destino = acharConta(c.destino) ?: return Resposta.Informacao("Não achei a conta ${c.destino}.", "contas", "Ver contas")
        if (origem.conta.id == destino.conta.id) return Resposta.Informacao("As duas contas são a mesma.")
        return Resposta.Confirmar(
            "Transferir entre contas?",
            listOf("De: ${origem.conta.nome} (saldo ${Dinheiro.formatar(origem.saldo)})", "Para: ${destino.conta.nome}", "Não conta como venda nem despesa"),
            c.valor,
        ) {
            repo.transferir(origem.conta.id, destino.conta.id, c.valor)
            Execucao("Transferi ${Dinheiro.formatar(c.valor)} de ${origem.conta.nome} para ${destino.conta.nome}.")
        }
    }

    private suspend fun perda(c: Comando.Perda): Resposta {
        val resolvidos = c.itens.map { it to acharProduto(it.nome) }
        resolvidos.firstOrNull { it.second == null }?.let { return Resposta.Informacao("Não achei ${it.first.nome} nos produtos.") }
        val prejuizo = resolvidos.sumOf { (i, p) -> Dinheiro.multiplicar(p!!.custo, i.quantidade) }
        return Resposta.Confirmar(
            "Registrar perda?",
            resolvidos.map { (i, p) -> "-${Quantidade.formatar(i.quantidade)} ${p!!.unidade} ${p.nome}" } + listOfNotNull(c.motivo?.let { "Motivo: $it" }),
            prejuizo,
        ) {
            resolvidos.forEach { (i, p) -> repo.registrarPerda(p!!.id, i.quantidade, c.motivo ?: "") }
            Execucao("Perda registrada. Prejuízo de ${Dinheiro.formatar(prejuizo)} pelo custo.")
        }
    }

    private suspend fun cancelarUltima(): Resposta {
        val v = dao.ultimaVenda() ?: return Resposta.Informacao("Não há venda para cancelar.")
        val vc = dao.vendaCompleta(v.id) ?: return Resposta.Informacao("Não há venda para cancelar.")
        val hora = v.data.paraDataHora().let { "%02d:%02d".format(it.hour, it.minute) }
        return Resposta.Confirmar(
            "Cancelar a venda nº ${v.id}?",
            listOf("Feita às $hora") + vc.itens.map { linhaItem(it.quantidade, it.unidade, it.nome, Dinheiro.multiplicar(it.precoUnitario, it.quantidade)) } +
                "Os produtos voltam para o estoque e o valor sai da conta",
            v.total,
        ) {
            repo.cancelarVenda(v.id, "Cancelada por voz")
            Execucao("Venda nº ${v.id} cancelada.")
        }
    }

    // ---------------------------------------------------------------- consultas

    private suspend fun consulta(c: Comando.Consulta): Resposta {
        val hoje = LocalDate.now()
        return when (c.tipo) {
            TipoConsulta.VENDAS -> {
                val (i, f) = Periodos.intervalo(c.periodo, hoje)
                val r = repo.resultado(i, f)
                var texto = Relatorio.resumo(r, c.periodo.rotulo)
                if (r.porForma.size > 1) texto += " " + r.porForma.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key.rotulo}: ${Dinheiro.formatar(it.value)}" } + "."
                Resposta.Informacao(texto, "relatorios", "Ver relatório")
            }
            TipoConsulta.LUCRO -> {
                val (i, f) = Periodos.intervalo(c.periodo, hoje)
                val r = repo.resultado(i, f)
                Resposta.Informacao(
                    "Lucro estimado ${c.periodo.rotulo}: ${Dinheiro.formatar(r.lucroEstimado)}. Vendas de ${Dinheiro.formatar(r.faturamento)}, " +
                        "custo das mercadorias ${Dinheiro.formatar(r.custoMercadorias)} e despesas de ${Dinheiro.formatar(r.despesas)}.",
                    "relatorios", "Ver relatório",
                )
            }
            TipoConsulta.DESPESAS -> {
                val (i, f) = Periodos.intervalo(c.periodo, hoje)
                val r = repo.resultado(i, f)
                val maiores = r.despesasPorDescricao.entries.take(3).joinToString(", ") { "${it.key} ${Dinheiro.formatar(it.value)}" }
                Resposta.Informacao(
                    if (r.despesas == 0L) "Nenhuma despesa lançada ${c.periodo.rotulo}."
                    else "Despesas ${c.periodo.rotulo}: ${Dinheiro.formatar(r.despesas)}. Maiores: $maiores.",
                    "contas", "Ver contas",
                )
            }
            TipoConsulta.MAIS_VENDIDOS -> {
                val (i, f) = Periodos.intervalo(c.periodo, hoje)
                val r = repo.resultado(i, f)
                if (r.produtos.isEmpty()) Resposta.Informacao("Ainda não há vendas ${c.periodo.rotulo}.")
                else Resposta.Informacao(
                    "Mais vendidos ${c.periodo.rotulo}: " + r.maisVendidos.take(5).mapIndexed { n, p -> "${n + 1}º ${p.nome}, ${Quantidade.formatar(p.quantidade)}" }.joinToString("; ") +
                        ". O que mais deu lucro foi ${r.maisLucrativos.first().nome}.",
                    "relatorios", "Ver relatório",
                )
            }
            TipoConsulta.ESTOQUE -> {
                if (c.alvo != null) {
                    val p = acharProduto(c.alvo) ?: return Resposta.Informacao("Não achei ${c.alvo} nos produtos.")
                    Resposta.Informacao(
                        "Tem ${Quantidade.formatar(p.estoque)} ${if (p.unidade == "un") "unidades" else p.unidade} de ${p.nome}." +
                            if (p.estoqueMinimo > 0 && p.estoque <= p.estoqueMinimo) " Está abaixo do mínimo, é bom comprar." else "",
                        "produto/${p.id}", "Abrir produto",
                    )
                } else faltando()
            }
            TipoConsulta.FALTANDO -> faltando()
            TipoConsulta.PRECO -> {
                val p = c.alvo?.let { acharProduto(it) } ?: return Resposta.Informacao("De qual produto? Fale por exemplo: \"quanto custa o arroz\".")
                Resposta.Informacao("${p.nome} custa ${Dinheiro.formatar(p.preco)}${if (p.unidade == "kg") " o quilo" else ""}.", "produto/${p.id}", "Abrir produto")
            }
            TipoConsulta.DIVIDA -> {
                if (c.alvo != null) {
                    val cl = acharCliente(c.alvo) ?: return Resposta.Informacao("Não achei o cliente ${Texto.capitalizar(c.alvo)}.")
                    val d = repo.dividaDoCliente(cl.id)
                    Resposta.Informacao(if (d > 0) "${cl.nome} deve ${Dinheiro.formatar(d)}." else "${cl.nome} não deve nada.", "cliente/${cl.id}", "Abrir cliente")
                } else {
                    val devedores = dao.listaClientes().filter { it.divida > 0 }.sortedByDescending { it.divida }
                    if (devedores.isEmpty()) Resposta.Informacao("Ninguém está devendo.")
                    else Resposta.Informacao(
                        "${devedores.size} ${if (devedores.size == 1) "cliente deve" else "clientes devem"}, total de ${Dinheiro.formatar(devedores.sumOf { it.divida })}. " +
                            "Maiores: " + devedores.take(3).joinToString(", ") { "${it.cliente.nome} ${Dinheiro.formatar(it.divida)}" } + ".",
                        "clientes", "Ver clientes",
                    )
                }
            }
            TipoConsulta.SALDO -> {
                val contas = dao.listaContas()
                if (c.alvo != null) {
                    val conta = acharConta(c.alvo) ?: return Resposta.Informacao("Não achei essa conta.")
                    Resposta.Informacao("Saldo de ${conta.conta.nome}: ${Dinheiro.formatar(conta.saldo)}.", "conta/${conta.conta.id}", "Abrir conta")
                } else {
                    Resposta.Informacao(
                        contas.joinToString(". ") { "${it.conta.nome}: ${Dinheiro.formatar(it.saldo)}" } + ". Total: ${Dinheiro.formatar(contas.sumOf { it.saldo })}.",
                        "contas", "Ver contas",
                    )
                }
            }
            TipoConsulta.PIX_PENDENTE -> {
                val pendentes = dao.listaPixPendentes()
                if (pendentes.isEmpty()) Resposta.Informacao("Não há Pix esperando confirmação.")
                else Resposta.Informacao("${pendentes.size} Pix esperando, total de ${Dinheiro.formatar(pendentes.sumOf { it.total })}. Confira no app do banco e confirme.", "pix", "Ver Pix")
            }
            TipoConsulta.SUGESTOES -> {
                val dicas = dicas()
                Resposta.Informacao(dicas.take(3).joinToString("\n\n") { "${it.titulo}: ${it.texto}" }, "dicas", "Ver todas as dicas")
            }
        }
    }

    private suspend fun faltando(): Resposta {
        val lista = Conselheiro.listaDeCompras(repo.situacaoProdutos(LocalDate.now()))
        if (lista.isEmpty()) return Resposta.Informacao("Nada faltando agora. O estoque está em dia.")
        return Resposta.Informacao(
            "Para comprar: " + lista.take(8).joinToString("; ") { "${Quantidade.formatar(it.quantidade)} ${it.nome} (${it.motivo})" } + ".",
            "dicas", "Lista de compras",
        )
    }

    suspend fun dicas() = run {
        val hoje = LocalDate.now()
        val (i, f) = Periodos.intervalo(Periodo.MES, hoje)
        val (ia, fa) = Periodos.anterior(Periodo.MES, hoje)
        val pendentes = dao.listaPixPendentes()
        Conselheiro.dicas(
            hoje, repo.resultado(i, f), repo.resultado(ia, fa), repo.situacaoProdutos(hoje), repo.situacaoClientes(),
            pendentes.sumOf { it.total }, pendentes.size,
        )
    }

    companion object {
        val AJUDA = """
            Você pode falar do seu jeito. Exemplos:
            • "Vendi 2 arroz e 1 coca no pix"
            • "Fiado para a Maria, 3 pães e 1 leite"
            • "A Maria pagou 30 reais"
            • "Comprei 10 pacotes de café a 12 reais cada"
            • "Gastei 80 reais de luz"
            • "Estragaram 3 tomates"
            • "Transfere 100 reais do dinheiro para o pix"
            • "Quanto vendi hoje?" / "Qual o lucro do mês?"
            • "Quanto tem de arroz?" / "O que está faltando?"
            • "Quem está devendo?" / "Qual o saldo?"
            • "Me dá uma dica para vender mais"
            • "Cancela a última venda"
        """.trimIndent()
    }
}
