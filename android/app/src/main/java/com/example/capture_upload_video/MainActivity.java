package com.example.capture_upload_video;

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.antonkarpenko.ffmpegkit.Session;

import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.net.Uri;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.android.gms.tasks.Tasks;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "video_preprocessor";

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler(
                        (call, result) -> {
                            if (call.method.equals("preProcessVideo")) {
                                String videoPath = call.argument("videoFilePath");
                                // llamo a la funcion interna pasando el resultado para responder despues
                                iniciarProcesamientoVideo(videoPath, result);
                            } else if (call.method.equals("setRotation")) {
                                result.success(null);
                            } else {
                                result.notImplemented();
                            }
                        }
                );
    }

    private boolean analizarFrame(FaceMeshDetector detector, String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) return false;
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(file));
            List<FaceMesh> cara = Tasks.await(detector.process(image));
            return cara != null && cara.size() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> extraerFrames(String videoPath) {
        List<String> rutasFrames = new ArrayList<>();
        File dir = new File(getCacheDir(), "frames");

        if (dir.exists()) { for (File f : dir.listFiles()) f.delete(); }
        dir.mkdirs();

        String patronArchivo = new File(dir, "img-%04d.bmp").getAbsolutePath();
        String comando = "-i " + videoPath + " -f image2 " + patronArchivo;

        Session session = FFmpegKit.execute(comando);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            File[] archivos = dir.listFiles((directory, name) -> name.endsWith(".bmp"));
            if (archivos != null) {
                java.util.Arrays.sort(archivos);
                for (File f : archivos) rutasFrames.add(f.getAbsolutePath());
            }
        }

        System.out.println("---> JAVA: Frames extraidos: " + rutasFrames.size());

        return rutasFrames;
    }

    private List<Boolean> procesarFramesEnParalelo(List<String> todosLosFrames) throws InterruptedException, ExecutionException {
        int nucleos = Runtime.getRuntime().availableProcessors();

        ExecutorService servicioEjecutor = Executors.newFixedThreadPool(nucleos);
        List<Callable<List<Boolean>>> tareas = new ArrayList<>();
        int cantidadTotalFrames = todosLosFrames.size();

        // Evitamos division por cero si no hay frames
        if (cantidadTotalFrames == 0) return new ArrayList<>();

        int tamanoLote = (int) Math.ceil((double) cantidadTotalFrames / nucleos);

        for (int i = 0; i < nucleos; i++) {
            int inicio = i * tamanoLote;
            int fin = Math.min(inicio + tamanoLote, cantidadTotalFrames);
            if (inicio >= fin) break;
            List<String> subListaFrames = todosLosFrames.subList(inicio, fin);

            tareas.add(() -> {
                FaceMeshDetectorOptions opciones = new FaceMeshDetectorOptions.Builder()
                        .setUseCase(FaceMeshDetectorOptions.FACE_MESH).build();
                FaceMeshDetector detectorLocal = FaceMeshDetection.getClient(opciones);
                List<Boolean> resultadosDelLote = new ArrayList<>();
                for (String rutaFrame : subListaFrames) {
                    resultadosDelLote.add(analizarFrame(detectorLocal, rutaFrame));
                }
                detectorLocal.close();
                return resultadosDelLote;
            });
        }
        List<Future<List<Boolean>>> futuros = servicioEjecutor.invokeAll(tareas);
        List<Boolean> resultadosCombinados = new ArrayList<>();
        for (Future<List<Boolean>> futuro : futuros) { resultadosCombinados.addAll(futuro.get()); }
        servicioEjecutor.shutdown();
        return resultadosCombinados;
    }

    private void iniciarProcesamientoVideo(String videoPath, MethodChannel.Result result) {
        new Thread(() -> {
            try {
                Trace.beginSection("JAVA_PREPROCESAMIENTO");

                // 1. extraccion
                long tiempoExtraccionInicio = System.currentTimeMillis();
                List<String> frames = extraerFrames(videoPath);
                long tiempoExtraccionFin = System.currentTimeMillis();

                if (frames.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            result.error("SIN_FRAMES", "No se pudieron extraer frames", null)
                    );
                    return;
                }

                // 2. procesamiento
                long tiempoMLInicio = System.currentTimeMillis();
                procesarFramesEnParalelo(frames);
                long tiempoMLFin = System.currentTimeMillis();

                // 3. mapa de tiempos en espanol
                Map<String, Long> tiempos = new HashMap<>();
                tiempos.put("extraccion", tiempoExtraccionFin - tiempoExtraccionInicio);
                tiempos.put("ml", tiempoMLFin - tiempoMLInicio);
                tiempos.put("total", (tiempoMLFin - tiempoMLInicio) + (tiempoExtraccionFin - tiempoExtraccionInicio));
                tiempos.put("frames", (long) frames.size());

                // 4. respuesta a flutter
                new Handler(Looper.getMainLooper()).post(() -> {
                    System.out.println("JAVA: Enviando datos: " + tiempos.toString());
                    result.success(tiempos);
                });
                Trace.endSection();
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        result.error("ERROR_JAVA", e.getMessage(), null)
                );
                Trace.endSection();
            }
        }).start();
    }
}