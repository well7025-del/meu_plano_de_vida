package br.com.mercadinhofacil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Navegacao
import br.com.mercadinhofacil.ui.TemaMercadinho

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MercadinhoApp
        setContent {
            val versao by app.prefs.versao.collectAsState()
            val escala = remember(versao) { app.prefs.tamanhoLetra }
            TemaMercadinho(escalaTexto = escala) {
                CompositionLocalProvider(LocalApp provides app) {
                    Navegacao(inicioNoTutorial = !app.prefs.tutorialVisto)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as MercadinhoApp).falante.calar()
    }
}
