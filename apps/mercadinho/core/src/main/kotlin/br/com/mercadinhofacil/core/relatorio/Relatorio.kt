package br.com.mercadinhofacil.core.relatorio

import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.core.voz.Periodo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

data class ItemVendido(
    val produtoId: Long?,
    val nome: String,
    val quantidade: Double,
    /** preço × quantidade, antes do desconto da venda */
    val receita: Long,
    /** custo × quantidade, no custo do dia da venda */
    val custo: Long,
)

data class VendaRegistro(
    val id: Long,
    val dataHora: LocalDateTime,
    val total: Long,
    val desconto: Long,
    val forma: FormaPagamento,
    val itens: List<ItemVendido>,
)

data class DespesaRegistro(val dataHora: LocalDateTime, val valor: Long, val descricao: String)

data class DesempenhoProduto(
    val produtoId: Long?,
    val nome: String,
    val quantidade: Double,
    val receita: Long,
    val custo: Long,
) {
    val lucro: Long get() = receita - custo
    /** Margem sobre o preço de venda, em %. */
    val margem: Double get() = if (receita > 0) lucro * 100.0 / receita else 0.0
}

data class Resultado(
    val inicio: LocalDateTime,
    val fim: LocalDateTime,
    val quantidadeVendas: Int,
    val faturamento: Long,
    val descontos: Long,
    /** Custo das mercadorias vendidas (CMV). */
    val custoMercadorias: Long,
    val despesas: Long,
    val porForma: Map<FormaPagamento, Long>,
    val produtos: List<DesempenhoProduto>,
    val porDia: Map<LocalDate, Long>,
    val porHora: Map<Int, Long>,
    val porDiaDaSemana: Map<DayOfWeek, Long>,
    val despesasPorDescricao: Map<String, Long>,
) {
    val lucroBruto: Long get() = faturamento - custoMercadorias
    val lucroEstimado: Long get() = lucroBruto - despesas
    val ticketMedio: Long get() = if (quantidadeVendas > 0) faturamento / quantidadeVendas else 0
    val margemBruta: Double get() = if (faturamento > 0) lucroBruto * 100.0 / faturamento else 0.0
    val maisVendidos: List<DesempenhoProduto> get() = produtos.sortedByDescending { it.quantidade }
    val maioresMargens: List<DesempenhoProduto> get() = produtos.filter { it.receita > 0 }.sortedByDescending { it.margem }
    val maisLucrativos: List<DesempenhoProduto> get() = produtos.sortedByDescending { it.lucro }
}

object Periodos {
    /** Início (inclusivo) e fim (exclusivo). */
    fun intervalo(p: Periodo, hoje: LocalDate): Pair<LocalDateTime, LocalDateTime> = when (p) {
        Periodo.HOJE -> hoje.atStartOfDay() to hoje.plusDays(1).atStartOfDay()
        Periodo.ONTEM -> hoje.minusDays(1).atStartOfDay() to hoje.atStartOfDay()
        Periodo.SEMANA -> hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay() to hoje.plusDays(1).atStartOfDay()
        Periodo.MES -> hoje.withDayOfMonth(1).atStartOfDay() to hoje.plusDays(1).atStartOfDay()
        Periodo.MES_PASSADO -> hoje.withDayOfMonth(1).minusMonths(1).atStartOfDay() to hoje.withDayOfMonth(1).atStartOfDay()
    }

    /** O mesmo trecho de tempo, imediatamente antes (para comparar). */
    fun anterior(p: Periodo, hoje: LocalDate): Pair<LocalDateTime, LocalDateTime> = when (p) {
        Periodo.HOJE -> intervalo(Periodo.HOJE, hoje.minusWeeks(1)) // mesmo dia da semana passada
        Periodo.ONTEM -> intervalo(Periodo.HOJE, hoje.minusDays(8))
        Periodo.SEMANA -> intervalo(Periodo.SEMANA, hoje.minusWeeks(1)).let { (i, _) -> i to i.plusDays(hoje.dayOfWeek.value.toLong()) }
        Periodo.MES -> {
            val inicio = hoje.withDayOfMonth(1).minusMonths(1)
            val fim = inicio.plusDays(hoje.dayOfMonth.toLong()).coerceAtMost(hoje.withDayOfMonth(1))
            inicio.atStartOfDay() to fim.atStartOfDay()
        }
        Periodo.MES_PASSADO -> intervalo(Periodo.MES_PASSADO, hoje.minusMonths(1))
    }
}

object Relatorio {

