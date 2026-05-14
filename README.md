# Instrucciones

### Java
Todos los archivos se encuentran en android/app/src/main/java/com
* Desde NativeCameraView.java en el constructor de la clase se puede cambiar la cantidad de hilos. 

### Perfetto
Si se quiere utilizar perfetto se debe ejecutar desde perfetto y se debe tener la app en el celular.
1. Abrir perfetto - Trace Viewer
2. Configurar que se quiere analizar entrando en Record New Trace.
3. Conectar el celular via USB, deberia aparecer en el celular dos carteles de permisos.
4. Comenzar a grabar la traza ( configurar mucho tiempoa asi no se corta a mitad de uso )
5. Usar la app
6. Cortar la traza
7. Perfetto abrira la traza grabada


### Consola
1. flutter run --profile | grep -v "updateAcquireFence"
* Esta ejeucion permite evitar un mensaje repetitivo por consola y poder ver comentarios por consola que esten en el codigo. 

