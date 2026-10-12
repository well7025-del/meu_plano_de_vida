package br.com.mercadinhofacil.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.MercadinhoApp
import br.com.mercadinhofacil.core.voz.FormaPagamento
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalApp = staticCompositionLocalOf<MercadinhoApp> { error("App não disponível") }

fun aviso(context: Context, texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

fun Long.dataHora(): String = formatoDataHora.format(Date(this))
fun Long.data(): String = formatoData.format(Date(this))
fun Long.hora(): String = formatoHora.format(Date(this))

@Composable
fun Tela(
    titulo: String,
    voltar: (() -> Unit)?,
    acoes: @Composable () -> Unit = {},
    rodape: @Composable () -> Unit = {},
    flutuante: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (voltar != null) {
                        IconButton(onClick = voltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", Modifier.size(30.dp)) }
                    }
                },
                actions = { acoes() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = rodape,
        floatingActionButton = flutuante,
        content = conteudo,
    )
}

/** Conteúdo com rolagem e espaçamento padrão. */
@Composable
fun Coluna(padding: PaddingValues, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = conteudo,
    )
}

/** Botão quadrado grande da tela inicial. */
@Composable
fun BotaoGrande(texto: String, icone: ImageVector, cor: Color, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Card(
        onClick = aoTocar,
        modifier = modifier.heightIn(min = 112.dp),
        colors = CardDefaults.cardColors(containerColor = cor, contentColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icone, null, Modifier.size(44.dp))
            Spacer(Modifier.size(6.dp))
            Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

/** Botão de ação principal, largo e alto. */
@Composable
fun BotaoPrincipal(
    texto: String,
    icone: ImageVector? = null,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.primary,
    habilitado: Boolean = true,
    aoTocar: () -> Unit,
) {
    Button(
        onClick = aoTocar,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (icone != null) {
            Icon(icone, null, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BotaoSecundario(texto: String, icone: ImageVector? = null, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    OutlinedButton(
        onClick = aoTocar,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (icone != null) {
            Icon(icone, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Campo(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    linhas: Int = 1,
    dica: String? = null,
    prefixo: String? = null,
    erro: Boolean = false,
    fim: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(maxLines = 1, softWrap = false, text = rotulo) },
        modifier = modifier.fillMaxWidth(),
        singleLine = linhas == 1,
        minLines = linhas,
        isError = erro,
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = dica?.let { { Text(it) } },
        prefix = prefixo?.let { { Text(it) } },
        trailingIcon = fim,
        keyboardOptions = KeyboardOptions(
            keyboardType = teclado,
            capitalization = if (teclado == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
    )
}

@Composable
fun CampoDinheiro(valor: String, aoMudar: (String) -> Unit, rotulo: String, modifier: Modifier = Modifier) =
    Campo(valor, { novo -> aoMudar(novo.filter { it.isDigit() || it == ',' || it == '.' }) }, rotulo, modifier, KeyboardType.Decimal, prefixo = "R$ ")

@Composable
fun Confirmacao(titulo: String, texto: String, botao: String = "Confirmar", corBotao: Color = MaterialTheme.colorScheme.primary, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = { Text(texto, fontSize = 18.sp) },
        confirmButton = {
            Button(onClick = aoConfirmar, colors = ButtonDefaults.buttonColors(containerColor = corBotao)) { Text(botao, fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Voltar", fontSize = 18.sp) } },
    )
}

@Composable
fun SeletorForma(forma: FormaPagamento?, aoEscolher: (FormaPagamento) -> Unit, permitirFiado: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormaPagamento.entries.filter { permitirFiado || it != FormaPagamento.FIADO }.forEach { f ->
            FilterChip(
                selected = f == forma,
                onClick = { aoEscolher(f) },
                label = { Text(maxLines = 1, softWrap = false, text = f.rotulo, fontSize = 18.sp, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) },
            )
        }
    }
}

@Composable
fun Vazio(texto: String, icone: ImageVector) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icone, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.size(12.dp))
        Text(texto, textAlign = TextAlign.Center, fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun CartaoInfo(titulo: String, valor: String, modifier: Modifier = Modifier, cor: Color = MaterialTheme.colorScheme.primary, detalhe: String? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge)
            Text(valor, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cor)
            detalhe?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun LinhaValor(rotulo: String, valor: String, destaque: Boolean = false, cor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.weight(1f), fontSize = if (destaque) 20.sp else 17.sp, fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal)
        Text(valor, fontSize = if (destaque) 22.sp else 17.sp, fontWeight = if (destaque) FontWeight.Bold else FontWeight.SemiBold, color = cor)
    }
}

@Composable
fun Secao(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun Carregando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
}

val Unidades = listOf("un" to "Unidade", "kg" to "Quilo (kg)", "l" to "Litro", "pct" to "Pacote", "cx" to "Caixa")

@Composable
fun SeletorUnidade(unidade: String, aoEscolher: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Unidades.forEach { (u, rotulo) ->
            FilterChip(selected = u == unidade, onClick = { aoEscolher(u) }, label = { Text(maxLines = 1, softWrap = false, text = rotulo, fontSize = 16.sp) })
        }
    }
}
