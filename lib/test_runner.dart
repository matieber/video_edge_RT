import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class TestRunner {
  final String rutaCsvLive = "/storage/emulated/0/Download/dataset/resultados_live.csv";

  // Esta funcion ya no procesa imagenes, solo recibe los resultados de Java y los guarda
Future<void> procesarResultadosNativos(BuildContext context, int duracion, int hardwareTotales, int procesados) async {
    
    int descartados = hardwareTotales - procesados;
    double fpsCamaraReal = hardwareTotales / duracion;
    double fpsModelo = procesados / duracion;

    String msjLog = """
    --- RESULTADOS ---
    Duracion: ${duracion}s
    Total Fotos del Sensor: $hardwareTotales
    Frames Procesados: $procesados
    Frames Descartados: $descartados
    FPS Camara Real: ${fpsCamaraReal.toStringAsFixed(2)}
    FPS Modelo: ${fpsModelo.toStringAsFixed(2)}
    -------------------------------------
    """;
    
    print(msjLog);

    await _guardarEnCsv(duracion, hardwareTotales, procesados, descartados, fpsCamaraReal, fpsModelo);


  }

  Future<void> _guardarEnCsv(int duracion, int hardwareTotales, int procesados, int droppeados, double fps, double fpsModelo) async {
    if (!await Permission.manageExternalStorage.isGranted) {
      await Permission.manageExternalStorage.request();
    }
    final archivoCsv = File(rutaCsvLive);
    if (!await archivoCsv.exists()) {
      await archivoCsv.writeAsString("Timestamp,Tecnologia,Duracion_s,Capturados,Procesados,Droppeados,FPS_Reales,FPS_Modelo\n");
    }

    String fechaHora = DateTime.now().toIso8601String();
    String lineaDatos = "$fechaHora,Java_CameraX,$duracion,$hardwareTotales,$procesados,$droppeados,${fps.toStringAsFixed(2)},${fpsModelo.toStringAsFixed(2)}\n";

    await archivoCsv.writeAsString(lineaDatos, mode: FileMode.append);
  }
}