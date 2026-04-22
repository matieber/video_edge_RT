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

import io.flutter.plugin.platform.PlatformView;

public class NativeCameraView implements PlatformView {

    // --- VARIABLES ESTATICAS PARA MAINACTIVITY ---
    public static boolean isBenchmarking = false;
    public static int framesHardwareTotales = 0;
    public static int framesEnviadosAlBuffer = 0; // La verdad absoluta del productor
    public static int framesProcesados = 0;
    public static int framesCara = 0;
    // ---------------------------------------------

    private final FrameLayout contenedor;
    private final PreviewView vistaPrevia;
    private final Context contexto;
    private final LifecycleOwner cicloDeVida;

    private ExecutorService ejecutorCamara;
    private ProcessCameraProvider proveedorCamara;

    private final BufferDeFrames buffer;
    private final PoolDeDetectores pool;

    private volatile boolean faseDeVaciadoIniciada = true;

    public NativeCameraView(@NonNull Context contexto, int id, Map<String, Object> params, LifecycleOwner cicloDeVida) {
        this.contexto = contexto;
        this.cicloDeVida = cicloDeVida;

        contenedor = new FrameLayout(contexto);
        vistaPrevia = new PreviewView(contexto);
        vistaPrevia.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        contenedor.addView(vistaPrevia);

        buffer = new BufferDeFrames();
        pool = new PoolDeDetectores(1, buffer); 

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
                            if (faseDeVaciadoIniciada) {
                                buffer.limpiarTodo();
                                framesHardwareTotales = 0;
                                framesEnviadosAlBuffer = 0;
                                framesProcesados = 0;
                                framesCara = 0;
                                pool.iniciar();
                                faseDeVaciadoIniciada = false;
                                Log.i("EDGE_RT", "====== INICIANDO CAPTURA (30s) ======");
                            }
                            int rotacion = imagenProxy.getImageInfo().getRotationDegrees();
                            Bitmap frame = imagenProxy.toBitmap();
                            buffer.agregarFrame(new FrameNativo(frame, rotacion));
                            framesEnviadosAlBuffer++;

                            // Log ligero para ver que la camara sigue viva (cada 50 frames)
                            if (framesEnviadosAlBuffer % 50 == 0) {
                                Log.d("EDGE_RT", "Productor: Capturados y en buffer " + framesEnviadosAlBuffer + " frames...");
                            }
                            
                        } else if (!faseDeVaciadoIniciada) {
                            faseDeVaciadoIniciada = true;
                            Log.i("EDGE_RT", "====== CAPTURA TERMINADA. INICIANDO VACIADO DE RAM ======");
                            new Thread(() -> vaciarBufferYActualizarStats()).start();
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

    private void vaciarBufferYActualizarStats() {
        try {
            while (buffer.obtenerTamano() > 0) {
                Thread.sleep(100); 
            }
            Thread.sleep(500); 

            pool.detener();

            framesProcesados = pool.framesProcesados.get();
            framesCara = pool.framesCara.get();

            Log.i("EDGE_RT", "====== VACIADO COMPLETADO ======");
            Log.i("EDGE_RT", "Disparos de hardware: " + framesHardwareTotales);
            Log.i("EDGE_RT", "Frames guardados en RAM: " + framesEnviadosAlBuffer);
            Log.i("EDGE_RT", "Frames procesados por ML Kit: " + framesProcesados);
            Log.i("EDGE_RT", "Rostros detectados: " + framesCara);

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