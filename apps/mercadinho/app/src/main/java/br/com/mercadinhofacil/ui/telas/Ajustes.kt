package br.com.mercadinhofacil.ui.telas

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadinhofacil.BuildConfig
import br.com.mercadinhofacil.core.impressao.EscPos
import br.com.mercadinhofacil.servicos.Aparelho
import br.com.mercadinhofacil.servicos.Backup
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.servicos.Impressora
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Confirmacao
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.data
import br.com.mercadinhofacil.ui.dataHora
import br.com.mercadinhofacil.ui.rememberImpressao
import kotlinx.coroutines.launch
import java.io.File

@Composable
private fun Item(titulo: String, subtitulo: String, icone: ImageVector, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitulo, fontSize = 15.sp) },
        leadingContent = { Icon(icone, null) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = aoTocar),
    )
    HorizontalDivider()
}

@Composable
fun TelaAjustes(nav: Nav) {
    val app = LocalApp.current
    val prefs = app.prefs
    val versao by prefs.versao.collectAsState()
    Tela("Ajustes", { nav.voltar() }) { padding ->
        Coluna(padding) {
            Item("Dados da loja", "Nome, endereço e telefone do comprovante", Icons.Filled.Store) { nav.ir("loja") }
            Item("Impressora", prefs.impressoraNome.ifBlank { "Nenhuma escolhida" }.also { versao }, Icons.Filled.Print) { nav.ir("impressora") }
            Item("Backup e restauração", if (prefs.ultimoBackup > 0) "Último: ${prefs.ultimoBackup.dataHora()}" else "Nenhum backup ainda", Icons.Filled.Backup) { nav.ir("backup") }
            Item("Histórico de alterações", "Tudo que foi lançado, alterado ou cancelado", Icons.Filled.History) { nav.ir("historico") }
            Item("Como usar (tutorial)", "Ver de novo o passo a passo", Icons.Filled.School) { nav.ir("tutorial") }

            Secao("Facilidades")
            var falar by remember { mutableStateOf(prefs.responderFalando) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Responder falando", Modifier.weight(1f), fontSize = 18.sp)
                Switch(falar, { falar = it; prefs.responderFalando = it })
            }
            var confirmar by remember { mutableStateOf(prefs.pedirConfirmacao) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pedir confirmação ao vender", Modifier.weight(1f), fontSize = 18.sp)
                Switch(confirmar, { confirmar = it; prefs.pedirConfirmacao = it })
            }
            Text("Tamanho das letras", fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0f to "Normal", 1.1f to "Grande", 1.25f to "Maior", 1.4f to "Enorme").forEach { (v, r) ->
                    FilterChip(prefs.tamanhoLetra == v, { prefs.tamanhoLetra = v }, label = { Text(r, fontSize = 16.sp) })
                }
            }
            Secao("Sobre")
            Text("Mercadinho Fácil ${BuildConfig.VERSION_NAME}", fontSize = 16.sp)
            Text(
                "Todos os dados ficam só neste celular: não há servidor, conta ou mensalidade. Faça backup com frequência e guarde uma cópia fora do celular.",
                fontSize = 15.sp, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun TelaLoja(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val prefs = app.prefs
    var nome by remember { mutableStateOf(prefs.lojaNome) }
    var endereco by remember { mutableStateOf(prefs.lojaEndereco) }
    var telefone by remember { mutableStateOf(prefs.lojaTelefone) }
    var rodape by remember { mutableStateOf(prefs.rodapeComprovante) }
    Tela("Dados da loja", { nav.voltar() }) { padding ->
        Coluna(padding) {
            Campo(nome, { nome = it }, "Nome da loja", dica = "ex.: Mercadinho da Ana")
            Campo(endereco, { endereco = it }, "Endereço")
            Campo(telefone, { telefone = it }, "Telefone / WhatsApp", teclado = KeyboardType.Phone)
            Campo(rodape, { rodape = it }, "Mensagem no fim do comprovante")
            BotaoPrincipal("Salvar", Icons.Filled.Save) {
                prefs.lojaNome = nome; prefs.lojaEndereco = endereco; prefs.lojaTelefone = telefone; prefs.rodapeComprovante = rodape
                aviso(context, "Salvo"); nav.voltar()
            }
        }
    }
}

@Composable
fun TelaImpressora(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val prefs = app.prefs
    var aparelhos by remember { mutableStateOf<List<Aparelho>>(emptyList()) }
    var escolhido by remember { mutableStateOf(prefs.impressoraEndereco) }
    var largura by remember { mutableIntStateOf(prefs.larguraPapel) }
    var qr by remember { mutableStateOf(prefs.imprimirQrPix) }
    var atualizar by remember { mutableIntStateOf(0) }
    val imprimir = rememberImpressao {}
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { atualizar++ }

    LaunchedEffect(atualizar) {
        if (!Impressora.temPermissao(context)) permissao.launch(Impressora.permissoesNecessarias())
        else aparelhos = Impressora.pareados(context)
    }

    Tela("Impressora", { nav.voltar() }) { padding ->
        Coluna(padding) {
            Text("1. Ligue a impressora térmica e pareie ela no Bluetooth do celular (a senha costuma ser 0000 ou 1234).\n2. Volte aqui e escolha a impressora na lista.", fontSize = 16.sp)
            BotaoSecundario("Abrir Bluetooth do celular", Icons.Filled.Bluetooth) {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (!Impressora.bluetoothLigado(context)) Text("O Bluetooth está desligado.", color = Vermelho, fontSize = 17.sp)
            Secao("Aparelhos pareados")
            if (aparelhos.isEmpty()) Text("Nenhum aparelho pareado encontrado.")
            aparelhos.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            escolhido = a.endereco; prefs.impressoraEndereco = a.endereco; prefs.impressoraNome = a.nome
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(escolhido == a.endereco, { escolhido = a.endereco; prefs.impressoraEndereco = a.endereco; prefs.impressoraNome = a.nome })
                    Column {
                        Text(a.nome, fontSize = 18.sp)
                        Text(a.endereco, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = { atualizar++ }) { Text("Atualizar lista") }
            Secao("Papel")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(largura == 32, { largura = 32; prefs.larguraPapel = 32 }, label = { Text("58 mm (mais comum)", fontSize = 16.sp) })
                FilterChip(largura == 48, { largura = 48; prefs.larguraPapel = 48 }, label = { Text("80 mm", fontSize = 16.sp) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Imprimir QR Code do Pix", Modifier.weight(1f), fontSize = 17.sp)
                Switch(qr, { qr = it; prefs.imprimirQrPix = it })
            }
            Text("Se a impressora sair com símbolos estranhos no lugar do QR, desligue a opção acima.", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
            BotaoPrincipal("Imprimir teste", Icons.Filled.Print, habilitado = escolhido.isNotBlank()) {
                val p = EscPos().iniciar().centro().negrito(true).linha(prefs.nomeExibicao).negrito(false)
                    .linha("Teste de impressao").linha("-".repeat(largura)).esquerda()
                    .linha("Se voce consegue ler isto,").linha("a impressora esta funcionando.").avancar(4)
                imprimir(p.paraBytes())
            }
        }
    }
}

@Composable
fun TelaBackup(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val prefs = app.prefs
    val versao by prefs.versao.collectAsState()
    var automatico by remember { mutableStateOf(prefs.backupAutomatico) }
    var trabalhando by remember { mutableStateOf(false) }
    var restaurarDe by remember { mutableStateOf<Uri?>(null) }
    var locais by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(versao, trabalhando) { locais = Backup.backupsLocais(context) }

    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) escopo.launch {
            trabalhando = true
            try {
                context.contentResolver.openOutputStream(uri)?.use { Backup.gerar(context, it) }
                prefs.ultimoBackup = System.currentTimeMillis()
                aviso(context, "Backup salvo!")
            } catch (e: Exception) { aviso(context, "Não consegui salvar: ${e.message}") }
            trabalhando = false
        }
    }
    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.backupPasta = uri.toString()
            aviso(context, "Pasta escolhida. Os backups automáticos também vão para lá.")
        }
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restaurarDe = uri }

    Tela("Backup", { nav.voltar() }) { padding ->
        Coluna(padding) {
            Text(
                "O backup é um arquivo com tudo: produtos, vendas, clientes, contas, fotos e configurações. Guarde uma cópia fora do celular (pendrive, computador, e-mail para você mesmo), porque se o celular quebrar ou for roubado, é ela que salva seus dados.",
                fontSize = 16.sp,
            )
            if (trabalhando) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            BotaoPrincipal("Fazer backup agora", Icons.Filled.Backup) {
                escopo.launch {
                    trabalhando = true
                    try {
                        val f = Backup.automatico(context)
                        aviso(context, "Backup feito: ${f.name}")
                    } catch (e: Exception) { aviso(context, "Erro: ${e.message}") }
                    trabalhando = false
                }
            }
            BotaoSecundario("Salvar backup em outro lugar", Icons.Filled.Save) { salvarComo.launch(Backup.nomeArquivo()) }
            BotaoSecundario("Enviar o último backup (WhatsApp, e-mail…)", Icons.Filled.Share) {
                val f = locais.firstOrNull()
                if (f == null) aviso(context, "Faça um backup primeiro") else Compartilhar.arquivos(context, listOf(f), "application/zip", null, "Enviar backup")
            }

            Secao("Backup automático")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fazer backup todo dia", Modifier.weight(1f), fontSize = 18.sp)
                Switch(automatico, { automatico = it; prefs.backupAutomatico = it; Backup.agendar(context, it) })
            }
            Text("Guarda os 10 últimos no celular." + if (prefs.backupPasta.isNotBlank()) " Também copia para a pasta escolhida." else "", fontSize = 15.sp)
            BotaoSecundario(if (prefs.backupPasta.isBlank()) "Escolher pasta extra (cartão de memória, pendrive…)" else "Trocar a pasta extra", Icons.Filled.FolderOpen) { escolherPasta.launch(null) }

            Secao("Restaurar")
            Text("Substitui TODOS os dados atuais pelos do backup. Antes, o app guarda uma cópia do que existe agora.", fontSize = 15.sp, color = Vermelho)
            BotaoSecundario("Restaurar de um arquivo", Icons.Filled.Restore) { abrir.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            if (locais.isNotEmpty()) {
                Text("Backups neste celular:", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                locais.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { restaurarDe = Uri.fromFile(f) }.padding(vertical = 8.dp)) {
                        Text(f.name, Modifier.weight(1f), fontSize = 16.sp)
                        Text("${f.length() / 1024} KB · ${f.lastModified().data()}", fontSize = 14.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    restaurarDe?.let { uri ->
        Confirmacao(
            "Restaurar este backup?", "Todos os dados atuais serão trocados pelos do arquivo e o app vai reiniciar.", "Restaurar", Vermelho,
            aoConfirmar = {
                restaurarDe = null
                escopo.launch {
                    trabalhando = true
                    val erro = Backup.restaurar(context, uri)
                    trabalhando = false
                    if (erro != null) aviso(context, erro) else { aviso(context, "Dados restaurados. Reiniciando…"); Backup.reiniciarApp(context) }
                }
            },
            aoCancelar = { restaurarDe = null },
        )
    }
}

@Composable
fun TelaHistorico(nav: Nav) {
    val app = LocalApp.current
    val itens by remember { app.repositorio.dao.historico() }.collectAsState(null)
    Tela("Histórico de alterações", { nav.voltar() }) { padding ->
        val lista = itens
        if (lista == null) { br.com.mercadinhofacil.ui.Carregando(); return@Tela }
        Coluna(padding) {
            if (lista.isEmpty()) Text("Nada registrado ainda.")
            lista.forEach { h ->
                Column(Modifier.fillMaxWidth()) {
                    Text(h.acao, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(h.detalhe, fontSize = 16.sp)
                    Text(h.data.dataHora(), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
            }
        }
    }
}
