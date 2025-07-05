import 'dart:io';
import 'package:capture_upload_video/utils.dart';
import 'package:capture_upload_video/video_checker.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:http/http.dart' as http;

// https://docs.flutter.dev/tools/devtools/cpu-profiler?gad_source=1&gclid=CjwKCAiA9bq6BhAKEiwAH6bqoAeGFQi-ig5iiTtJJm8WolS1crzwpuxd8NfhF3Mtb8no18wQOLmhRhoCs3UQAvD_BwE&gclsrc=aw.ds

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

  @override
  void dispose() {
    _videoPlayerController.dispose();
    super.dispose();
  }

  Future _initVideoPlayer() async {
    //print("created file${widget.filePath}");
    _videoPlayerController = VideoPlayerController.file(File(widget.filePath));
    await _videoPlayerController.initialize();
    await _videoPlayerController.setLooping(true);
    await _videoPlayerController.play();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Preview'),
        elevation: 0,
        backgroundColor: Colors.black26,
        actions: [
          IconButton(
            icon: const Icon(Icons.check),
            onPressed: () async {
              fc.setRotation();
              int ts = DateTime.now().millisecondsSinceEpoch;
              await fc.preProcessVideo(widget.filePath);
              print("Video preprocessed time: ${DateTime.now().millisecondsSinceEpoch - ts}");
              //uploadFileToServer();
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
  //time when start sending video to server
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
