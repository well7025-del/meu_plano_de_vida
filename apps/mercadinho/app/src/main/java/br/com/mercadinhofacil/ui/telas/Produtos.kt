package br.com.mercadinhofacil.ui.telas

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.core.Quantidade
import br.com.mercadinhofacil.core.ia.Conselheiro
import br.com.mercadinhofacil.dados.Produto
import br.com.mercadinhofacil.dados.TipoEstoque
import br.com.mercadinhofacil.servicos.Compartilhar
import br.com.mercadinhofacil.servicos.Imagens
import br.com.mercadinhofacil.ui.BotaoPrincipal
import br.com.mercadinhofacil.ui.BotaoSecundario
import br.com.mercadinhofacil.ui.Campo
import br.com.mercadinhofacil.ui.CampoDinheiro
import br.com.mercadinhofacil.ui.Carregando
import br.com.mercadinhofacil.ui.Coluna
import br.com.mercadinhofacil.ui.Laranja
import br.com.mercadinhofacil.ui.LeitorCodigo
import br.com.mercadinhofacil.ui.LocalApp
import br.com.mercadinhofacil.ui.Nav
import br.com.mercadinhofacil.ui.Secao
import br.com.mercadinhofacil.ui.SeletorUnidade
import br.com.mercadinhofacil.ui.Tela
import br.com.mercadinhofacil.ui.Vazio
import br.com.mercadinhofacil.ui.Vermelho
import br.com.mercadinhofacil.ui.aviso
import br.com.mercadinhofacil.ui.dataHora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FotoProduto(nome: String?, tamanho: Int = 56) {
    val context = LocalContext.current
    val bmp = remember(nome) { Imagens.carregarFoto(context, nome, 300)?.asImageBitmap() }
    if (bmp != null) {
        Image(bmp, null, Modifier.size(tamanho.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(
            Modifier
                .size(tamanho.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.outline) }
    }
}

@Composable
fun TelaProdutos(nav: Nav) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val produtos by remember { app.repositorio.dao.produtos() }.collectAsState(null)
    var busca by remember { mutableStateOf("") }
    var soBaixos by remember { mutableStateOf(false) }
    var lendo by remember { mutableStateOf(false) }

    Tela(
        "Produtos e estoque", { nav.voltar() },
        acoes = { IconButton(onClick = { lendo = true }) { Icon(Icons.Filled.QrCodeScanner, "Buscar pelo código", Modifier.size(28.dp)) } },
        flutuante = {
            ExtendedFloatingActionButton(onClick = { nav.ir("produto/0") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Novo produto", fontSize = 18.sp) })
        },
    ) { padding ->
        val lista = produtos
        if (lista == null) { Carregando(); return@Tela }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(12.dp)) {
                Campo(busca, { busca = it }, "Buscar", fim = { Icon(Icons.Filled.Search, null) })
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val baixos = lista.count { it.estoqueMinimo > 0 && it.estoque <= it.estoqueMinimo || it.estoque <= 0 }
                    FilterChip(soBaixos, { soBaixos = !soBaixos }, label = { Text(maxLines = 1, softWrap = false, text = "Acabando ($baixos)") }, leadingIcon = { Icon(Icons.Filled.Warning, null) })
                    Text("${lista.size} produtos", Modifier.weight(1f), fontSize = 15.sp)
                }
            }
            val filtrados = filtrarProdutos(lista, busca).filter { !soBaixos || it.estoque <= 0 || (it.estoqueMinimo > 0 && it.estoque <= it.estoqueMinimo) }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                if (filtrados.isEmpty()) item {
                    Vazio(if (lista.isEmpty()) "Nenhum produto cadastrado. Toque em \"Novo produto\"." else "Nada encontrado.", Icons.Filled.Inventory2)
                }
                items(filtrados, key = { it.id }) { p ->
                    val baixo = p.estoque <= 0 || (p.estoqueMinimo > 0 && p.estoque <= p.estoqueMinimo)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.ir("produto/${p.id}") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FotoProduto(p.foto)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nome, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Estoque: ${Quantidade.formatar(p.estoque)} ${p.unidade}",
                                color = if (baixo) Vermelho else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (baixo) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Dinheiro.formatar(p.preco), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { nav.ir("estoque/${p.id}") }) { Text("Estoque") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (lendo) {
        LeitorCodigo(aoLer = { codigo ->
            lendo = false
            escopo.launch {
                val p = app.repositorio.dao.produtoPorCodigo(codigo)
                if (p != null) nav.ir("produto/${p.id}") else {
                    aviso(context, "Código novo. Vamos cadastrar.")
                    app.prefs.sp.edit().putString("codigoNovo", codigo).apply()
                    nav.ir("produto/0")
                }
            }
        }, aoFechar = { lendo = false })
    }
}

@Composable
fun TelaProduto(nav: Nav, produtoId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var original by remember { mutableStateOf<Produto?>(null) }
    var carregado by remember { mutableStateOf(produtoId == 0L) }

    var nome by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf("un") }
    var preco by remember { mutableStateOf("") }
    var custo by remember { mutableStateOf("") }
    var estoque by remember { mutableStateOf("") }
    var minimo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var apelidos by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<String?>(null) }
    var noCatalogo by remember { mutableStateOf(true) }
    var lendo by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf(false) }
    var arquivoCamera by remember { mutableStateOf<File?>(null) }
    val categorias by remember { app.repositorio.dao.categorias() }.collectAsState(emptyList())

    LaunchedEffect(produtoId) {
        if (produtoId != 0L) {
            app.repositorio.dao.produto(produtoId)?.let { p ->
                original = p
                nome = p.nome; codigo = p.codigoBarras.orEmpty(); categoria = p.categoria; unidade = p.unidade
                preco = if (p.preco > 0) Dinheiro.formatar(p.preco, false) else ""
                custo = if (p.custo > 0) Dinheiro.formatar(p.custo, false) else ""
                estoque = Quantidade.formatar(p.estoque); minimo = if (p.estoqueMinimo > 0) Quantidade.formatar(p.estoqueMinimo) else ""
                descricao = p.descricao; apelidos = p.apelidos; foto = p.foto; noCatalogo = p.noCatalogo
            }
            carregado = true
        } else {
            app.prefs.sp.getString("codigoNovo", null)?.let { codigo = it; app.prefs.sp.edit().remove("codigoNovo").apply() }
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) escopo.launch { foto = withContext(Dispatchers.IO) { Imagens.salvarFotoProduto(context, uri) } ?: foto }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = arquivoCamera
        if (ok && f != null) escopo.launch { foto = withContext(Dispatchers.IO) { Imagens.salvarFotoProduto(context, Uri.fromFile(f)) } ?: foto }
    }
    fun tirarFoto() {
        val f = File(Compartilhar.pastaTemporaria(context), "foto-${System.currentTimeMillis()}.jpg")
        arquivoCamera = f
        camera.launch(Compartilhar.uriDe(context, f))
    }
    val permissaoCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) tirarFoto() }

    val precoC = Dinheiro.ler(preco)
    val custoC = Dinheiro.ler(custo)

    fun salvar() {
        if (nome.isBlank()) { aviso(context, "Escreva o nome do produto"); return }
        if (precoC == null) { aviso(context, "Coloque o preço de venda"); return }
        val base = original ?: Produto(nome = nome)
        val p = base.copy(
            nome = nome.trim(), codigoBarras = codigo.trim().ifBlank { null }, categoria = categoria.trim(), unidade = unidade,
            preco = precoC, custo = custoC ?: 0, estoque = Quantidade.ler(estoque) ?: 0.0, estoqueMinimo = Quantidade.ler(minimo) ?: 0.0,
            descricao = descricao.trim(), apelidos = apelidos.trim(), foto = foto, noCatalogo = noCatalogo,
        )
        escopo.launch {
            val repetido = p.codigoBarras?.let { app.repositorio.dao.produtoPorCodigo(it) }
            if (repetido != null && repetido.id != p.id) { aviso(context, "Esse código já é do produto ${repetido.nome}"); return@launch }
            app.repositorio.salvarProduto(p)
            aviso(context, "Produto salvo")
            nav.voltar()
        }
    }

    Tela(if (produtoId == 0L) "Novo produto" else "Editar produto", { nav.voltar() }) { padding ->
        if (!carregado) { Carregando(); return@Tela }
        Coluna(padding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FotoProduto(foto, 96)
                Spacer(Modifier.width(12.dp))
                Column {
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) tirarFoto()
                        else permissaoCamera.launch(Manifest.permission.CAMERA)
                    }) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Tirar foto", fontSize = 17.sp) }
                    TextButton(onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Escolher da galeria", fontSize = 17.sp)
                    }
                }
            }
            Campo(nome, { nome = it }, "Nome do produto *", dica = "ex.: Arroz Tio João 5kg")
            Campo(
                codigo, { codigo = it }, "Código de barras", teclado = KeyboardType.Number,
                fim = { IconButton(onClick = { lendo = true }) { Icon(Icons.Filled.QrCodeScanner, "Ler código") } },
            )
            Text("Vende por:", fontSize = 17.sp)
            SeletorUnidade(unidade) { unidade = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoDinheiro(preco, { preco = it }, "Preço de venda *", Modifier.weight(1f))
                CampoDinheiro(custo, { custo = it }, "Custo (quanto paga)", Modifier.weight(1f))
            }
            if (precoC != null && custoC != null && custoC > 0 && precoC > 0) {
                val lucro = precoC - custoC
                val margem = lucro * 100.0 / precoC
                Text(
                    "Lucro por ${if (unidade == "un") "unidade" else unidade}: ${Dinheiro.formatar(lucro)} (${"%.0f".format(margem)}% do preço)",
                    color = if (lucro <= 0) Vermelho else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
            }
            if (custoC != null && custoC > 0) {
                val sugestao = Conselheiro.precoSugerido(custoC, 30.0)
                TextButton(onClick = { preco = Dinheiro.formatar(sugestao, false) }) {
                    Text("Usar preço sugerido: ${Dinheiro.formatar(sugestao)} (30% de margem)", fontSize = 15.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(estoque, { estoque = it }, "Estoque atual", Modifier.weight(1f), KeyboardType.Decimal)
                Campo(minimo, { minimo = it }, "Avisar quando chegar em", Modifier.weight(1f), KeyboardType.Decimal)
            }
            Campo(categoria, { categoria = it }, "Categoria", dica = "ex.: Mercearia, Bebidas, Limpeza")
            if (categorias.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    categorias.forEach { c -> FilterChip(categoria == c, { categoria = c }, label = { Text(maxLines = 1, softWrap = false, text = c) }) }
                }
            }
            Campo(descricao, { descricao = it }, "Descrição (aparece no catálogo)", linhas = 2)
            Campo(apelidos, { apelidos = it }, "Outros nomes que o povo fala", dica = "ex.: refri, coquinha (separe por vírgula)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar no catálogo", Modifier.weight(1f), fontSize = 17.sp)
                Switch(noCatalogo, { noCatalogo = it })
            }
            BotaoPrincipal("Salvar", Icons.Filled.Save) { salvar() }
            if (original != null) {
                BotaoSecundario("Entrada, perda e histórico de estoque", Icons.Filled.Inventory2) { nav.ir("estoque/$produtoId") }
                TextButton(onClick = { excluir = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, null, tint = Vermelho); Text("Remover produto", color = Vermelho, fontSize = 17.sp)
                }
            }
        }
    }

    if (lendo) LeitorCodigo(aoLer = { codigo = it; lendo = false }, aoFechar = { lendo = false })

    if (excluir) {
        br.com.mercadinhofacil.ui.Confirmacao(
            "Remover ${original?.nome}?", "Ele some da lista, mas as vendas antigas continuam nos relatórios.", "Remover", Vermelho,
            aoConfirmar = {
                excluir = false
                escopo.launch { original?.let { app.repositorio.salvarProduto(it.copy(ativo = false)) }; nav.voltar() }
            },
            aoCancelar = { excluir = false },
        )
    }
}

