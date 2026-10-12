package br.com.mercadinhofacil.core.voz

/**
 * Troca números por extenso por algarismos: "vinte e cinco" -> "25",
 * "meia duzia" -> "6", "duzentos e cinquenta" -> "250". O reconhecedor de voz
 * às vezes devolve algarismos e às vezes palavras; depois disto o
 * interpretador só lida com algarismos.
 */
object NumerosFalados {

    private val unidades = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4,
        "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10,
        "onze" to 11, "doze" to 12, "treze" to 13, "catorze" to 14, "quatorze" to 14, "quinze" to 15,
        "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19,
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50, "sessenta" to 60,
        "setenta" to 70, "oitenta" to 80, "noventa" to 90,
        "cem" to 100, "cento" to 100, "duzentos" to 200, "duzentas" to 200, "trezentos" to 300,
        "trezentas" to 300, "quatrocentos" to 400, "quatrocentas" to 400, "quinhentos" to 500,
        "quinhentas" to 500, "seiscentos" to 600, "seiscentas" to 600, "setecentos" to 700,
        "setecentas" to 700, "oitocentos" to 800, "oitocentas" to 800, "novecentos" to 900,
        "novecentas" to 900,
    )

    private fun ehNumero(p: String) = p in unidades || p == "mil"

    /** Recebe texto já normalizado (minúsculo, sem acento). */
    fun converter(texto: String): String {
        val p = texto.split(' ').filter { it.isNotEmpty() }
        val saida = mutableListOf<String>()
        var i = 0
        while (i < p.size) {
            // "meia duzia" / "uma duzia" / "duas duzias"
            if (p[i] == "meia" && p.getOrNull(i + 1)?.startsWith("duzia") == true) {
                saida += "6"; i += 2; continue
            }
            if (p[i].startsWith("duzia")) {
                val anterior = saida.lastOrNull()?.toIntOrNull()
                if (anterior != null) saida[saida.lastIndex] = (anterior * 12).toString() else saida += "12"
                i++; continue
            }
            // "meio quilo", "meio kg"
            if ((p[i] == "meio" || p[i] == "meia") && p.getOrNull(i + 1)?.let { it.startsWith("quilo") || it == "kg" || it.startsWith("litro") } == true) {
                saida += "0.5"; i++; continue
            }
            if (!ehNumero(p[i])) { saida += p[i]; i++; continue }

            var total = 0
            var grupo = 0
            var j = i
            while (j < p.size) {
                val w = p[j]
                when {
                    w == "mil" -> { total += (if (grupo == 0) 1 else grupo) * 1000; grupo = 0; j++ }
                    w in unidades -> { grupo += unidades.getValue(w); j++ }
                    // "vinte e cinco": o "e" só une se vier outro número depois.
                    w == "e" && p.getOrNull(j + 1)?.let { ehNumero(it) && it != "mil" } == true &&
                        p.getOrNull(j - 1)?.let { ehNumero(it) } == true &&
                        !continuaComoValor(p, j) -> j++
                    else -> break
                }
            }
            saida += (total + grupo).toString()
            i = j
        }
        return saida.joinToString(" ")
    }

    /**
     * "cinco e cinquenta" depois de "por" ou "reais" é preço (R$ 5,50), não 55.
     * Detecta: número pequeno (<= 9 ou dezena) + "e" + dezena, sem "mil/cento".
     */
    private fun continuaComoValor(p: List<String>, posE: Int): Boolean {
        val antes = unidades[p[posE - 1]] ?: return false
        val depois = unidades[p[posE + 1]] ?: return false
        // "vinte e cinco" (20 + 5): dezena + unidade é soma normal.
        if (antes % 10 == 0 && antes in 20..90 && depois in 1..9) return false
        // "cento e vinte" (100 + 20): soma normal.
        if (antes >= 100) return false
        // "dois e cinquenta" / "quatro e noventa": reais e centavos.
        return depois in 10..99
    }
}
