import 'dart:io';
import 'package:capture_upload_video/VideoPreProcessor.dart';
import 'package:capture_upload_video/utils.dart';
import 'package:capture_upload_video/video_checker.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:http/http.dart' as http;

class VideoPage extends StatefulWidget {
  final String filePath;
  final double adj_w;
  final double adj_h;

  const VideoPage({Key? key, required this.filePath, required this.adj_w, required this.adj_h}) : super(key: key);

  @override
  _VideoPageState createState() => _VideoPageState();
}

class _VideoPageState extends State<VideoPage> {
  late VideoPlayerController _videoPlayerController;
  FrameChecker fc = FrameChecker();
  VideoPreProcessor javaPreProcess = VideoPreProcessor();

  @override
  void dispose() {
    _videoPlayerController.dispose();
    super.dispose();
  }

  Future _initVideoPlayer() async {
    _videoPlayerController = VideoPlayerController.file(File(widget.filePath));
    await _videoPlayerController.initialize();
    await _videoPlayerController.setLooping(true);
    await _videoPlayerController.play();
  }

  // funcion para guardar el video en la carpeta de pruebas
  Future<void> _saveToDataset() async {
    try {
      // chequeo de seguridad
      if (!_videoPlayerController.value.isInitialized) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Espera a que cargue el video..."))
        );
        return;
      }

      // 1. Detecto la duracion
      int seconds = _videoPlayerController.value.duration.inSeconds;

      // 2. Defino la subcarpeta (corte en 15 segundos)
      String subfolder = (seconds > 15) ? "largos" : "cortos";

      // La ruta ahora incluye la subcarpeta
      final String datasetPath = "/storage/emulated/0/Download/dataset/$subfolder/";
      final dir = Directory(datasetPath);

      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      int ts = DateTime.now().millisecondsSinceEpoch;
      // Agrego la duracion al nombre del archivo tambien para facilitar
      String newPath = "${dir.path}vid_${seconds}s_$ts.mp4";

      File original = File(widget.filePath);
      await original.copy(newPath);

      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Guardado en carpeta /$subfolder"))
      );
      print("Guardado en: $newPath");

    } catch (e) {
      print("Error al guardar: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Preview'),
        elevation: 0,
        backgroundColor: Colors.black26,
        actions: [
          // boton para guardar en dataset
          IconButton(
            icon: const Icon(Icons.save_alt),
            onPressed: () {
              _saveToDataset();
            },
          ),
          IconButton(
            icon: const Icon(Icons.check),
            onPressed: () async {
              int ts = DateTime.now().millisecondsSinceEpoch;
              await fc.preProcessVideo(widget.filePath);
              javaPreProcess.preProcessVideo(widget.filePath);
              Navigator.pop(context);
            },
          )
        ],
      ),
      extendBodyBehindAppBar: false,
      body: FutureBuilder(
        future: _initVideoPlayer(),
        builder: (context, state) {
          if (state.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          } else {
            return VideoPlayer(_videoPlayerController);
          }
        },
      ),
    );
  }

  void uploadFileToServer() async {
    String user = await setOrGetUsername(context);
    var request = http.MultipartRequest(
        "POST", Uri.parse('http://192.168.2.194:8080/face_recordings/$user'));
    String w = widget.adj_w.toString();
    String h = widget.adj_h.toString();
    int ts = DateTime
        .now()
        .millisecondsSinceEpoch;
    request.fields['json'] = '{"filename":"vid_${user}_$ts.mp4", "width_adjustment": "$w", "height_adjustment": "$h"}';
    request.files.add(
        await http.MultipartFile.fromPath('file', widget.filePath));

    ts = DateTime.now().millisecondsSinceEpoch;
    request.send().then((response) {
      http.Response.fromStream(response).then((onValue) {
        try {
          print("RTT (millis): ${DateTime.now().millisecondsSinceEpoch - ts} Server Response: ${onValue.body.characters}");
        } catch (e) {
          print(e.toString());
        }
      });
    });
  }
}
