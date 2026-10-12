package br.com.mercadinhofacil.servicos

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Envio de texto, imagem e PDF pelo WhatsApp (ou outro app, se não tiver WhatsApp). */
object Compartilhar {

    private val pacotesWhatsApp = listOf("com.whatsapp", "com.whatsapp.w4b")

    private fun whatsapp(context: Context): String? = pacotesWhatsApp.firstOrNull { pacote ->
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pacote, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun temWhatsApp(context: Context) = whatsapp(context) != null

    private fun abrir(context: Context, intent: Intent, titulo: String) {
        val pacote = whatsapp(context)
        if (pacote != null) {
            try {
                context.startActivity(Intent(intent).setPackage(pacote).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        context.startActivity(Intent.createChooser(intent, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun texto(context: Context, texto: String, titulo: String = "Enviar") {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        abrir(context, i, titulo)
    }

    /** Abre a conversa com um número específico já com a mensagem escrita. */
    fun textoPara(context: Context, telefone: String, texto: String) {
        var numero = telefone.filter { it.isDigit() }
        if (numero.length in 10..11) numero = "55$numero"
        if (numero.length < 12) return texto(context, texto)
        val uri = Uri.parse("https://wa.me/$numero?text=" + Uri.encode(texto))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            texto(context, texto)
        }
    }

    fun uriDe(context: Context, arquivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivo)

    fun pastaTemporaria(context: Context): File = File(context.cacheDir, "compartilhar").apply { mkdirs() }

    fun salvarImagem(context: Context, bitmap: Bitmap, nome: String): File {
        val f = File(pastaTemporaria(context), nome)
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun arquivos(context: Context, arquivos: List<File>, mime: String, texto: String? = null, titulo: String = "Enviar") {
        val uris = ArrayList(arquivos.map { uriDe(context, it) })
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        i.type = mime
        texto?.let { i.putExtra(Intent.EXTRA_TEXT, it) }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        abrir(context, i, titulo)
    }

    fun copiar(context: Context, rotulo: String, texto: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(rotulo, texto))
        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
    }
}
