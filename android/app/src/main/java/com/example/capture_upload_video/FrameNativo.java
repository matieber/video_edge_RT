package com.example.capture_upload_video;

import android.graphics.Bitmap;

// Clase envoltorio para no perder la metadata de la camara
public class FrameNativo {
    public final Bitmap bitmap;
    public final int rotacion;

    public FrameNativo(Bitmap bitmap, int rotacion) {
        this.bitmap = bitmap;
        this.rotacion = rotacion;
    }
}