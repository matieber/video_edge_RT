package com.example.capture_upload_video;

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

// --- Usamos el paquete de antonkarpenko ---
import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.antonkarpenko.ffmpegkit.Session;

// Importaciones de ML Kit 
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.net.Uri;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Collections;
import java.util.Arrays;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.ExecutionException;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "video_preprocessor";

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler(
                        (call, result) -> {
                            if (call.method.equals("preProcessVideo")) {
                                String videoPath = call.argument("videoFilePath");
                                startVideoProcessing(videoPath);
                                result.success(null);
                            } else if (call.method.equals("setRotation")) {
                                result.success(null);
                            } else {
                                result.notImplemented();
                            }
                        }
                );
    }
    private boolean analizarFrame(FaceMeshDetector detector, String imagePath) {
        try {
            // Verificar si el archivo existe
            File file = new File(imagePath);
            if (!file.exists()) return false;

            // Crear InputImage desde archivo de imagen que vino como String y fue cambiado a tipo File
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(file));

            /*  La clave a modificar esta aca, esto lo hice asi para probar si anda FFmpegkit y ML Kit porque importarlos a java me trajo
                dolores de cabeza. Detector.Process devuelve una promesa de que en algun momento estara listo y con Task.await espera a que termine.
                Esto no esta bien aca deberia entrar la logica de correr varios hilos en simultanteo que se encargen de procesar una cierta
                cantidad de frames pero fue a modo de prueba para ver si andaban las importaciones de libererias.
            */
            List<FaceMesh> cara = Tasks.await(detector.process(image));

            // Retorna true si hay cara y si hay 1 sola cara. False en caso contrario.
            return cara != null && cara.size() == 1;

        } catch (Exception e) {
            System.out.println("JAVA: Error analizando frame: " + e.getMessage());
            return false;
        }
    }

    private List<String> extractFrames(String videoPath) {
        //Es el resultado que tenemos que devolver ya que la idea es poder devolver la lista con los frames.
        List<String> framePaths = new ArrayList<>();

        // 1. Inicilizar estrucutra contenedora
        // getCacheDir evita usando la cache de la app pedir permisos al celular de acceso a galeria
        File dir = new File(getCacheDir(), "frames");
        if (dir.exists()) {
            // Limpiar frames viejos
            for (File f : dir.listFiles()) f.delete();
        }
        dir.mkdirs(); // Crea un directorio

        /*
            Esto lo tuve que hacer copiando la logica de extractFrames de dart pero con ayuda de IA
            La idea es preparar el comando para que FFmpegKit cambie el video por frames
        * */
        String filePattern = new File(dir, "img-%04d.bmp").getAbsolutePath();

        // 2. Comando FFmpeg
        String command = "-i " + videoPath + " -f image2 " + filePattern;

        System.out.println("JAVA: Extrayendo frames");
        Session session = FFmpegKit.execute(command);
        //es de tipo Session para poder obtener el codigo de retorno y saber si hubo error
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            System.out.println("JAVA: Extracción exitosa.");
            // Listar los archivos generados y ordenarlos
            File[] files = dir.listFiles((directory, name) -> name.endsWith(".bmp"));
            if (files != null) {
                // Ordenar por nombre para asegurar secuencia (img-0001, img-0002)
                java.util.Arrays.sort(files);
                for (File f : files) {
                    framePaths.add(f.getAbsolutePath());
                }
            }
        } else {
            System.out.println("JAVA: Error en FFmpeg: " + session.getAllLogsAsString());
        }

        return framePaths;
    }

    private List<Boolean> procesarFramesEnParalelo(List<String> todosLosFrames) throws InterruptedException, ExecutionException {
        // Definir cuántos hilos usar.
        // Empezamos con el número de núcleos de procesador disponibles en el dispositivo.
        int nucleos = Runtime.getRuntime().availableProcessors();
        int cantidadHilos = nucleos;

        // ExecutorService: Objeto nativo de Java que gestiona un pool de hilos para ejecutar tareas en paralelo.
        ExecutorService servicioEjecutor = Executors.newFixedThreadPool(cantidadHilos);
        List<Callable<List<Boolean>>> tareas = new ArrayList<>();

        int cantidadTotalFrames = todosLosFrames.size();

        // Calculamos el tamano del lote de frames para cada hilo.
        // Usamos Math.ceil para asegurar que si la división no es exacta, se redondee hacia arriba.
        int tamanoLote = (int) Math.ceil((double) cantidadTotalFrames / cantidadHilos);

        System.out.println("JAVA: Usando " + cantidadHilos + " hilos para " + cantidadTotalFrames + " frames. Tamano aprox. por lote: " + tamanoLote);

        // Dividir la lista de frames en lotes para asignar a cada hilo
        for (int i = 0; i < cantidadHilos; i++) {
            int inicio = i * tamanoLote;
            int fin = Math.min(inicio + tamanoLote, cantidadTotalFrames);

            if (inicio >= fin) break; // Si ya no hay mas frames para repartir, salimos del bucle.

            List<String> subListaFrames = todosLosFrames.subList(inicio, fin);

            // Crear la tarea (Callable) para este hilo específico
            tareas.add(() -> {
                // --- CÓDIGO QUE SE EJECUTA EN PARALELO (DENTRO DEL HILO SECUNDARIO) ---

                // 1. Instanciar un detector NUEVO e INDEPENDIENTE para este hilo.
                // ML Kit no es thread-safe si compartimos la misma instancia entre hilos.
                FaceMeshDetectorOptions opciones = new FaceMeshDetectorOptions.Builder()
                        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                        .build();
                FaceMeshDetector detectorLocal = FaceMeshDetection.getClient(opciones);

                List<Boolean> resultadosDelLote = new ArrayList<>();

                // 2. Procesar el lote de frames asignado a este hilo
                for (String rutaFrame : subListaFrames) {
                    // Llamamos a tu método existente analizarFrame pasando el detector local
                    boolean resultado = analizarFrame(detectorLocal, rutaFrame);
                    resultadosDelLote.add(resultado);
                }

                // 3. Cerrar el detector de este hilo para liberar memoria nativa
                detectorLocal.close();

                return resultadosDelLote;
                // -----------------------------------------------------------------------
            });
        }

        // "invokeAll" ejecuta todas las tareas en la lista y bloquea el hilo principal (que es secundario) hasta que TODAS terminen.
        List<Future<List<Boolean>>> futuros = servicioEjecutor.invokeAll(tareas);

        List<Boolean> resultadosCombinados = new ArrayList<>();

        // Recolectar y combinar los resultados de cada hilo
        for (Future<List<Boolean>> futuro : futuros) {
            // futuro.get() obtiene el resultado del hilo.
            // Aunque invokeAll ya esperó, llamar a get() es necesario para extraer el valor (la lista de booleanos).
            resultadosCombinados.addAll(futuro.get());
        }

        servicioEjecutor.shutdown(); // apagar el servicio para liberar el pool de hilos y recursos.

        return resultadosCombinados;
    }

