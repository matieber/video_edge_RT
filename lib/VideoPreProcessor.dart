import 'package:flutter/services.dart';
import 'resultadoBenchmark.dart';

class VideoPreProcessor {
  static const MethodChannel _channel = MethodChannel('video_preprocessor');

  Future<void> setRotation() async {
    try {
      await _channel.invokeMethod('setRotation');
    } on PlatformException catch (e) {
      print('Error setRotation: ${e.message}');
    }
  }

  // devuelve ResultadoBenchmark
  Future<ResultadoBenchmark?> preProcessVideo(String rutaVideo) async {
    try {
      print('Dart: Llamando a Java...');
      // invoco el metodo nativo
      final Map<dynamic, dynamic> resultado = await _channel.invokeMethod('preProcessVideo', {'videoFilePath': rutaVideo});

      print('Dart: Respuesta de Java: $resultado');

      return ResultadoBenchmark(
        nombreVideo: rutaVideo.split('/').last,
        tecnologia: "Hilos Java",
        tiempoExtraccion: resultado['extraccion'] ?? 0,
        tiempoML: resultado['ml'] ?? 0,
        tiempoTotal: resultado['total'] ?? 0,
        cantidadFrames: resultado['frames'] ?? 0
      );

    } on PlatformException catch (e) {
      print('Error desde Java: ${e.message}');
      return null;
    }
  }
}