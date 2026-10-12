package br.com.mercadinhofacil.servicos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import br.com.mercadinhofacil.core.Dinheiro
import br.com.mercadinhofacil.dados.Produto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Catálogo para o cliente ver no WhatsApp, sem instalar nada: PDF, imagens
 * ou texto. Feito todo no celular.
 */
object Catalogo {

    data class Opcoes(
        val titulo: String,
        val subtitulo: String = "",
        val rodape: String = "",
        val mostrarDescricao: Boolean = true,
    )

    private const val VERDE = 0xFF1B7F3B.toInt()

    fun texto(produtos: List<Produto>, o: Opcoes): String = buildString {
        appendLine("*${o.titulo}*")
        if (o.subtitulo.isNotBlank()) appendLine(o.subtitulo)
        appendLine()
        produtos.groupBy { it.categoria.ifBlank { "Produtos" } }.toSortedMap().forEach { (cat, lista) ->
            appendLine("*${cat.uppercase()}*")
            lista.forEach { p ->
                append("• ${p.nome} — *${Dinheiro.formatar(p.preco)}*")
                if (p.unidade == "kg") append(" o kg") else if (p.unidade == "l") append(" o litro")
                appendLine()
                if (o.mostrarDescricao && p.descricao.isNotBlank()) appendLine("   _${p.descricao}_")
            }
            appendLine()
        }
        if (o.rodape.isNotBlank()) appendLine(o.rodape)
    }.trimEnd()

