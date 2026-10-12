package br.com.mercadinhofacil.core

import br.com.mercadinhofacil.core.voz.Comando
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.core.voz.Interpretador
import br.com.mercadinhofacil.core.voz.ItemFalado
import br.com.mercadinhofacil.core.voz.NumerosFalados
import br.com.mercadinhofacil.core.voz.Periodo
import br.com.mercadinhofacil.core.voz.TipoConsulta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InterpretadorTest {

    private fun venda(frase: String) = assertIs<Comando.Venda>(Interpretador.interpretar(frase), frase)

    @Test
    fun numerosPorExtenso() {
        assertEquals("25 arroz", NumerosFalados.converter("vinte e cinco arroz"))
        assertEquals("6 ovos", NumerosFalados.converter("meia duzia ovos"))
        assertEquals("250", NumerosFalados.converter("duzentos e cinquenta"))
        assertEquals("1500", NumerosFalados.converter("mil e quinhentos"))
        assertEquals("125", NumerosFalados.converter("cento e vinte e cinco"))
        assertEquals("por 4 e 50", NumerosFalados.converter("por quatro e cinquenta"))
        assertEquals("2 arroz e 1 feijao", NumerosFalados.converter("dois arroz e um feijao"))
    }

    @Test
    fun vendaSimplesNoPix() {
        val v = venda("Vendi dois arroz e uma coca no pix")
        assertEquals(FormaPagamento.PIX, v.forma)
        assertEquals(listOf(ItemFalado(2.0, "arroz"), ItemFalado(1.0, "coca")), v.itens)
    }

    @Test
    fun vendaComAlgarismosECartao() {
        val v = venda("vende 3 pão francês, 1 leite e 2 kg de tomate no cartão")
        assertEquals(FormaPagamento.CARTAO, v.forma)
        assertEquals(listOf(ItemFalado(3.0, "pao frances"), ItemFalado(1.0, "leite"), ItemFalado(2.0, "tomate")), v.itens)
    }

    @Test
    fun vendaEmGramas() {
        val v = venda("vendi 500 gramas de queijo em dinheiro")
        assertEquals(FormaPagamento.DINHEIRO, v.forma)
        assertEquals(0.5, v.itens.single().quantidade)
        assertEquals("queijo", v.itens.single().nome)
    }

    @Test
    fun vendaFiadoComCliente() {
        val v = venda("anota fiado pra dona Maria duas caixas de leite")
        assertEquals(FormaPagamento.FIADO, v.forma)
        assertEquals("maria", v.cliente)
        assertEquals(listOf(ItemFalado(2.0, "leite")), v.itens)

        val v2 = venda("vendi 1 sabão em pó fiado para o João")
        assertEquals(FormaPagamento.FIADO, v2.forma)
        assertEquals("joao", v2.cliente)
        assertEquals("sabao em po", v2.itens.single().nome)
    }

    @Test
    fun vendaComPrecoFalado() {
        val v = venda("vendi 2 picolé a 3 reais cada no dinheiro")
        assertEquals(ItemFalado(2.0, "picole", precoUnitario = 300), v.itens.single())
        val v2 = venda("vendi um bolo por R$ 15,50")
        assertEquals(ItemFalado(1.0, "bolo", precoUnitario = 1550), v2.itens.single())
        val v3 = venda("vendi um bolo por quinze e cinquenta")
        assertEquals(1550L, v3.itens.single().precoUnitario)
    }

    @Test
    fun vendaComDesconto() {
        val v = venda("vendi 1 fardo de cerveja com desconto de 5 reais no pix")
        assertEquals(500L, v.descontoValor)
        assertEquals("cerveja", v.itens.single().nome)
        val v2 = venda("vendi 2 refrigerante desconto de 10%")
        assertEquals(10.0, v2.descontoPercentual)
    }

    @Test
    fun compraComPreco() {
        val c = assertIs<Comando.Compra>(Interpretador.interpretar("comprei 10 pacotes de arroz a 4 e 50 cada e 5 feijão a 6 reais cada"))
        assertEquals(listOf(ItemFalado(10.0, "arroz", 450), ItemFalado(5.0, "feijao", 600)), c.itens)
        assertEquals(7500L, c.valorTotal)

        val c2 = assertIs<Comando.Compra>(Interpretador.interpretar("chegou 2 fardos de coca total 80 reais paguei no pix"))
        assertEquals(listOf(ItemFalado(2.0, "coca")), c2.itens)
        assertEquals(8000L, c2.valorTotal)
        assertEquals("pix", c2.conta)
    }

    @Test
    fun despesa() {
        val d = assertIs<Comando.Despesa>(Interpretador.interpretar("paguei a conta de luz de 180 reais no pix"))
        assertEquals(18000L, d.valor)
        assertEquals("Conta De Luz", d.descricao)
        assertEquals("pix", d.conta)
        val d2 = assertIs<Comando.Despesa>(Interpretador.interpretar("gastei cinquenta com gasolina"))
        assertEquals(5000L, d2.valor)
        assertEquals("Gasolina", d2.descricao)
    }

    @Test
    fun recebimentoDeFiado() {
        val r = assertIs<Comando.RecebimentoFiado>(Interpretador.interpretar("a Maria pagou 30 reais no pix"))
        assertEquals("maria", r.cliente)
        assertEquals(3000L, r.valor)
        assertEquals(FormaPagamento.PIX, r.forma)

        val r2 = assertIs<Comando.RecebimentoFiado>(Interpretador.interpretar("recebi do seu José 20 reais"))
        assertEquals("jose", r2.cliente)
        assertEquals(2000L, r2.valor)

        val r3 = assertIs<Comando.RecebimentoFiado>(Interpretador.interpretar("o Pedro quitou tudo"))
        assertEquals("pedro", r3.cliente)
        assertEquals(null, r3.valor)
    }

    @Test
    fun transferencia() {
        val t = assertIs<Comando.Transferencia>(Interpretador.interpretar("transferi 100 reais do dinheiro para o pix"))
        assertEquals(10000L, t.valor)
        assertEquals("dinheiro", t.origem)
        assertEquals("pix", t.destino)
    }

    @Test
    fun perda() {
        val p = assertIs<Comando.Perda>(Interpretador.interpretar("estragaram 3 tomates"))
        assertEquals(listOf(ItemFalado(3.0, "tomates")), p.itens)
        assertEquals("Estragou", p.motivo)
    }

    @Test
    fun consultas() {
        fun c(f: String) = assertIs<Comando.Consulta>(Interpretador.interpretar(f), f)
        assertEquals(Comando.Consulta(TipoConsulta.VENDAS, Periodo.HOJE), c("quanto vendi hoje?"))
        assertEquals(Comando.Consulta(TipoConsulta.LUCRO, Periodo.MES), c("qual foi o lucro do mês"))
        assertEquals(TipoConsulta.DIVIDA, c("quanto o João está devendo").tipo)
        assertEquals("joao", c("quanto o João está devendo").alvo)
        assertEquals("maria", c("quanto a Maria deve").alvo)
        assertEquals(Comando.Consulta(TipoConsulta.DIVIDA, Periodo.HOJE, null), c("quem está devendo?"))
        assertEquals("arroz", c("quanto tem de arroz no estoque").alvo)
        assertEquals(TipoConsulta.ESTOQUE, c("quanto tem de arroz no estoque").tipo)
        assertEquals(Comando.Consulta(TipoConsulta.SALDO, Periodo.HOJE, "pix"), c("quanto tem no pix?"))
        assertEquals(TipoConsulta.SALDO, c("qual o saldo das contas").tipo)
        assertEquals(TipoConsulta.PRECO, c("quanto custa o café?").tipo)
        assertEquals("cafe", c("quanto custa o café?").alvo)
        assertEquals(TipoConsulta.SUGESTOES, c("me dá uma dica para vender mais").tipo)
        assertEquals(TipoConsulta.FALTANDO, c("o que está faltando?").tipo)
        assertEquals(TipoConsulta.MAIS_VENDIDOS, c("quais os produtos mais vendidos").tipo)
        assertEquals(Periodo.ONTEM, c("quanto eu gastei ontem").periodo)
        assertEquals(TipoConsulta.DESPESAS, c("quanto eu gastei ontem").tipo)
    }

    @Test
    fun outros() {
        assertIs<Comando.CancelarUltimaVenda>(Interpretador.interpretar("cancela a última venda"))
        assertIs<Comando.FecharCaixa>(Interpretador.interpretar("fechar o caixa"))
        assertIs<Comando.NaoEntendi>(Interpretador.interpretar(""))
    }

    @Test
    fun semelhancaDeNomes() {
        val produtos = listOf("Arroz Tio João 5kg", "Feijão Carioca 1kg", "Coca-Cola 2L", "Café Pilão 500g", "Pão Francês")
        assertEquals("Arroz Tio João 5kg", Texto.melhor("arroz", produtos) { listOf(it) })
        assertEquals("Coca-Cola 2L", Texto.melhor("coca", produtos) { listOf(it) })
        assertEquals("Feijão Carioca 1kg", Texto.melhor("feijoes", produtos) { listOf(it) })
        assertEquals("Pão Francês", Texto.melhor("paes frances", produtos) { listOf(it) })
        assertEquals("Café Pilão 500g", Texto.melhor("cafe", produtos) { listOf(it) })
        assertEquals(null, Texto.melhor("sabonete", produtos) { listOf(it) })
    }
}
