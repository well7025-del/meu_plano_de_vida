package br.com.mercadinhofacil.servicos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import br.com.mercadinhofacil.core.impressao.Comprovante
import br.com.mercadinhofacil.core.impressao.DadosComprovante
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object Imagens {

    fun qrCode(conteudo: String, tamanho: Int = 600): Bitmap {
        val matriz = QRCodeWriter().encode(
            conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        val pixels = IntArray(matriz.width * matriz.height) { i ->
            if (matriz.get(i % matriz.width, i / matriz.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matriz.width, matriz.height, Bitmap.Config.ARGB_8888)
    }

    /** QR em preto e branco para a impressora térmica (largura em pontos: 384 = 58 mm). */
    fun qrParaImpressora(conteudo: String, largura: Int = 256): Triple<BooleanArray, Int, Int> {
        val m = QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, largura, largura, mapOf(EncodeHintType.MARGIN to 1))
        return Triple(BooleanArray(m.width * m.height) { i -> m.get(i % m.width, i / m.width) }, m.width, m.height)
    }

    /** Comprovante como imagem (fica bonito no WhatsApp e não desalinha). */
    fun comprovante(d: DadosComprovante): Bitmap {
        val texto = Comprovante.texto(d, 34)
        val linhas = texto.trimEnd().lines()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 30f
            color = Color.BLACK
        }
        val alturaLinha = 40
        val largura = (paint.measureText("M") * 34).toInt() + 80
        val qr = d.pixCopiaECola?.let { qrCode(it, 420) }
        val altura = 60 + linhas.size * alturaLinha + (qr?.let { it.height + 90 } ?: 0) + 40
        val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = 70f
        for ((i, l) in linhas.withIndex()) {
            paint.isFakeBoldText = i == 0 || l.startsWith("TOTAL")
            c.drawText(l, 40f, y, paint)
            y += alturaLinha
        }
        if (qr != null) {
            paint.isFakeBoldText = true
            val legenda = "Pague com o QR Code Pix"
            c.drawText(legenda, (largura - paint.measureText(legenda)) / 2, y + 30, paint)
            c.drawBitmap(qr, ((largura - qr.width) / 2).toFloat(), y + 50, null)
        }
        return bmp
    }

    /** Copia a foto escolhida para dentro do app, reduzida (economiza espaço e o backup fica leve). */
    fun salvarFotoProduto(context: Context, origem: Uri, maximo: Int = 800): String? = runCatching {
        val pasta = File(context.filesDir, "fotos").apply { mkdirs() }
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(origem)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
        var amostra = 1
        while (opcoes.outWidth / (amostra * 2) >= maximo && opcoes.outHeight / (amostra * 2) >= maximo) amostra *= 2
        val bmp = context.contentResolver.openInputStream(origem)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return null
        val rotacao = context.contentResolver.openInputStream(origem)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val escala = minOf(1f, maximo.toFloat() / maxOf(bmp.width, bmp.height))
        val matriz = Matrix().apply { postScale(escala, escala); postRotate(rotacao) }
        val final = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matriz, true)
        val nome = "p_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(pasta, nome)).use { final.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        nome
    }.getOrNull()

    fun arquivoFoto(context: Context, nome: String?): File? =
        nome?.let { File(File(context.filesDir, "fotos"), it) }?.takeIf { it.exists() }

    fun carregarFoto(context: Context, nome: String?, maximo: Int = 400): Bitmap? {
        val f = arquivoFoto(context, nome) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var amostra = 1
        while (o.outWidth / (amostra * 2) >= maximo) amostra *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = amostra })
    }
}