    private fun textoQuebrado(c: Canvas, texto: String, paint: TextPaint, x: Float, y: Float, largura: Int, maxLinhas: Int): Float {
        val layout = StaticLayout.Builder.obtain(texto, 0, texto.length, paint, largura)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLinhas)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    /** Desenha um cartão de produto na área [r]. */
    private fun cartao(context: Context, c: Canvas, p: Produto, r: RectF, o: Opcoes, escala: Float) {
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0E0.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * escala }
        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(r, 10 * escala, 10 * escala, fundo)
        c.drawRoundRect(r, 10 * escala, 10 * escala, borda)
        val pad = 8 * escala
        val areaFoto = RectF(r.left + pad, r.top + pad, r.right - pad, r.top + r.height() * 0.55f)
        val foto = Imagens.carregarFoto(context, p.foto, 500)
        if (foto != null) {
            val proporcao = minOf(areaFoto.width() / foto.width, areaFoto.height() / foto.height)
            val w = foto.width * proporcao
            val h = foto.height * proporcao
            val destino = RectF(areaFoto.centerX() - w / 2, areaFoto.centerY() - h / 2, areaFoto.centerX() + w / 2, areaFoto.centerY() + h / 2)
            c.drawBitmap(foto, Rect(0, 0, foto.width, foto.height), destino, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val cinza = Paint().apply { color = 0xFFF2F2F2.toInt() }
            c.drawRoundRect(areaFoto, 8 * escala, 8 * escala, cinza)
            val inicial = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBDBDBD.toInt(); textSize = 40 * escala; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            c.drawText(p.nome.take(1).uppercase(), areaFoto.centerX(), areaFoto.centerY() + 14 * escala, inicial)
        }
        var y = areaFoto.bottom + 6 * escala
        val largura = (r.width() - 2 * pad).toInt()
        val nome = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF212121.toInt(); textSize = 13 * escala; typeface = Typeface.DEFAULT_BOLD }
        y += textoQuebrado(c, p.nome, nome, r.left + pad, y, largura, 2)
        if (o.mostrarDescricao && p.descricao.isNotBlank()) {
            val desc = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF757575.toInt(); textSize = 10 * escala }
            y += 2 * escala + textoQuebrado(c, p.descricao, desc, r.left + pad, y + 2 * escala, largura, 2)
        }
        val preco = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = VERDE; textSize = 17 * escala; typeface = Typeface.DEFAULT_BOLD }
        val sufixo = when (p.unidade) { "kg" -> " /kg"; "l" -> " /litro"; else -> "" }
        c.drawText(Dinheiro.formatar(p.preco) + sufixo, r.left + pad, r.bottom - pad - 2 * escala, preco)
    }

    private fun cabecalho(c: Canvas, largura: Float, o: Opcoes, escala: Float): Float {
        val faixa = Paint().apply { color = VERDE }
        val altura = (if (o.subtitulo.isNotBlank()) 70 else 52) * escala
        c.drawRect(0f, 0f, largura, altura, faixa)
        val titulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22 * escala; typeface = Typeface.DEFAULT_BOLD }
        c.drawText(o.titulo, 20 * escala, 34 * escala, titulo)
        if (o.subtitulo.isNotBlank()) {
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12 * escala }
            c.drawText(o.subtitulo, 20 * escala, 56 * escala, sub)
        }
        return altura
    }

    private fun rodape(c: Canvas, largura: Float, altura: Float, texto: String, escala: Float) {
        if (texto.isBlank()) return
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF616161.toInt(); textSize = 10 * escala; textAlign = Paint.Align.CENTER }
        c.drawText(texto, largura / 2, altura - 14 * escala, p)
    }

    /** PDF A4, 2 colunas × 3 linhas por página. */
    suspend fun pdf(context: Context, produtos: List<Produto>, o: Opcoes): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val largura = 595f
        val altura = 842f
        val colunas = 3
        val linhas = 4
        val porPagina = colunas * linhas
        val paginas = produtos.chunked(porPagina).ifEmpty { listOf(emptyList()) }
        paginas.forEachIndexed { i, lista ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(largura.toInt(), altura.toInt(), i + 1).create())
            val c = page.canvas
            c.drawColor(0xFFF7F7F7.toInt())
            val topo = cabecalho(c, largura, o, 1f) + 12
            val margem = 16f
            val espaco = 10f
            val w = (largura - 2 * margem - (colunas - 1) * espaco) / colunas
            val h = (altura - topo - 40 - (linhas - 1) * espaco) / linhas
            lista.forEachIndexed { j, p ->
                val x = margem + (j % colunas) * (w + espaco)
                val y = topo + (j / colunas) * (h + espaco)
                cartao(context, c, p, RectF(x, y, x + w, y + h), o, 1f)
            }
            rodape(c, largura, altura, o.rodape.ifBlank { "Página ${i + 1} de ${paginas.size}" }, 1f)
            doc.finishPage(page)
        }
        val f = File(Compartilhar.pastaTemporaria(context), "catalogo.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        f
    }

    /** Imagens 1080 px de largura, 6 produtos por imagem (bom para status e conversa do WhatsApp). */
    suspend fun imagens(context: Context, produtos: List<Produto>, o: Opcoes): List<File> = withContext(Dispatchers.IO) {
        val escala = 1080f / 400f
        val largura = 1080
        val colunas = 2
        val porImagem = 6
        val grupos = produtos.chunked(porImagem).ifEmpty { listOf(emptyList()) }
        grupos.mapIndexed { i, lista ->
            val linhas = (lista.size + colunas - 1) / colunas
            val topoBase = (if (o.subtitulo.isNotBlank()) 70 else 52) * escala + 12 * escala
            val hCartao = 200 * escala
            val espaco = 10 * escala
            val altura = (topoBase + linhas * (hCartao + espaco) + 36 * escala).toInt().coerceAtLeast(400)
            val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFFF7F7F7.toInt())
            val topo = cabecalho(c, largura.toFloat(), o, escala) + 12 * escala
            val margem = 14 * escala
            val w = (largura - 2 * margem - (colunas - 1) * espaco) / colunas
            lista.forEachIndexed { j, p ->
                val x = margem + (j % colunas) * (w + espaco)
                val y = topo + (j / colunas) * (hCartao + espaco)
                cartao(context, c, p, RectF(x, y, x + w, y + hCartao), o, escala)
            }
            rodape(c, largura.toFloat(), altura.toFloat(), o.rodape.ifBlank { if (grupos.size > 1) "${i + 1}/${grupos.size}" else "" }, escala)
            val f = Compartilhar.salvarImagem(context, bmp, "catalogo-${i + 1}.png")
            bmp.recycle()
            f
        }
    }
}
