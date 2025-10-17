import 'package:flutter/services.dart';

class VideoPreProcessor {
  static const MethodChannel _channel = MethodChannel('video_preprocessor');

  Future<void> setRotation() async {
    try {
      await _channel.invokeMethod('setRotation');
      print('Llamada a setRotation en Java iniciada.');
    } on PlatformException catch (e) {
      print('Error al llamar a setRotation en Java: ${e.message}');
    }
  }

  Future<void> preProcessVideo(String videoFilePath) async {
    try {
      await _channel.invokeMethod('preProcessVideo', {'videoFilePath': videoFilePath});
      print('Llamada a preProcessVideo en Java iniciada.');
    } on PlatformException catch (e) {
      print('Error al preprocesar el video con Java: ${e.message}');
    }
  }
}
