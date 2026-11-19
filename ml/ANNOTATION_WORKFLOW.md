# Flujo de anotación visual CONSEDE

## Objetivo
Crear polígonos YOLO Segmentation para una única clase: `costal` (clase 0), usando los 114 fotogramas del paquete de anotación.

## Etiquetado con el editor CONSEDE
1. Abrir el archivo HTML del etiquetador de contornos en Chrome.
2. Recorrer los 114 fotogramas.
3. Tocar sobre el borde del costal para agregar vértices y cerrar el polígono.
4. Dibujar un polígono por cada costal individualmente distinguible.
5. Seguir sólo el contorno visible; no completar partes ocultas por imaginación.
6. Marcar para revisión los fotogramas donde no se pueda separar con confianza cada costal.
7. No etiquetar rodillos, bandas, pallets, personas, maquinaria ni fondo.
8. Exportar las etiquetas YOLO Segmentation y conservar también el respaldo JSON.

## División de datos
La propuesta está en `ANNOTATION_MANIFEST.csv`: WA0161 y WA0167 se reservan para validación; los otros ocho videos para entrenamiento. La separación es por video completo para evitar fuga entre fotogramas de una misma secuencia.

## Control de calidad
- Cada imagen debe tener un TXT del mismo nombre; los TXT vacíos son válidos si no hay costales visibles.
- Cada línea debe contener clase 0 seguida de al menos tres pares x,y normalizados.
- Revisar manualmente contornos cortados, costales juntos, oclusiones y falsos objetos.
- Ejecutar `python ml/validate_dataset.py`.
- No iniciar entrenamiento hasta que la validación estructural pase y las máscaras hayan sido revisadas visualmente.

## Estado
La revisión visual de contacto permite reconocer escenarios y fotogramas difíciles, pero no sustituye la creación y revisión de los 114 polígonos. No considerar completo el dataset hasta que el editor exporte las etiquetas y el control de calidad se haya realizado.

## Importar la exportación
Después de exportar, extraer el ZIP y ejecutar desde la raíz del repositorio:

```bash
python ml/import_cvat_export.py \
  --manifest ml/ANNOTATION_MANIFEST.csv \
  --images RUTA/A/IMAGENES \
  --labels RUTA/A/ETIQUETAS \
  --output ml/dataset/costales
python ml/validate_dataset.py
```

El importador exige las 114 imágenes y sus 114 TXT, comprueba la clase y la geometría de los polígonos, y aplica la separación train/val del manifest. No sobrescribir anotaciones manuales sin conservar una copia.

## Evaluación posterior al entrenamiento
Cuando `ml/train.py` genere `runs/conseDe_costales_seg/weights/best.pt`, evaluar videos completos:

```bash
python ml/evaluate_videos.py \
  --weights runs/conseDe_costales_seg/weights/best.pt \
  --videos /ruta/video1.mp4 /ruta/video2.mp4 \
  --output runs/video_evaluation \
  --line 50 --conf 0.25
```

Revisar los videos anotados y comparar el conteo automático con un conteo manual de referencia. La ejecución del script por sí sola no demuestra precisión suficiente.
