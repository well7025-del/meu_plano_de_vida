package br.com.mercadinhofacil.ui.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav

private data class Passo(val icone: ImageVector, val titulo: String, val texto: String)

private val passos = listOf(
    Passo(Icons.Filled.Storefront, "Bem-vindo!", "Este app ajuda a cuidar do seu mercadinho: vendas, estoque, fiado, Pix e dinheiro do caixa. Tudo fica guardado só no seu celular, sem mensalidade."),
    Passo(Icons.Filled.Mic, "Fale com o app", "Toque no botão laranja \"Toque e fale\" e fale do seu jeito: \"vendi 2 arroz no pix\", \"a Maria pagou 30 reais\", \"quanto vendi hoje?\". O app mostra o que entendeu e só grava depois que você confirmar."),
    Passo(Icons.Filled.Inventory2, "Cadastre os produtos", "Em \"Produtos e estoque\", coloque o nome, o preço de venda e quanto você paga (custo). Dá para ler o código de barras com a câmera e tirar foto para o catálogo."),
    Passo(Icons.Filled.PointOfSale, "Venda rápido", "Em \"Vender\", busque ou leia o código do produto, confira o total e escolha como o cliente pagou. O comprovante pode ir pelo WhatsApp ou para a impressora Bluetooth."),
    Passo(Icons.Filled.People, "Fiado sem caderninho", "Venda no fiado escolhendo o cliente. Quando ele pagar, mesmo que só uma parte, registre em \"Clientes e fiado\". Dá para mandar a cobrança com o Pix pelo WhatsApp."),
    Passo(Icons.Filled.QrCode2, "Receba por Pix", "Cadastre sua chave Pix uma vez. O app gera o QR Code com o valor certo e marca o Pix como \"esperando\" até você confirmar que caiu."),
    Passo(Icons.Filled.AccountBalanceWallet, "Dinheiro organizado", "Cada venda entra na conta certa: Dinheiro, Pix ou Cartão. Lance as despesas, transfira entre contas e feche o caixa no fim do dia."),
    Passo(Icons.Filled.Backup, "Não perca nada", "O app faz backup todo dia no celular. Uma vez por semana, mande o backup para fora do celular (Ajustes > Backup). Qualquer venda ou lançamento pode ser cancelado e tudo fica no histórico."),
)

@Composable
fun TelaTutorial(nav: Nav) {
    val app = LocalApp.current
    var pagina by remember { mutableIntStateOf(0) }
    var nomeLoja by remember { mutableStateOf(app.prefs.lojaNome) }
    val ultima = passos.size
    DisposableEffect(Unit) { onDispose { app.falante.calar() } }

    fun terminar() {
        if (nomeLoja.isNotBlank()) app.prefs.lojaNome = nomeLoja
        app.prefs.tutorialVisto = true
        nav.inicio()
    }

    BackHandler(enabled = pagina > 0) { pagina-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { terminar() }) { Text("Pular", fontSize = 18.sp) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (pagina < ultima) {
                val p = passos[pagina]
                Box(
                    Modifier
                        .size(140.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(p.icone, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.size(24.dp))
                Text(p.titulo, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.size(12.dp))
                Text(p.texto, fontSize = 20.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { app.falante.falar("${p.titulo}. ${p.texto}") }) {
                    Icon(Icons.Filled.VolumeUp, null); Text(" Ouvir", fontSize = 18.sp)
                }
            } else {
                Text("Qual o nome do seu comércio?", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Campo(nomeLoja, { nomeLoja = it }, "Nome da loja", dica = "ex.: Mercadinho da Ana")
                Spacer(Modifier.size(8.dp))
                Text("Aparece no comprovante e no catálogo. Dá para mudar depois em Ajustes.", fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            (0..ultima).forEach { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (i == pagina) 12.dp else 8.dp)
                        .background(if (i == pagina) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape),
                )
            }
        }
        BotaoPrincipal(if (pagina < ultima) "Próximo" else "Começar a usar") {
            app.falante.calar()
            if (pagina < ultima) pagina++ else terminar()
        }
    }
}
