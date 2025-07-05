import 'dart:io';

import 'package:camera/camera.dart';
import 'package:capture_upload_video/camera_view.dart';
import 'package:capture_upload_video/utils.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:light/light.dart';
import 'package:confirm_dialog/confirm_dialog.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'VideoPage.dart';

class CameraPage extends StatefulWidget {
  const CameraPage({super.key});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  bool _isLoading = true;
  bool _isRecording = false;
  String _luxString = 'Unknown';
  Light? _light;
  StreamSubscription? _subscription;
  MyClipper clipper = MyClipper();
  final int RECORDING_MAX_SECS = 3;
  late int _countDownSeconds;
  late int currFrame = 0;

  int _currentIndex = 0;

  late CameraView cameraView;
  late CameraController _cameraController;

  final String _serverIp = 'Not set';

  Timer? _timer;

  void _onTabTapped(int index) {
    setState(() {
      _currentIndex = index;
    });
  }

  void _startTimer() {
    if (_timer != null) {
      _timer!.cancel(); // Cancel any previous timers
    }
    setState(() {
      _countDownSeconds = RECORDING_MAX_SECS; // Reset the timer value
    });

    _timer = Timer.periodic(Duration(seconds: 1), (timer) {
      if (_countDownSeconds >= 1) {
        setState(() {
          _countDownSeconds--;
        });
      } else {
        timer.cancel();
        if (_isRecording)
          _recordVideo(); //stop video automatically when the timer reaches 0
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _cameraController.dispose();
    super.dispose();
  }

  void onData(int luxValue) async {
    setState(() {
      _luxString = "$luxValue";
    });
  }

  void stopListening() {
    _subscription?.cancel();
  }

  void startListening() {
    _light = Light();
    try {
      _subscription = _light?.lightSensorStream.listen(onData);
    } on LightException catch (exception) {
      print("light sensor initialization failed");
    }
  }

  _recordVideo() async {
    if (_isRecording) {
      final file = await _cameraController.stopVideoRecording();
      print("video file stored at: ${file.path}");
      setState(() => _isRecording = false);
      final route = MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => VideoPage(
            filePath: file.path,
            adj_w: clipper.get_width_adjustment(),
            adj_h: clipper.get_height_adjustment()),
      );
      Navigator.push(context, route);
    } else {
      currFrame = 0;
      await _cameraController.prepareForVideoRecording();
      await _cameraController.startVideoRecording();
      _startTimer();
      setState(() {
        _isRecording = true;
        _countDownSeconds = RECORDING_MAX_SECS;
      });
    }
  }

  Future<String> _getMaxLightLevel() async {
    try {
      final SharedPreferences prefs = await SharedPreferences.getInstance();
      final String? level = prefs.getString('maximum_light');

      return level ?? _luxString;
    } catch (e) {
      print(e);
      return _luxString;
    }
  }

  Widget _getCurrentPage() {
    if (_currentIndex == 0) {
      return _getRecordPage();
    }
    if (_currentIndex == 1) {
      return _getLightPage();
    }
    return _getConfigurationPage();
  }

  Widget _getRecordPage() {
    return Center(
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          //CameraPreview(_cameraController),
          Container(
              decoration: BoxDecoration(
                border:
                Border.all(color: Colors.blue, width: 4), // Add a border
              ),
              child: ClipOval(
                clipper: clipper,
                child: CameraPreview(_cameraController),
              )),
          Row(children: [
            Padding(
              padding: const EdgeInsets.all(25),
              child: FloatingActionButton(
                heroTag: "record",
                backgroundColor: Colors.red,
                child: Icon(_isRecording ? Icons.stop : Icons.circle),
                onPressed: () => _recordVideo(),
              ),
            ),
            _isRecording
                ? Text('Hold still! ' + _countDownSeconds.toString() + ' secs.',
                style: DefaultTextStyle.of(context)
                    .style
                    .apply(fontSizeFactor: 0.3))
                : SizedBox.shrink(),
          ])
        ],
      ),
    );
  }

