package br.com.mercadinhofacil.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density

val Verde = Color(0xFF1B7F3B)
val VerdeClaro = Color(0xFFD8F3DF)
val Laranja = Color(0xFFE8710A)
val Vermelho = Color(0xFFC62828)
val Azul = Color(0xFF1565C0)
val Roxo = Color(0xFF6A1B9A)
val Ciano = Color(0xFF00838F)
val Marrom = Color(0xFF6D4C41)
val Cinza = Color(0xFF546E7A)

private val Claro = lightColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    primaryContainer = VerdeClaro,
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Laranja,
    onSecondary = Color.White,
    error = Vermelho,
    background = Color(0xFFF8FAF7),
    surface = Color.White,
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF7DDB95),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005227),
    secondary = Color(0xFFFFB77A),
    error = Color(0xFFFFB4AB),
)

/** Letras um pouco maiores que o padrão; a pessoa pode aumentar mais em Ajustes. */
@Composable
fun TemaMercadinho(escalaTexto: Float = 1.1f, conteudo: @Composable () -> Unit) {
    val densidade = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidade.density, densidade.fontScale * escalaTexto)) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) Escuro else Claro,
            typography = Typography(),
            content = conteudo,
        )
    }
}
