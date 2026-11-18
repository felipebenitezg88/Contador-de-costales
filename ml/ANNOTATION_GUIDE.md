# Guía de etiquetado del detector CONSEDE

## Material fuente
Los 10 videos originales están en `ml/dataset/costales/videos/raw/`. La extracción automática produjo un artefacto de 114 fotogramas para revisión, llamado `costales-fotogramas-para-etiquetar`, en GitHub Actions.

## Etiquetado requerido
Usar CVAT, Label Studio u otra herramienta compatible con YOLO Detection. Crear una sola clase, exactamente `costal` (índice 0). Exportar en formato Ultralytics YOLO.

- Una caja por costal identificable.
- No basarse en franjas, colores, marcas ni texto impreso.
- Incluir sacos lisos y estampados, juntos, deformados y parcialmente ocultos.
- En oclusión, encerrar sólo la parte visible del saco.
- No etiquetar banda, rodillos, pallets, personas ni maquinaria.
- Si dos sacos se tocan, separarlos en cajas distintas cuando sus límites sean distinguibles.
- No inventar cajas para objetos ambiguos.

## División sin fuga de datos
Separar por video completo, no por fotogramas aleatorios. Ningún video fuente puede aparecer simultáneamente en train y val. Distribuir videos distintos entre ambos conjuntos y conservar escenas difíciles para validación.

## Estructura de entrega
Copiar imágenes y etiquetas con el mismo nombre base:
```
ml/dataset/costales/
  images/train/  labels/train/
  images/val/    labels/val/
```
Cada imagen debe tener un .txt correspondiente. Cada línea debe ser `0 x_centro y_centro ancho alto`, con coordenadas normalizadas entre 0 y 1.

## Comprobación y entrenamiento
Ejecutar `python ml/validate_dataset.py`. El validador exige al menos 20 imágenes y 50 cajas en train, 5 imágenes y 10 cajas en val, etiquetas válidas y separación por video. Después, ejecutar `python ml/train.py` o lanzar el workflow manualmente. El workflow guarda pesos, métricas y exportación TFLite como artefacto.

No considerar listo el modelo hasta probarlo sobre videos completos no usados en entrenamiento, revisando omisiones, falsos positivos, cajas duplicadas y costales muy juntos/ocultos.
