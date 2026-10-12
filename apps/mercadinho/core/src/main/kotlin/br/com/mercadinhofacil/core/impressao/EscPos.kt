package br.com.mercadinhofacil.core.impressao

import br.com.mercadinhofacil.core.Texto
import java.io.ByteArrayOutputStream

/**
 * Comandos ESC/POS, o "idioma" das impressoras térmicas portáteis (58 mm e
 * 80 mm) vendidas no Brasil. O texto vai sem acento porque a maioria dessas
 * impressoras chega configurada numa tabela de caracteres que não tem "ç" ou
 * "ã" no mesmo lugar.
 */
class EscPos {
    private val saida = ByteArrayOutputStream()

    private fun bytes(vararg b: Int) = apply { b.forEach { saida.write(it) } }

    fun iniciar() = bytes(0x1B, 0x40)

    fun esquerda() = bytes(0x1B, 0x61, 0)
    fun centro() = bytes(0x1B, 0x61, 1)
    fun direita() = bytes(0x1B, 0x61, 2)

    fun negrito(ligado: Boolean) = bytes(0x1B, 0x45, if (ligado) 1 else 0)

    /** Letra com o dobro da altura e da largura. */
    fun grande(ligado: Boolean) = bytes(0x1D, 0x21, if (ligado) 0x11 else 0x00)

    fun texto(s: String) = apply { saida.write(Texto.ascii(s).toByteArray(Charsets.US_ASCII)) }

    fun linha(s: String = "") = texto(s).bytes(0x0A)

    fun avancar(linhas: Int) = bytes(0x1B, 0x64, linhas.coerceIn(0, 255))

    /** Corte parcial (as portáteis sem guilhotina simplesmente ignoram). */
    fun cortar() = bytes(0x1D, 0x56, 0x42, 0)

    /** QR Code nativo da impressora (modelo 2). Tamanho do módulo de 1 a 16. */
    fun qrCode(conteudo: String, modulo: Int = 6) = apply {
        val dados = conteudo.toByteArray(Charsets.US_ASCII)
        bytes(0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0) // modelo 2
        bytes(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, modulo.coerceIn(1, 16)) // tamanho
        bytes(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 0x31) // correção de erro M
        val tamanho = dados.size + 3
        bytes(0x1D, 0x28, 0x6B, tamanho and 0xFF, (tamanho shr 8) and 0xFF, 0x31, 0x50, 0x30)
        saida.write(dados)
        bytes(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30) // imprimir
    }

    /**
     * Imagem em preto e branco (GS v 0), para impressoras que não entendem o QR
     * nativo. [pixels] tem largura × altura posições; true = preto.
     */
    fun imagem(pixels: BooleanArray, largura: Int, altura: Int) = apply {
        val bytesPorLinha = (largura + 7) / 8
        bytes(0x1D, 0x76, 0x30, 0, bytesPorLinha and 0xFF, (bytesPorLinha shr 8) and 0xFF, altura and 0xFF, (altura shr 8) and 0xFF)
        for (y in 0 until altura) {
            for (bx in 0 until bytesPorLinha) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = bx * 8 + bit
                    if (x < largura && pixels[y * largura + x]) b = b or (0x80 shr bit)
                }
                saida.write(b)
            }
        }
    }

    fun paraBytes(): ByteArray = saida.toByteArray()

    companion object {
        /** Monta o comprovante pronto para mandar à impressora. */
        fun comprovante(d: DadosComprovante, largura: Int = 32, qrNativo: Boolean = true): ByteArray {
            val p = EscPos().iniciar()
            p.centro().negrito(true)
            if (d.loja.length <= largura / 2) p.grande(true).linha(d.loja).grande(false) else p.linha(d.loja.take(largura))
            p.negrito(false)
            val corpo = Comprovante.texto(d.copy(loja = ""), largura).lines().drop(1)
            p.esquerda()
            for (l in corpo) {
                when {
                    l.startsWith("TOTAL") -> p.negrito(true).linha(l).negrito(false)
                    else -> p.linha(l)
                }
            }
            if (qrNativo && d.pixCopiaECola != null) {
                p.centro().linha("Aponte a camera do app do banco:").qrCode(d.pixCopiaECola).linha().esquerda()
            }
            return p.avancar(4).cortar().paraBytes()
        }
    }
}
