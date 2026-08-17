package com.example.capture_upload_video;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class BufferDeFrames {
    
    // Cola concurrente. Al no poner limite crecera hasta llenar la RAM
    private final LinkedBlockingQueue<FrameNativo> colaDeFrames;

    public BufferDeFrames() {
        this.colaDeFrames = new LinkedBlockingQueue<>();
    }

    // Metodo para el Productor (Camara)
    // offer() inserta al final de la cola instantaneamente
    public void agregarFrame(FrameNativo frame) {
        if (frame != null) {
            colaDeFrames.offer(frame);
        }
    }

    // Nuevo metodo para el Consumidor: Extrae por lotes
    public List<FrameNativo> extraerLote(int tamanoLote) throws InterruptedException {
        List<FrameNativo> lote = new ArrayList<>();
        
        // take() bloquea el hilo si la cola esta vacia. Garantiza que el lote tenga al menos 1 frame.
        lote.add(colaDeFrames.take());
        
        // drainTo() saca hasta (tamanoLote - 1) elementos adicionales de golpe sin bloquear si no hay mas
        colaDeFrames.drainTo(lote, tamanoLote - 1);
        
        return lote;
    }

    // Para saber cuantos frames hay atascados esperando
    public int obtenerTamano() {
        return colaDeFrames.size();
    }

    // Para limpiar memoria si cancelamos el proceso de golpe
    public void limpiarTodo() {
        FrameNativo frameNativo;
        while ((frameNativo = colaDeFrames.poll()) != null) {
            if (!frameNativo.bitmap.isRecycled()) {
                frameNativo.bitmap.recycle();
            }
        }
    }
}