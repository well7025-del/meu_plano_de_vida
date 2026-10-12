package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.ia.Conselheiro
import br.com.mercadinhofacil.core.ia.Dica
import br.com.mercadinhofacil.core.ia.ItemCompra
import br.com.mercadinhofacil.core.ia.TipoDica
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.LinhaValor
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import java.time.LocalDate

@Composable
fun TelaDicas(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    var dicas by remember { mutableStateOf<List<Dica>?>(null) }
    var compras by remember { mutableStateOf<List<ItemCompra>>(emptyList()) }
    LaunchedEffect(Unit) {
        dicas = app.assistente.dicas()
        compras = Conselheiro.listaDeCompras(app.repositorio.situacaoProdutos(LocalDate.now()))
    }
    DisposableEffect(Unit) { onDispose { app.falante.calar() } }

    Tela("Dicas para o seu negócio", { nav.voltar() }) { padding ->
        val lista = dicas
        if (lista == null) { Carregando(); return@Tela }
        Coluna(padding) {
            Text("Análise feita no seu celular com as suas vendas, estoque e contas do mês.", fontSize = 15.sp)
            lista.forEach { d ->
                val (cor, icone) = when (d.tipo) {
                    TipoDica.URGENTE -> Color(0xFFFFEBEE) to Icons.Filled.Error
                    TipoDica.ATENCAO -> Color(0xFFFFF8E1) to Icons.Filled.Warning
                    TipoDica.OPORTUNIDADE -> Color(0xFFE3F2FD) to Icons.Filled.Lightbulb
                    TipoDica.BOA_NOTICIA -> Color(0xFFE8F5E9) to Icons.Filled.CheckCircle
                }
                Card(colors = CardDefaults.cardColors(containerColor = cor, contentColor = Color(0xFF212121))) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icone, null, Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(d.titulo, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { app.falante.falar("${d.titulo}. ${d.texto}") }) { Icon(Icons.Filled.VolumeUp, "Ouvir") }
                        }
                        Text(d.texto, fontSize = 17.sp, lineHeight = 24.sp)
                    }
                }
            }

            Secao("Lista de compras (7 dias)")
            if (compras.isEmpty()) Text("Nada para comprar agora. Cadastre o estoque mínimo dos produtos para a lista ficar mais precisa.", fontSize = 16.sp)
            else {
                compras.forEach { c -> LinhaValor("${Quantidade.formatar(c.quantidade)} ${c.unidade} ${c.nome}", "≈ ${Dinheiro.formatar(c.custoEstimado)}") }
                LinhaValor("Total estimado", Dinheiro.formatar(compras.sumOf { it.custoEstimado }), destaque = true)
                BotaoSecundario("Enviar lista no WhatsApp", Icons.Filled.Share) {
                    val texto = "*Lista de compras - ${app.prefs.nomeExibicao}*\n\n" +
                        compras.joinToString("\n") { "• ${Quantidade.formatar(it.quantidade)} ${it.unidade} ${it.nome}" }
                    Compartilhar.texto(context, texto, "Enviar lista")
                }
            }
        }
    }
}
