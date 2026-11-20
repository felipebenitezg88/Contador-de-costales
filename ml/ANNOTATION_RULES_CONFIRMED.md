# Reglas confirmadas para segmentar costales CONSEDE

Estas reglas se aplican al dataset YOLO Segmentation de una sola clase: `costal`. **Sólo se cuentan costales que pasan por la banda transportadora.**

## Criterio de inclusión
- Etiquetar únicamente costales físicamente apoyados sobre la banda transportadora.
- No etiquetar costales apilados, almacenados o situados fuera de la banda, aunque aparezcan en el mismo fotograma.
- Detectar un costal sobre la banda no equivale a contarlo: el conteo ocurre una sola vez cuando su seguimiento cruza la línea virtual.
- La aplicación restringirá detección/conteo a una región de interés (ROI) de la banda, que deberá calibrarse con la vista real de la cámara.

## Criterio visual
- Identificar cada costal por su forma, volumen, dimensiones y apariencia física.
- No depender de logotipos, texto, franjas ni colores impresos.
- El fondo, la banda transportadora, rodillos, pallets y maquinaria no son objetos de la clase.

## Polígonos por instancia
- Dibujar un polígono independiente por cada costal sobre la banda que pueda distinguirse como una unidad.
- Ajustar el polígono al contorno físico visible, sin sobreajustar arrugas pequeñas.
- No duplicar un mismo costal ni usar un polígono conjunto para varios costales separados.
- Cuando dos costales sobre la banda se tocan o superponen, mantener una máscara por instancia sólo si sus límites visibles permiten separarlos.
- Para costales parcialmente ocultos, anotar sólo la región visible; no inventar límites ocultos.
- Si no es posible separar con confianza dos costales sobre la banda, marcar la imagen para revisión y no crear polígonos arbitrarios.
- Si no hay costales sobre la banda en un fotograma, el archivo de etiqueta debe estar vacío.

## Formato
- Formato Ultralytics YOLO Segmentation: `class_id x1 y1 x2 y2 ...`, coordenadas normalizadas.
- Única clase: `0 costal`.
- Cada polígono debe contener al menos tres pares de coordenadas.
- Cada imagen debe tener su archivo .txt correspondiente, incluso si está vacío.
- Las capturas de pantalla con rectángulos o interfaz no deben incorporarse como imágenes de entrenamiento.

## Control de calidad
- Revisar visualmente cada máscara sobre su imagen antes de incluirla en train o val.
- Confirmar que las máscaras de costales del fondo estén excluidas.
- Separar entrenamiento y validación por video/secuencia.
- No iniciar entrenamiento hasta que las imágenes y polígonos reales superen la validación estructural y visual.
