# Detector CONSEDE: preparación y entrenamiento

Este directorio contiene la configuración inicial para entrenar un detector de **una sola clase: `costal`**. El modelo no debe depender de franjas, logotipos ni texto: debe aprender la forma, volumen, textura y dimensiones aproximadas del saco.

## Estado

- [x] Definir una clase única: `costal`.
- [x] Definir estructura y reglas de etiquetado.
- [x] Añadir configuración y script reproducible de entrenamiento.
- [ ] Incorporar los videos/fotografías originales al dataset.
- [ ] Etiquetar cada costal en las imágenes.
- [ ] Entrenar y medir precisión/recall en validación.
- [ ] Exportar y probar el modelo en Android.

**No hay todavía un modelo entrenado.** No se debe sustituir el detector actual hasta completar validación y pruebas reales.

## Estructura esperada

```
ml/dataset/costales/
  images/
    train/
    val/
  labels/
    train/
    val/
```

Cada imagen debe tener un archivo de etiqueta YOLO con el mismo nombre base. La clase única usa el índice `0`.

## Reglas de etiquetado

1. Dibujar una caja independiente por cada costal visible y distinguible, también cuando esté apilado o fuera de la futura zona de conteo.
2. Incluir costales blancos lisos, impresos con cualquier color, con franjas o sin ellas.
3. Incluir distintas orientaciones, deformaciones, distancias e iluminación.
4. Si un costal está parcialmente tapado, etiquetar la parte visible del costal; no inventar el contorno oculto.
5. Etiquetar por separado dos costales que se tocan o están muy juntos, siempre que se distingan visualmente.
6. No etiquetar estructuras, rodillos, banda transportadora, personas ni objetos que no sean costales.
7. La zona de conteo se limitará después mediante la lógica de seguimiento y línea virtual; no se debe enseñar al detector a ignorar costales reales por su ubicación.
8. Evitar que fotogramas casi idénticos del mismo tramo de video queden repartidos entre entrenamiento y validación. Separar por video o por secuencia para evitar fuga de datos.

## Entrenar

Instalar dependencias de `requirements.txt`, colocar y etiquetar los datos en la estructura indicada y ejecutar desde la raíz del repositorio:

```bash
python -m pip install -r ml/requirements.txt
python ml/train.py
```

El entrenamiento usa YOLO11 nano como punto de partida. Los resultados se guardan en `runs/conseDe_costales/`.

## Criterio para aprobar el modelo

No basta con que detecte costales aislados. La validación debe incluir costales muy juntos, parcialmente ocultos, sin impresión y con impresiones variadas. Revisar falsos positivos, costales omitidos y duplicados en secuencias de video. El conteo por línea virtual y seguimiento temporal se valida después, por separado del detector.
