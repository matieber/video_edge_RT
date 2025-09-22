package com.example.capture_upload_video;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.embedding.android.FlutterActivity;

public class MainActivity extends FlutterActivity {
    //Definicion del canal por parte de java.
    private static final String CHANNEL = "video_preprocessor";

    //Metodo que inicia cuando se inicializa el motor de flutter, sacado de la documentacion de Flutter.
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        //Clase que establece conexion entre Flutter y Android.
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler(                                      //Metodo para definir que va a hacer android cuando flutter le mande un mensaje
                        (call, result) -> {                 // call -> nombre del metodo que flutter pidio ejecutar, result srive para devolver una respuesta.
                            // this method is invoked on the main thread.
                            // TODO

                        }
                );
    }

}
