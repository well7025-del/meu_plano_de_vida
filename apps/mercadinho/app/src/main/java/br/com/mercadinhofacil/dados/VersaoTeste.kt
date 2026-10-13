package br.com.mercadinhofacil.dados

import br.com.mercadinhofacil.BuildConfig

/** Cadastro além do limite da versão de teste. A mensagem já explica o que fazer. */
class LimiteDoTeste(mensagem: String) : IllegalStateException(mensagem)

/**
 * Limites da versão de teste. Na versão completa [ativa] é false e nada é limitado.
 * O limite de vendas conta todas as vendas já feitas, inclusive canceladas,
 * para não dar para "renovar" o teste cancelando vendas.
 */
object VersaoTeste {
    val ativa: Boolean = BuildConfig.TESTE
    val produtos: Int = BuildConfig.LIMITE_PRODUTOS
    val clientes: Int = BuildConfig.LIMITE_CLIENTES
    val vendas: Int = BuildConfig.LIMITE_VENDAS
    val linkCompra: String = BuildConfig.LINK_COMPRA

    private const val COMO_CONTINUAR =
        "Compre a versão completa para continuar sem limite. Seus cadastros vão junto: faça um backup aqui e restaure na versão completa."

    fun mensagemProdutos() = "A versão de teste permite até $produtos produtos. $COMO_CONTINUAR"
    fun mensagemClientes() = "A versão de teste permite até $clientes clientes. $COMO_CONTINUAR"
    fun mensagemVendas() = "A versão de teste permite até $vendas vendas. $COMO_CONTINUAR"
}
