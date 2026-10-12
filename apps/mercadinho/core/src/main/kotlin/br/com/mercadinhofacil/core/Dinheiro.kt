package br.com.mercadinhofacil.core

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Todo valor em dinheiro do app é guardado em centavos (Long). Assim R$ 0,10 +
 * R$ 0,20 dá exatamente R$ 0,30, coisa que Double não garante.
 */
object Dinheiro {

    /** 123456 -> "R$ 1.234,56" */
    fun formatar(centavos: Long, comSimbolo: Boolean = true): String {
        val sinal = if (centavos < 0) "-" else ""
        val positivo = abs(centavos)
        val reais = positivo / 100
        val cent = positivo % 100
        val reaisTexto = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        val numero = "$reaisTexto,${cent.toString().padStart(2, '0')}"
        return if (comSimbolo) "${sinal}R$ $numero" else "$sinal$numero"
    }

    /** Para o leitor de voz: 1250 -> "12 reais e 50 centavos". */
    fun porExtenso(centavos: Long): String {
        val positivo = abs(centavos)
        val reais = positivo / 100
        val cent = positivo % 100
        val partes = mutableListOf<String>()
        if (reais > 0) partes += if (reais == 1L) "1 real" else "$reais reais"
        if (cent > 0) partes += if (cent == 1L) "1 centavo" else "$cent centavos"
        if (partes.isEmpty()) return "zero reais"
        val texto = partes.joinToString(" e ")
        return if (centavos < 0) "menos $texto" else texto
    }

    /**
     * Lê o que a pessoa digitou: "12", "12,5", "12,50", "1.234,56", "R$ 3,99",
     * "3.99". Devolve null se não for um valor.
     */
    fun ler(texto: String): Long? {
        var t = texto.trim().replace("R$", "", ignoreCase = true).replace(" ", "")
        if (t.isEmpty()) return null
        val negativo = t.startsWith("-")
        if (negativo) t = t.drop(1)
        if (!t.all { it.isDigit() || it == ',' || it == '.' }) return null
        val ultimaVirgula = t.lastIndexOf(',')
        val ultimoPonto = t.lastIndexOf('.')
        val separadorDecimal = when {
            ultimaVirgula >= 0 -> ultimaVirgula
            // "3.99" é decimal; "1.234" é milhar.
            ultimoPonto >= 0 && t.length - ultimoPonto - 1 in 1..2 -> ultimoPonto
            else -> -1
        }
        val inteiro: String
        val fracao: String
        if (separadorDecimal >= 0) {
            inteiro = t.substring(0, separadorDecimal).replace(".", "").replace(",", "")
            fracao = t.substring(separadorDecimal + 1).replace(".", "").replace(",", "")
        } else {
            inteiro = t.replace(".", "").replace(",", "")
            fracao = ""
        }
        if (inteiro.isEmpty() && fracao.isEmpty()) return null
        if (fracao.length > 2) return null
        val reais = if (inteiro.isEmpty()) 0L else inteiro.toLongOrNull() ?: return null
        val cent = fracao.padEnd(2, '0').ifEmpty { "00" }.toLong()
        val valor = reais * 100 + cent
        return if (negativo) -valor else valor
    }

    fun deReais(reais: Double): Long = (reais * 100).roundToLong()

    /** preço unitário × quantidade (que pode ser fracionada, ex. 0,5 kg). */
    fun multiplicar(centavos: Long, quantidade: Double): Long = (centavos * quantidade).roundToLong()

    fun porcentagem(centavos: Long, percentual: Double): Long = (centavos * percentual / 100.0).roundToLong()
}

object Quantidade {
    /** 2.0 -> "2", 0.5 -> "0,5", 1.25 -> "1,25" */
    fun formatar(q: Double): String {
        if (q == q.toLong().toDouble()) return q.toLong().toString()
        return "%.3f".format(java.util.Locale.ROOT, q).trimEnd('0').trimEnd('.').replace('.', ',')
    }

    fun ler(texto: String): Double? =
        texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
}
