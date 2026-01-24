import 'dart:async';
import 'dart:io';
//import 'package:ffmpeg_kit_flutter_min_gpl/ffprobe_kit.dart';
import 'package:ffmpeg_kit_flutter_new/ffprobe_kit.dart';
//import 'package:image/image.dart' as img;

import 'package:flutter/services.dart'; // Para usar BackgroundIsolateBinaryMessenger
//import 'package:dartcv4/core.dart';
import 'package:camera/camera.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:google_ml_kit/google_ml_kit.dart';
import 'package:permission_handler/permission_handler.dart';
//import 'package:dartcv4/dartcv.dart' as cv;
//import 'package:ffmpeg_kit_flutter_min_gpl/ffmpeg_kit.dart';
//import 'package:ffmpeg_kit_flutter_min_gpl/return_code.dart';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'dart:developer' as developer;
import 'dart:isolate';
import 'resultadoBenchmark.dart';

class FrameChecker{

  //face detection filter: true indicates that the frame contains a face while
  // false indicates that the frame doesn't contain a face.
  List<bool> faceFilterPassValues = List.empty(growable: true);

  //TODO:
  // Blur filter: true indicates that the frame contains passes the blur
  // filter or equivalently that the blur value is under the configured
  // threshold. False indicates that the frame doesn't pass the blur filter.
  List<bool> blurFilterPass = List.empty(growable: true);

  final FaceMeshDetector _meshDetector =
  FaceMeshDetector(option: FaceMeshDetectorOptions.boundingBoxOnly);

  late InputImageRotation imageRotation;

  late String basePath;
  int currFrame = 0;
  final String FRAME_EXTENSION = "bmp";

  void dispose(){
    _meshDetector.close();
  }

  Future<void> setRotation() async {
    final cameras = await availableCameras();
    final frontCamera = cameras.firstWhere(
            (camera) => camera.lensDirection == CameraLensDirection.front);

    final cameraController = CameraController(
      frontCamera,
      // Set to ResolutionPreset.high. Do NOT set it to ResolutionPreset.max
      // because for some phones does NOT work.
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: Platform.isAndroid
          ? ImageFormatGroup.nv21
          : ImageFormatGroup.bgra8888,
    );
    final sensorOrientation = frontCamera.sensorOrientation;
    InputImageRotation? rotation;
    if (Platform.isIOS) {
      rotation = InputImageRotationValue.fromRawValue(sensorOrientation);
    } else if (Platform.isAndroid) {
      var rotationCompensation =
      _orientations[cameraController.value.deviceOrientation];

      if (frontCamera.lensDirection == CameraLensDirection.front) {
        // front-facing
        rotationCompensation = (sensorOrientation + rotationCompensation!) % 360;
      } else {
        // back-facing
        rotationCompensation =
            (sensorOrientation - rotationCompensation! + 360) % 360;
      }
      imageRotation = InputImageRotationValue.fromRawValue(rotationCompensation)!;
    }
  }

  Future requestPermission(Permission permission) async {
    print("Requesting permission: $permission");
    PermissionStatus status = await permission.status;
    print("Permission status: $status");

    if (status.isPermanentlyDenied) {
      print("Permission is permanently denied");
    } else if (status.isDenied) {
      print("Permission is denied");
      status = await permission.request();
      print("Permission status on requesting again: $status");
    } else {
      print("Permission is not permanently denied");
      status = await permission.request();
    }
  }

  Future<String> getDownloadDirectory() async {
    bool dirDownloadExists = true;
    String directory;
    directory = "/storage/emulated/0/Download/";
    dirDownloadExists = await Directory(directory).exists();
    if(dirDownloadExists){
      directory = "/storage/emulated/0/Download/";
    }else{
      directory = "/storage/emulated/0/Downloads/";
    }
    return directory;
  }

  /*Future<void> processVideoOpenCV(String filePath) async {
    //final vc = cv.VideoCapture.fromFile(file.path);


    final vc = await cv.VideoCaptureAsync.fromFileAsync(filePath, apiPreference: cv.CAP_ANY);
    Mat frame = Mat.empty();
    if (!vc.isOpened) {
      print("cap is not open");
      return;
    }
    //(bool, cv.Mat) a = vc.read();
    //print(a.$1);
    //final  = await vc.readAsync();
  }*/

