import 'package:flutter/services.dart';

class VideoPreProcessor {
  static const MethodChannel _channel = MethodChannel('video_preprocessor');

  Future<void> preProcessVideo(String videoFilePath) async {
    try {
      final result = await _channel.invokeMethod('preProcessVideo', {'videoFilePath': videoFilePath});
    } on PlatformException catch (e) {
      print('Error al preprocesar el video con Java');
    }
  }
}
