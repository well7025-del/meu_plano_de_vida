package br.com.mercadinhofacil.core.voz

import br.com.mercadinhofacil.core.Texto

/**
 * Entende frases do dia a dia do balcão e transforma em um [Comando].
 *
 * Funciona 100% no aparelho, sem internet: é um conjunto de regras pensado para
 * o jeito que o comerciante fala ("vendi dois arroz e uma coca no pix",
 * "a Maria pagou trinta reais", "quanto vendi hoje?"). Nada é gravado aqui: o
 * app mostra o que entendeu e pede confirmação antes de lançar.
 */
object Interpretador {

    private const val VALOR = "valor"

    fun interpretar(frase: String): Comando {
        val pergunta = frase.contains('?')
        val base = NumerosFalados.converter(Texto.normalizar(frase).replace("?", ""))
        if (base.isBlank()) return Comando.NaoEntendi(frase)
        val t = marcarValores(" $base ").trim()

        return when {
            tem(t, "ajuda", "o que voce faz", "o que voce sabe", "como funciona", "como usar") -> Comando.Ajuda
            tem(t, "cancela", "cancelar", "desfaz", "desfazer", "apaga", "apagar") && tem(t, "venda") ->
                Comando.CancelarUltimaVenda
            tem(t, "fecha o caixa", "fechar o caixa", "fecha caixa", "fechar caixa", "fechamento") -> Comando.FecharCaixa
            pergunta || ehPergunta(t) -> consulta(t)
            tem(t, "transferi", "transfere", "transferir", "transferencia", "passei", "passa ", "passar ", "move ", "mover", "tira ", "tirei") &&
                Regex("\\bd[oa]s? .+ (para|pra|pro|pro) ").containsMatchIn(t) -> transferencia(t) ?: Comando.NaoEntendi(frase)
            Regex("\\b(pagou|acertou|abateu|quitou|me pagou|pagaram)\\b").containsMatchIn(t) ||
                Regex("\\brecebi (d[oa]|do seu|da dona|da senhora|do senhor) ").containsMatchIn(t) ->
                recebimento(t) ?: Comando.NaoEntendi(frase)
            tem(t, "perdi", "perdeu", "estragou", "estragaram", "estragado", "venceu", "venceram", "vencido", "quebrou", "quebraram", "perda", "joguei fora", "jogar fora", "furou", "mofou") ->
                perda(t) ?: Comando.NaoEntendi(frase)
            tem(t, "comprei", "compramos", "chegou", "chegaram", "entrou", "entraram", "entrada de", "repus", "reposicao", "abasteci", "recebi mercadoria") ->
                compra(t) ?: Comando.NaoEntendi(frase)
            tem(t, "gastei", "gastamos", "paguei", "pagamos", "despesa", "conta de luz", "conta de agua", "pagamento de", "pagar a conta", "gasto com", "gasto de") ->
                despesa(t) ?: Comando.NaoEntendi(frase)
            else -> venda(t) ?: Comando.NaoEntendi(frase)
        }
    }

    // ---------------------------------------------------------------- valores

    private val reRs = Regex("r ?\\$ ?(\\d+)(?:[.,](\\d{1,2}))?(?: e (\\d{1,2}) centavos?)?")
    private val reReais = Regex("\\b(\\d+)(?:[.,](\\d{1,2}))? ?(?:reais|real|conto|contos|pila|pilas|pau|paus)(?: e (\\d{1,2})(?: centavos?)?)?")
    private val reCentavos = Regex("\\b(\\d{1,2}) centavos?\\b")
    private val rePrecoContexto = Regex("\\b(por|a|custa|custando|custou|sai a|sai por|de) (\\d+) e (\\d{2})\\b")

    /** Troca "R$ 4,50", "4 reais e 50", "por 4 e 50" por "valor450". */
    internal fun marcarValores(s: String): String {
        var t = s
        t = reRs.replace(t) { m -> " $VALOR${centavos(m.groupValues[1], m.groupValues[2], m.groupValues[3])} " }
        t = reReais.replace(t) { m -> " $VALOR${centavos(m.groupValues[1], m.groupValues[2], m.groupValues[3])} " }
        t = reCentavos.replace(t) { m -> " $VALOR${m.groupValues[1].toLong()} " }
        t = rePrecoContexto.replace(t) { m -> "${m.groupValues[1]} $VALOR${centavos(m.groupValues[2], m.groupValues[3], "")} " }
        return t.replace(Regex("\\s+"), " ")
    }