// Metodo que distribuye las tareas del preprocesamiento en un hilo nuevo para no bloquear el hilo principal.
    private void startVideoProcessing(String videoPath) {
        new Thread(() -> {
            long timeProcessTotal = System.currentTimeMillis();

            // 1. Extraer Frames
            long timeExtract = System.currentTimeMillis();
            List<String> frames = extractFrames(videoPath);
            System.out.println("JAVA: Cantidad de Frames: " + frames.size());

            if (frames.isEmpty()) {
                System.out.println("JAVA: No se extrajeron frames.");
                return;
            }
            long endTimeExtract = System.currentTimeMillis();

            // 2. Procesamiento Paralelo
            long timeProcess = System.currentTimeMillis();
            List<Boolean> resultadosFinales = new ArrayList<>();

            try {
                resultadosFinales = procesarFramesEnParalelo(frames);
            } catch (InterruptedException | ExecutionException e) {
                System.out.println("JAVA: Error en procesamiento paralelo: " + e.getMessage());
                e.printStackTrace();
            }

            long endTimeProcess = System.currentTimeMillis();
            System.out.println("-----> Extract time JAVA                     ------>" + (endTimeExtract - timeExtract) + "ms");
            System.out.println("-----> ML time JAVA                          ------>" + (endTimeProcess - timeProcess) + "ms");

            long endTimeProcessTotal = System.currentTimeMillis();
            System.out.println("-----> TIEMPO TOTAL PREPROCESSING VIDEO JAVA ------>" + (endTimeProcessTotal - timeProcessTotal) + "ms");

        }).start();
    }
}