package br.com.mercadinhofacil.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.mercadinhofacil.ui.telas.TelaAjustes
import br.com.mercadinhofacil.ui.telas.TelaBackup
import br.com.mercadinhofacil.ui.telas.TelaCatalogo
import br.com.mercadinhofacil.ui.telas.TelaCliente
import br.com.mercadinhofacil.ui.telas.TelaClientes
import br.com.mercadinhofacil.ui.telas.TelaCobrancaPix
import br.com.mercadinhofacil.ui.telas.TelaComprovante
import br.com.mercadinhofacil.ui.telas.TelaConta
import br.com.mercadinhofacil.ui.telas.TelaContas
import br.com.mercadinhofacil.ui.telas.TelaDicas
import br.com.mercadinhofacil.ui.telas.TelaEstoque
import br.com.mercadinhofacil.ui.telas.TelaFechamento
import br.com.mercadinhofacil.ui.telas.TelaHistorico
import br.com.mercadinhofacil.ui.telas.TelaImpressora
import br.com.mercadinhofacil.ui.telas.TelaInicio
import br.com.mercadinhofacil.ui.telas.TelaLancamento
import br.com.mercadinhofacil.ui.telas.TelaLoja
import br.com.mercadinhofacil.ui.telas.TelaPix
import br.com.mercadinhofacil.ui.telas.TelaProduto
import br.com.mercadinhofacil.ui.telas.TelaProdutos
import br.com.mercadinhofacil.ui.telas.TelaRelatorios
import br.com.mercadinhofacil.ui.telas.TelaTransferencia
import br.com.mercadinhofacil.ui.telas.TelaTutorial
import br.com.mercadinhofacil.ui.telas.TelaVender
import br.com.mercadinhofacil.ui.telas.TelaVendas
import br.com.mercadinhofacil.ui.telas.TelaVoz

/** Atalhos de navegação usados pelas telas. */
class Nav(private val nav: NavHostController) {
    fun ir(rota: String) = nav.navigate(rota) { launchSingleTop = true }
    fun voltar() { if (!nav.popBackStack()) nav.navigate("inicio") }
    /** Vai para a rota tirando a tela atual da pilha (ex.: depois de vender, abre o comprovante). */
    fun trocar(rota: String) {
        val atual = nav.currentDestination?.route
        nav.navigate(rota) { if (atual != null) popUpTo(atual) { inclusive = true } }
    }
    fun inicio() = nav.navigate("inicio") { popUpTo(nav.graph.id) { inclusive = true } }
}

/** Janela que aparece quando a versão de teste chega ao limite de algum cadastro. */
@Composable
private fun JanelaLimiteTeste() {
    val app = LocalApp.current
    val context = LocalContext.current
    val mensagem by app.limiteAtingido.collectAsState()
    val texto = mensagem ?: return
    AlertDialog(
        onDismissRequest = { app.limiteAtingido.value = null },
        title = { Text("Limite da versão de teste") },
        text = { Text(texto, fontSize = 18.sp) },
        confirmButton = {
            Button(onClick = { app.limiteAtingido.value = null; abrirCompra(context) }) { Text("Comprar a versão completa", fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = { app.limiteAtingido.value = null }) { Text("Agora não", fontSize = 17.sp) } },
    )
}

@Composable
fun Navegacao(inicioNoTutorial: Boolean) {
    JanelaLimiteTeste()
    val controlador = rememberNavController()
    val nav = Nav(controlador)
    val id = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(controlador, startDestination = if (inicioNoTutorial) "tutorial" else "inicio") {
        composable("inicio") { TelaInicio(nav) }
        composable("tutorial") { TelaTutorial(nav) }
        composable("voz") { TelaVoz(nav) }
        composable("vender") { TelaVender(nav) }
        composable("vendas") { TelaVendas(nav) }
        composable("comprovante/{id}", id) { TelaComprovante(nav, it.arguments?.getLong("id") ?: 0) }
        composable("produtos") { TelaProdutos(nav) }
        composable("produto/{id}", id) { TelaProduto(nav, it.arguments?.getLong("id") ?: 0) }
        composable("estoque/{id}", id) { TelaEstoque(nav, it.arguments?.getLong("id") ?: 0) }
        composable("clientes") { TelaClientes(nav) }
        composable("cliente/{id}", id) { TelaCliente(nav, it.arguments?.getLong("id") ?: 0) }
        composable("contas") { TelaContas(nav) }
        composable("conta/{id}", id) { TelaConta(nav, it.arguments?.getLong("id") ?: 0) }
        composable("lancamento/{tipo}", listOf(navArgument("tipo") { type = NavType.StringType })) {
            TelaLancamento(nav, it.arguments?.getString("tipo") ?: "DESPESA")
        }
        composable("transferencia") { TelaTransferencia(nav) }
        composable("fechamento/{id}", id) { TelaFechamento(nav, it.arguments?.getLong("id") ?: 0) }
        composable("relatorios") { TelaRelatorios(nav) }
        composable("catalogo") { TelaCatalogo(nav) }
        composable("pix") { TelaPix(nav) }
        composable("cobranca") { TelaCobrancaPix(nav) }
        composable("dicas") { TelaDicas(nav) }
        composable("ajustes") { TelaAjustes(nav) }
        composable("loja") { TelaLoja(nav) }
        composable("impressora") { TelaImpressora(nav) }
        composable("backup") { TelaBackup(nav) }
        composable("historico") { TelaHistorico(nav) }
    }
}