    private fun centavos(reais: String, decimal: String, centavosFalados: String): Long {
        val r = reais.toLong() * 100
        val d = when {
            decimal.isNotEmpty() -> decimal.padEnd(2, '0').toLong()
            centavosFalados.isNotEmpty() -> centavosFalados.toLong()
            else -> 0
        }
        return r + d
    }

    private val reValor = Regex("\\b$VALOR(\\d+)\\b")

    private fun valores(t: String): List<Long> = reValor.findAll(t).map { it.groupValues[1].toLong() }.toList()

    /** Primeiro valor em dinheiro; se não houver, o primeiro número solto ("gastei 50 de luz"). */
    private fun valorOuNumero(t: String): Long? =
        valores(t).firstOrNull()
            ?: Regex("\\b(\\d+)(?:[.,](\\d{1,2}))?\\b").find(t)?.let { centavos(it.groupValues[1], it.groupValues[2], "") }

    // ---------------------------------------------------------------- utilidades

    private fun tem(t: String, vararg chaves: String): Boolean {
        val s = " $t "
        return chaves.any { c -> if (c.endsWith(" ")) s.contains(" $c") else Regex("\\b${Regex.escape(c)}").containsMatchIn(s) }
    }

    private val palavrasDePergunta = listOf(
        "quanto", "quantos", "quantas", "qual", "quais", "quem", "como", "o que", "mostra", "mostrar", "me fala",
        "me diz", "me da uma dica", "da uma dica", "dica", "dicas", "sugestao", "sugestoes", "conselho", "analisa",
        "analise", "relatorio", "resumo", "tem ", "ainda tem", "sera que", "consulta", "consultar", "ver o", "ver a",
    )

    private fun ehPergunta(t: String): Boolean = palavrasDePergunta.any { t.startsWith(it) } ||
        tem(t, "dica", "sugest", "conselho", "melhorar as vendas", "vender mais")

    private val paradas = setOf(
        "no", "na", "nos", "nas", "pelo", "pela", "em", "de", "do", "da", "com", "fiado", "pix", "cartao", "dinheiro",
        "credito", "debito", "hoje", "agora", "e", "mais", "que", "ai", "reais", "real", "tudo", "o", "a", "ja",
        "deve", "devendo", "pagou", "acertou", "abateu", "quitou", "me", "por", "para", "pra", "pro", "ta", "esta",
        "tem", "conta", "cada", "desconto", "inteiro", "toda", "total", "restante", "resto",
    )

    private val tratamentos = setOf("o", "a", "seu", "sr", "senhor", "dona", "dna", "senhora", "tia", "tio", "vizinho", "vizinha")

