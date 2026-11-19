# Reglas confirmadas para segmentar costales CONSEDE

Estas reglas se aplican al dataset YOLO Segmentation de una sola clase: `costal`.

## Criterio visual
- Identificar cada costal por su forma, volumen, dimensiones y apariencia física.
- No depender de logotipos, texto (por ejemplo, Pantaleon), franjas ni colores impresos: pueden faltar, cambiar o no ser visibles.
- El fondo, la banda transportadora, rodillos, pallets y maquinaria no son objetos de la clase.

## Polígonos por instancia
- Dibujar un polígono independiente por cada costal que pueda distinguirse como una unidad.
- Ajustar el polígono al contorno físico visible, siguiendo pliegues generales y deformaciones sin sobreajustar arrugas pequeñas.
- No duplicar un mismo costal ni usar un polígono conjunto para varios costales separados.
- Cuando dos costales se tocan o se superponen, mantener una máscara por instancia si sus límites visibles permiten separarlos.
- Para costales parcialmente ocultos, anotar sólo la región visible; no inventar límites ocultos.
- Si no es posible separar con confianza dos costales apilados, marcar la imagen para revisión y no crear polígonos arbitrarios.

## Formato
- Formato Ultralytics YOLO Segmentation: `class_id x1 y1 x2 y2 ...`, coordenadas normalizadas.
- Única clase: `0 costal`.
- Cada polígono debe contener al menos tres pares de coordenadas.
- Cada imagen debe tener su archivo .txt correspondiente, incluso si está vacío.
- Las capturas de pantalla con rectángulos o interfaz no deben incorporarse como imágenes de entrenamiento.

## Control de calidad
- Revisar visualmente cada máscara sobre su imagen antes de incluirla en train o val.
- Separar entrenamiento y validación por video/secuencia.
- No iniciar entrenamiento hasta que las imágenes y polígonos reales superen la validación estructural y visual.
