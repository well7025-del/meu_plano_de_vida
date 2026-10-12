package br.com.mercadinhofacil.core.voz

enum class FormaPagamento(val rotulo: String) {
    DINHEIRO("Dinheiro"),
    PIX("Pix"),
    CARTAO("Cartão"),
    FIADO("Fiado"),
}

enum class Periodo(val rotulo: String) {
    HOJE("hoje"),
    ONTEM("ontem"),
    SEMANA("nesta semana"),
    MES("neste mês"),
    MES_PASSADO("no mês passado"),
}

/** Um item como foi falado. O app depois liga o nome a um produto cadastrado. */
data class ItemFalado(
    val quantidade: Double,
    val nome: String,
    /** Preço por unidade quando a pessoa disse ("a 5 reais cada"). */
    val precoUnitario: Long? = null,
    /** Valor total do item quando a pessoa disse ("10 arroz por 50 reais"). */
    val precoTotal: Long? = null,
)

enum class TipoConsulta {
    VENDAS, LUCRO, ESTOQUE, DIVIDA, SALDO, DESPESAS, MAIS_VENDIDOS, PRECO, SUGESTOES, PIX_PENDENTE, FALTANDO,
}

sealed class Comando {
    data class Venda(
        val itens: List<ItemFalado>,
        val forma: FormaPagamento?,
        val cliente: String? = null,
        val descontoValor: Long? = null,
        val descontoPercentual: Double? = null,
    ) : Comando()

    /** Mercadoria que chegou: aumenta o estoque e, se teve valor, lança a despesa. */
    data class Compra(
        val itens: List<ItemFalado>,
        val valorTotal: Long?,
        val conta: String? = null,
        val fornecedor: String? = null,
    ) : Comando()

    data class Despesa(val valor: Long, val descricao: String, val conta: String? = null) : Comando()

    data class RecebimentoFiado(val cliente: String, val valor: Long?, val forma: FormaPagamento?) : Comando()

    data class Transferencia(val valor: Long, val origem: String, val destino: String) : Comando()

    data class Perda(val itens: List<ItemFalado>, val motivo: String?) : Comando()

    data class Consulta(
        val tipo: TipoConsulta,
        val periodo: Periodo = Periodo.HOJE,
        val alvo: String? = null,
    ) : Comando()

    object CancelarUltimaVenda : Comando()

    object FecharCaixa : Comando()

    object Ajuda : Comando()

    data class NaoEntendi(val texto: String) : Comando()
}
