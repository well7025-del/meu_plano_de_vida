package br.com.mercadinhofacil.core.ia

import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.relatorio.Resultado
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class SituacaoProduto(
    val id: Long,
    val nome: String,
    val estoque: Double,
    val estoqueMinimo: Double,
    val preco: Long,
    val custo: Long,
    val unidade: String,
    /** Quantidade vendida nos últimos 30 dias. */
    val vendido30Dias: Double,
    val ultimaVenda: LocalDate?,
    val cadastradoEm: LocalDate,
)

data class SituacaoCliente(val nome: String, val divida: Long, val compraFiadoMaisAntiga: LocalDate?)

enum class TipoDica { URGENTE, ATENCAO, OPORTUNIDADE, BOA_NOTICIA }

data class Dica(val tipo: TipoDica, val titulo: String, val texto: String, val prioridade: Int)

data class ItemCompra(val produtoId: Long, val nome: String, val quantidade: Double, val unidade: String, val custoEstimado: Long, val motivo: String)

/**
 * O "consultor" do mercadinho. Analisa vendas, estoque, margens, despesas e
 * fiado e devolve dicas em linguagem simples, ordenadas pelo que mais importa.
 *
 * Roda inteiro no celular, com regras de gestão de varejo — nenhum dado
 * comercial sai do aparelho.
 */
object Conselheiro {

    private val diasDaSemana = mapOf(
        DayOfWeek.MONDAY to "segunda-feira", DayOfWeek.TUESDAY to "terça-feira", DayOfWeek.WEDNESDAY to "quarta-feira",
        DayOfWeek.THURSDAY to "quinta-feira", DayOfWeek.FRIDAY to "sexta-feira", DayOfWeek.SATURDAY to "sábado",
        DayOfWeek.SUNDAY to "domingo",
    )

    fun nomeDia(d: DayOfWeek) = diasDaSemana.getValue(d)

    /** Lista de compras para cobrir [dias] de venda, mais o estoque mínimo. */
    fun listaDeCompras(produtos: List<SituacaoProduto>, dias: Int = 7): List<ItemCompra> =
        produtos.mapNotNull { p ->
            val mediaDia = p.vendido30Dias / 30.0
            val alvo = max(p.estoqueMinimo, 0.0) + mediaDia * dias
            val falta = alvo - p.estoque
            val abaixoDoMinimo = p.estoqueMinimo > 0 && p.estoque <= p.estoqueMinimo
            if (falta <= 0.0 || (!abaixoDoMinimo && mediaDia * dias < 0.5)) return@mapNotNull null
            val q = if (p.unidade == "kg" || p.unidade == "l") (ceil(falta * 10) / 10) else ceil(falta)
            val motivo = when {
                p.estoque <= 0 -> "acabou"
                abaixoDoMinimo -> "abaixo do mínimo (${Quantidade.formatar(p.estoque)} de ${Quantidade.formatar(p.estoqueMinimo)})"
                else -> "deve acabar em ${diasAteAcabar(p) ?: dias} dias"
            }
            ItemCompra(p.id, p.nome, q, p.unidade, Dinheiro.multiplicar(p.custo, q), motivo)
        }.sortedBy { if (it.motivo == "acabou") 0 else 1 }

    fun diasAteAcabar(p: SituacaoProduto): Int? {
        val mediaDia = p.vendido30Dias / 30.0
        if (mediaDia <= 0.0) return null
        return (p.estoque / mediaDia).toInt()
    }

