#!/bin/bash
echo "Trayendo resultados del celular..."
adb pull /storage/emulated/0/Download/dataset/resultados_live.csv ./resultados_live.csv
echo "Listo. Archivo actualizado."