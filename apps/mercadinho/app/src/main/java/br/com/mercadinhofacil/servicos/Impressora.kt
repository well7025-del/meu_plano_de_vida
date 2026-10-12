package br.com.mercadinhofacil.servicos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

data class Aparelho(val nome: String, val endereco: String)

/**
 * Impressora térmica portátil por Bluetooth (perfil serial SPP, que é o que
 * as impressoras de 58/80 mm vendidas no Brasil usam). A impressora precisa
 * estar pareada nas configurações de Bluetooth do celular.
 */
object Impressora {

    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun permissoesNecessarias(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else emptyArray()

    fun temPermissao(context: Context): Boolean = permissoesNecessarias().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun adaptador(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun bluetoothLigado(context: Context) = adaptador(context)?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun pareados(context: Context): List<Aparelho> {
        if (!temPermissao(context)) return emptyList()
        val a = adaptador(context) ?: return emptyList()
        return a.bondedDevices.orEmpty()
            .map { Aparelho(it.name ?: it.address, it.address) }
            // Impressoras costumam ter "printer", "pos", "mpt", "pt-" no nome: vêm primeiro.
            .sortedByDescending { d -> listOf("print", "pos", "mpt", "pt-", "mini", "therm", "58", "80").any { d.nome.lowercase().contains(it) } }
    }

    @SuppressLint("MissingPermission")
    private fun conectar(device: BluetoothDevice, adaptador: BluetoothAdapter): BluetoothSocket {
        adaptador.cancelDiscovery()
        return try {
            device.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
        } catch (e: Exception) {
            // Algumas impressoras chinesas só aceitam a conexão "insegura".
            device.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
        }
    }

    /** Envia os bytes ESC/POS. Devolve null se deu certo, ou a mensagem de erro em português. */
    @SuppressLint("MissingPermission")
    suspend fun imprimir(context: Context, endereco: String, dados: ByteArray): String? = withContext(Dispatchers.IO) {
        if (endereco.isBlank()) return@withContext "Escolha a impressora em Ajustes > Impressora."
        if (!temPermissao(context)) return@withContext "Permita o acesso ao Bluetooth para imprimir."
        val a = adaptador(context) ?: return@withContext "Este celular não tem Bluetooth."
        if (!a.isEnabled) return@withContext "Ligue o Bluetooth do celular."
        val device = try { a.getRemoteDevice(endereco) } catch (_: IllegalArgumentException) { null }
            ?: return@withContext "Impressora não encontrada. Escolha de novo em Ajustes."
        var socket: BluetoothSocket? = null
        try {
            socket = conectar(device, a)
            val saida = socket.outputStream
            // Manda em pedaços: impressoras baratas têm buffer pequeno e perdem dados.
            dados.toList().chunked(512).forEach { pedaco ->
                saida.write(pedaco.toByteArray())
                saida.flush()
                delay(30)
            }
            delay(400)
            null
        } catch (e: Exception) {
            "Não consegui falar com a impressora. Confira se ela está ligada, com papel e perto do celular."
        } finally {
            runCatching { socket?.close() }
        }
    }
}
