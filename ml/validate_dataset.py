"""Valida polígonos YOLO-seg y separación por video antes de entrenar."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "ml/dataset/costales"
EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def sequence_id(path: Path) -> str:
    return re.sub(r"_t\d+$", "", path.stem)


def inspect_split(split: str):
    image_dir = DATASET / "images" / split
    label_dir = DATASET / "labels" / split
    images = sorted(p for p in image_dir.rglob("*") if p.suffix.lower() in EXTS) if image_dir.exists() else []
    errors, instances, sequences = [], 0, set()
    for image in images:
        label = label_dir / (image.stem + ".txt")
        sequences.add(sequence_id(image))
        if not label.is_file():
            errors.append(f"Falta etiqueta: {label.relative_to(ROOT)}")
            continue
        for line_no, line in enumerate(label.read_text(encoding="utf-8").splitlines(), 1):
            parts = line.split()
            if len(parts) < 7 or len(parts) % 2 == 0:
                errors.append(f"{label.name}:{line_no}: YOLO-seg requiere clase y al menos 3 pares x,y")
                continue
            try:
                values = list(map(float, parts))
            except ValueError:
                errors.append(f"{label.name}:{line_no}: contiene valores no numéricos")
                continue
            cls, coords = values[0], values[1:]
            if cls != 0:
                errors.append(f"{label.name}:{line_no}: clase {cls}; la única clase válida es 0")
            if any(not 0 <= v <= 1 for v in coords):
                errors.append(f"{label.name}:{line_no}: coordenadas del polígono fuera del rango 0..1")
            if len(coords) < 6 or len(coords) % 2:
                errors.append(f"{label.name}:{line_no}: polígono inválido")
            instances += 1
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
