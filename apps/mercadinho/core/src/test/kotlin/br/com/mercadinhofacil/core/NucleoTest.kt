package br.com.mercadinhofacil.core

import br.com.mercadinhofacil.core.ia.Conselheiro
import br.com.mercadinhofacil.core.ia.SituacaoCliente
import br.com.mercadinhofacil.core.ia.SituacaoProduto
import br.com.mercadinhofacil.core.impressao.Comprovante
import br.com.mercadinhofacil.core.impressao.DadosComprovante
import br.com.mercadinhofacil.core.impressao.EscPos
import br.com.mercadinhofacil.core.impressao.LinhaComprovante
import br.com.mercadinhofacil.core.pix.DadosPix
import br.com.mercadinhofacil.core.pix.Pix
import br.com.mercadinhofacil.core.pix.TipoChave
import br.com.mercadinhofacil.core.relatorio.DespesaRegistro
import br.com.mercadinhofacil.core.relatorio.ItemVendido
import br.com.mercadinhofacil.core.relatorio.Periodos
import br.com.mercadinhofacil.core.relatorio.Relatorio
import br.com.mercadinhofacil.core.relatorio.VendaRegistro
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.core.voz.Periodo
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NucleoTest {

    @Test
    fun dinheiro() {
        assertEquals("R$ 1.234,56", Dinheiro.formatar(123456))
        assertEquals("R$ 0,05", Dinheiro.formatar(5))
        assertEquals("-R$ 10,00", Dinheiro.formatar(-1000))
        assertEquals(1250L, Dinheiro.ler("12,5"))
        assertEquals(399L, Dinheiro.ler("R$ 3,99"))
        assertEquals(399L, Dinheiro.ler("3.99"))
        assertEquals(123456L, Dinheiro.ler("1.234,56"))
        assertEquals(123400L, Dinheiro.ler("1.234"))
        assertNull(Dinheiro.ler("abc"))
        assertEquals("12 reais e 50 centavos", Dinheiro.porExtenso(1250))
        assertEquals(750L, Dinheiro.multiplicar(1500, 0.5))
        assertEquals("0,5", Quantidade.formatar(0.5))
        assertEquals("3", Quantidade.formatar(3.0))
    }

    @Test
    fun crcDoExemploDoBancoCentral() {
        val semCrc = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304"
        assertEquals("1D3D", Pix.crc16(semCrc))
    }

    @Test
    fun pixCopiaECola() {
        val dados = DadosPix("José da Silva Ção", "Banco X", "(11) 98765-4321", TipoChave.TELEFONE, "São Paulo")
        val c = Pix.copiaECola(dados, 1050, "VENDA42")
        assertTrue(c.startsWith("000201"))
        assertTrue(c.contains("0014br.gov.bcb.pix0114+5511987654321"))
        assertTrue(c.contains("540510.50"))
        assertTrue(c.contains("5917JOSE DA SILVA CAO"))
        assertTrue(c.contains("6009SAO PAULO"))
        assertTrue(c.contains("62110507VENDA42"))
        assertEquals(Pix.crc16(c.dropLast(4)), c.takeLast(4))

        val semValor = Pix.copiaECola(dados.copy(chave = "fulano@Email.com", tipo = TipoChave.EMAIL), null)
        assertTrue(!semValor.contains("5405"))
        assertTrue(semValor.contains("fulano@email.com"))
    }

    @Test
    fun chavesPix() {
        assertEquals(null, Pix.normalizarChave("111.111.111-11", TipoChave.CPF))
        assertEquals("52998224725", Pix.normalizarChave("529.982.247-25", TipoChave.CPF))
        assertEquals(TipoChave.CPF, Pix.detectarTipo("529.982.247-25"))
        assertEquals(TipoChave.TELEFONE, Pix.detectarTipo("(21) 99999-0000"))
        assertEquals(TipoChave.EMAIL, Pix.detectarTipo("a@b.com"))
        assertEquals(TipoChave.CNPJ, Pix.detectarTipo("11.222.333/0001-81"))
        assertEquals("11222333000181", Pix.normalizarChave("11.222.333/0001-81", TipoChave.CNPJ))
    }

    private fun comprovante() = DadosComprovante(
        loja = "Mercadinho da Ana", numero = 7, dataHora = "12/10/2026 10:30",
        itens = listOf(
            LinhaComprovante("Arroz 5kg", 2.0, "un", 2599, 5198),
            LinhaComprovante("Queijo mussarela", 0.25, "kg", 4800, 1200),
        ),
        subtotal = 6398, desconto = 398, total = 6000, forma = "Dinheiro", situacao = "PAGO",
        valorRecebido = 10000, troco = 4000,
    )

    @Test
    fun comprovanteEmColunas() {
        val t = Comprovante.texto(comprovante(), 32)
        t.lines().filter { it.isNotEmpty() }.forEach { assertTrue(it.length <= 32, "linha larga demais: '$it'") }
        assertTrue(t.contains("TOTAL                   R$ 60,00"))
        assertTrue(t.contains("  0,25 kg x 48,00          12,00"))
        assertTrue(Comprovante.whatsapp(comprovante()).contains("*Total: R$ 60,00*"))
    }

    @Test
    fun escPosSemAcento() {
        val bytes = EscPos.comprovante(comprovante().copy(loja = "Mercearia São João"))
        assertEquals(0x1B, bytes[0].toInt())
        assertEquals(0x40, bytes[1].toInt())
        assertTrue(bytes.all { it >= 0 }, "só ASCII")
        assertTrue(String(bytes, Charsets.US_ASCII).contains("Mercearia Sao Joao"))
    }

    private val agora = LocalDateTime.of(2026, 10, 12, 10, 0)

    private fun vendas() = listOf(
        VendaRegistro(1, agora, 900, 100, FormaPagamento.PIX, listOf(
            ItemVendido(1, "Arroz", 1.0, 600, 400),
            ItemVendido(2, "Feijão", 1.0, 400, 300),
        )),
        VendaRegistro(2, agora.plusHours(2), 1200, 0, FormaPagamento.DINHEIRO, listOf(ItemVendido(1, "Arroz", 2.0, 1200, 800))),
        VendaRegistro(3, agora.minusDays(1), 500, 0, FormaPagamento.FIADO, listOf(ItemVendido(2, "Feijão", 1.0, 500, 300))),
    )

    @Test
    fun relatorioDoDia() {
        val (ini, fim) = Periodos.intervalo(Periodo.HOJE, agora.toLocalDate())
        val r = Relatorio.calcular(ini, fim, vendas(), listOf(DespesaRegistro(agora, 300, "luz")))
        assertEquals(2, r.quantidadeVendas)
        assertEquals(2100L, r.faturamento)
        assertEquals(1500L, r.custoMercadorias)
        assertEquals(600L, r.lucroBruto)
        assertEquals(300L, r.lucroEstimado)
        val arroz = r.produtos.first { it.produtoId == 1L }
        assertEquals(3.0, arroz.quantidade)
        assertEquals(1740L, arroz.receita) // 600 - 60 de desconto + 1200
        assertEquals(r.faturamento, r.produtos.sumOf { it.receita })
        assertEquals(mapOf(FormaPagamento.PIX to 900L, FormaPagamento.DINHEIRO to 1200L), r.porForma)
        assertEquals("Arroz", r.maisVendidos.first().nome)
    }

    @Test
    fun periodos() {
        val hoje = LocalDate.of(2026, 10, 14) // quarta
        assertEquals(LocalDate.of(2026, 10, 12).atStartOfDay(), Periodos.intervalo(Periodo.SEMANA, hoje).first)
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay() to LocalDate.of(2026, 10, 1).atStartOfDay(), Periodos.intervalo(Periodo.MES_PASSADO, hoje))
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay() to LocalDate.of(2026, 9, 15).atStartOfDay(), Periodos.anterior(Periodo.MES, hoje))
    }

    @Test
    fun conselheiro() {
        val hoje = agora.toLocalDate()
        val (ini, fim) = Periodos.intervalo(Periodo.MES, hoje)
        val mes = Relatorio.calcular(ini, fim, vendas(), emptyList())
        val produtos = listOf(
            SituacaoProduto(1, "Arroz", 2.0, 5.0, 600, 400, "un", 60.0, hoje, hoje.minusDays(90)),
            SituacaoProduto(2, "Feijão", 0.0, 2.0, 500, 300, "un", 10.0, hoje, hoje.minusDays(90)),
            SituacaoProduto(3, "Vela", 10.0, 0.0, 200, 250, "un", 0.0, null, hoje.minusDays(60)),
        )
        val dicas = Conselheiro.dicas(hoje, mes, null, produtos, listOf(SituacaoCliente("Zé", 5000, hoje.minusDays(45))))
        val titulos = dicas.map { it.titulo }
        assertEquals("Produtos em falta", titulos.first())
        assertTrue("Vendendo no prejuízo" in titulos)
        assertTrue("Vai acabar logo" in titulos)
        assertTrue("Mercadoria parada" in titulos)
        assertTrue("Fiado atrasado" in titulos)

        val lista = Conselheiro.listaDeCompras(produtos, 7)
        assertEquals(listOf("Feijão", "Arroz"), lista.map { it.nome })
        assertEquals(17.0, lista.first { it.nome == "Arroz" }.quantidade) // 5 + 2*7 - 2

        assertEquals(650L, Conselheiro.precoSugerido(450, 30.0))
        assertEquals(1649L, Conselheiro.precoSugerido(1150, 30.0))
        assertEquals(275L, Conselheiro.arredondarPreco(260))
    }
}