    fun calcular(
        inicio: LocalDateTime,
        fim: LocalDateTime,
        vendas: List<VendaRegistro>,
        despesas: List<DespesaRegistro>,
    ): Resultado {
        val noPeriodo = vendas.filter { !it.dataHora.isBefore(inicio) && it.dataHora.isBefore(fim) }
        val despesasPeriodo = despesas.filter { !it.dataHora.isBefore(inicio) && it.dataHora.isBefore(fim) }

        val porProduto = linkedMapOf<String, DesempenhoProduto>()
        for (v in noPeriodo) {
            val bruto = v.itens.sumOf { it.receita }
            for (item in v.itens) {
                // O desconto da venda é repartido entre os itens pelo peso de cada um.
                val parteDesconto = if (bruto > 0) (v.desconto.toDouble() * item.receita / bruto).roundToLong() else 0
                val chave = item.produtoId?.toString() ?: "n:" + item.nome.lowercase()
                val atual = porProduto[chave]
                porProduto[chave] = if (atual == null) {
                    DesempenhoProduto(item.produtoId, item.nome, item.quantidade, item.receita - parteDesconto, item.custo)
                } else {
                    atual.copy(
                        quantidade = atual.quantidade + item.quantidade,
                        receita = atual.receita + item.receita - parteDesconto,
                        custo = atual.custo + item.custo,
                    )
                }
            }
        }

        return Resultado(
            inicio = inicio,
            fim = fim,
            quantidadeVendas = noPeriodo.size,
            faturamento = noPeriodo.sumOf { it.total },
            descontos = noPeriodo.sumOf { it.desconto },
            custoMercadorias = noPeriodo.sumOf { v -> v.itens.sumOf { it.custo } },
            despesas = despesasPeriodo.sumOf { it.valor },
            porForma = noPeriodo.groupBy { it.forma }.mapValues { (_, l) -> l.sumOf { it.total } },
            produtos = porProduto.values.toList(),
            porDia = noPeriodo.groupBy { it.dataHora.toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.total } }.toSortedMap(),
            porHora = noPeriodo.groupBy { it.dataHora.hour }.mapValues { (_, l) -> l.sumOf { it.total } }.toSortedMap(),
            porDiaDaSemana = noPeriodo.groupBy { it.dataHora.dayOfWeek }.mapValues { (_, l) -> l.sumOf { it.total } }.toSortedMap(),
            despesasPorDescricao = despesasPeriodo.groupBy { it.descricao.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }
                .mapValues { (_, l) -> l.sumOf { it.valor } }
                .toList().sortedByDescending { it.second }.toMap(),
        )
    }

    /** Resumo falado/escrito do período. */
    fun resumo(r: Resultado, nomePeriodo: String): String = buildString {
        if (r.quantidadeVendas == 0) {
            append("Nenhuma venda $nomePeriodo.")
        } else {
            append("${r.quantidadeVendas} ${if (r.quantidadeVendas == 1) "venda" else "vendas"} $nomePeriodo, ")
            append("somando ${Dinheiro.formatar(r.faturamento)}. ")
            append("Lucro bruto de ${Dinheiro.formatar(r.lucroBruto)}")
            if (r.despesas > 0) append(", e depois das despesas de ${Dinheiro.formatar(r.despesas)}, sobra ${Dinheiro.formatar(r.lucroEstimado)}")
            append(".")
        }
    }

    /** Texto do relatório para compartilhar (WhatsApp, e-mail, contador). */
    fun texto(r: Resultado, loja: String, nomePeriodo: String): String = buildString {
        appendLine("*$loja* — Relatório $nomePeriodo")
        appendLine()
        appendLine("Vendas: ${r.quantidadeVendas}")
        appendLine("Faturamento: ${Dinheiro.formatar(r.faturamento)}")
        if (r.descontos > 0) appendLine("Descontos dados: ${Dinheiro.formatar(r.descontos)}")
        appendLine("Custo das mercadorias: ${Dinheiro.formatar(r.custoMercadorias)}")
        appendLine("Lucro bruto: ${Dinheiro.formatar(r.lucroBruto)} (${"%.1f".format(r.margemBruta)}%)")
        appendLine("Despesas: ${Dinheiro.formatar(r.despesas)}")
        appendLine("*Lucro estimado: ${Dinheiro.formatar(r.lucroEstimado)}*")
        appendLine("Ticket médio: ${Dinheiro.formatar(r.ticketMedio)}")
        if (r.porForma.isNotEmpty()) {
            appendLine()
            appendLine("Por forma de pagamento:")
            r.porForma.entries.sortedByDescending { it.value }.forEach { appendLine("• ${it.key.rotulo}: ${Dinheiro.formatar(it.value)}") }
        }
        if (r.produtos.isNotEmpty()) {
            appendLine()
            appendLine("Mais vendidos:")
            r.maisVendidos.take(10).forEachIndexed { i, p ->
                appendLine("${i + 1}. ${p.nome} — ${br.com.mercadinhofacil.core.Quantidade.formatar(p.quantidade)} un, ${Dinheiro.formatar(p.receita)}")
            }
        }
    }.trimEnd()
}
