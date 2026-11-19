# Guía de etiquetado de segmentación CONSEDE

## Material fuente
El paquete de anotación contiene 114 fotogramas extraídos de 10 videos. El etiquetador HTML de contornos permite dibujar polígonos directamente sobre cada imagen y exportar YOLO Segmentation.

## Etiquetado requerido
Usar una sola clase, exactamente `costal` (índice 0), en formato **Ultralytics YOLO Segmentation**.

- Dibujar un polígono independiente por cada costal identificable.
- Seguir el contorno físico visible; usar suficientes vértices para representar costales deformados y orientados de distintas formas.
- No basarse en franjas, colores, marcas ni texto impreso.
- Incluir costales lisos y estampados, deformados y parcialmente ocultos cuando su región visible sea distinguible.
- En oclusión, etiquetar únicamente la superficie visible; no inventar el contorno oculto.
- No etiquetar banda, rodillos, pallets, personas, maquinaria ni fondo.
- Si dos costales se tocan, separarlos en polígonos distintos sólo cuando el límite entre ambos sea identificable.
- Marcar para revisión cualquier fotograma ambiguo; no inventar polígonos.
- Un fotograma sin costales visibles debe tener un TXT vacío.

## División sin fuga de datos
Separar por video completo, no por fotogramas aleatorios. Ningún video fuente puede aparecer simultáneamente en train y val. El manifest reserva WA0161 y WA0167 para validación y los demás videos para entrenamiento.

## Formato YOLO Segmentation
Cada imagen debe tener un TXT correspondiente. Cada instancia se escribe en una línea:
`0 x1 y1 x2 y2 x3 y3 ...`
Las coordenadas son pares x,y normalizados entre 0 y 1. Cada polígono requiere al menos tres puntos. La clase única es 0.

## Comprobación y entrenamiento
Ejecutar `python ml/validate_dataset.py`. El validador comprueba los polígonos, la clase y la separación por video. Después, ejecutar `python ml/train.py` o lanzar el workflow manualmente. El entrenamiento usa YOLO11n-seg y genera métricas de cajas y máscaras, además de exportación TFLite.

No considerar listo el modelo hasta probarlo sobre videos completos no usados en entrenamiento, revisando máscaras, omisiones, falsos positivos, instancias duplicadas y costales muy juntos u ocultos.
