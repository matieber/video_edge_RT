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
    
    private ExecutorService poolDeHilos;
    private final BufferDeFrames buffer;
    private List<FaceMeshDetector> detectores;
    private final int cantidadHilos;
    
    private volatile boolean estaCorriendo = false;
    
    public final AtomicInteger framesProcesados = new AtomicInteger(0);
    public final AtomicInteger framesCara = new AtomicInteger(0);

    public PoolDeDetectores(int cantidadHilos, BufferDeFrames buffer) {
        this.buffer = buffer;
        this.cantidadHilos = cantidadHilos;
    }

    public void iniciar() {
        estaCorriendo = true;
        framesProcesados.set(0);
        framesCara.set(0);
        
        // Se crea el pool y los detectores siempre al iniciar. 
        this.poolDeHilos = Executors.newFixedThreadPool(cantidadHilos);
        this.detectores = new ArrayList<>();

        FaceMeshDetectorOptions opciones = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH).build();

        for (int i = 0; i < cantidadHilos; i++) {
            FaceMeshDetector detector = FaceMeshDetection.getClient(opciones);
            detectores.add(detector);
            final int idHilo = i;
            // Imprimimos por consola numero de hilo 
            Log.d("PoolDetectores", "SOY EL HILO NUMERO  ---> " + idHilo);
            // Ponemos a los obreros a trabajar en segundo plano
            poolDeHilos.execute(() -> procesarCiclo(detector, idHilo));
        }
    }

    private void procesarCiclo(FaceMeshDetector detector, int idHilo) {
        while (estaCorriendo) {
            FrameNativo frameNativo = null;
            try {
                // Si el buffer esta vacio, el hilo duerme aca. No consume CPU.
                frameNativo = buffer.extraerFrame(); 
                InputImage imagen = InputImage.fromBitmap(frameNativo.bitmap, frameNativo.rotacion);

                List<FaceMesh> rostros = Tasks.await(detector.process(imagen));

                framesProcesados.incrementAndGet();

                if (rostros != null && !rostros.isEmpty()) {
                    framesCara.incrementAndGet();
                }

                if (framesProcesados.get() % 50 == 0) {
                    Log.d("EDGE_RT", "Consumidor: Procesados " + framesProcesados.get() + " frames...");
                }

            } catch (InterruptedException e) {
                // Esto salta limpio cuando llamamos a shutdownNow() desde el hilo supervisor
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e("PoolDetectores", "Error de inferencia en hilo " + idHilo, e);
            } finally {
                // Liberamos la memoria del bitmap una vez procesado para evitar OOM posterior a procesar el frame
                if (frameNativo != null && frameNativo.bitmap != null && !frameNativo.bitmap.isRecycled()) {
                        frameNativo.bitmap.recycle();
                }
            }
        }
    }

    public void detener() {
        estaCorriendo = false;
        if (poolDeHilos != null) {
            poolDeHilos.shutdownNow(); 
        }
        if (detectores != null) {
            for (FaceMeshDetector detector : detectores) {
                detector.close();
            }
        }
    }
}