  Future<void> isolateFunction(List<dynamic> args) async {
    final SendPort sendPort = args[0];
    final list = args[1];
    final lower = args[2];
    final upper = args[3];
    BackgroundIsolateBinaryMessenger.ensureInitialized(args[4]);
    try {
      final FaceMeshDetector isolateDetector = FaceMeshDetector(option: FaceMeshDetectorOptions.faceMesh);
      final sublist = list.sublist(lower, upper + 1);
      final List<bool> results = List<bool>.empty(growable: true);
      for (final frame in sublist) {
        final InputImage inputImage = await _createImageInputFromFile(frame);
        final faces = await isolateDetector.processImage(inputImage);
        results.add(faces.length == 1);
      }
      isolateDetector.close();
      sendPort.send(results); // Enviar resultado al hilo principal
      /*
      developer.Timeline.startSync('Procesamiento frames [$lower - $upper]');
      final stopwatch = Stopwatch()..start();
      while (stopwatch.elapsed.inSeconds < 10) {
        // Perform a heavy computation
        for (int i = 0; i < 1000000; i++) {
          double x = i * i * i.toDouble();
        }
      }
      stopwatch.stop();
      sendPort.send(List<bool>.empty(growable: false));
      developer.Timeline.finishSync();*/
    } catch (e) {
      sendPort.send(List<bool>.empty(growable: false)); // Enviar -1 en caso de error
      print('PROCCESING FRAME ${currFrame} IN ISOLATE: 0  ${e.toString()}');
    }
  }

  Future<ResultadoBenchmark> preProcessVideo(String rutaVideo) async {
    print("PROCESANDO VIDEO DART: $rutaVideo");
    developer.Timeline.startSync('FLUTTER_PREPROCESAMIENTO');

    int tiempoInicioTotal = DateTime.now().millisecondsSinceEpoch;
    developer.Timeline.startSync('PREPROCESSING VIDEO');

    currFrame = 0;
    int tiempoInicioExtraccion = DateTime.now().millisecondsSinceEpoch;
    var framesPaths = await extractFramesDisco(rutaVideo);
    int tiempoFinExtraccion = DateTime.now().millisecondsSinceEpoch;

    print("CANTIDAD DE FRAMES: ${framesPaths.length}");

    final List<Future<List<bool>>> futures = [];
    final rootIsolateToken = RootIsolateToken.instance!;

    int partes = Platform.numberOfProcessors;
    int tamanoParte = (framesPaths.length / partes).ceil();

    for (int i = 0; i < partes; i++) {
      int lower = i * tamanoParte;
      int upper = ((i + 1) * tamanoParte).clamp(0, framesPaths.length) - 1;

      final completer = Completer<List<bool>>();
      final receivePort = ReceivePort();
      await Isolate.spawn(
          isolateFunction,
          [receivePort.sendPort, framesPaths, lower, upper, rootIsolateToken],
          debugName: 'Procesamiento-$i'
      );
      receivePort.listen((message) {
        completer.complete(message as List<bool>);
        receivePort.close();
      });
      futures.add(completer.future);
    }
    await Future.wait(futures);
    int tiempoFinML = DateTime.now().millisecondsSinceEpoch;

    print("DART Extraccion: ${tiempoFinExtraccion - tiempoInicioExtraccion} ms");
    print("DART ML: ${tiempoFinML - tiempoFinExtraccion} ms");
    print("DART Total: ${tiempoFinML - tiempoInicioTotal} ms");
    developer.Timeline.finishSync();

    developer.Timeline.finishSync();
    return ResultadoBenchmark(
      nombreVideo: rutaVideo.split('/').last,
      tecnologia: "Isolates Dart",
      tiempoExtraccion: tiempoFinExtraccion - tiempoInicioExtraccion,
      tiempoML: tiempoFinML - tiempoFinExtraccion,
      tiempoTotal: tiempoFinML - tiempoInicioTotal,
      cantidadFrames: framesPaths.length
    );
  }

