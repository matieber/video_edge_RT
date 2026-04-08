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

public class NativeCameraView implements PlatformView {
    private final FrameLayout container;
    private final PreviewView previewView;
    private ExecutorService cameraExecutor;

    public static boolean isBenchmarking = false;
    public static int framesCapturados = 0;
    public static int framesProcesados = 0;
    
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

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @OptIn(markerClass = ExperimentalGetImage.class)
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (!isBenchmarking) {          // Hasta no tocar el boton rojo, descarta todos los frames
                            imageProxy.close();
                            return;
                        }

                        framesCapturados++;

                        if (isBusy) {
                            imageProxy.close();
                            return;
                        }
                        
                        isBusy = true;
                        // Extraemos la imagen en formato MediaImage, que es el que ML Kit necesita
                        Image mediaImage = imageProxy.getImage(); 
                        
                        if (mediaImage != null) {
                            // Ml Kit necesita los pixeles crudos y los metadatos de rotacion por eso llevamos la MediaImage a InputImage
                            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                            Trace.beginAsyncSection("MLKit_Inferencia_FaceMesh", framesCapturados);
                            detector.process(image)
                                    .addOnSuccessListener(faces -> {
                                        if (!faces.isEmpty()) framesProcesados++;
                                    })
                                    .addOnCompleteListener(task -> {
                                        isBusy = false;
                                        imageProxy.close();
                                        Trace.endAsyncSection("MLKit_Inferencia_FaceMesh", framesCapturados);
                                    });
                        } else {
                            isBusy = false;
                            imageProxy.close();
                        }
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                
                // Usamos la autoridad maxima del MainActivity
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