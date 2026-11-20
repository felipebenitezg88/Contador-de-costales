# Flujo de anotación visual CONSEDE

## Objetivo
Crear polígonos YOLO Segmentation para una única clase: `costal` (clase 0), usando los 114 fotogramas del paquete de anotación. **El objetivo de conteo es exclusivamente la banda transportadora.**

## Regla principal de inclusión
- Etiquetar únicamente los costales que estén físicamente sobre la banda transportadora.
- No etiquetar costales apilados, almacenados o situados fuera de la banda, aunque sean claramente visibles en el mismo fotograma.
- Si un costal está entrando o saliendo de la banda, etiquetarlo sólo cuando se encuentre apoyado en la superficie de transporte.
- La línea virtual de conteo y el seguimiento temporal determinarán cuándo sumar cada costal; detectarlo sobre la banda no significa sumarlo todavía.
- En la aplicación se limitará la detección a la región de interés (ROI) correspondiente a la banda. La ROI deberá ajustarse a la perspectiva y posición reales de la cámara; no asumir coordenadas hasta verificarlas en los fotogramas.

## Etiquetado con el editor CONSEDE
1. Abrir el editor de contornos en Chrome.
2. Recorrer los 114 fotogramas.
3. Dibujar un polígono independiente alrededor de cada costal que esté sobre la banda y pueda distinguirse.
4. Seguir sólo el contorno visible; no completar partes ocultas por imaginación.
5. No etiquetar costales del fondo, pilas fuera de la banda, rodillos, estructura de la banda, pallets, personas, maquinaria ni otros objetos.
6. Si un costal sobre la banda está parcialmente tapado, etiquetar sólo su parte visible.
7. Si dos costales sobre la banda se tocan, separarlos sólo cuando sus límites visibles permitan hacerlo con confianza; si no, marcar el fotograma para revisión.
8. Si no hay costales sobre la banda en un fotograma, crear el TXT correspondiente vacío.
9. Exportar las etiquetas YOLO Segmentation y conservar también el respaldo JSON.

## División de datos
La propuesta está en `ANNOTATION_MANIFEST.csv`: WA0161 y WA0167 se reservan para validación; los otros ocho videos para entrenamiento. La separación es por video completo para evitar fuga entre fotogramas de una misma secuencia.

## Control de calidad
- Cada imagen debe tener un TXT del mismo nombre; los TXT vacíos son válidos si no hay costales sobre la banda.
- Cada línea debe contener clase 0 seguida de al menos tres pares x,y normalizados.
- Confirmar especialmente que ninguna máscara incluya costales de las pilas del fondo.
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
Cuando `ml/train.py` genere `runs/conseDe_costales_seg/weights/best.pt`, evaluar videos completos. Revisar que las detecciones fuera de la ROI de la banda no se cuenten, que cada costal se cuente una sola vez al cruzar la línea virtual y comparar el resultado con un conteo manual de referencia. La ejecución del script por sí sola no demuestra precisión suficiente.
