# capture_upload_video

A new Flutter project.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
# Etapa 1 

## Comentarios del proceso para compilar el prototipo.
1. El mayor problema para la compilacion fue este: + ffmpeg_kit_flutter_min_gpl 6.0.3 (discontinued). La dependencia ya no funciona en 2025 

2. Cambio en las versiones de setting.gradle y gradle-wrapper.properties para evitar warnings
    plugins {
    id "dev.flutter.flutter-plugin-loader" version "1.0.0"
    id "com.android.application" version "8.3.0" apply false
    id "org.jetbrains.kotlin.android" version "1.8.10" apply false
    }
    distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-all.zip

3. Estos cambios en las versiones descriptas en el 2. trajeron nuevos erros en el plugin light-3.0.1 que requiere en el build.gradle especificar el namespace del AndroidManifest, el problema que el cambio se hace en la cache para solucionarlo temporalmente y que al ejecutar no aparezca el error. 

4. Tanto el problema 1. como el 3. puedan requerir 2 soluciones una es descargar light-3.0.1 y ffmpeg_kit_flutter_min_gpl 6.0.3  para que sea accedido dentro del proyecto y otra es hacer un fork y modificar el pubspec.yaml para que acceda a los plugin en GitHub 