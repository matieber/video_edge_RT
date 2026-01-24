class ResultadoBenchmark {
  final String nombreVideo;
  final String tecnologia; // "Isolates Dart" o "Hilos Java"
  final int tiempoExtraccion;
  final int tiempoML;
  final int tiempoTotal;
  final int cantidadFrames;

  ResultadoBenchmark({
    required this.nombreVideo,
    required this.tecnologia,
    required this.tiempoExtraccion,
    required this.tiempoML,
    required this.tiempoTotal,
    required this.cantidadFrames
  });

  // genera la linea de texto para el archivo csv
  String generarLineaCsv() {
    return '$nombreVideo,$tecnologia,$tiempoExtraccion,$tiempoML,$tiempoTotal,$cantidadFrames';
  }
}