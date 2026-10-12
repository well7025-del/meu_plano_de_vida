package br.com.mercadinhofacil.core.pix

import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Texto

enum class TipoChave(val rotulo: String) {
    CPF("CPF"),
    CNPJ("CNPJ"),
    TELEFONE("Celular"),
    EMAIL("E-mail"),
    ALEATORIA("Chave aleatória"),
}

data class DadosPix(
    val titular: String,
    val instituicao: String,
    val chave: String,
    val tipo: TipoChave,
    val cidade: String,
)

/**
 * Gera o "Pix Copia e Cola" (BR Code estático do Banco Central) e o texto
 * para o QR Code. Não precisa de internet nem de conta em nenhum serviço: o
 * banco de quem paga lê os dados direto do código.
 *
 * Referência: Manual de Padrões para Iniciação do Pix (BCB), seções do BR Code.
 */
object Pix {

    /** Ajusta a chave para o formato que o Banco Central exige. Devolve null se for inválida. */
    fun normalizarChave(chave: String, tipo: TipoChave): String? {
        val c = chave.trim()
        val digitos = c.filter { it.isDigit() }
        return when (tipo) {
            TipoChave.CPF -> digitos.takeIf { it.length == 11 && cpfValido(it) }
            TipoChave.CNPJ -> digitos.takeIf { it.length == 14 && cnpjValido(it) }
            TipoChave.TELEFONE -> when {
                digitos.length in 10..11 -> "+55$digitos"
                digitos.length in 12..13 && digitos.startsWith("55") -> "+$digitos"
                else -> null
            }
            TipoChave.EMAIL -> c.lowercase().takeIf { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(it) && it.length <= 77 }
            TipoChave.ALEATORIA -> c.lowercase().takeIf { Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(it) }
        }
    }

    /** Tenta adivinhar o tipo pelo que foi digitado. */
    fun detectarTipo(chave: String): TipoChave {
        val c = chave.trim()
        val digitos = c.filter { it.isDigit() }
        return when {
            c.contains('@') -> TipoChave.EMAIL
            Regex("^[0-9a-fA-F]{8}-").containsMatchIn(c) -> TipoChave.ALEATORIA
            c.startsWith("+") || c.contains('(') -> TipoChave.TELEFONE
            digitos.length == 14 -> TipoChave.CNPJ
            digitos.length == 11 && cpfValido(digitos) -> TipoChave.CPF
            digitos.length in 10..11 -> TipoChave.TELEFONE
            else -> TipoChave.ALEATORIA
        }
    }

    fun cpfValido(cpf: String): Boolean {
        if (cpf.length != 11 || cpf.all { it == cpf[0] }) return false
        val n = cpf.map { it - '0' }
        val d1 = (0 until 9).sumOf { n[it] * (10 - it) }.let { (it * 10) % 11 % 10 }
        val d2 = (0 until 10).sumOf { n[it] * (11 - it) }.let { (it * 10) % 11 % 10 }
        return d1 == n[9] && d2 == n[10]
    }

    fun cnpjValido(cnpj: String): Boolean {
        if (cnpj.length != 14 || cnpj.all { it == cnpj[0] }) return false
        val n = cnpj.map { it - '0' }
        val p1 = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val p2 = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val d1 = (0 until 12).sumOf { n[it] * p1[it] }.let { if (it % 11 < 2) 0 else 11 - it % 11 }
        val d2 = (0 until 13).sumOf { n[it] * p2[it] }.let { if (it % 11 < 2) 0 else 11 - it % 11 }
        return d1 == n[12] && d2 == n[13]
    }

    private fun campo(id: String, valor: String): String {
        require(valor.length <= 99) { "Campo $id longo demais" }
        return id + valor.length.toString().padStart(2, '0') + valor
    }

    private fun limpar(texto: String, max: Int): String =
        Texto.ascii(texto).uppercase().replace(Regex("[^A-Z0-9 .\\-]"), "").trim().take(max).trim()

    /**
     * @param valorCentavos null = quem paga digita o valor.
     * @param identificador até 25 letras/números (ex. "VENDA123"); "***" = sem identificador.
     */
    fun copiaECola(dados: DadosPix, valorCentavos: Long?, identificador: String = "***", descricao: String? = null): String {
        val chave = normalizarChave(dados.chave, dados.tipo)
            ?: throw IllegalArgumentException("Chave Pix inválida para o tipo ${dados.tipo.rotulo}")
        val txid = identificador.filter { it.isLetterOrDigit() && it.code < 128 }.take(25).ifEmpty { "***" }

        var conta = campo("00", "br.gov.bcb.pix") + campo("01", chave)
        val desc = descricao?.let { Texto.ascii(it).take(40).trim() }?.takeIf { it.isNotEmpty() }
        if (desc != null && conta.length + 4 + desc.length <= 99) conta += campo("02", desc)

        val sb = StringBuilder()
        sb.append(campo("00", "01"))
        sb.append(campo("26", conta))
        sb.append(campo("52", "0000"))
        sb.append(campo("53", "986"))
        if (valorCentavos != null && valorCentavos > 0) {
            sb.append(campo("54", "${valorCentavos / 100}.${(valorCentavos % 100).toString().padStart(2, '0')}"))
        }
        sb.append(campo("58", "BR"))
        sb.append(campo("59", limpar(dados.titular, 25).ifEmpty { "RECEBEDOR" }))
        sb.append(campo("60", limpar(dados.cidade, 15).ifEmpty { "BRASIL" }))
        sb.append(campo("62", campo("05", txid)))
        sb.append("6304")
        sb.append(crc16(sb.toString()))
        return sb.toString()
    }

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), como pede o BR Code. */
    fun crc16(texto: String): String {
        var crc = 0xFFFF
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }

    /** Mensagem pronta para mandar ao cliente pelo WhatsApp. */
    fun mensagemCobranca(dados: DadosPix, valorCentavos: Long?, copiaECola: String?, loja: String, referencia: String? = null): String = buildString {
        append("*$loja* - Pagamento por Pix\n")
        if (referencia != null) append("$referencia\n")
        if (valorCentavos != null) append("Valor: *${Dinheiro.formatar(valorCentavos)}*\n")
        append("\nChave Pix (${dados.tipo.rotulo}):\n")
        append("${dados.chave.trim()}\n")
        append("Titular: ${dados.titular}\n")
        append("Banco: ${dados.instituicao}\n")
        if (copiaECola != null) {
            append("\nPix Copia e Cola (copie o código abaixo e cole no app do seu banco, na opção Pix Copia e Cola):\n")
            append(copiaECola)
            append("\n")
        }
        append("\nDepois de pagar, envie o comprovante por aqui. Obrigado!")
    }
}
