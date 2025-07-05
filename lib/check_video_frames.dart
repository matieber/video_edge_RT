import 'dart:collection';

// Retorna una ventana deslizante (de tamaño n) basada en los datos de seq
Iterable<List<T>> rollingWindow<T>(List<T> seq, int n) sync* {
  Iterator<T> it = seq.iterator;
  Queue<T> result = Queue();

  while (result.length < n && it.moveNext()) {
    result.add(it.current);
  }

  if (result.length == n) {
    yield List.from(result);
  }

  while (it.moveNext()) {
    result.removeFirst();
    result.add(it.current);
    yield List.from(result);
  }
}

// Cuenta el porcentaje de elementos 'true' en la secuencia
double countGoodFramesPercent(List<bool> seq) {
  int count = seq.where((item) => item).length;
  return count / seq.length;
}

// Verifica si la ventana contiene un máximo de elementos 'false' consecutivos permitidos
bool checkConsecutiveFrames(List<bool> w, int consecutiveSubsetMaxSize) {
  for (var subset in rollingWindow(w, consecutiveSubsetMaxSize + 1)) {
    int count = subset.where((item) => !item).length;
    if (count == consecutiveSubsetMaxSize + 1) {
      return false;
    }
  }
  return true;
}

// Retorna el total de ventanas generadas y el total de ventanas correctas según el criterio establecido
Map<String, int> analizeStream(List<bool> seq,
    {int slidingSize = 5, double minGoodFramesPercent = 0.6, int consecutiveSubsetMaxSize = 1}) {
  int total = 1 + seq.length - slidingSize;
  int goodOnes = 0;

  for (var w in rollingWindow(seq, slidingSize)) {
    if (countGoodFramesPercent(w) < minGoodFramesPercent) {
      continue;
    }
    if (!checkConsecutiveFrames(w, consecutiveSubsetMaxSize)) {
      continue;
    }
    goodOnes++;
  }

  return {'total': total, 'goodOnes': goodOnes};
}

// Retorna las subsecuencias a partir de seq de tamaño n con un solapamiento especificado
Iterable<List<T>> rollingWindowV2<T>(List<T> seq, int n, int overlap) sync* {
  if (n <= overlap) {
    throw ArgumentError("Wrong arguments");
  }
  int skipped = n - overlap - 1;
  bool first = true;

  for (var w in rollingWindow(seq, n)) {
    if (first) {
      first = false;
      yield w;
      continue;
    }
    if (skipped == 0) {
      skipped = n - overlap - 1;
      yield w;
      continue;
    }
    skipped--;
  }
}

// Analiza la secuencia con soporte para el parámetro de solapamiento
Map<String, int> analizeStreamV2(List<bool> seq,
    {int slidingSize = 5, double minGoodFramesPercent = 0.6, int consecutiveSubsetMaxSize = 1, int overlap = 3}) {
  int total = 0;
  int goodOnes = 0;

  for (var w in rollingWindowV2(seq, slidingSize, overlap)) {
    total++;
    if (countGoodFramesPercent(w) < minGoodFramesPercent) {
      continue;
    }
    if (!checkConsecutiveFrames(w, consecutiveSubsetMaxSize)) {
      continue;
    }
    goodOnes++;
  }

  return {'total': total, 'goodOnes': goodOnes};
}

void main() {
  List<bool> seq = [false, true, true, true, false, false, true, true, false, false];
  print(analizeStreamV2(seq));
  seq = [false, true, false, true, false, true, false, true, false, true];
  print(analizeStreamV2(seq));
}

