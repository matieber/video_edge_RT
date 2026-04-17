package com.example.capture_upload_video;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import io.flutter.plugin.platform.PlatformView;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.Image;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;
import android.os.Trace;

import androidx.camera.camera2.interop.Camera2Interop;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

public class NativeCameraView implements PlatformView {
    private final FrameLayout container;
    private final PreviewView previewView;
    private ExecutorService cameraExecutor;

    public static boolean isBenchmarking = false;
    public static int framesProcesados = 0;
    public static int framesHardwareTotales = 0;
    
    private boolean isBusy = false;
    private FaceMeshDetector detector;
    private Context context;
    private LifecycleOwner lifecycleOwner; // Referencia directa a la actividad

    NativeCameraView(@NonNull Context context, int id, Map<String, Object> creationParams, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        
        container = new FrameLayout(context);
        previewView = new PreviewView(context);
        
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        // Forzamos a que ocupe todo el cuadrado de Flutter
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
                
        container.addView(previewView);

        cameraExecutor = Executors.newSingleThreadExecutor();
        FaceMeshDetectorOptions options = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH).build();
        detector = FaceMeshDetection.getClient(options);

        iniciarCamara();
    }

private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 1. Empezamos a armar el ImageAnalysis
                ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);

                // 2. Escuchamos los frames que salen del sensor de la camara (hardware) para contarlos y medir el FPS real de captura
                Camera2Interop.Extender ext = new Camera2Interop.Extender(analysisBuilder);
                ext.setSessionCaptureCallback(new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        // Esto se dispara fisicamente por cada foto que saca el sensor de la camara al momento de la captura
                        if (isBenchmarking) {
                            framesHardwareTotales++; 
                        }
                    }
                });

                // 3. Construimos el ImageAnalysis
                ImageAnalysis imageAnalysis = analysisBuilder.build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @OptIn(markerClass = ExperimentalGetImage.class)
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (!isBenchmarking) {          
                            imageProxy.close();
                            return;
                        }

                        if (isBusy) {
                            imageProxy.close();
                            return;
                        }
                        
                        isBusy = true;
                        Image mediaImage = imageProxy.getImage(); 
                        if (mediaImage != null) {
                            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                            Trace.beginAsyncSection("MLKit_Inferencia_FaceMesh", framesHardwareTotales);
                            detector.process(image)
                                    .addOnSuccessListener(faces -> {
                                        if (faces != null && !faces.isEmpty()) {
                                            framesProcesados++;
                                        }
                                    })
                                    .addOnCompleteListener(task -> {
                                        isBusy = false;
                                        imageProxy.close();
                                        Trace.endAsyncSection("MLKit_Inferencia_FaceMesh", framesHardwareTotales);
                                    });
                        } else {
                            isBusy = false;
                            imageProxy.close();
                        }
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("EDGE_RT", "Error al iniciar la camara nativa", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @NonNull @Override public View getView() { return container; }
    
    @Override public void dispose() {
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (detector != null) detector.close();
    }
}