package com.example.capture_upload_video;

import io.flutter.embedding.android.FlutterActivity;

public class MainActivity extends FlutterActivity {
}

/*

package com.example.capture_upload_video;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    //Definicion del canal por parte de java.
    private static final String CHANNEL = "video_preprocessor";

    //Metodo que inicia cuando se inicializa el motor de flutter, sacado de la documentacion de Flutter.
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        //Clase que establece conexion entre Flutter y Android.
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler(                                      //Metodo para definir que va a hacer android cuando flutter le mande un mensaje
                        (call, result) -> {                 // call -> nombre del metodo que flutter pidio ejecutar, result srive para devolver una respuesta.
                            // this method is invoked on the main thread.
                            // TODO

                            //IF/ELSE para diferenciar cuando se llama a setRotation o preProcessVideo y no tener que crear dos canales. 
                            if (call.method.equals("setRotation")) {
                                // --- SET ROTATION
                                try{
                                    //Llama al metodo que realiza el calculo de la rotacion y envia el resultado por el canal.
                                    int rotation = getRotationCompensation();
                                    result.success(rotation);
                                } catch (CameraAccessException e){
                                    Log.e("MainActivity", "Error al acceder a la cámara", e);
                                    result.error("CAMERA_ERROR", "No se pudo acceder a la cámara.", e.getMessage());
                                }

                            } else if (call.method.equals("preProcessVideo")) {
                                // -- PRE PROCES VIDEO
                                
                                
                            }
                        }
                );
    }

}

/* 
    Realizamos el metodo setRotation implemntado en dart pero en java
    1. Metodo es privado y en lugar de async/wait utilizamos throws para manejar errores en operacion que pueden fallar ya que hay que acceder al hardware
    No es necesario hacer un metodo asincrono porque Java es lenguaje nativo de Android, en dart era necesario porque dart trabaja en una capa superior de abstraccion
    por lo que es necesario esperar a obtener los datos del hardware de la camara.

    2. CameraManager es el sistema de android en java para manejar camaras. 

    3. Hay que seleccionar la camara frontal por lo que iteramos los ids de camaras. 

    4. Obtenemos la orientacion del sensor. 

    5. Se obtiene la rotacion del dispositivo meidante windowManager del sistema, devuelve un valor que es una constane y luego con switch se busca convertirla 
    a grados logrando el mismo resultado que el mapa en dart

    6. Calculo de la compensacion 
 */

/*
    //1. 
private int getRotationCompensation() throws CameraAccessException {
    //2. 
    CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMARA_SERVICE);
    //3.
    String frontCameraId = null;
    for (String cameraId : cameraManager.getCameraIdList()) {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontCameraId = cameraId;
            break;
        }
    }

    if (frontCameraId == null) {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "No se encontró la cámara frontal.");
        }

    //4. 
    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(frontCameraId);
    int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

    //5.
    int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int rotationDegrees = 0;
        switch (deviceRotation) {
            case Surface.ROTATION_0:   rotationDegrees = 0;   break;
            case Surface.ROTATION_90:  rotationDegrees = 90;  break;
            case Surface.ROTATION_180: rotationDegrees = 180; break;
            case Surface.ROTATION_270: rotationDegrees = 270; break;
        }
    
    //6. 
    int rotationCompensation = (sensorOrientation + rotationDegrees) % 360;

    //7. 
    return rotationCompensation;
}
*/