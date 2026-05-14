import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class TestRunner {
  final String rutaCsvLive = "/storage/emulated/0/Download/dataset/resultados_live.csv";

  Future<void> procesarResultadosNativos(
      BuildContext context, 
      int duracion, 
      int hardwareTotales, 
      int enRam,
      int procesadosTotal,
      int caras,
      int procesadosStream,
      int procesadosVaciado,
      double tiempoVaciado) async {
    
    double fpsCamaraReal = hardwareTotales / duracion;
    double fpsModeloStream = procesadosStream / duracion; // FPS mientras se grababa

    String msjLog = """
    --- RESULTADOS EDGE RT ---
    Duracion Stream: ${duracion}s
    Tiempo Vaciado: ${tiempoVaciado.toStringAsFixed(2)}s
    Fotos Sensor: $hardwareTotales
    Guardadas RAM: $enRam
    Procesados Total: $procesadosTotal
    Rostros Detectados: $caras
    Proc. en Stream: $procesadosStream (FPS: ${fpsModeloStream.toStringAsFixed(2)})
    Proc. en Vaciado: $procesadosVaciado
    -------------------------------------
    """;
    
    print(msjLog);

    await _guardarEnCsv(duracion, hardwareTotales, enRam, procesadosTotal, caras, procesadosStream, procesadosVaciado, tiempoVaciado, fpsCamaraReal, fpsModeloStream);
  }

  Future<void> _guardarEnCsv(
      int duracion, int hardTotales, int ram, int procTot, int caras, 
      int procStream, int procVaciado, double tiempoVac, double fpsCam, double fpsMod) async {
    
    if (!await Permission.manageExternalStorage.isGranted) {
      await Permission.manageExternalStorage.request();
    }
    
    final archivoCsv = File(rutaCsvLive);
    if (!await archivoCsv.exists()) {
      await archivoCsv.writeAsString("Timestamp,Duracion_s,Disp_Hardware,Guardados_RAM,Procesados_Total,Caras_Detectadas,Proc_Stream,Proc_Vaciado,Tiempo_Vaciado_s,FPS_Camara,FPS_Modelo_Stream\n");
    }

    String fechaHora = DateTime.now().toIso8601String();
    String lineaDatos = "$fechaHora,$duracion,$hardTotales,$ram,$procTot,$caras,$procStream,$procVaciado,${tiempoVac.toStringAsFixed(2)},${fpsCam.toStringAsFixed(2)},${fpsMod.toStringAsFixed(2)}\n";

    await archivoCsv.writeAsString(lineaDatos, mode: FileMode.append);
  }
}