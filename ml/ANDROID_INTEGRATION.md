# Integración del detector YOLO en Android

## Estado actual
La actividad Android usa actualmente ML Kit Object Detection genérico. Ese detector no es el modelo especializado YOLO de la clase `costal`; no debe presentarse como si ya estuviera integrado.

## Condición para integrar
Primero debe existir un dataset anotado y revisado, pasar `python ml/validate_dataset.py`, entrenar YOLO y evaluar los vídeos completos. El archivo TFLite exportado debe probarse en Android y deben registrarse sus dimensiones y tensores reales antes de escribir el decodificador.

## Sustitución prevista en MainActivity
1. Sustituir `ObjectDetection.getClient(...)` por un intérprete TensorFlow Lite que cargue el modelo CONSEDE desde `app/src/main/assets/`.
2. Preprocesar cada frame de CameraX: rotación, letterbox/resize según el modelo, conversión RGB y normalización idéntica al entrenamiento.
3. Decodificar la salida del TFLite, aplicar confianza y NMS, y conservar sólo la clase 0 (`costal`).
4. Convertir las cajas al sistema de coordenadas de PreviewView teniendo en cuenta rotación y letterbox.
5. Pasar detecciones al tracker temporal; contar una sola vez por ID al cruzar la línea configurada.
6. Mantener historial, meta, velocidad y controles actuales sin bloquear el hilo de cámara.

## Pruebas de aceptación
- Verificar que la salida del modelo coincide con las cajas del mismo frame procesado en Python.
- Probar vídeo continuo, costales juntos, deformados, girados, parcialmente ocultos y cambios de iluminación.
- Medir latencia/FPS en el teléfono objetivo y uso de memoria.
- Confirmar que un ID no se cuenta dos veces y que una pérdida/reasignación de ID no infla el conteo.
- No retirar el detector actual hasta que el TFLite esté integrado y validado en el dispositivo.

## Importante
No se debe fijar un decodificador YOLO ni dimensiones de tensor por suposición: la exportación concreta puede variar según versión y opciones de Ultralytics. Esta integración queda pendiente de tener el modelo entrenado y su TFLite verificable.
