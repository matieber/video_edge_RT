import 'dart:io';
import 'package:flutter/services.dart'; // Necesario para StandardMessageCodec
import 'package:capture_upload_video/utils.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:light/light.dart';
import 'package:confirm_dialog/confirm_dialog.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:capture_upload_video/test_runner.dart';

class CameraPage extends StatefulWidget {
  const CameraPage({super.key});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  static const platform = MethodChannel('benchmark_channel');
  bool _isLoading = true;
  bool _isFlushing = false;
  bool _isRecording = false;
  String _luxString = 'Unknown';
  Light? _light;
  StreamSubscription? _subscription;
  MyClipper clipper = MyClipper();
  
  late int _countDownSeconds;
  int _currentIndex = 0;
  Timer? _timer;

  void _onTabTapped(int index) {
    setState(() {
      _currentIndex = index;
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    stopListening();
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
    } catch (exception) {
      print("light sensor initialization failed");
    }
  }

  // Nuevo Timer especifico para el test visual
  void _startLiveTimer() {
    if (_timer != null) {
      _timer!.cancel();
    }
    setState(() {
      _countDownSeconds = 60; // Forzamos los 30 segundos
    });

    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_countDownSeconds > 0) {
        setState(() {
          _countDownSeconds--;
        });
      } else {
        timer.cancel();
        setState(() {
          _isRecording = false;
        });
      }
    });
  }

void _iniciarTestLive() async {
    if (_isRecording) return;

    setState(() {
      _isRecording = true;
      _isFlushing = false;
    });

    _startLiveTimer();

    await platform.invokeMethod('startBenchmark');
    await Future.delayed(const Duration(seconds: 60));

    // Aca terminan los 30 seg. Mostramos cartel de vaciando.
    setState(() {
      _isFlushing = true;
    });

    // Dart se va a quedar clavado en esta linea HASTA que Java termine el buffer
    final Map<dynamic, dynamic> stats = await platform.invokeMethod('stopBenchmark');

    // Java termino, sacamos los carteles
    setState(() {
      _isRecording = false;
      _isFlushing = false;
    });

    // Extraemos TODAS las nuevas metricas
    TestRunner runner = TestRunner();
    await runner.procesarResultadosNativos(
        context, 
        60, 
        (stats['hardwareTotales'] ?? 0).toInt(),
        (stats['enviadosRAM'] ?? 0).toInt(),
        (stats['procesadosTotal'] ?? 0).toInt(),
        (stats['caras'] ?? 0).toInt(),
        (stats['procesadosStream'] ?? 0).toInt(),
        (stats['procesadosVaciado'] ?? 0).toInt(),
        (stats['tiempoVaciado'] ?? 0).toDouble()
    );
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
    final screenSize = MediaQuery.of(context).size;

return Center(
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          Align(
            alignment: Alignment.topCenter,
            child: Padding(
              padding: const EdgeInsets.only(top: 50.0),
              child: Container(
                  width: screenSize.width * 0.9,
                  height: screenSize.height * 0.65,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.blue, width: 4),
                    // Sacamos el borderRadius de aca, el ClipOval hace el trabajo
                  ),
                  // Volvemos a usar TU clipper original que estaba perfecto
                  child: ClipOval(
                    clipper: clipper, 
                    child: const NativeCameraWidget(),
                  ),
              ),
            ),
          ),
          
          // --- Cartel de Vaciado Superpuesto ---
          if (_isFlushing)
            Container(
              color: Colors.black.withOpacity(0.7),
              child: const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(color: Colors.white),
                    SizedBox(height: 20),
                    Text(
                      "Procesando frames restantes en memoria...\nAguarde un instante.",
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                    )
                  ],
                ),
              ),
            ),
          // ------------------------------------

          Row(
            mainAxisAlignment: MainAxisAlignment.center, // Centrado
            children: [
            Padding(
              padding: const EdgeInsets.all(25),
              child: FloatingActionButton(
                heroTag: "record",
                backgroundColor: _isFlushing ? Colors.grey : Colors.red,
                // Deshabilitar boton si esta vaciando
                onPressed: _isFlushing ? null : () => _iniciarTestLive(),
                child: Icon(_isRecording ? Icons.stop : Icons.camera_front),
              ),
            ),
            _isRecording && !_isFlushing
                ? Text('Test Live: $_countDownSeconds segs.',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold))
                : const SizedBox.shrink(),
          ])
        ],
      ),
    );
  }

  Widget _getLightPage() {
    return Center(
        child: Column(children: [
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
            return const CircularProgressIndicator();
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
    return Center(
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      FutureBuilder<String>(
        future: setOrGetUsername(context),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const CircularProgressIndicator();
          } else if (snapshot.hasError) {
            return Text('Error: ${snapshot.error}');
          } else {
            return Text('Usuario: ${snapshot.data}');
          }
        },
      ),
      const SizedBox(height: 50),
      const Text("Configuración de Benchmark",
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
      const SizedBox(height: 20),
      const Padding(
        padding: EdgeInsets.all(20.0),
        child: Text(
          "El test nativo en vivo ahora se ejecuta desde la pestaña 'Record' (botón rojo) para previsualizar la cámara nativa en tiempo real.",
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.grey),
        ),
      )
    ]));
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
          title: const Text('iPPG dataset creator'),
        ),
        body: _getCurrentPage(),
        bottomNavigationBar: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: _onTabTapped,
          items: const [
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
    // Ya no inicializamos la camara de Dart, Java se encarga de todo.
    setState(() {
      _isLoading = false;
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

    double aspect = height / width;
    if (aspect > 1.77) {
      return Rect.fromCenter(
          center: center,
          width: size.width * _adj_w_177,
          height: size.height * _adj_h_177);
    } else {
      return Rect.fromCenter(
          center: center,
          width: size.width * _adj_w,
          height: size.height * _adj_h);
    }
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

class NativeCameraWidget extends StatelessWidget {
  const NativeCameraWidget({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return const AndroidView(
      viewType: 'native_camera_view',
      layoutDirection: TextDirection.ltr,
      creationParamsCodec: StandardMessageCodec(),
    );
  }
}