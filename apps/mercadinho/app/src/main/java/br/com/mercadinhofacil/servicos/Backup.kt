package br.com.mercadinhofacil.servicos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mercadinhofacil.MercadinhoApp
import br.com.mercadinhofacil.dados.BancoDados
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cópia de segurança num único arquivo .zip: banco de dados, configurações e
 * fotos dos produtos. Fica no próprio celular (e numa pasta escolhida pelo
 * usuário, se ele quiser). Nada vai para servidor.
 */
object Backup {

    private const val CONFIG = "config.json"
    private const val MARCA = "mercadinho-facil-backup.txt"

    fun pastaLocal(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }

    fun nomeArquivo(): String =
        "mercadinho-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".zip"

    private fun configParaJson(valores: Map<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in valores) {
            val t = when (v) {
                is String -> "s"; is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> null
            } ?: continue
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o.toString()
    }

    private fun jsonParaConfig(texto: String): Map<String, Any?> {
        val o = JSONObject(texto)
        val m = mutableMapOf<String, Any?>()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            m[k] = when (e.getString("t")) {
                "s" -> e.getString("v"); "b" -> e.getBoolean("v"); "i" -> e.getInt("v")
                "l" -> e.getLong("v"); "f" -> e.getDouble("v").toFloat(); else -> null
            }
        }
        return m
    }

    /** Escreve o backup completo em [saida]. */
    suspend fun gerar(context: Context, saida: OutputStream) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MercadinhoApp
        // Junta o diário (WAL) no arquivo principal antes de copiar.
        app.banco.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val db = context.getDatabasePath(BancoDados.NOME)
        ZipOutputStream(saida.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MARCA))
            zip.write("Mercadinho Fácil - backup de ${Date()}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BancoDados.NOME))
            db.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(CONFIG))
            zip.write(configParaJson(app.prefs.exportar()).toByteArray())
            zip.closeEntry()
            File(context.filesDir, "fotos").listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("fotos/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Backup no celular (guarda os 10 mais novos) + na pasta escolhida, se houver. */
    suspend fun automatico(context: Context): File = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MercadinhoApp
        val pasta = pastaLocal(context)
        val arquivo = File(pasta, nomeArquivo())
        FileOutputStream(arquivo).use { gerar(context, it) }
        pasta.listFiles { f -> f.name.startsWith("mercadinho-") && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(10)
            ?.forEach { it.delete() }
        val destino = app.prefs.backupPasta
        if (destino.isNotBlank()) {
            runCatching { copiarParaPasta(context, Uri.parse(destino), arquivo) }
        }
        app.prefs.ultimoBackup = System.currentTimeMillis()
        arquivo
    }

    private fun copiarParaPasta(context: Context, arvore: Uri, arquivo: File) {
        val pai = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))
        val novo = DocumentsContract.createDocument(context.contentResolver, pai, "application/zip", arquivo.name) ?: return
        context.contentResolver.openOutputStream(novo)?.use { out -> arquivo.inputStream().use { it.copyTo(out) } }
    }

    fun backupsLocais(context: Context): List<File> =
        pastaLocal(context).listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Substitui todos os dados pelos do backup e reinicia o app. Antes, guarda
     * uma cópia do estado atual (caso a pessoa escolha o arquivo errado).
     */
    suspend fun restaurar(context: Context, origem: Uri): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MercadinhoApp
        val temp = File(context.cacheDir, "restaurar").apply { deleteRecursively(); mkdirs() }
        var temBanco = false
        var temMarca = false
        try {
            context.contentResolver.openInputStream(origem)?.use { entrada ->
                ZipInputStream(entrada.buffered()).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        val destino = File(temp, e.name)
                        if (!destino.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                            return@withContext "Arquivo de backup inválido."
                        }
                        if (e.isDirectory) destino.mkdirs() else {
                            destino.parentFile?.mkdirs()
                            FileOutputStream(destino).use { zip.copyTo(it) }
                            if (e.name == BancoDados.NOME) temBanco = true
                            if (e.name == MARCA) temMarca = true
                        }
                        e = zip.nextEntry
                    }
                }
            } ?: return@withContext "Não consegui abrir o arquivo."
        } catch (ex: Exception) {
            return@withContext "Este arquivo não é um backup do Mercadinho Fácil."
        }
        if (!temBanco || !temMarca) return@withContext "Este arquivo não é um backup do Mercadinho Fácil."

        runCatching { automatico(context) } // segurança: guarda o estado atual antes de trocar

        app.banco.close()
        val db = context.getDatabasePath(BancoDados.NOME)
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal")).forEach { it.delete() }
        File(temp, BancoDados.NOME).copyTo(db, overwrite = true)
        val fotos = File(context.filesDir, "fotos")
        File(temp, "fotos").takeIf { it.exists() }?.let { novas ->
            fotos.deleteRecursively()
            novas.copyRecursively(fotos, overwrite = true)
        }
        File(temp, CONFIG).takeIf { it.exists() }?.let { f ->
            val pastaBackup = app.prefs.backupPasta
            app.prefs.importar(jsonParaConfig(f.readText()))
            app.prefs.backupPasta = pastaBackup
        }
        temp.deleteRecursively()
        null
    }

    fun reiniciarApp(context: Context) {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    fun agendar(context: Context, ligado: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (ligado) {
            wm.enqueueUniquePeriodicWork(
                "backup-diario", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build(),
            )
        } else {
            wm.cancelUniqueWork("backup-diario")
        }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Backup.automatico(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
