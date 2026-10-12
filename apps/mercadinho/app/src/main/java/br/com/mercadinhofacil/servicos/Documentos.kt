package br.com.mercadinhofacil.servicos

import android.content.Context
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.impressao.DadosComprovante
import br.com.mercadinhofacil.core.impressao.EscPos
import br.com.mercadinhofacil.core.impressao.LinhaComprovante
import br.com.mercadinhofacil.core.pix.Pix
import br.com.mercadinhofacil.core.voz.FormaPagamento
import br.com.mercadinhofacil.dados.Preferencias
import br.com.mercadinhofacil.dados.StatusVenda
import br.com.mercadinhofacil.dados.VendaComItens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Monta comprovantes e cobranças a partir dos dados salvos. */
object Documentos {

    fun situacao(status: String) = when (status) {
        StatusVenda.PAGA -> "PAGO"
        StatusVenda.PENDENTE -> "AGUARDANDO PIX"
        StatusVenda.FIADO -> "FIADO"
        StatusVenda.CANCELADA -> "CANCELADA"
        else -> status
    }

    fun rotuloForma(forma: String) = runCatching { FormaPagamento.valueOf(forma).rotulo }.getOrDefault(forma)

    /** Copia e Cola do Pix de uma venda (null se o Pix não estiver configurado ou a chave for inválida). */
    fun pixDaVenda(prefs: Preferencias, vendaId: Long, valor: Long): String? {
        val dados = prefs.dadosPix ?: return null
        return runCatching { Pix.copiaECola(dados, valor, "VENDA$vendaId") }.getOrNull()
    }

    fun comprovante(prefs: Preferencias, vc: VendaComItens, cliente: String?): DadosComprovante {
        val v = vc.venda
        val mostrarPix = v.status == StatusVenda.PENDENTE || (v.status == StatusVenda.FIADO && prefs.dadosPix != null)
        val pix = if (mostrarPix) prefs.dadosPix else null
        return DadosComprovante(
            loja = prefs.nomeExibicao,
            endereco = prefs.lojaEndereco,
            telefone = prefs.lojaTelefone,
            numero = v.id,
            dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(v.data)),
            itens = vc.itens.map { LinhaComprovante(it.nome, it.quantidade, it.unidade, it.precoUnitario, Dinheiro.multiplicar(it.precoUnitario, it.quantidade)) },
            subtotal = v.subtotal,
            desconto = v.desconto,
            total = v.total,
            forma = rotuloForma(v.forma),
            situacao = situacao(v.status),
            cliente = cliente,
            valorRecebido = v.valorRecebido?.takeIf { v.forma == FormaPagamento.DINHEIRO.name },
            troco = v.valorRecebido?.let { it - v.total }?.takeIf { it > 0 && v.forma == FormaPagamento.DINHEIRO.name },
            pixCopiaECola = if (pix != null && v.status == StatusVenda.PENDENTE) pixDaVenda(prefs, v.id, v.total) else null,
            pixChave = pix?.chave,
            pixTitular = pix?.let { "${it.titular} - ${it.instituicao}" },
            rodape = prefs.rodapeComprovante.ifBlank { null },
        )
    }

    /** Bytes para a impressora térmica. Usa o QR nativo ou, se desligado, imagem. */
    fun bytesImpressao(prefs: Preferencias, d: DadosComprovante): ByteArray {
        val qr = if (prefs.imprimirQrPix) d else d.copy(pixCopiaECola = null)
        return EscPos.comprovante(qr, prefs.larguraPapel)
    }

    fun mensagemCobranca(context: Context, prefs: Preferencias, nome: String, valor: Long, detalhe: String?): String {
        val dados = prefs.dadosPix
        val saudacao = "Olá, $nome! Tudo bem?\n"
        if (dados == null) {
            return saudacao + "Passando para lembrar do valor em aberto de *${Dinheiro.formatar(valor)}* aqui no ${prefs.nomeExibicao}." +
                (detalhe?.let { "\n$it" } ?: "") + "\nObrigado!"
        }
        val codigo = runCatching { Pix.copiaECola(dados, valor, "FIADO") }.getOrNull()
        return saudacao + Pix.mensagemCobranca(dados, valor, codigo, prefs.nomeExibicao, detalhe)
    }
}
