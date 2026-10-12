package br.com.mercadinhofacil.core

import java.text.Normalizer

object Texto {

    private val marcas = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** "Feijão Carioca" -> "feijao carioca" */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(marcas, "")
            .lowercase()
            .replace(Regex("[^a-z0-9,.%$/\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Remove acentos e símbolos fora do ASCII (impressoras térmicas baratas). */
    fun ascii(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(marcas, "")
            .map { if (it.code in 32..126 || it == '\n') it else '?' }
            .joinToString("")
            .replace("?", "")

    fun capitalizar(texto: String): String =
        texto.split(" ").joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }

    fun distancia(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var anterior = IntArray(b.length + 1) { it }
        var atual = IntArray(b.length + 1)
        for (i in 1..a.length) {
            atual[0] = i
            for (j in 1..b.length) {
                val custo = if (a[i - 1] == b[j - 1]) 0 else 1
                atual[j] = minOf(atual[j - 1] + 1, anterior[j] + 1, anterior[j - 1] + custo)
            }
            val t = anterior; anterior = atual; atual = t
        }
        return anterior[b.length]
    }

    private fun singular(p: String): String = when {
        p.length > 3 && p.endsWith("oes") -> p.dropLast(3) + "ao"
        p.length > 3 && p.endsWith("aes") -> p.dropLast(3) + "ao"
        p.length > 4 && p.endsWith("res") -> p.dropLast(2)
        p.length > 3 && p.endsWith("s") -> p.dropLast(1)
        else -> p
    }

    private val palavrasVazias = setOf("de", "da", "do", "das", "dos", "o", "a", "os", "as", "um", "uma", "com")

    private fun tokens(t: String): List<String> =
        normalizar(t).split(' ', '-', '/').filter { it.isNotBlank() && it !in palavrasVazias }.map(::singular)

    private fun parecida(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 3 && b.startsWith(a)) return true
        if (b.length >= 3 && a.startsWith(b)) return true
        val limite = when {
            minOf(a.length, b.length) <= 3 -> 0
            minOf(a.length, b.length) <= 6 -> 1
            else -> 2
        }
        return distancia(a, b) <= limite
    }

    /**
     * Quanto o que a pessoa falou ("coca") parece com um nome cadastrado
     * ("Coca-Cola 2L"). 0 = nada a ver, 1 = igual. Pensado para o reconhecedor
     * de voz, que erra acento, plural e às vezes uma letra.
     */
    fun semelhanca(falado: String, cadastrado: String): Double {
        val f = tokens(falado)
        val c = tokens(cadastrado)
        if (f.isEmpty() || c.isEmpty()) return 0.0
        if (f == c) return 1.0
        val achados = f.count { pf -> c.any { pc -> parecida(pf, pc) } }
        if (achados == 0) return 0.0
        val cobreFalado = achados.toDouble() / f.size
        val cobreCadastro = c.count { pc -> f.any { pf -> parecida(pf, pc) } }.toDouble() / c.size
        // O que foi falado pesa mais: "arroz" deve achar "Arroz Tio João 5kg".
        val primeiraBate = if (parecida(f.first(), c.first())) 0.1 else 0.0
        return (0.65 * cobreFalado + 0.25 * cobreCadastro + primeiraBate).coerceAtMost(1.0)
    }

    /** Escolhe o melhor candidato. Devolve null se nenhum passar do mínimo. */
    fun <T> melhor(falado: String, candidatos: List<T>, minimo: Double = 0.6, nomes: (T) -> List<String>): T? =
        candidatos
            .map { c -> c to nomes(c).maxOfOrNull { semelhanca(falado, it) }.orZero() }
            .filter { it.second >= minimo }
            .maxByOrNull { it.second }
            ?.first

    private fun Double?.orZero() = this ?: 0.0
}