  Widget _getLightPage() {
    return Center(child:Column(children: [
      Padding(
        padding: const EdgeInsets.all(25),
        child: FloatingActionButton(
            heroTag: "light",
            backgroundColor: Colors.yellow,
            child: const Icon(Icons.light_mode_outlined),
            onPressed: () => {_setMaximumLightLevel()}),
      ),
      Text('Current ambient light: $_luxString\n',
          style: DefaultTextStyle.of(context).style.apply(fontSizeFactor: 0.3)),
      FutureBuilder<String>(
        future: _getMaxLightLevel(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return CircularProgressIndicator();
          } else if (snapshot.hasError) {
            return Text('Error: ${snapshot.error}');
          } else {
            return Text('Saved ambient light: ${snapshot.data}');
          }
        },
      ),
    ]));
  }

  Widget _getConfigurationPage() {
    return Center(child:Column(children: [
      FutureBuilder<String>(
          future: setOrGetUsername(context),
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return CircularProgressIndicator();
            } else if (snapshot.hasError) {
              return Text('Error: ${snapshot.error}');
            } else {
              return Text('Username: ${snapshot.data}');
            }
          },
    )]));
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Container(
        color: Colors.white,
        child: const Center(
          child: CircularProgressIndicator(),
        ),
      );
    } else {
      return Scaffold(
        appBar: AppBar(
          title: Text('iPPG dataset creator'),
        ),
        body: _getCurrentPage(),
        bottomNavigationBar: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: _onTabTapped,
          items: [
            BottomNavigationBarItem(
              icon: Icon(Icons.video_camera_front_outlined),
              label: 'Record',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.light_mode_outlined),
              label: 'Light',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.fact_check),
              label: 'Configuration',
            ),
          ],
        ),
      );
    }
  }

  //@override
  Widget build2(BuildContext context) {
    Future<String> future = _getMaxLightLevel();
    if (_isLoading) {
      return Container(
        color: Colors.white,
        child: const Center(
          child: CircularProgressIndicator(),
        ),
      );
    } else {
      return Center(
        child: Stack(
          alignment: Alignment.bottomCenter,
          children: [
            //CameraPreview(_cameraController),
            Container(
                decoration: BoxDecoration(
                  border:
                      Border.all(color: Colors.blue, width: 4), // Add a border
                ),
                child: ClipOval(
                  clipper: clipper,
                  child: CameraPreview(_cameraController),
                )),
            Row(children: [
              Padding(
                padding: const EdgeInsets.all(25),
                child: FloatingActionButton(
                  heroTag: "record",
                  backgroundColor: Colors.red,
                  child: Icon(_isRecording ? Icons.stop : Icons.circle),
                  onPressed: () => _recordVideo(),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(25),
                child: FloatingActionButton(
                  heroTag: "light",
                  backgroundColor: Colors.yellow,
                  child: const Icon(Icons.light_mode_outlined),
                  onPressed: () => {_setMaximumLightLevel()},
                ),
              ),
              Text('Lx: $_luxString\n',
                  style: DefaultTextStyle.of(context)
                      .style
                      .apply(fontSizeFactor: 0.3)),
              _isRecording
                  ? Text('\n\n\n' + _countDownSeconds.toString() + ' s.',
                      style: DefaultTextStyle.of(context)
                          .style
                          .apply(fontSizeFactor: 0.3))
                  : SizedBox.shrink(),
              FutureBuilder<String>(
                  future: future,
                  builder:
                      (BuildContext context, AsyncSnapshot<String> snapshot) {
                    String msg = "Unset";
                    if (snapshot.hasData) {
                      msg = snapshot.data!;
                    } else if (snapshot.hasError) {
                      msg = "Error";
                    }
                    return Text(' Max: $msg\n',
                        style: DefaultTextStyle.of(context)
                            .style
                            .apply(fontSizeFactor: 0.3));
                  })
            ])
          ],
        ),
      );
    }
  }

  _setMaximumLightLevel() async {
    String currLight = _luxString;
    String dialogText =
        'You will set ($currLight) as the maximum light level. Proceed?';
    if (await confirm(
      context,
      title: const Text('Confirm'),
      content: Text(dialogText),
      textOK: const Text('Yes'),
      textCancel: const Text('No'),
    )) {
      final SharedPreferences prefs = await SharedPreferences.getInstance();
      await prefs.setString('maximum_light', currLight);
    }
    setState(() {
      _currentIndex = _currentIndex;
    });
  }

  _initCamera() async {
    _isLoading = true;
    _isRecording = false;
    final cameras = await availableCameras();
    final frontCamera = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front);

    _cameraController = CameraController(
      frontCamera,
      // Set to ResolutionPreset.high. Do NOT set it to ResolutionPreset.max because for some phones does NOT work.
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: Platform.isAndroid
          ? ImageFormatGroup.nv21
          : ImageFormatGroup.bgra8888,
    );
    _cameraController.initialize().then((_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _isLoading = false;
      });
    });
    startListening();
  }

  @override
  void initState() {
    super.initState();
    _initCamera();
  }
}

class MyClipper extends CustomClipper<Rect> {
  final double _adj_w = 0.55;
  final double _adj_h = 0.7;
  final double _adj_w_177 = 0.8;
  final double _adj_h_177 = 0.8;
  double height = 0;
  double width = 0;

  @override
  Rect getClip(Size size) {
    Offset center = size.center(const Offset(0, -40));
    height = size.height;
    width = size.width;

    print("Clip size: (" + height.toString() + ", " + width.toString() + ")");
    double aspect = height / width;
    if (aspect > 1.77)
      return Rect.fromCenter(
          center: center,
          width: size.width * _adj_w_177,
          height: size.height * _adj_h_177);
    else
      return Rect.fromCenter(
          center: center,
          width: size.width * _adj_w,
          height: size.height * _adj_h);
  }

  @override
  bool shouldReclip(CustomClipper<Rect> oldClipper) {
    return false;
  }

  double get_width_adjustment() {
    return _adj_w;
  }

  double get_height_adjustment() {
    return _adj_h;
  }
}