    fun dicas(
        hoje: LocalDate,
        mes: Resultado,
        mesAnterior: Resultado?,
        produtos: List<SituacaoProduto>,
        clientes: List<SituacaoCliente>,
        pixPendentes: Long = 0,
        quantidadePixPendentes: Int = 0,
    ): List<Dica> {
        val dicas = mutableListOf<Dica>()

        // 1. Produto sem estoque ou acabando
        val zerados = produtos.filter { it.estoque <= 0 && (it.vendido30Dias > 0 || it.estoqueMinimo > 0) }
        if (zerados.isNotEmpty()) {
            dicas += Dica(
                TipoDica.URGENTE, "Produtos em falta",
                "Acabou: ${zerados.take(5).joinToString { it.nome }}${if (zerados.size > 5) " e mais ${zerados.size - 5}" else ""}. " +
                    "Cliente que não acha o que procura vai comprar no concorrente.",
                100,
            )
        }
        val acabando = produtos.filter { it.estoque > 0 }
            .mapNotNull { p -> diasAteAcabar(p)?.takeIf { it <= 3 }?.let { p to it } }
            .sortedBy { it.second }
        if (acabando.isNotEmpty()) {
            dicas += Dica(
                TipoDica.ATENCAO, "Vai acabar logo",
                acabando.take(5).joinToString("; ") { (p, d) -> "${p.nome}: ${if (d == 0) "acaba hoje" else "uns $d dias"}" } +
                    ". Veja a lista de compras para repor na quantidade certa.",
                90,
            )
        }
        val abaixoMinimo = produtos.filter { it.estoque > 0 && it.estoqueMinimo > 0 && it.estoque <= it.estoqueMinimo && acabando.none { a -> a.first.id == it.id } }
        if (abaixoMinimo.isNotEmpty()) {
            dicas += Dica(
                TipoDica.ATENCAO, "Abaixo do estoque mínimo",
                abaixoMinimo.take(6).joinToString { "${it.nome} (${Quantidade.formatar(it.estoque)})" } + ".",
                80,
            )
        }

        // 2. Preço abaixo do custo ou margem muito baixa
        val prejuizo = produtos.filter { it.custo > 0 && it.preco in 1..it.custo }
        if (prejuizo.isNotEmpty()) {
            dicas += Dica(
                TipoDica.URGENTE, "Vendendo no prejuízo",
                prejuizo.take(5).joinToString("; ") { "${it.nome}: custa ${Dinheiro.formatar(it.custo)} e vende a ${Dinheiro.formatar(it.preco)}" } +
                    ". Cada unidade vendida assim tira dinheiro do caixa.",
                95,
            )
        }
        val margemBaixa = produtos.filter { it.custo > 0 && it.preco > it.custo && (it.preco - it.custo) * 100.0 / it.preco < 12 && it.vendido30Dias > 0 }
        if (margemBaixa.isNotEmpty()) {
            dicas += Dica(
                TipoDica.OPORTUNIDADE, "Margem apertada",
                "Estes produtos deixam menos de 12% de lucro: " +
                    margemBaixa.take(5).joinToString { p ->
                        val sugerido = Dinheiro.deReais(p.custo / 100.0 / 0.75).let { arredondarPreco(it) }
                        "${p.nome} (sugestão: ${Dinheiro.formatar(sugerido)})"
                    } + ". Um aumento pequeno quase sempre passa despercebido.",
                60,
            )
        }
        val semCusto = produtos.count { it.custo <= 0 && it.vendido30Dias > 0 }
        if (semCusto > 0) {
            dicas += Dica(
                TipoDica.ATENCAO, "Produtos sem custo cadastrado",
                "$semCusto produto${if (semCusto > 1) "s vendidos não têm" else " vendido não tem"} preço de custo. Sem ele, o lucro do relatório fica maior do que o real.",
                55,
            )
        }

        // 3. Produtos parados
        val parados = produtos.filter { p ->
            p.estoque > 0 && ChronoUnit.DAYS.between(p.cadastradoEm, hoje) >= 30 &&
                (p.ultimaVenda == null || ChronoUnit.DAYS.between(p.ultimaVenda, hoje) >= 30)
        }
        if (parados.isNotEmpty()) {
            val dinheiroParado = parados.sumOf { Dinheiro.multiplicar(it.custo, it.estoque) }
            dicas += Dica(
                TipoDica.OPORTUNIDADE, "Mercadoria parada",
                "${parados.size} produto${if (parados.size > 1) "s" else ""} sem venda há mais de 30 dias" +
                    (if (dinheiroParado > 0) ", ${Dinheiro.formatar(dinheiroParado)} parados na prateleira" else "") +
                    ": ${parados.take(4).joinToString { it.nome }}. Faça uma promoção, um combo ou coloque perto do caixa.",
                50,
            )
        }

        // 4. Vendas em relação ao mês anterior
        if (mesAnterior != null && mesAnterior.faturamento > 0 && mes.faturamento > 0) {
            val variacao = (mes.faturamento - mesAnterior.faturamento) * 100.0 / mesAnterior.faturamento
            if (variacao >= 10) {
                dicas += Dica(TipoDica.BOA_NOTICIA, "Vendas subindo", "Você vendeu ${variacao.roundToInt()}% a mais que no mesmo período do mês passado. Continue assim!", 30)
            } else if (variacao <= -10) {
                dicas += Dica(
                    TipoDica.ATENCAO, "Vendas caindo",
                    "As vendas estão ${(-variacao).roundToInt()}% menores que no mesmo período do mês passado. " +
                        "Mande o catálogo no WhatsApp para os clientes e confira se não está faltando produto que sai muito.",
                    75,
                )
            }
        }

        // 5. Despesas pesando
        if (mes.faturamento > 0 && mes.despesas > 0) {
            val peso = mes.despesas * 100.0 / mes.faturamento
            if (mes.lucroEstimado < 0) {
                dicas += Dica(
                    TipoDica.URGENTE, "O mês está no vermelho",
                    "As despesas (${Dinheiro.formatar(mes.despesas)}) estão maiores que o lucro das vendas (${Dinheiro.formatar(mes.lucroBruto)}). " +
                        (mes.despesasPorDescricao.entries.firstOrNull()?.let { "A maior despesa é ${it.key}: ${Dinheiro.formatar(it.value)}. " } ?: "") +
                        "Corte o que der e revise os preços.",
                    97,
                )
            } else if (peso > 25) {
                dicas += Dica(
                    TipoDica.ATENCAO, "Despesas altas",
                    "As despesas já são ${peso.roundToInt()}% do que você vendeu no mês." +
                        (mes.despesasPorDescricao.entries.firstOrNull()?.let { " A maior é ${it.key} (${Dinheiro.formatar(it.value)})." } ?: ""),
                    65,
                )
            }
        }

        // 6. Fiado
        val devedores = clientes.filter { it.divida > 0 }
        val totalFiado = devedores.sumOf { it.divida }
        val antigos = devedores.filter { it.compraFiadoMaisAntiga != null && ChronoUnit.DAYS.between(it.compraFiadoMaisAntiga, hoje) > 30 }
        if (antigos.isNotEmpty()) {
            dicas += Dica(
                TipoDica.ATENCAO, "Fiado atrasado",
                "${antigos.size} cliente${if (antigos.size > 1) "s devem" else " deve"} há mais de 30 dias: " +
                    antigos.sortedByDescending { it.divida }.take(4).joinToString { "${it.nome} (${Dinheiro.formatar(it.divida)})" } +
                    ". Mande a cobrança com o Pix pelo WhatsApp — é só tocar no cliente.",
                85,
            )
        } else if (totalFiado > 0 && mes.faturamento > 0 && totalFiado * 100 / mes.faturamento > 20) {
            dicas += Dica(TipoDica.ATENCAO, "Muito dinheiro no fiado", "Há ${Dinheiro.formatar(totalFiado)} na rua em fiado. Combine datas de pagamento com os clientes.", 70)
        }

        if (quantidadePixPendentes > 0) {
            dicas += Dica(
                TipoDica.ATENCAO, "Pix esperando confirmação",
                "$quantidadePixPendentes Pix (${Dinheiro.formatar(pixPendentes)}) ainda não foram confirmados. Confira no app do banco e marque como recebido.",
                88,
            )
        }

        // 7. Oportunidades de venda
        val campeoes = mes.maisLucrativos.take(3).filter { it.lucro > 0 }
        if (campeoes.isNotEmpty()) {
            dicas += Dica(
                TipoDica.OPORTUNIDADE, "Seus campeões de lucro",
                "Quem mais deixa dinheiro no mês: ${campeoes.joinToString { "${it.nome} (${Dinheiro.formatar(it.lucro)})" }}. " +
                    "Deixe esses produtos à vista, nunca deixe faltar e destaque no catálogo.",
                40,
            )
        }
        if (mes.porDiaDaSemana.size >= 3) {
            val melhor = mes.porDiaDaSemana.maxByOrNull { it.value }!!
            val pior = mes.porDiaDaSemana.minByOrNull { it.value }!!
            if (melhor.key != pior.key) {
                dicas += Dica(
                    TipoDica.OPORTUNIDADE, "Dia mais fraco",
                    "${nomeDia(melhor.key).replaceFirstChar { it.uppercase() }} é o dia que mais vende; ${nomeDia(pior.key)} é o mais fraco. " +
                        "Experimente uma oferta só de ${nomeDia(pior.key)} para movimentar.",
                    25,
                )
            }
        }
        if (mes.porHora.size >= 4) {
            val pico = mes.porHora.maxByOrNull { it.value }!!.key
            dicas += Dica(TipoDica.OPORTUNIDADE, "Horário de pico", "O movimento é maior por volta das ${pico}h. Deixe o troco e o estoque da frente prontos antes desse horário.", 20)
        }
        if (mes.quantidadeVendas >= 20 && mes.ticketMedio in 1..2000) {
            dicas += Dica(
                TipoDica.OPORTUNIDADE, "Aumente o valor de cada venda",
                "Cada cliente gasta em média ${Dinheiro.formatar(mes.ticketMedio)}. Ofereça um item a mais no caixa (pão com café, refrigerante com salgado) — é o jeito mais fácil de vender mais.",
                22,
            )
        }

        if (dicas.isEmpty()) {
            dicas += Dica(
                TipoDica.BOA_NOTICIA, "Tudo em ordem",
                if (produtos.isEmpty()) "Cadastre seus produtos com preço de custo e estoque mínimo para eu poder analisar o seu negócio."
                else "Não encontrei problemas agora. Registre todas as vendas e despesas para as análises ficarem cada vez melhores.",
                0,
            )
        }
        return dicas.sortedByDescending { it.prioridade }
    }

    /** Arredonda para preço "de prateleira": termina em ,49 / ,99 ou múltiplo de 0,50. */
    fun arredondarPreco(centavos: Long): Long {
        if (centavos <= 0) return 0
        val reais = centavos / 100
        val cent = centavos % 100
        return when {
            centavos < 1000 -> ((centavos + 24) / 25) * 25 // até R$ 10: passo de 0,25
            cent == 0L -> centavos
            cent <= 49 -> reais * 100 + 49
            else -> reais * 100 + 99
        }
    }

    /** Preço de venda para atingir uma margem (sobre o preço). */
    fun precoSugerido(custo: Long, margemPercentual: Double = 30.0): Long =
        arredondarPreco(Dinheiro.deReais(custo / 100.0 / (1 - margemPercentual / 100.0)))
}