  Future<void> preProcessVideoOld(String videoFilePath) async {
    currFrame = 0;
    var framesPaths = await extractFrames(videoFilePath);
    print("frames count: ${framesPaths.length}");
    final startTime = DateTime.now(); // Captura el tiempo inicial
    for (final frame in framesPaths) {
      await _faceDetector(frame, currFrame);
      currFrame++;
    }
    print("Face Filter pass: ${faceFilterPassValues}");
    final endTime = DateTime.now(); // Captura el tiempo final
    final duration = endTime.difference(startTime); // Diferencia en tiempo
    print("Duración: ${duration.inMilliseconds} ms");
    //_deleteFrames();
  }

  Future<void> _deleteFrames() async{
    List<Future> futures = <Future>[];
    new Directory('/storage/emulated/0/Download/').listSync().forEach((frame) async {

        if (frame.path.endsWith(FRAME_EXTENSION) &&
            FileSystemEntity.typeSync(frame.path) == FileSystemEntityType.file) {
            futures.add((frame as File).delete());
        }
    });
    print ("Wait for delete files");
    Future.wait(futures);
  }

  Future<void> _faceDetector(String framePath, int framenro) async {

      InputImage inputImage = await _createImageInputFromFile(framePath);
      bool faceCountFilterValue = false;

      final meshes = await _meshDetector.processImage(inputImage);
      /*if (inputImage.metadata?.size != null &&
          inputImage.metadata?.rotation != null) {*/
        //final FaceMeshPoint fir = meshes.first.triangles.first.points.first;
        //print('Primer punto de Triangulo: x:${fir.x} y:${fir.y} z:${fir.z}');
        if (meshes.length == 1) {
          faceCountFilterValue = true;
        }
      faceFilterPassValues.add(faceCountFilterValue);
      print('frame ${framenro}: ${faceCountFilterValue}');
    }

  Future<List<String>> extractFrames(String videoFilePath) async {
    List<String> ret = List.empty(growable: true);
    await requestPermission(Permission.manageExternalStorage);
    final String directory = await getDownloadDirectory();

    var command = '-i ${videoFilePath} -f image2 ${directory}img-%04d.$FRAME_EXTENSION';

    await FFmpegKit.execute(command).then((session) async {
      final returnCode = await session.getReturnCode();

      String? logs = await session.getAllLogsAsString();

      if (ReturnCode.isSuccess(returnCode)) {
        //  _logger.info('Frame Export Success');
        var totalFrames = await _getFramesNumber(videoFilePath);

        print("DART: Frames extraidos: $totalFrames");

        for (var a = 1; a <= totalFrames; a++) {
          if (a < 10) {
            ret.add('${directory}img-000$a.$FRAME_EXTENSION');
          } else if (a < 100) {
            ret.add('${directory}img-00$a.$FRAME_EXTENSION');
          } else if (a < 1000) {
            ret.add('${directory}img-0$a.$FRAME_EXTENSION');
          } else {
            ret.add('${directory}img-$a.$FRAME_EXTENSION');
          }
        }
      }});
    return ret;
  }

  /*Future<int> _getFramesNumber(String videoFilePath) async {  ----------------------- Quedo obsoleto tras la actualizacion de ffmpeg_kit_flutter_new
    List<String> ffProbeVideoInfo = await _getVideoInfo(videoFilePath);
    String framesInfo = ffProbeVideoInfo[56];//56 is the position where nb_frames
    // information is supposed to be within the output of the ffprobe command.
    print("framesInfo: $framesInfo");
    return int.parse(framesInfo.split("=")[1]);
  }*/

  Future<int> _getFramesNumber(String videoFilePath) async {
    List<String> ffProbeVideoInfo = await _getVideoInfo(videoFilePath);
    // Busca la línea que contiene nb_frames
    String? framesInfo = ffProbeVideoInfo.firstWhere(
      (line) => line.contains("nb_frames"),
      orElse: () => ""
    );
    print("framesInfo DART =============>>>> $framesInfo");
    if (framesInfo.isEmpty || !framesInfo.contains("=")) {
      throw Exception("No se encontró nb_frames en la info del video");
    }
    String value = framesInfo.split("=")[1];
    if (value == "N/A") {
      throw Exception("nb_frames no disponible para este video");
    }
    return int.parse(value);
  }

