package br.com.mercadinhofacil.core.impressao

import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade

data class LinhaComprovante(
    val nome: String,
    val quantidade: Double,
    val unidade: String,
    val precoUnitario: Long,
    val total: Long,
)

data class DadosComprovante(
    val loja: String,
    val endereco: String? = null,
    val telefone: String? = null,
    val numero: Long,
    val dataHora: String,
    val itens: List<LinhaComprovante>,
    val subtotal: Long,
    val desconto: Long,
    val total: Long,
    val forma: String,
    /** "PAGO", "AGUARDANDO PIX", "FIADO", "CANCELADA" */
    val situacao: String,
    val cliente: String? = null,
    val valorRecebido: Long? = null,
    val troco: Long? = null,
    val pixCopiaECola: String? = null,
    val pixChave: String? = null,
    val pixTitular: String? = null,
    val rodape: String? = "Obrigado pela preferência!",
)

/** Texto do comprovante: para impressora térmica (colunas fixas) e para WhatsApp. */
object Comprovante {

    private fun centro(s: String, largura: Int) = s.take(largura).let { " ".repeat((largura - it.length) / 2) + it }

    private fun colunas(esq: String, dir: String, largura: Int): String {
        val espaco = largura - dir.length
        val e = if (esq.length >= espaco) esq.take(espaco - 1) else esq
        return e + " ".repeat(largura - e.length - dir.length) + dir
    }

    private fun qtd(l: LinhaComprovante) =
        Quantidade.formatar(l.quantidade) + if (l.unidade.isNotBlank() && l.unidade != "un") " ${l.unidade}" else ""

    /** 32 colunas = papel de 58 mm; 48 colunas = 80 mm. */
    fun texto(d: DadosComprovante, largura: Int = 32): String = buildString {
        val traco = "-".repeat(largura)
        appendLine(centro(d.loja.uppercase(), largura))
        d.endereco?.takeIf { it.isNotBlank() }?.let { appendLine(centro(it, largura)) }
        d.telefone?.takeIf { it.isNotBlank() }?.let { appendLine(centro("Tel: $it", largura)) }
        appendLine(traco)
        appendLine(colunas("Venda No ${d.numero}", d.dataHora, largura))
        d.cliente?.let { appendLine("Cliente: $it") }
        appendLine(traco)
        for (l in d.itens) {
            appendLine(l.nome.take(largura))
            appendLine(colunas("  ${qtd(l)} x ${Dinheiro.formatar(l.precoUnitario, false)}", Dinheiro.formatar(l.total, false), largura))
        }
        appendLine(traco)
        if (d.desconto > 0) {
            appendLine(colunas("Subtotal", Dinheiro.formatar(d.subtotal), largura))
            appendLine(colunas("Desconto", "-" + Dinheiro.formatar(d.desconto), largura))
        }
        appendLine(colunas("TOTAL", Dinheiro.formatar(d.total), largura))
        appendLine(colunas("Pagamento", d.forma, largura))
        d.valorRecebido?.let { appendLine(colunas("Recebido", Dinheiro.formatar(it), largura)) }
        d.troco?.takeIf { it > 0 }?.let { appendLine(colunas("Troco", Dinheiro.formatar(it), largura)) }
        appendLine(colunas("Situação", d.situacao, largura))
        if (d.pixChave != null) {
            appendLine(traco)
            appendLine("Pague com Pix")
            appendLine("Chave: ${d.pixChave}")
            d.pixTitular?.let { appendLine("Titular: $it") }
        }
        d.rodape?.let {
            appendLine(traco)
            appendLine(centro(it, largura))
        }
        appendLine(centro("Sem valor fiscal", largura))
    }

    fun whatsapp(d: DadosComprovante): String = buildString {
        appendLine("*${d.loja}*")
        appendLine("Comprovante da venda nº ${d.numero}")
        appendLine(d.dataHora)
        d.cliente?.let { appendLine("Cliente: $it") }
        appendLine()
        for (l in d.itens) {
            appendLine("• ${qtd(l)} x ${l.nome} — ${Dinheiro.formatar(l.total)}")
        }
        appendLine()
        if (d.desconto > 0) {
            appendLine("Subtotal: ${Dinheiro.formatar(d.subtotal)}")
            appendLine("Desconto: -${Dinheiro.formatar(d.desconto)}")
        }
        appendLine("*Total: ${Dinheiro.formatar(d.total)}*")
        appendLine("Pagamento: ${d.forma} (${d.situacao.lowercase()})")
        d.troco?.takeIf { it > 0 }?.let { appendLine("Troco: ${Dinheiro.formatar(it)}") }
        if (d.pixChave != null) {
            appendLine()
            appendLine("*Pague com Pix*")
            appendLine("Chave: ${d.pixChave}")
            d.pixTitular?.let { appendLine("Titular: $it") }
            d.pixCopiaECola?.let {
                appendLine()
                appendLine("Pix Copia e Cola:")
                appendLine(it)
            }
        }
        d.rodape?.let { appendLine(); appendLine(it) }
    }.trimEnd()
}
