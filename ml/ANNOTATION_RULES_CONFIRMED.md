# Reglas confirmadas para etiquetar costales CONSEDE

Estas reglas se aplican al dataset de detección YOLO de una sola clase: `costal`.

## Criterio visual
- Identificar cada costal por su forma, volumen, dimensiones y apariencia física.
- No depender de logotipos, texto (por ejemplo, Pantaleon), franjas ni colores impresos: pueden faltar, cambiar o no ser visibles.
- El fondo, la banda transportadora, rodillos, pallets y maquinaria no son objetos de la clase.

## Cajas delimitadoras
- Dibujar una caja por cada costal que pueda distinguirse como una unidad.
- Ajustar la caja al contorno visible del costal, sin incluir grandes áreas del transportador o del fondo.
- No duplicar un mismo costal ni usar una caja conjunta para varios costales separados.
- Cuando dos costales se tocan o se superponen, mantener una caja por instancia; se permite que las cajas se solapen si los objetos realmente se solapan.
- Para costales parcialmente ocultos, anotar sólo cuando su identidad y extensión visible permitan delimitar una instancia razonablemente. No inventar los límites ocultos.
- Si no es posible separar con confianza dos costales apilados, no crear cajas arbitrarias para esa región.

## Formato
- Formato YOLO: `class_id x_center y_center width height`, coordenadas normalizadas entre 0 y 1.
- Única clase: `0 costal`.
- Cada imagen debe tener su archivo .txt correspondiente, incluso si no contiene instancias (archivo vacío).
- Las capturas de pantalla con rectángulos rojos aportadas como ejemplo sirven como referencia de criterio; no deben incorporarse como imágenes de entrenamiento porque los rectángulos y la interfaz contaminarían la apariencia visual que aprenderá el modelo.

## Control de calidad
- Revisar visualmente cada imagen junto con sus cajas antes de incluirla en train o val.
- Separar entrenamiento y validación por video/secuencia para evitar fotogramas casi idénticos en ambos conjuntos.
- No iniciar entrenamiento hasta que las imágenes y etiquetas reales superen la validación del dataset.
