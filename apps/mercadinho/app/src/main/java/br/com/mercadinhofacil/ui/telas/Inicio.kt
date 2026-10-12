package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.dados.paraMillis
import br.com.mercadinhofacil.ui.Azul
import br.com.mercadinhofacil.ui.BotaoGrande
import br.com.mercadinhofacil.ui.Ciano
import br.com.mercadinhofacil.ui.Cinza
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Marrom
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Roxo
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Verde
import br.com.mercadinhofacil.ui.Vermelho
import java.time.LocalDate

@Composable
fun TelaInicio(nav: Nav) {
    val app = LocalApp.current
    val dao = app.repositorio.dao
    val versao by app.prefs.versao.collectAsState()
    val inicioDoDia = remember { LocalDate.now().paraMillis() }
    val vendidoHoje by remember { dao.totalVendidoDesde(inicioDoDia) }.collectAsState(0L)
    val vendasHoje by remember { dao.quantidadeVendasDesde(inicioDoDia) }.collectAsState(0)
    val pendentes by remember { dao.pixPendentes() }.collectAsState(emptyList())
    val nome = remember(versao) { app.prefs.nomeExibicao }

    Tela(
        titulo = nome,
        voltar = null,
        acoes = {
            IconButton(onClick = { nav.ir("tutorial") }) { Icon(Icons.Filled.Help, "Como usar", Modifier.size(28.dp)) }
            IconButton(onClick = { nav.ir("ajustes") }) { Icon(Icons.Filled.Settings, "Ajustes", Modifier.size(28.dp)) }
        },
    ) { padding ->
        Coluna(padding) {
            // Microfone em destaque: o jeito mais fácil de usar o app.
            Card(
                onClick = { nav.ir("voz") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Laranja, contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Toque e fale", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("\"Vendi 2 arroz no pix\", \"quanto vendi hoje?\"", fontSize = 16.sp)
                    }
                }
            }

            Card(
                onClick = { nav.ir("vendas") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vendido hoje", fontSize = 16.sp)
                        Text(Dinheiro.formatar(vendidoHoje), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("$vendasHoje ${if (vendasHoje == 1) "venda" else "vendas"}", fontSize = 18.sp)
                }
            }

            if (pendentes.isNotEmpty()) {
                Card(
                    onClick = { nav.ir("pix") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Laranja, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${pendentes.size} Pix esperando confirmação (${Dinheiro.formatar(pendentes.sumOf { it.total })}). Toque para conferir.",
                            fontSize = 17.sp, color = Color(0xFF5D4037),
                        )
                    }
                }
            }

            val botoes = listOf(
                Triple("Vender", Icons.Filled.PointOfSale, Verde) to "vender",
                Triple("Produtos e estoque", Icons.Filled.Inventory2, Azul) to "produtos",
                Triple("Clientes e fiado", Icons.Filled.People, Laranja) to "clientes",
                Triple("Caixa e contas", Icons.Filled.AccountBalanceWallet, Ciano) to "contas",
                Triple("Receber Pix", Icons.Filled.QrCode2, Color(0xFF00897B)) to "pix",
                Triple("Relatórios", Icons.Filled.BarChart, Roxo) to "relatorios",
                Triple("Catálogo", Icons.Filled.MenuBook, Marrom) to "catalogo",
                Triple("Dicas para vender mais", Icons.Filled.Lightbulb, Color(0xFFF9A825)) to "dicas",
                Triple("Vendas feitas", Icons.Filled.ReceiptLong, Vermelho) to "vendas",
                Triple("Ajustes e backup", Icons.Filled.Settings, Cinza) to "ajustes",
            )
            botoes.chunked(2).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    linha.forEach { (b, rota) ->
                        BotaoGrande(b.first, b.second, b.third, Modifier.weight(1f)) { nav.ir(rota) }
                    }
                    if (linha.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
