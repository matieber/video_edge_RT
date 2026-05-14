package com.example.capture_upload_video;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;

public class NativeCameraView implements PlatformView {

    // --- VARIABLES ESTATICAS PARA MAINACTIVITY ---
    public static boolean isBenchmarking = false;
    public static int framesHardwareTotales = 0;
    public static int framesEnviadosAlBuffer = 0; 
    public static int framesProcesadosStream = 0;
    public static int framesCara = 0;


    // Objeto para responderle a Flutter despues del vaciado
    public static MethodChannel.Result pendingResult;

    private final FrameLayout contenedor;
    private final PreviewView vistaPrevia;
    private final Context contexto;
    private final LifecycleOwner cicloDeVida;

    private ExecutorService ejecutorCamara;
    private ProcessCameraProvider proveedorCamara;

    private final BufferDeFrames buffer;
    private final PoolDeDetectores pool;

    private volatile boolean esPrimerFrameDelBenchmark = true;

    public NativeCameraView(@NonNull Context contexto, int id, Map<String, Object> params, LifecycleOwner cicloDeVida) {
        this.contexto = contexto;
        this.cicloDeVida = cicloDeVida;

        contenedor = new FrameLayout(contexto);
        vistaPrevia = new PreviewView(contexto);
        vistaPrevia.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        vistaPrevia.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        contenedor.addView(vistaPrevia);

        buffer = new BufferDeFrames();
        pool = new PoolDeDetectores(6, buffer); 

        ejecutorCamara = Executors.newSingleThreadExecutor();
        iniciarCamara();
    }

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> futuroProveedor = ProcessCameraProvider.getInstance(contexto);
        futuroProveedor.addListener(() -> {
            try {
                proveedorCamara = futuroProveedor.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(vistaPrevia.getSurfaceProvider());

                ImageAnalysis.Builder builderAnalisis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);

                Camera2Interop.Extender ext = new Camera2Interop.Extender(builderAnalisis);
                ext.setSessionCaptureCallback(new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        if (isBenchmarking) {
                            framesHardwareTotales++;
                        }
                    }
                });

                ImageAnalysis analizadorDeImagen = builderAnalisis.build();

                analizadorDeImagen.setAnalyzer(ejecutorCamara, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imagenProxy) {
                        if (isBenchmarking) {
                            if (esPrimerFrameDelBenchmark) { 
                                buffer.limpiarTodo();
                                Log.d("EDGE_BENCH","START_TEST|1 hilo stream - buffering");
                                framesHardwareTotales = 0;
                                framesEnviadosAlBuffer = 0;
                                framesProcesadosStream = 0;
                                framesCara = 0;
                                pool.iniciar();
                                esPrimerFrameDelBenchmark = false;
                            }
                            
                            int rotacion = imagenProxy.getImageInfo().getRotationDegrees();
                            buffer.agregarFrame(new FrameNativo(imagenProxy.toBitmap(), rotacion));
                            framesEnviadosAlBuffer++;
                            
                        } else if (!esPrimerFrameDelBenchmark) { // fin de 30s pero venimos de recibir frames
                            esPrimerFrameDelBenchmark = true;
                            // Aca arranca el vaciado
                            framesProcesadosStream = pool.framesProcesados.get();
                            long inicioVaciado = System.currentTimeMillis();
                            // Hilo para manejar el vaciado sin bloquear el analizador de imagen o el pool de hilos
                            new Thread(() -> vaciarBufferYResponder(inicioVaciado)).start();
                        }
                        imagenProxy.close();
                    }
                });

                CameraSelector selectorCamara = CameraSelector.DEFAULT_FRONT_CAMERA;
                proveedorCamara.unbindAll();
                proveedorCamara.bindToLifecycle(cicloDeVida, selectorCamara, preview, analizadorDeImagen);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(contexto));
    }

    private void vaciarBufferYResponder(long inicioVaciadoMillis) {
        try {
            // A medida que el pool vacia, este hilo duerme un tiempo para evitar un busy waiting 
            while (buffer.obtenerTamano() > 0) {
                Thread.sleep(100);              
            }
            // Tiempo extra para asegurar que el pool de hilos termine de procesar los ultimos frames
            Thread.sleep(500);

            pool.detener();
            long finVaciadoMillis = System.currentTimeMillis();

            // Calculamos metricas finales
            float tiempoVaciadoSegundos = (finVaciadoMillis - inicioVaciadoMillis) / 1000f;
            int procesadosTotal = pool.framesProcesados.get();
            int procesadosVaciado = procesadosTotal - framesProcesadosStream;
            int carasTotales = pool.framesCara.get();

            // Armamos el paquete para Dart
            Map<String, Object> stats = new HashMap<>();
            stats.put("hardwareTotales", framesHardwareTotales);
            stats.put("enviadosRAM", framesEnviadosAlBuffer);
            stats.put("procesadosTotal", procesadosTotal);
            stats.put("caras", carasTotales);
            stats.put("procesadosStream", framesProcesadosStream);
            stats.put("procesadosVaciado", procesadosVaciado);
            stats.put("tiempoVaciado", tiempoVaciadoSegundos);
            
            Log.d("EDGE_BENCH", "END_TEST|1 hilo stream - buffering|" + framesHardwareTotales + "|" + carasTotales);
            // Respondemos a Flutter en el hilo principal
            if (pendingResult != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    pendingResult.success(stats);
                    pendingResult = null; // Limpiamos
                });
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @NonNull @Override public View getView() { return contenedor; }

    @Override public void dispose() {
        isBenchmarking = false;
        if (ejecutorCamara != null) ejecutorCamara.shutdown();
        if (pool != null) pool.detener();
        if (proveedorCamara != null) proveedorCamara.unbindAll();
    }
}