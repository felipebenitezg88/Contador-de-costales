"""Valida etiquetas YOLO-seg, integridad del dataset y separación por video."""
from pathlib import Path
import math
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "ml/dataset/costales"
EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def sequence_id(path: Path) -> str:
    return re.sub(r"_t\d+$", "", path.stem)


def polygon_area(points):
    return abs(sum(
        points[i][0] * points[(i + 1) % len(points)][1]
        - points[(i + 1) % len(points)][0] * points[i][1]
        for i in range(len(points))
    )) / 2


def inspect_split(split: str):
    image_dir = DATASET / "images" / split
    label_dir = DATASET / "labels" / split
    images = sorted(p for p in image_dir.rglob("*") if p.suffix.lower() in EXTS) if image_dir.exists() else []
    errors, instances, sequences = [], 0, set()
    expected_labels = set()

    for image in images:
        label = label_dir / (image.stem + ".txt")
        expected_labels.add(label.resolve())
        sequences.add(sequence_id(image))
        if not label.is_file():
            errors.append(f"Falta etiqueta: {label.relative_to(ROOT)}")
            continue

        for line_no, line in enumerate(label.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            parts = line.split()
            if len(parts) < 7 or len(parts) % 2 == 0:
                errors.append(f"{label.name}:{line_no}: se requiere clase y al menos 3 pares x,y")
                continue
            try:
                values = list(map(float, parts))
            except ValueError:
                errors.append(f"{label.name}:{line_no}: contiene valores no numéricos")
                continue
            if not all(math.isfinite(v) for v in values):
                errors.append(f"{label.name}:{line_no}: contiene NaN o infinito")
                continue

            cls, coords = values[0], values[1:]
            if cls != 0:
                errors.append(f"{label.name}:{line_no}: clase {cls}; la única clase válida es 0")
            if len(coords) < 6 or len(coords) % 2:
                errors.append(f"{label.name}:{line_no}: polígono inválido")
                continue
            if any(not 0 <= v <= 1 for v in coords):
                errors.append(f"{label.name}:{line_no}: coordenadas fuera del rango 0..1")
                continue

            points = list(zip(coords[::2], coords[1::2]))
            if len(set(points)) < 3:
                errors.append(f"{label.name}:{line_no}: menos de 3 vértices distintos")
                continue
            if polygon_area(points) <= 1e-6:
                errors.append(f"{label.name}:{line_no}: polígono sin área válida")
                continue
            instances += 1

    if label_dir.exists():
        for label in label_dir.rglob("*.txt"):
            if label.resolve() not in expected_labels:
                errors.append(f"Etiqueta huérfana sin imagen: {label.relative_to(ROOT)}")

    return images, instances, sequences, errors


def main():
    total_errors, split_data = [], {}
    for split in ("train", "val"):
        images, instances, sequences, errors = inspect_split(split)
        split_data[split] = (images, instances, sequences)
        total_errors.extend(errors)
        print(f"{split}: {len(images)} imágenes, {instances} polígonos/instancias, {len(sequences)} videos")
    overlap = split_data["train"][2] & split_data["val"][2]
    if overlap:
        total_errors.append("Fuga de datos: videos presentes en train y val: " + ", ".join(sorted(overlap)))
    if len(split_data["train"][0]) < 20 or len(split_data["val"][0]) < 5:
        total_errors.append("Dataset insuficiente: se requieren al menos 20 imágenes train y 5 val.")
    if split_data["train"][1] < 50 or split_data["val"][1] < 10:
        total_errors.append("Etiquetas insuficientes: se requieren al menos 50 polígonos train y 10 val.")
    if total_errors:
        print("\nVALIDACIÓN FALLIDA:")
        for error in total_errors:
            print("- " + error)
        sys.exit(1)
    print("\nVALIDACIÓN CORRECTA. Dataset de segmentación apto estructuralmente para iniciar entrenamiento.")


if __name__ == "__main__":
    main()
