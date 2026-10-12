package br.com.mercadinhofacil.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Leitor de código de barras e QR Code pela câmera. O reconhecimento roda no
 * próprio celular (modelo do ML Kit embutido no app), sem internet.
 */
@Composable
fun LeitorCodigo(titulo: String = "Aponte para o código de barras", aoLer: (String) -> Unit, aoFechar: () -> Unit) {
    val context = LocalContext.current
    var permitido by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permitido = ok
        if (!ok) { aviso(context, "Sem permissão da câmera não dá para ler códigos."); aoFechar() }
    }
    LaunchedEffect(Unit) { if (!permitido) pedir.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (permitido) Camera(aoLer)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .size(220.dp)
                    .border(3.dp, Color.White, RoundedCornerShape(16.dp)),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(titulo, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
            IconButton(
                onClick = aoFechar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    .background(Color(0x88000000), RoundedCornerShape(28.dp)),
            ) { Icon(Icons.Filled.Close, "Fechar", tint = Color.White, modifier = Modifier.size(36.dp)) }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun Camera(aoLer: (String) -> Unit) {
    val context = LocalContext.current
    val dono = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val leitor: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE,
            ).build(),
        )
    }
    var lido by remember { mutableStateOf(false) }
    val visao = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val futuro = ProcessCameraProvider.getInstance(context)
        futuro.addListener({
            val provedor = futuro.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(visao.surfaceProvider) }
            val analise = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analise.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || lido) { proxy.close(); return@setAnalyzer }
                val imagem = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                leitor.process(imagem)
                    .addOnSuccessListener { codigos ->
                        val valor = codigos.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                        if (valor != null && !lido) {
                            lido = true
                            ContextCompat.getMainExecutor(context).execute { aoLer(valor) }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                provedor.unbindAll()
                provedor.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            leitor.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { visao }, modifier = Modifier.fillMaxSize())
}
