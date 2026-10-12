package br.com.mercadinhofacil

import android.app.Application
import br.com.mercadinhofacil.dados.BancoDados
import br.com.mercadinhofacil.dados.Preferencias
import br.com.mercadinhofacil.dados.Repositorio
import br.com.mercadinhofacil.servicos.Backup
import br.com.mercadinhofacil.voz.Assistente
import br.com.mercadinhofacil.voz.Falante
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Guarda as peças do app. Tudo local: banco SQLite, preferências e arquivos no celular. */
class MercadinhoApp : Application() {

    lateinit var banco: BancoDados
        private set
    lateinit var repositorio: Repositorio
        private set
    lateinit var prefs: Preferencias
        private set
    val assistente by lazy { Assistente(this) }
    val falante by lazy { Falante(this) }
    val escopo = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        banco = BancoDados.abrir(this)
        repositorio = Repositorio(banco)
        prefs = Preferencias(this)
        escopo.launch { repositorio.prepararContasPadrao() }
        Backup.agendar(this, prefs.backupAutomatico)
    }
}
