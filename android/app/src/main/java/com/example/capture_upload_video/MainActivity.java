package com.example.capture_upload_video;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "benchmark_channel";
    private static final int CAMERA_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Solicitamos el permiso de camara nativamente al sistema operativo
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        flutterEngine.getPlatformViewsController().getRegistry()
                .registerViewFactory("native_camera_view", new NativeCameraFactory(this));

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (call.method.equals("startBenchmark")) {
                        // Ya no reseteamos variables aca, NativeCameraView lo hace solo al arrancar.
                        NativeCameraView.isBenchmarking = true;
                        result.success(null);
                    } 
                    else if (call.method.equals("stopBenchmark")) {
                        NativeCameraView.isBenchmarking = false;
                        // Le pasamos el result a NativeCameraView para que responda despues del vaciado
                        NativeCameraView.pendingResult = result;
                    } 
                    else {
                        result.notImplemented();
                    }
                });
    }
}