    /** Pega um nome de pessoa começando em [inicio]: até 3 palavras, para em número ou palavra comum. */
    private fun nomeEm(palavras: List<String>, inicio: Int): String? {
        var i = inicio
        while (i < palavras.size && palavras[i] in tratamentos) i++
        val nome = mutableListOf<String>()
        while (i < palavras.size && nome.size < 3) {
            val p = palavras[i]
            if (p in paradas || p.first().isDigit() || p.startsWith(VALOR)) break
            nome += p
            i++
        }
        return nome.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun forma(t: String): FormaPagamento? = when {
        tem(t, "fiado", "pendura", "pendurar", "pendurei", "caderneta", "caderno", "na conta d", "anota pr", "anota para", "anotar pr", "marca pr", "marca na conta", "vale") -> FormaPagamento.FIADO
        tem(t, "pix") -> FormaPagamento.PIX
        tem(t, "cartao", "credito", "debito", "maquininha", "maquina") -> FormaPagamento.CARTAO
        tem(t, "dinheiro", "especie", "a vista", "avista", "cash", "nota") -> FormaPagamento.DINHEIRO
        else -> null
    }

    private fun conta(t: String): String? = when {
        tem(t, "pix", "banco", "conta digital") -> "pix"
        tem(t, "cartao", "maquininha") -> "cartao"
        tem(t, "dinheiro", "caixa", "gaveta", "especie") -> "dinheiro"
        else -> null
    }

    private fun periodo(t: String): Periodo = when {
        tem(t, "ontem") -> Periodo.ONTEM
        tem(t, "mes passado", "ultimo mes") -> Periodo.MES_PASSADO
        tem(t, "semana", "7 dias", "sete dias") -> Periodo.SEMANA
        tem(t, "mes", "30 dias", "trinta dias") -> Periodo.MES
        else -> Periodo.HOJE
    }

    // ---------------------------------------------------------------- itens

    private val unidadesPeso = mapOf(
        "kg" to 1.0, "quilo" to 1.0, "quilos" to 1.0, "kilo" to 1.0, "kilos" to 1.0,
        "g" to 0.001, "grama" to 0.001, "gramas" to 0.001, "gr" to 0.001,
        "litro" to 1.0, "litros" to 1.0, "l" to 1.0, "ml" to 0.001,
    )

    private val embalagens = setOf(
        "un", "und", "unidade", "unidades", "pacote", "pacotes", "pct", "caixa", "caixas", "cx", "lata", "latas",
        "garrafa", "garrafas", "fardo", "fardos", "saco", "sacos", "sacola", "pote", "potes", "vidro", "vidros",
        "bandeja", "bandejas", "peca", "pecas", "rolo", "rolos", "copo", "copos", "dose", "doses", "maco", "macos",
    )

    /** Palavras que podem sobrar depois de tirar o verbo e o pagamento. */
    private val enchimento = setOf(
        "eu", "agora", "ai", "hoje", "por", "favor", "ja", "aqui", "la", "entao", "tambem", "so", "mais", "ne", "ok",
        "vendi", "vende", "vender", "venda", "vendeu", "vendemos", "saiu", "sairam", "registra", "registrar", "registre",
        "lanca", "lancar", "lance", "marca", "marcar", "anota", "anotar", "coloca", "colocar", "bota", "botar", "passa",
        "passei", "comprei", "compramos", "compra", "chegou", "chegaram", "entrou", "entraram", "entrada", "repus",
        "reposicao", "abasteci", "recebi", "mercadoria", "perdi", "perdeu", "perda", "estragou", "estragaram",
        "estragado", "estragados", "venceu", "venceram", "vencido", "vencidos", "quebrou", "quebraram", "joguei",
        "jogar", "fora", "furou", "mofou", "uma", "um", "pra", "para", "pro", "o", "a", "os", "as", "foi", "foram",
    )

    private fun limparEnchimento(s: String): String {
        val palavras = s.trim().split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (palavras.isNotEmpty() && palavras.first() in enchimento) palavras.removeAt(0)
        while (palavras.isNotEmpty() && palavras.last() in enchimento + setOf("de", "do", "da", "e", "no", "na", "com")) palavras.removeAt(palavras.lastIndex)
        return palavras.joinToString(" ")
    }

    private val reItem = Regex("^(\\d+(?:[.,]\\d+)?)? ?(.*)$")
    private val rePrecoItem = Regex("\\s*(?:\\b(?:a|por|de|custando|custa|sai a|sai por)\\s+)?$VALOR(\\d+)(?:\\s+(cada|o|a|unidade|cada um|cada uma|a unidade|o quilo|o kg|o litro))?\\s*$")

    internal fun itens(texto: String): List<ItemFalado> {
        val pedacos = texto
            .replace(Regex(",\\s"), " e ")
            .split(Regex("\\s(?:e|mais|tambem)\\s"))
            .map { limparEnchimento(it) }
            .filter { it.isNotBlank() }
        val itens = mutableListOf<ItemFalado>()
        for (p in pedacos) {
            var s = p
            var unit: Long? = null
            var total: Long? = null
            rePrecoItem.find(s)?.let { m ->
                val v = m.groupValues[1].toLong()
                if (m.groupValues[2].isNotEmpty()) unit = v else total = v
                s = s.substring(0, m.range.first).trim()
            }
            val m = reItem.find(s.trim()) ?: continue
            var qtd = m.groupValues[1].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
            val resto = m.groupValues[2].trim().split(' ').filter { it.isNotEmpty() }.toMutableList()
            if (resto.isNotEmpty() && resto.first() in unidadesPeso) {
                val fator = unidadesPeso.getValue(resto.removeAt(0))
                qtd = (qtd ?: 1.0) * fator
            } else if (resto.isNotEmpty() && resto.first() in embalagens) {
                resto.removeAt(0)
            }
            while (resto.isNotEmpty() && resto.first() in setOf("de", "do", "da", "dos", "das")) resto.removeAt(0)
            val nome = limparEnchimento(resto.joinToString(" "))
            if (nome.isBlank() || nome.all { it.isDigit() }) {
                // "e mais 5 reais" sozinho não é item.
                continue
            }
            val q = qtd ?: 1.0
            if (total != null && q == 1.0) { unit = total; total = null }
            itens += ItemFalado(q, nome, unit, total)
        }
        return itens
    }

    // ---------------------------------------------------------------- intenções

    private fun venda(t: String): Comando? {
        val forma = forma(t)
        var s = " $t "
        var cliente: String? = null
        var descontoValor: Long? = null
        var descontoPct: Double? = null

        Regex("\\bcom desconto(?: de)? (?:$VALOR(\\d+)|(\\d+(?:[.,]\\d+)?) ?(?:%|por cento))").find(s)?.let { m ->
            if (m.groupValues[1].isNotEmpty()) descontoValor = m.groupValues[1].toLong()
            else descontoPct = m.groupValues[2].replace(',', '.').toDoubleOrNull()
            s = s.removeRange(m.range)
        }
        Regex("\\bdesconto(?: de)? (?:$VALOR(\\d+)|(\\d+(?:[.,]\\d+)?) ?(?:%|por cento))").find(s)?.let { m ->
            if (m.groupValues[1].isNotEmpty()) descontoValor = m.groupValues[1].toLong()
            else descontoPct = m.groupValues[2].replace(',', '.').toDoubleOrNull()
            s = s.removeRange(m.range)
        }

        if (forma == FormaPagamento.FIADO) {
            val marcador = Regex("\\b(?:fiado|pendura|pendurar|pendurei|anota|anotar|marca|caderneta|na conta|no nome|vale)(?: (?:para|pra|pro|pro|pa|d[oa]s?|no nome d[oa]|na conta d[oa]|no caderno d[oa]))? (.+)$")
            val m = marcador.find(s.trim())
            if (m != null) {
                val palavras = m.groupValues[1].split(' ')
                val inicio = if (palavras.firstOrNull() in setOf("para", "pra", "pro", "pa", "do", "da", "dos", "das")) 1 else 0
                cliente = nomeEm(palavras, inicio)
            }
            if (cliente == null) {
                Regex("\\b(?:para|pra|pro|d[oa]) (.+)$").find(s.trim())?.let { mm -> cliente = nomeEm(mm.groupValues[1].split(' '), 0) }
            }
            if (cliente != null) s = s.replace(Regex("\\b(?:para|pra|pro|pa|d[oa]s?|no nome d[oa]|na conta d[oa]|no caderno d[oa])? ?(?:o |a |seu |dona )?${Regex.escape(cliente!!)}\\b"), " ")
        }

        s = s.replace(
            Regex("\\b(no|na|pelo|pela|em|com|via|de)? ?(pix|cartao de credito|cartao de debito|cartao|credito|debito|maquininha|maquina|dinheiro|especie|a vista|avista)\\b"),
            " ",
        )
        s = s.replace(Regex("\\b(fiado|pendura|pendurar|pendurei|na caderneta|caderneta|no caderno|na conta|no nome|vale)\\b"), " ")
        val itens = itens(s.replace(Regex("\\s+"), " ").trim())
        if (itens.isEmpty()) return null
        return Comando.Venda(itens, forma, cliente, descontoValor, descontoPct)
    }

    private fun compra(t: String): Comando? {
        var s = " $t "
        val c = if (tem(t, "paguei no", "paguei com", "pelo pix", "no pix", "do pix", "no cartao", "com cartao", "do caixa", "em dinheiro", "no dinheiro")) conta(t) else null
        var fornecedor: String? = null
        Regex("\\b(?:do fornecedor|da distribuidora|do atacado|do atacadao|da fabrica|do representante) ?([a-z ]+?)?(?=$| $VALOR|\\d| por| paguei| no | com )").find(s)?.let { m ->
            fornecedor = m.value.trim()
            s = s.removeRange(m.range)
        }
        s = s.replace(Regex("\\b(paguei|pagamos|pago)? ?(no|na|pelo|pela|em|com|do|da)? ?(pix|cartao|dinheiro|caixa|especie|maquininha)\\b"), " ")
        var total: Long? = null
        // "comprei 10 arroz e 5 feijao, total 80 reais" / "... tudo por 80"
        Regex("\\b(?:total(?: de)?|tudo(?: por)?|deu|no total|ao todo|paguei|gastei)\\s+(?:de |por )?$VALOR(\\d+)").find(s)?.let { m ->
            total = m.groupValues[1].toLong()
            s = s.removeRange(m.range)
        }
        val itens = itens(s.replace(Regex("\\s+"), " ").trim())
        if (itens.isEmpty()) return null
        if (total == null && itens.all { it.precoTotal != null || it.precoUnitario != null }) {
            total = itens.sumOf { it.precoTotal ?: Math.round((it.precoUnitario ?: 0) * it.quantidade) }
        }
        return Comando.Compra(itens, total, c, fornecedor)
    }

    private fun despesa(t: String): Comando? {
        val valor = valorOuNumero(t) ?: return null
        val c = conta(t)
        var s = " $t "
        s = s.replace(reValor, " ")
        s = s.replace(Regex("\\b(\\d+)(?:[.,]\\d{1,2})?\\b"), " ")
        s = s.replace(Regex("\\b(no|na|pelo|pela|em|com|do|da)? ?(pix|cartao|dinheiro|caixa|especie|maquininha|gaveta)\\b"), " ")
        s = s.replace(Regex("\\b(gastei|gastamos|paguei|pagamos|despesa|pagamento|lanca|lancar|registra|anota|uma|um|eu|hoje|agora|reais|real)\\b"), " ")
        var descricao = s.replace(Regex("\\s+"), " ").trim()
        while (true) {
            val antes = descricao
            descricao = descricao.removePrefix("de ").removePrefix("com ").removePrefix("a ").removePrefix("o ")
                .removePrefix("na ").removePrefix("no ").removePrefix("da ").removePrefix("do ").removePrefix("pra ").removePrefix("para ")
            descricao = descricao.removeSuffix(" de").removeSuffix(" com").removeSuffix(" e")
            if (descricao == antes) break
        }
        return Comando.Despesa(valor, Texto.capitalizar(descricao.ifBlank { "despesa" }), c)
    }

    private fun recebimento(t: String): Comando? {
        val palavras = t.split(' ')
        var cliente: String? = null
        Regex("\\brecebi (?:d[oa]|do seu|da dona|da senhora|do senhor) ").find(t)?.let { m ->
            cliente = nomeEm(t.substring(m.range.last + 1).split(' '), 0)
        }
        if (cliente == null) {
            val i = palavras.indexOfFirst { it in setOf("pagou", "acertou", "abateu", "quitou", "pagaram") }
            if (i > 0) {
                val inicioNome = palavras.subList(0, i).let { antes ->
                    // "a maria me pagou" -> remove "me"
                    antes.filter { it != "me" && it != "ja" && it != "hoje" && it != "agora" }
                }
                cliente = nomeEm(inicioNome, 0)
                if (cliente == null) cliente = inicioNome.filter { it !in tratamentos }.takeLast(2).joinToString(" ").ifBlank { null }
            }
        }
        if (cliente == null) return null
        val tudo = tem(t, "tudo", "quitou", "a divida toda", "o total", "o que devia", "tudo que devia", "inteiro")
        val valor = if (tudo && valores(t).isEmpty()) null else valorOuNumero(t)
        val f = forma(t)?.takeIf { it != FormaPagamento.FIADO }
        return Comando.RecebimentoFiado(cliente!!, valor, f)
    }

    private fun transferencia(t: String): Comando? {
        val valor = valorOuNumero(t) ?: return null
        val m = Regex("\\bd[oa]s? (?:conta d[oa] )?([a-z]+(?: [a-z]+)?) (?:para|pra|pro) (?:o |a |conta d[oa] )?([a-z]+(?: [a-z]+)?)").find(t)
            ?: return null
        val origem = m.groupValues[1].split(' ').first()
        val destino = m.groupValues[2].split(' ').first()
        return Comando.Transferencia(valor, origem, destino)
    }

    private fun perda(t: String): Comando? {
        var motivo: String? = null
        when {
            tem(t, "venceu", "venceram", "vencido") -> motivo = "Vencido"
            tem(t, "estrag", "mofou") -> motivo = "Estragou"
            tem(t, "quebr", "furou") -> motivo = "Quebrou"
        }
        val s = t.replace(Regex("\\b(porque|pois|que)\\b.*$"), " ")
        val itens = itens(s)
        if (itens.isEmpty()) return null
        return Comando.Perda(itens, motivo)
    }

    private fun alvoDepois(t: String, vararg ancoras: String): String? {
        for (a in ancoras) {
            val m = Regex("\\b${Regex.escape(a)} (?:o |a |os |as |de |do |da |dos |das )*(.+)$").find(t) ?: continue
            val alvo = m.groupValues[1]
                .replace(Regex("\\b(hoje|agora|ainda|no estoque|em estoque|estoque|na loja|aqui|ai|sobrando|sobrou|restando|resta|tem|ta|esta|custando|custa|o|a|e)\\b"), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (alvo.isNotBlank()) return alvo
        }
        return null
    }

    private fun consulta(t: String): Comando {
        val p = periodo(t)
        return when {
            tem(t, "dica", "sugest", "conselho", "melhorar", "vender mais", "analisa", "analise", "como estou", "como esta o negocio", "como vai o negocio", "como ta o negocio") ->
                Comando.Consulta(TipoConsulta.SUGESTOES, p)
            tem(t, "pendente") || (tem(t, "pix") && tem(t, "falta", "esperando", "nao caiu", "caiu")) -> Comando.Consulta(TipoConsulta.PIX_PENDENTE)
            tem(t, "deve", "devendo", "divida", "dividas", "fiado", "pendurado", "caderneta") -> {
                val alvo = Regex("^(?:quanto|qual|quem|o quanto)(?: e| que)?(?: a divida| o fiado| o debito)?(?: d[oa])? (?:o |a |seu |dona )?(.+?) (?:deve|ta devendo|esta devendo|me deve)").find(t)?.groupValues?.get(1)
                    ?: alvoDepois(t, "divida do", "divida da", "fiado do", "fiado da", "conta do", "conta da", "deve o", "deve a")
                Comando.Consulta(TipoConsulta.DIVIDA, p, alvo?.takeIf { it !in setOf("me", "esta", "ta", "que", "mais") })
            }
            tem(t, "faltando", "acabando", "acabou", "preciso comprar", "lista de compra", "o que comprar", "repor", "comprar", "estoque baixo", "em falta") ->
                Comando.Consulta(TipoConsulta.FALTANDO)
            tem(t, "mais vendido", "mais vendidos", "vende mais", "saiu mais", "sai mais", "mais sai", "campeao") ->
                Comando.Consulta(TipoConsulta.MAIS_VENDIDOS, if (p == Periodo.HOJE && !tem(t, "hoje")) Periodo.MES else p)
            tem(t, "saldo", "no caixa", "na gaveta", "no pix", "no banco", "no cartao", "nas contas", "em dinheiro", "na conta") && !tem(t, "vendi", "vendas") -> {
                val alvo = when {
                    tem(t, "pix", "banco") -> "pix"
                    tem(t, "cartao") -> "cartao"
                    tem(t, "dinheiro", "gaveta", "caixa") && !tem(t, "saldo") -> "dinheiro"
                    else -> null
                }
                Comando.Consulta(TipoConsulta.SALDO, p, alvo)
            }
            tem(t, "lucro", "lucrei", "ganhei", "ganho", "sobrou de dinheiro", "rendeu") -> Comando.Consulta(TipoConsulta.LUCRO, if (tem(t, "hoje")) Periodo.HOJE else p)
            tem(t, "gastei", "gastos", "gasto", "despesa", "despesas", "paguei") -> Comando.Consulta(TipoConsulta.DESPESAS, p)
            tem(t, "preco", "custa", "valor do", "valor da", "quanto ta o", "quanto ta a", "quanto e o", "quanto e a") ->
                Comando.Consulta(TipoConsulta.PRECO, p, alvoDepois(t, "preco do", "preco da", "preco de", "preco", "custa", "valor do", "valor da", "quanto ta", "quanto e"))
            tem(t, "estoque", "sobrou", "sobrando", "resta", "restam", "ainda tem", "quantos", "quantas", "tem de") || t.startsWith("tem ") ->
                Comando.Consulta(TipoConsulta.ESTOQUE, p, alvoDepois(t, "estoque de", "estoque do", "estoque da", "quantos", "quantas", "tem de", "ainda tem", "sobrou de", "resta de", "tem"))
            tem(t, "vendi", "vendemos", "venda", "vendas", "vendeu", "faturei", "faturamento", "faturou", "entrou", "movimento", "resumo", "relatorio") ->
                Comando.Consulta(TipoConsulta.VENDAS, p)
            else -> Comando.Consulta(TipoConsulta.SUGESTOES, p)
        }
    }
}
