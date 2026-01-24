import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:capture_upload_video/VideoPreProcessor.dart';
import 'package:capture_upload_video/video_checker.dart';
import 'resultadoBenchmark.dart';

class TestRunner {
  final String rutaBase = "/storage/emulated/0/Download/dataset/";
  final String rutaCsv = "/storage/emulated/0/Download/dataset/resultados_benchmark.csv";


  Future<void> correrTestAleatorio(BuildContext context, {required String nombreCarpeta}) async {
      print("Buscando videos en: $nombreCarpeta");

      if (!await Permission.manageExternalStorage.isGranted) {
        await Permission.manageExternalStorage.request();
      }

      final dir = Directory("$rutaBase$nombreCarpeta/");

      if (!await dir.exists()) {
        print("Error: La carpeta $nombreCarpeta no existe");
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('No existe la carpeta $nombreCarpeta'))
        );
        return;
      }

      List<FileSystemEntity> videos = dir.listSync()
          .where((file) => file.path.endsWith('.mp4'))
          .toList();

      if (videos.isEmpty) {
        print("Error: Carpeta vacia");
        return;
      }

      // preparo el archivo csv
      final archivoCsv = File(rutaCsv);
      if (!await archivoCsv.exists()) {
        await archivoCsv.writeAsString(
            "Video,Tecnologia,Tiempo_Extraccion_ms,Tiempo_ML_ms,Tiempo_Total_ms\n");
      }

      final random = Random();
      final archivoVideo = videos[random.nextInt(videos.length)];
      String nombreArchivo = archivoVideo.path
          .split('/')
          .last;

      print("Video elegido: $nombreArchivo");
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Procesando: $nombreArchivo'))
      );

      //final dartChecker = FrameChecker();
      final javaProcessor = VideoPreProcessor();

      await Future.delayed(const Duration(seconds: 45));

    /*
      // --- TEST DART ---
      print("--- Corriendo Dart ---");
      final swDart = Stopwatch()
        ..start();
      try {
        ResultadoBenchmark resultado = await dartChecker.preProcessVideo(
            archivoVideo.path);
        await _guardarEnCsv(archivoCsv, resultado);
      } catch (e) {
        print("Fallo Dart: $e");
      } // -------------------------------

      swDart.stop();
      */
      // pausa para enfriar
      //await Future.delayed(const Duration(seconds: 10));


      // --- TEST JAVA ---
      print("--- Corriendo Java ---");
      final swJava = Stopwatch()..start();
      try {
        ResultadoBenchmark? resultado = await javaProcessor.preProcessVideo(archivoVideo.path);
        if (resultado != null) {
          await _guardarEnCsv(archivoCsv, resultado);
        }
      } catch (e) {
        print("Fallo Java: $e");
      }
      swJava.stop();

      //dartChecker.dispose();

      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Datos guardados en resultados_benchmark.csv'))
      );
  }

  Future<void> _guardarEnCsv(File archivo, ResultadoBenchmark resultado) async {
    await archivo.writeAsString("${resultado.generarLineaCsv()}\n", mode: FileMode.append);
    print("Guardado en CSV: ${resultado.generarLineaCsv()}");
  }
}