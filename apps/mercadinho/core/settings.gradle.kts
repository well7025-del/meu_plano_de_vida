// Núcleo do app sem dependência de Android: comandos de voz, Pix, comprovante,
// impressão ESC/POS, relatórios e dicas. Roda e testa em qualquer JVM; o app
// Android compila estas mesmas fontes direto (ver app/build.gradle.kts).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mercadinho-core"
