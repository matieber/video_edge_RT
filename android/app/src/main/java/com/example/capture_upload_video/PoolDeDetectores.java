package com.example.capture_upload_video;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolDeDetectores {
    
    private final ExecutorService poolDeHilos;
    private final BufferDeFrames buffer;
    private final List<FaceMeshDetector> detectores;
    
    private volatile boolean estaCorriendo = false;
    
    // Contadores separados
    public final AtomicInteger framesProcesados = new AtomicInteger(0);
    public final AtomicInteger framesCara = new AtomicInteger(0);

    public PoolDeDetectores(int cantidadHilos, BufferDeFrames buffer) {
        this.buffer = buffer;
        this.poolDeHilos = Executors.newFixedThreadPool(cantidadHilos);
        this.detectores = new ArrayList<>();

        FaceMeshDetectorOptions opciones = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH).build();

        for (int i = 0; i < cantidadHilos; i++) {
            detectores.add(FaceMeshDetection.getClient(opciones));
        }
    }

    public void iniciar() {
        estaCorriendo = true;
        // Reseteamos por si es la segunda vez que se corre
        framesProcesados.set(0);
        framesCara.set(0);
        
        for (int i = 0; i < detectores.size(); i++) {
            final FaceMeshDetector detector = detectores.get(i);
            final int idHilo = i;
            poolDeHilos.execute(() -> procesarCiclo(detector, idHilo));
        }
    }

    private void procesarCiclo(FaceMeshDetector detector, int idHilo) {
        while (estaCorriendo) {
            FrameNativo frameNativo = null;
            try {
                frameNativo = buffer.extraerFrame(); 
                InputImage imagen = InputImage.fromBitmap(frameNativo.bitmap, frameNativo.rotacion);

                // Aca capturamos el resultado real de la inferencia
                List<FaceMesh> rostros = Tasks.await(detector.process(imagen));

                // Si no lanzo excepcion, el frame fue procesado exitosamente
                framesProcesados.incrementAndGet();

                // Verificamos si efectivamente encontro una cara
                if (rostros != null && !rostros.isEmpty()) {
                    framesCara.incrementAndGet();
                }

                // Log ligero para ver actividad en el consumidor (cada 50 procesados)
                if (framesProcesados.get() % 50 == 0) {
                    Log.d("EDGE_RT", "Consumidor: Procesados " + framesProcesados.get() + " frames...");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e("PoolDetectores", "Error de inferencia en hilo " + idHilo, e);
            } finally {
                if (frameNativo != null && frameNativo.bitmap != null && !frameNativo.bitmap.isRecycled()) {
                        frameNativo.bitmap.recycle();
                    }
            }
        }
    }

    public void detener() {
        estaCorriendo = false;
        poolDeHilos.shutdownNow(); 
        for (FaceMeshDetector detector : detectores) {
            detector.close();
        }
    }
}