# Inventario preliminar de fotogramas CONSEDE

Revisión visual preliminar de las hojas de contacto generadas a partir de los 10 videos fuente. Este inventario sirve para planear la anotación; **no equivale a etiquetas YOLO revisadas**.

## Cobertura

| Video fuente | Fotogramas | Escena observada en hoja de contacto |
|---|---:|---|
| WA0158 | 14 | Costales blancos sobre rodillos/equipo transportador; movimiento y recortes parciales. |
| WA0159 | 16 | Varios costales próximos o superpuestos sobre rodillos; límites individuales variables. |
| WA0160 | 16 | Costales sobre rodillos, algunos juntos y con oclusiones. |
| WA0161 | 10 | Toma vertical de área de trabajo; aparecen costales y personas/entorno. |
| WA0162 | 11 | Costales sobre una banda o mesa transportadora en área de trabajo. |
| WA0163 | 10 | Toma vertical cercana de costales en transportador; distintas orientaciones. |
| WA0164 | 12 | Toma vertical de transportador/área de manejo; revisar límites y objetos ajenos. |
| WA0165 | 6 | Costales sobre transportador con rodillos/equipo industrial. |
| WA0166 | 12 | Tomas cercanas de costales sobre banda/transportador; impresiones visibles en algunos. |
| WA0167 | 7 | Vista más amplia de transportador y costales en movimiento/área industrial. |
| **Total** | **114** | **10 videos fuente** |

## Criterios confirmados para la anotación

- Una sola clase: `costal` (índice YOLO `0`).
- Delimitar cada costal distinguible por separado, sin usar marca, texto, color de franjas o logotipo como condición.
- Incluir costales lisos y estampados, deformados, orientados de distintas maneras y parcialmente ocultos cuando su parte visible sea identificable.
- No inventar el contorno oculto ni etiquetar rodillos, bandas, maquinaria, personas o fondo.
- Crear un archivo TXT por imagen; si no hay costales identificables, dejar el TXT vacío.
- Separar train y val por video completo, nunca por fotogramas mezclados.

## Estado real

- [x] 10 videos fuente muestreados.
- [x] 114 fotogramas extraídos y agrupados por video.
- [x] Hojas de contacto revisadas para identificar variedad de escenas.
- [ ] Cajas individuales revisadas y guardadas para los 114 fotogramas.
- [ ] División definitiva train/val basada en videos completos.
- [ ] Validación del dataset (mínimo 20 imágenes/50 cajas train y 5 imágenes/10 cajas val).
- [ ] Entrenamiento, evaluación en videos no usados e integración Android.

**No iniciar entrenamiento todavía:** no hay anotaciones verificadas. El resumen visual de las hojas de contacto no permite producir coordenadas de caja suficientemente precisas. La anotación debe hacerse sobre cada imagen original a resolución completa.
