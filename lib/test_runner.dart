import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class TestRunner {
  final String rutaCsvLive = "/storage/emulated/0/Download/dataset/resultados_live.csv";

  // Esta funcion ya no procesa imagenes, solo recibe los resultados de Java y los guarda
  Future<void> procesarResultadosNativos(BuildContext context, int duracion, int capturados, int procesados) async {
    
    int framesDroppeados = capturados - procesados;
    double fpsReales = procesados / duracion;

    String msjLog = """
    --- RESULTADOS NATIVOS (CAMERA-X) ---
    Duracion: ${duracion}s
    Capturados: $capturados
    Procesados: $procesados
    Descartados: $framesDroppeados
    FPS Reales: ${fpsReales.toStringAsFixed(2)}
    -------------------------------------
    """;
    
    print(msjLog);

    await _guardarEnCsv(duracion, capturados, procesados, framesDroppeados, fpsReales);

    if (context.mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text("Resultados Nativos (Zero-Copy)"),
            content: Text(msjLog),
            actions: [
              TextButton(
                child: Text("OK"),
                onPressed: () => Navigator.of(context).pop(),
              )
            ],
          );
        },
      );
    }
  }

  Future<void> _guardarEnCsv(int duracion, int capturados, int procesados, int droppeados, double fps) async {
    if (!await Permission.manageExternalStorage.isGranted) {
      await Permission.manageExternalStorage.request();
    }
    final archivoCsv = File(rutaCsvLive);
    if (!await archivoCsv.exists()) {
      await archivoCsv.writeAsString("Timestamp,Tecnologia,Duracion_s,Capturados,Procesados,Droppeados,FPS_Reales\n");
    }

    String fechaHora = DateTime.now().toIso8601String();
    String lineaDatos = "$fechaHora,Java_CameraX,$duracion,$capturados,$procesados,$droppeados,${fps.toStringAsFixed(2)}\n";

    await archivoCsv.writeAsString(lineaDatos, mode: FileMode.append);
  }
}