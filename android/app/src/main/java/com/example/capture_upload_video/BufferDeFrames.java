package com.example.capture_upload_video;

import android.graphics.Bitmap;
import java.util.concurrent.LinkedBlockingQueue;

public class BufferDeFrames {
    
    // Cola concurrente. Al no poner limite, crecera hasta llenar la RAM
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

    // Metodo para el Consumidor (Modelo ML)
    // take() bloquea el hilo si no hay frames hasta que llegue uno nuevo
    public FrameNativo extraerFrame() throws InterruptedException {
        return colaDeFrames.take(); 
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