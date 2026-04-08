package com.example.capture_upload_video;

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "benchmark_channel";

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        flutterEngine.getPlatformViewsController().getRegistry()
                .registerViewFactory("native_camera_view", new NativeCameraFactory(this));

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (call.method.equals("startBenchmark")) {
                        NativeCameraView.framesCapturados = 0;
                        NativeCameraView.framesProcesados = 0;
                        NativeCameraView.isBenchmarking = true;
                        result.success(null);
                    } 
                    else if (call.method.equals("stopBenchmark")) {
                        NativeCameraView.isBenchmarking = false;
                        Map<String, Integer> stats = new HashMap<>();
                        stats.put("capturados", NativeCameraView.framesCapturados);
                        stats.put("procesados", NativeCameraView.framesProcesados);
                        result.success(stats);
                    } 
                    else {
                        result.notImplemented();
                    }
                });
    }
}