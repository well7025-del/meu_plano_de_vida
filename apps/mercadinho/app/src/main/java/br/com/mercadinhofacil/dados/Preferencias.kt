package br.com.mercadinhofacil.dados

import android.content.Context
import android.content.SharedPreferences
import br.com.mercadinhofacil.core.pix.DadosPix
import br.com.mercadinhofacil.core.pix.TipoChave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Configurações da loja, do Pix, da impressora e do backup. */
class Preferencias(context: Context) {

    val sp: SharedPreferences = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private val _versao = MutableStateFlow(0)
    /** Muda toda vez que algo é salvo, para as telas se atualizarem. */
    val versao: StateFlow<Int> = _versao

    private fun editar(bloco: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(bloco).apply()
        _versao.value++
    }

    var lojaNome: String
        get() = sp.getString("lojaNome", "") ?: ""
        set(v) = editar { putString("lojaNome", v.trim()) }
    var lojaEndereco: String
        get() = sp.getString("lojaEndereco", "") ?: ""
        set(v) = editar { putString("lojaEndereco", v.trim()) }
    var lojaTelefone: String
        get() = sp.getString("lojaTelefone", "") ?: ""
        set(v) = editar { putString("lojaTelefone", v.trim()) }
    var rodapeComprovante: String
        get() = sp.getString("rodape", "Obrigado pela preferência!") ?: ""
        set(v) = editar { putString("rodape", v) }

    val nomeExibicao: String get() = lojaNome.ifBlank { "Meu Mercadinho" }

    var pixTitular: String
        get() = sp.getString("pixTitular", "") ?: ""
        set(v) = editar { putString("pixTitular", v.trim()) }
    var pixBanco: String
        get() = sp.getString("pixBanco", "") ?: ""
        set(v) = editar { putString("pixBanco", v.trim()) }
    var pixChave: String
        get() = sp.getString("pixChave", "") ?: ""
        set(v) = editar { putString("pixChave", v.trim()) }
    var pixTipo: TipoChave
        get() = runCatching { TipoChave.valueOf(sp.getString("pixTipo", "") ?: "") }.getOrDefault(TipoChave.TELEFONE)
        set(v) = editar { putString("pixTipo", v.name) }
    var pixCidade: String
        get() = sp.getString("pixCidade", "") ?: ""
        set(v) = editar { putString("pixCidade", v.trim()) }

    val dadosPix: DadosPix?
        get() = if (pixChave.isBlank() || pixTitular.isBlank()) null
        else DadosPix(pixTitular, pixBanco, pixChave, pixTipo, pixCidade.ifBlank { "BRASIL" })

    var impressoraEndereco: String
        get() = sp.getString("impressoraEndereco", "") ?: ""
        set(v) = editar { putString("impressoraEndereco", v) }
    var impressoraNome: String
        get() = sp.getString("impressoraNome", "") ?: ""
        set(v) = editar { putString("impressoraNome", v) }
    /** 32 colunas (58 mm) ou 48 colunas (80 mm) */
    var larguraPapel: Int
        get() = sp.getInt("larguraPapel", 32)
        set(v) = editar { putInt("larguraPapel", v) }
    var imprimirQrPix: Boolean
        get() = sp.getBoolean("imprimirQrPix", true)
        set(v) = editar { putBoolean("imprimirQrPix", v) }

    var responderFalando: Boolean
        get() = sp.getBoolean("responderFalando", true)
        set(v) = editar { putBoolean("responderFalando", v) }
    var tamanhoLetra: Float
        get() = sp.getFloat("tamanhoLetra", 1.1f)
        set(v) = editar { putFloat("tamanhoLetra", v) }
    /** Usa a janela de voz do Google em vez do microfone dentro do app (celulares onde o outro não funciona). */
    var vozPelaJanela: Boolean
        get() = sp.getBoolean("vozPelaJanela", false)
        set(v) = editar { putBoolean("vozPelaJanela", v) }
    var tutorialVisto: Boolean
        get() = sp.getBoolean("tutorialVisto", false)
        set(v) = editar { putBoolean("tutorialVisto", v) }
    var pedirConfirmacao: Boolean
        get() = sp.getBoolean("pedirConfirmacao", true)
        set(v) = editar { putBoolean("pedirConfirmacao", v) }

    var backupAutomatico: Boolean
        get() = sp.getBoolean("backupAutomatico", true)
        set(v) = editar { putBoolean("backupAutomatico", v) }
    /** Pasta escolhida pelo usuário (cartão de memória, pendrive, pasta do Drive sincronizada...) */
    var backupPasta: String
        get() = sp.getString("backupPasta", "") ?: ""
        set(v) = editar { putString("backupPasta", v) }
    var ultimoBackup: Long
        get() = sp.getLong("ultimoBackup", 0)
        set(v) = editar { putLong("ultimoBackup", v) }

    /** Tudo como texto, para ir junto no arquivo de backup. */
    fun exportar(): Map<String, Any?> = sp.all.filterKeys { it != "backupPasta" && it != "ultimoBackup" }

    fun importar(valores: Map<String, Any?>) {
        editar {
            for ((k, v) in valores) {
                when (v) {
                    is String -> putString(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Double -> putFloat(k, v.toFloat())
                }
            }
        }
    }

    companion object {
        const val ARQUIVO = "config"
    }
}
