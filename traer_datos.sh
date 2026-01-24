#!/bin/bash
echo "Trayendo resultados del celular..."
adb pull /storage/emulated/0/Download/dataset/resultados_benchmark.csv ./resultados_benchmark.csv
echo "Listo. Archivo actualizado."