  Future<List<String>> _getVideoInfo(String videoFilePath) {
    final _completer = Completer<List<String>>();
    var videoInfo = '-i ${videoFilePath} -show_streams -hide_banner';
    FFprobeKit.executeAsync(videoInfo, (session) async {
      final returnCode = await session.getReturnCode();
      final output = await session.getOutput();
      if (ReturnCode.isSuccess(returnCode)) {
        _completer.complete(_parseVideoInfo(output));
      } else {
        _handleFailure(returnCode: returnCode, completer: _completer);
      }
    });
    return _completer.future;
  }

  FutureOr<List<String>>? _parseVideoInfo(String? output) {
    if (output == null || output.isEmpty) {
      throw Exception("No data");
    }
    try {
      return output.split('\n');
    } catch (e) {
          return null;
    }
  }

  void _handleFailure({
    required ReturnCode? returnCode,
    required Completer<List<String>?> completer,
  }) {
    if (ReturnCode.isCancel(returnCode)) {
      print("ffprobe commande canceled");
      completer.complete(null);
    } else {
      final e = Exception("command fail");
      completer.completeError(e);
    }
  }

  Future<InputImage> _createImageInputFromFile(String framePath) async {

    /*final cmd = img.Command();
    // Decode the image file at the given path
    cmd.decodeImageFile(framePath);

    var image = await cmd.getImage();
    var metadata = InputImageMetadata(size: Size(image!.width.toDouble(), image.height.toDouble()), format: _getInputImageFormat(), bytesPerRow: image.rowStride, rotation: imageRotation);
    return InputImage.fromBytes(bytes: image.toUint8List(), metadata: metadata);*/
    return InputImage.fromFilePath(framePath);
  }

  InputImageFormat _getInputImageFormat(){
    return Platform.isAndroid
        ? InputImageFormat.nv21
        : InputImageFormat.bgra8888;
  }

  final _orientations = {
    DeviceOrientation.portraitUp: 0,
    DeviceOrientation.landscapeLeft: 90,
    DeviceOrientation.portraitDown: 180,
    DeviceOrientation.landscapeRight: 270,
  };


  Future<List<String>> extractFramesDisco(String videoFilePath) async {
    List<String> ret = List.empty(growable: true);
    await requestPermission(Permission.manageExternalStorage);
    final String directory = await getDownloadDirectory();

    final dir = Directory(directory);

    // 1. Limpieza previa
    if (await dir.exists()) {
      try {
        final List<FileSystemEntity> existingFiles = dir.listSync();
        for (var file in existingFiles) {
          if (file.path.endsWith(FRAME_EXTENSION)) {
            await file.delete();
          }
        }
      } catch (e) {
        print("Error limpiando carpeta: $e");
      }
    }

    // 2. Ejecutar FFmpeg (Genera los archivos fisicos)
    var command = '-i ${videoFilePath} -f image2 ${directory}img-%04d.$FRAME_EXTENSION';

    await FFmpegKit.execute(command).then((session) async {
      final returnCode = await session.getReturnCode();

      if (ReturnCode.isSuccess(returnCode)) {

        // Leemos el directorio fisico
        if (await dir.exists()) {
          final List<FileSystemEntity> files = dir.listSync();

          // Filtramos solo los BMP y obtenemos sus rutas
          var bmpFiles = files
              .where((file) => file.path.endsWith(FRAME_EXTENSION))
              .map((file) => file.path)
              .toList();

          // Ordenamos alfabeticamente para asegurar el orden del video
          bmpFiles.sort();

          ret = bmpFiles;

          print("DART_DEBUG: Frames encontrados en disco: ${ret.length}");
        }
      } else {
        print("DART_ERROR: FFmpeg fallo con codigo $returnCode");
      }
    });

    return ret;
  }
}

