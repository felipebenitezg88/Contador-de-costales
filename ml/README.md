# Detector CONSEDE: preparación y entrenamiento

Este directorio contiene la preparación del detector de **una sola clase: `costal`**. El modelo debe aprender forma, volumen, textura y dimensiones; no debe depender de franjas, colores, logotipos ni texto impreso.

## Estado verificado

- [x] Definir una clase única: `costal`.
- [x] Incorporar los 10 videos fuente al repositorio.
- [x] Extraer 114 fotogramas de revisión (artefacto de GitHub Actions).
- [x] Definir reglas de etiquetado y separación por video.
- [x] Añadir validador de etiquetas YOLO.
- [x] Preparar entrenamiento, evaluación y exportación TFLite automatizados.
- [ ] Etiquetar manualmente los costales en imágenes.
- [ ] Ejecutar entrenamiento con datos etiquetados.
- [ ] Revisar métricas y falsos positivos/omisiones en videos completos.
- [ ] Integrar y probar el modelo en Android.

**Todavía no existe un modelo entrenado.** El entrenamiento se detendrá deliberadamente hasta que existan suficientes imágenes y etiquetas verificadas. No se deben generar etiquetas automáticas sin revisión visual ni sustituir el detector actual hasta superar pruebas reales.

## Material fuente y fotogramas

Los 10 videos originales están en `dataset/costales/videos/raw/`. El workflow **Preparar fotogramas para etiquetado** terminó correctamente y produjo 114 imágenes de revisión en el artefacto `costales-fotogramas-para-etiquetar` (10 videos, muestreo aproximado cada 2 segundos). Los fotogramas están disponibles en GitHub Actions por 30 días desde la ejecución.

## Estructura del dataset

```
ml/dataset/costales/
  images/train/
  images/val/
  labels/train/
  labels/val/
```

Cada imagen debe tener un archivo de etiqueta YOLO con el mismo nombre base. La clase única usa el índice `0`.

## Reglas de etiquetado

1. Dibujar una caja independiente por cada costal visible y distinguible, también cuando esté apilado o fuera de la futura zona de conteo.
2. Incluir costales blancos lisos, impresos con cualquier color, con franjas o sin ellas.
3. Incluir distintas orientaciones, deformaciones, distancias e iluminación.
4. Si un costal está parcialmente tapado, etiquetar la parte visible; no inventar el contorno oculto.
5. Etiquetar por separado dos costales que se tocan o están muy juntos, siempre que se distingan visualmente.
6. No etiquetar estructuras, rodillos, banda transportadora, personas ni objetos ajenos.
7. La zona de conteo se limitará después mediante seguimiento y línea virtual; no enseñar al detector a ignorar costales por su ubicación.
8. Separar por video completo para evitar que fotogramas casi idénticos aparezcan en train y val.

Consulta [ANNOTATION_GUIDE.md](ANNOTATION_GUIDE.md) para los pasos detallados.

## Validar y entrenar

Instalar dependencias y validar desde la raíz:

```bash
python -m pip install -r ml/requirements.txt
python ml/validate_dataset.py
python ml/train.py
```

El validador exige al menos 20 imágenes y 50 cajas en train, 5 imágenes y 10 cajas en val, archivos de etiqueta válidos y separación por video. El workflow **Entrenar detector de costales CONSEDE** también ejecuta estos controles, entrena YOLO11n, calcula precisión/recall/mAP y exporta TFLite. El modelo y los reportes se guardan como artefacto de Actions.

## Criterio para aprobar el modelo

No basta con que detecte costales aislados. Probar videos completos no usados en entrenamiento y revisar costales muy juntos, parcialmente ocultos, lisos y con impresiones variadas. Medir falsos positivos, costales omitidos y duplicados. El conteo final por línea virtual y seguimiento temporal debe validarse por separado del detector.
