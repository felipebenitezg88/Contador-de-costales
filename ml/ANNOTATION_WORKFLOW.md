# Flujo de anotación visual CONSEDE

## Objetivo
Crear etiquetas YOLO Detection para una única clase: `costal` (clase 0), usando los 114 fotogramas originales del paquete de anotación.

## Importar a CVAT
1. Crear un proyecto o tarea de tipo **Object Detection**.
2. Cargar las 114 imágenes del ZIP de preparación.
3. Crear una etiqueta llamada exactamente `costal`.
4. Dibujar un rectángulo por cada costal identificable en cada imagen.
5. Ajustar la caja al área visible del costal; si está parcialmente oculto, no inventar la parte tapada.
6. No etiquetar rodillos, bandas, pallets, personas, maquinaria ni fondo.
7. No usar franjas, marcas o texto como criterio para decidir si es costal.
8. Exportar como **Ultralytics YOLO Detection**.

## División de datos
La propuesta está en `ANNOTATION_MANIFEST.csv`: WA0161 y WA0167 se reservan para validación; los otros ocho vídeos para entrenamiento. La separación es por vídeo completo para evitar fuga entre fotogramas de una misma secuencia.

Al exportar, colocar imágenes y archivos TXT en:
- `ml/dataset/costales/images/train/`
- `ml/dataset/costales/labels/train/`
- `ml/dataset/costales/images/val/`
- `ml/dataset/costales/labels/val/`

## Control de calidad
- Cada imagen debe tener un TXT del mismo nombre; los TXT pueden estar vacíos si no hay costales identificables.
- Cada línea YOLO debe tener cinco valores: `0 x_centro y_centro ancho alto`, normalizados entre 0 y 1.
- Revisar manualmente cajas cortadas, costales juntos, solapamientos y falsos objetos.
- Ejecutar `python ml/validate_dataset.py`.
- No iniciar entrenamiento hasta que la validación estructural pase y las cajas hayan sido revisadas visualmente.

## Estado
Este flujo no contiene etiquetas inventadas. El manifest registra las 114 imágenes como pendientes hasta que se anoten y revisen.


## Importar la exportación de CVAT sin mezclar vídeos
El repositorio incluye `ml/import_cvat_export.py`. Después de extraer el ZIP de CVAT, ubica las carpetas que contienen las imágenes y los TXT YOLO (pueden estar dentro de subcarpetas) y ejecuta desde la raíz del repositorio:

```bash
python ml/import_cvat_export.py \
  --manifest ml/ANNOTATION_MANIFEST.csv \
  --images RUTA/A/IMAGENES \
  --labels RUTA/A/ETIQUETAS \
  --output ml/dataset/costales
python ml/validate_dataset.py
```

El importador exige que estén las 114 imágenes y sus 114 TXT correspondientes (un TXT vacío es válido cuando una imagen no contiene costales identificables), comprueba que sólo exista la clase 0 y aplica automáticamente la separación train/val del manifest por vídeo. Si falta una etiqueta o hay una caja inválida, se detiene y reporta el problema. No sobrescribas anotaciones manuales sin conservar una copia de la exportación original.
