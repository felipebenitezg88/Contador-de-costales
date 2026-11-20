# Detector CONSEDE: preparación y entrenamiento

Este directorio contiene la preparación del detector de **una sola clase: `costal`**. El sistema debe contar exclusivamente los costales que pasan sobre la banda transportadora. El modelo debe aprender forma, volumen, textura y dimensiones; no debe depender de franjas, colores, logotipos ni texto impreso.

## Estado verificado

- [x] Definir una clase única: `costal`.
- [x] Incorporar los 10 videos fuente al repositorio.
- [x] Extraer 114 fotogramas de revisión (artefacto de GitHub Actions).
- [x] Definir reglas de etiquetado y separación por video.
- [x] Añadir validador de etiquetas YOLO.
- [x] Preparar entrenamiento, evaluación y exportación TFLite automatizados.
- [ ] Etiquetar manualmente los costales que estén sobre la banda.
- [ ] Ejecutar entrenamiento con datos etiquetados.
- [ ] Revisar métricas y falsos positivos/omisiones en videos completos.
- [ ] Integrar y probar el modelo en Android.

**Todavía no existe un modelo entrenado.** El entrenamiento se detendrá deliberadamente hasta que existan suficientes imágenes y etiquetas verificadas. No se deben generar etiquetas automáticas sin revisión visual ni sustituir el detector actual hasta superar pruebas reales.

## Regla de conteo y etiquetado

1. Etiquetar únicamente costales físicamente sobre la banda transportadora.
2. Excluir de las etiquetas los costales apilados o ubicados fuera de la banda, incluso si aparecen en el fondo del fotograma.
3. Un costal sobre la banda se detecta mientras está en la zona, pero sólo se suma una vez cuando su seguimiento cruce la línea virtual de conteo.
4. La aplicación debe restringir el conteo a una región de interés (ROI) que cubra la banda. Sus límites se ajustarán con los fotogramas reales de la cámara; no se deben inventar coordenadas.
5. Los rodillos, estructura, pallets, personas y maquinaria nunca se etiquetan como costales.

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

Cada imagen debe tener un archivo de etiqueta YOLO-seg con el mismo nombre base. El manifest reserva WA0161 y WA0167 para validación y exige confirmar cada fotograma antes de importar. La clase única usa el índice `0`.

## Reglas de segmentación

- Dibujar un polígono independiente por cada costal distinguible que esté sobre la banda.
- Seguir el contorno físico visible; para un costal parcialmente oculto, no inventar la parte tapada.
- Separar costales que se tocan sólo cuando sus límites sean distinguibles.
- Si no hay costales sobre la banda, dejar el TXT correspondiente vacío.
- No dibujar máscaras sobre costales del fondo o pilas fuera de la banda.
- Separar entrenamiento y validación por video completo para evitar fuga entre fotogramas casi idénticos.

Consulta [ANNOTATION_WORKFLOW.md](ANNOTATION_WORKFLOW.md) para los pasos detallados.

## Validar y entrenar

Instalar dependencias y validar desde la raíz:

```bash
python -m pip install -r ml/requirements.txt
python ml/validate_dataset.py
python ml/train.py
```

El validador exige al menos 20 imágenes y 50 polígonos en train, 5 imágenes y 10 polígonos en val, archivos de etiqueta válidos y separación por video. El workflow **Entrenar detector de costales CONSEDE** también ejecuta estos controles, entrena YOLO11n, calcula precisión/recall/mAP y exporta TFLite. El modelo y los reportes se guardan como artefacto de Actions.

## Criterio para aprobar el modelo

Probar videos completos no usados en entrenamiento. Revisar costales juntos, parcialmente ocultos, lisos y con impresiones variadas, y confirmar que los costales del fondo no se detecten dentro de la ROI ni se sumen. Medir falsos positivos, costales omitidos y duplicados. Validar por separado que cada costal sobre la banda se cuente una sola vez al cruzar la línea virtual.