@Composable
fun TelaEstoque(nav: Nav, produtoId: Long) {
    val app = LocalApp.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val produto by remember { app.repositorio.dao.observarProduto(produtoId) }.collectAsState(null)
    val historico by remember { app.repositorio.dao.movimentosEstoque(produtoId) }.collectAsState(emptyList())
    val contas by remember { app.repositorio.dao.contas() }.collectAsState(emptyList())
    var aba by remember { mutableIntStateOf(0) }
    var quantidade by remember { mutableStateOf("") }
    var custo by remember { mutableStateOf("") }
    var pago by remember { mutableStateOf(true) }
    var contaId by remember { mutableStateOf<Long?>(null) }
    var motivo by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }

    Tela(produto?.nome ?: "Estoque", { nav.voltar() }) { padding ->
        val p = produto
        if (p == null) { Carregando(); return@Tela }
        Coluna(padding) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Estoque agora", fontSize = 16.sp)
                    Text("${Quantidade.formatar(p.estoque)} ${p.unidade}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Custo médio: ${Dinheiro.formatar(p.custo)} · Vale ${Dinheiro.formatar(Dinheiro.multiplicar(p.custo, p.estoque.coerceAtLeast(0.0)))}")
                }
            }
            TabRow(aba) {
                Tab(aba == 0, { aba = 0 }, text = { Text("Chegou mercadoria", fontSize = 16.sp) })
                Tab(aba == 1, { aba = 1 }, text = { Text("Perda", fontSize = 16.sp) })
            }
            Campo(quantidade, { quantidade = it }, "Quantidade (${p.unidade})", teclado = KeyboardType.Decimal)
            if (aba == 0) {
                CampoDinheiro(custo, { custo = it }, "Custo de cada (${p.unidade}) — deixe vazio para manter ${Dinheiro.formatar(p.custo)}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paguei agora (tira do caixa)", Modifier.weight(1f), fontSize = 17.sp)
                    Switch(pago, { pago = it })
                }
                if (pago) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        contas.forEach { c -> FilterChip((contaId ?: contas.firstOrNull()?.conta?.id) == c.conta.id, { contaId = c.conta.id }, label = { Text(maxLines = 1, softWrap = false, text = c.conta.nome) }) }
                    }
                }
            } else {
                Campo(motivo, { motivo = it }, "Motivo", dica = "ex.: venceu, estragou, quebrou")
            }
            BotaoPrincipal(if (aba == 0) "Lançar entrada" else "Lançar perda", if (aba == 0) Icons.Filled.Add else Icons.Filled.RemoveCircle, cor = if (aba == 0) MaterialTheme.colorScheme.primary else Laranja) {
                val q = Quantidade.ler(quantidade)
                if (q == null || q <= 0) aviso(context, "Digite a quantidade") else confirmar = true
            }

            Secao("Histórico")
            if (historico.isEmpty()) Text("Sem movimentações ainda.")
            historico.forEach { m ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (m.tipo) {
                                TipoEstoque.ENTRADA -> "Entrada"; TipoEstoque.VENDA -> "Venda nº ${m.vendaId}"; TipoEstoque.PERDA -> "Perda"
                                TipoEstoque.CANCELAMENTO -> "Venda cancelada"; else -> "Ajuste"
                            } + if (m.observacao.isNotBlank() && m.tipo != TipoEstoque.CANCELAMENTO) " — ${m.observacao}" else "",
                            fontSize = 16.sp,
                        )
                        Text(m.data.dataHora(), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        (if (m.quantidade > 0) "+" else "") + Quantidade.formatar(m.quantidade),
                        color = if (m.quantidade > 0) MaterialTheme.colorScheme.primary else Vermelho, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    val p = produto
    if (confirmar && p != null) {
        val q = Quantidade.ler(quantidade) ?: 0.0
        val c = Dinheiro.ler(custo)
        val conta = if (aba == 0 && pago) (contaId ?: contas.firstOrNull()?.conta?.id) else null
        val valor = Dinheiro.multiplicar(c ?: p.custo, q)
        br.com.mercadinhofacil.ui.Confirmacao(
            if (aba == 0) "Confirmar entrada?" else "Confirmar perda?",
            if (aba == 0) "+${Quantidade.formatar(q)} ${p.unidade} de ${p.nome}" + if (conta != null) "\nPago: ${Dinheiro.formatar(valor)}" else ""
            else "-${Quantidade.formatar(q)} ${p.unidade} de ${p.nome}\nPrejuízo: ${Dinheiro.formatar(Dinheiro.multiplicar(p.custo, q))}",
            aoConfirmar = {
                confirmar = false
                escopo.launch {
                    try {
                        if (aba == 0) app.repositorio.entradaEstoque(p.id, q, c, conta, if (conta != null) valor else null)
                        else app.repositorio.registrarPerda(p.id, q, motivo)
                        quantidade = ""; custo = ""; motivo = ""
                        aviso(context, "Estoque atualizado")
                    } catch (e: Exception) {
                        aviso(context, e.message ?: "Erro")
                    }
                }
            },
            aoCancelar = { confirmar = false },
        )
    }
}
