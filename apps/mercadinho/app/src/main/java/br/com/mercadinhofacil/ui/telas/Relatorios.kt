package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.relatorio.Periodos
import br.com.mercadinhofacil.core.relatorio.Relatorio
import br.com.mercadinhofacil.core.relatorio.Resultado
import br.com.mercadinhofacil.core.voz.Periodo
import br.com.mercadinhofacil.dados.FluxoDeCaixa
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.ui.CartaoInfo
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.LinhaValor
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Verde
import br.com.mercadinhofacil.ui.Vermelho
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TelaRelatorios(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    var periodo by remember { mutableStateOf(Periodo.HOJE) }
    var resultado by remember { mutableStateOf<Resultado?>(null) }
    var fluxo by remember { mutableStateOf<FluxoDeCaixa?>(null) }

    LaunchedEffect(periodo) {
        resultado = null
        val (i, f) = Periodos.intervalo(periodo, LocalDate.now())
        fluxo = app.repositorio.fluxo(i, f)
        resultado = app.repositorio.resultado(i, f)
    }

    Tela("Relatórios", { nav.voltar() }, acoes = {
        IconButton(onClick = {
            resultado?.let { r ->
                val texto = Relatorio.texto(r, app.prefs.nomeExibicao, periodo.rotulo) + (fluxo?.let { "\n\n" + textoFluxo(it) } ?: "")
                Compartilhar.texto(context, texto, "Enviar relatório")
            }
        }) { Icon(Icons.Filled.Share, "Compartilhar") }
    }) { padding ->
        Coluna(padding) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Periodo.HOJE to "Hoje", Periodo.ONTEM to "Ontem", Periodo.SEMANA to "Semana", Periodo.MES to "Mês", Periodo.MES_PASSADO to "Mês passado").forEach { (p, rotulo) ->
                    FilterChip(periodo == p, { periodo = p }, label = { Text(maxLines = 1, softWrap = false, text = rotulo, fontSize = 17.sp) })
                }
            }
            val r = resultado
            if (r == null) { Carregando(); return@Coluna }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartaoInfo("Vendas", Dinheiro.formatar(r.faturamento), Modifier.weight(1f), detalhe = "${r.quantidadeVendas} vendas")
                CartaoInfo(
                    "Lucro estimado", Dinheiro.formatar(r.lucroEstimado), Modifier.weight(1f),
                    cor = if (r.lucroEstimado < 0) Vermelho else Verde, detalhe = "depois das despesas",
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinhaValor("Vendas (faturamento)", Dinheiro.formatar(r.faturamento))
                    if (r.descontos > 0) LinhaValor("Descontos dados", "-" + Dinheiro.formatar(r.descontos))
                    LinhaValor("Custo das mercadorias vendidas", "-" + Dinheiro.formatar(r.custoMercadorias))
                    LinhaValor("Lucro bruto (${"%.0f".format(r.margemBruta)}%)", Dinheiro.formatar(r.lucroBruto))
                    LinhaValor("Despesas", "-" + Dinheiro.formatar(r.despesas))
                    HorizontalDivider()
                    LinhaValor("Lucro estimado", Dinheiro.formatar(r.lucroEstimado), destaque = true, cor = if (r.lucroEstimado < 0) Vermelho else Verde)
                    LinhaValor("Ticket médio (por venda)", Dinheiro.formatar(r.ticketMedio))
                }
            }
            if (r.produtos.any { it.custo == 0L && it.receita > 0 }) {
                Text("Alguns produtos vendidos estão sem custo cadastrado; o lucro real é menor que o mostrado.", color = Vermelho, fontSize = 15.sp)
            }

            if (r.porForma.isNotEmpty()) {
                Secao("Por forma de pagamento")
                r.porForma.entries.sortedByDescending { it.value }.forEach { LinhaValor(it.key.rotulo, Dinheiro.formatar(it.value)) }
            }

            if (r.porDia.size > 1) {
                Secao("Vendas por dia")
                Barras(r.porDia.map { (d, v) -> d.format(DateTimeFormatter.ofPattern("dd/MM")) + " " + nomeCurto(d) to v })
            }

            fluxo?.let { f ->
                Secao("Fluxo de caixa")
                Text("Dinheiro que realmente entrou e saiu das contas (fiado só entra quando é pago).", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                LinhaValor("Vendas recebidas", Dinheiro.formatar(f.entradasVendas), cor = Verde)
                LinhaValor("Fiado recebido", Dinheiro.formatar(f.recebimentosFiado), cor = Verde)
                if (f.outrasEntradas > 0) LinhaValor("Outras entradas", Dinheiro.formatar(f.outrasEntradas), cor = Verde)
                LinhaValor("Despesas", "-" + Dinheiro.formatar(f.despesas), cor = Vermelho)
                LinhaValor("Compras de mercadoria", "-" + Dinheiro.formatar(f.compras), cor = Vermelho)
                if (f.retiradas > 0) LinhaValor("Retiradas do dono", "-" + Dinheiro.formatar(f.retiradas), cor = Vermelho)
                if (f.ajustes != 0L) LinhaValor("Ajustes de caixa", Dinheiro.formatar(f.ajustes))
                HorizontalDivider()
                LinhaValor("Saldo do período", Dinheiro.formatar(f.saldo), destaque = true, cor = if (f.saldo < 0) Vermelho else Verde)
            }

            if (r.produtos.isNotEmpty()) {
                Secao("Mais vendidos")
                r.maisVendidos.take(10).forEachIndexed { i, p ->
                    LinhaValor("${i + 1}. ${p.nome} (${Quantidade.formatar(p.quantidade)})", Dinheiro.formatar(p.receita))
                }
                Secao("Maiores margens")
                r.maioresMargens.filter { it.custo > 0 }.take(10).forEach { p ->
                    LinhaValor(p.nome, "${"%.0f".format(p.margem)}% · lucro ${Dinheiro.formatar(p.lucro)}")
                }
                val ruins = r.produtos.filter { it.custo > 0 && it.margem < 10 }
                if (ruins.isNotEmpty()) {
                    Secao("Margem baixa (menos de 10%)")
                    ruins.sortedBy { it.margem }.take(10).forEach { p -> LinhaValor(p.nome, "${"%.0f".format(p.margem)}%", cor = Vermelho) }
                }
            }
            if (r.despesasPorDescricao.isNotEmpty()) {
                Secao("Despesas")
                r.despesasPorDescricao.forEach { (d, v) -> LinhaValor(d, Dinheiro.formatar(v)) }
            }
        }
    }
}

private fun nomeCurto(d: LocalDate) = when (d.dayOfWeek.value) { 1 -> "seg"; 2 -> "ter"; 3 -> "qua"; 4 -> "qui"; 5 -> "sex"; 6 -> "sáb"; else -> "dom" }

private fun textoFluxo(f: FluxoDeCaixa) = buildString {
    appendLine("Fluxo de caixa:")
    appendLine("Entradas: ${Dinheiro.formatar(f.entradas)}")
    appendLine("Saídas: ${Dinheiro.formatar(f.saidas)}")
    append("Saldo: ${Dinheiro.formatar(f.saldo)}")
}

/** Barras horizontais com o valor escrito ao lado (lê sem depender da cor). */
@Composable
private fun Barras(dados: List<Pair<String, Long>>) {
    val maximo = dados.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        dados.forEach { (rotulo, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rotulo, Modifier.width(92.dp), fontSize = 14.sp)
                Box(Modifier.weight(1f).height(18.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth((v.toFloat() / maximo).coerceIn(0.01f, 1f))
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
                Text(Dinheiro.formatar(v), Modifier.width(110.dp).padding(start = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
