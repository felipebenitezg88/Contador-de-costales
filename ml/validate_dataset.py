"""Valida estructura, etiquetas YOLO y separación por video antes de entrenar."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "ml/dataset/costales"
EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def sequence_id(path: Path) -> str:
    # Los fotogramas extraídos conservan el nombre del video fuente.
    return re.sub(r"_t\d+$", "", path.stem)


def inspect_split(split: str):
    image_dir = DATASET / "images" / split
    label_dir = DATASET / "labels" / split
    images = sorted(p for p in image_dir.rglob("*") if p.suffix.lower() in EXTS)
    errors, boxes, sequences = [], 0, set()
    for image in images:
        label = label_dir / (image.stem + ".txt")
        sequences.add(sequence_id(image))
        if not label.is_file():
            errors.append(f"Falta etiqueta: {label.relative_to(ROOT)}")
            continue
        for line_no, line in enumerate(label.read_text(encoding="utf-8").splitlines(), 1):
            parts = line.split()
            if len(parts) != 5:
                errors.append(f"{label.name}:{line_no}: se esperan 5 valores YOLO")
                continue
            try:
                cls, x, y, w, h = map(float, parts)
            except ValueError:
                errors.append(f"{label.name}:{line_no}: contiene valores no numéricos")
                continue
            if cls != 0:
                errors.append(f"{label.name}:{line_no}: clase {cls}; la única clase válida es 0")
            if not all(0 <= v <= 1 for v in (x, y, w, h)) or w <= 0 or h <= 0:
                errors.append(f"{label.name}:{line_no}: caja fuera del rango normalizado o sin área")
            boxes += 1
    return images, boxes, sequences, errors


def main():
    total_errors, split_data = [], {}
    for split in ("train", "val"):
        images, boxes, sequences, errors = inspect_split(split)
        split_data[split] = (images, boxes, sequences)
        total_errors.extend(errors)
        print(f"{split}: {len(images)} imágenes, {boxes} cajas, {len(sequences)} videos/secuencias")
    overlap = split_data["train"][2] & split_data["val"][2]
    if overlap:
        total_errors.append("Fuga de datos: videos presentes en train y val: " + ", ".join(sorted(overlap)))
    if len(split_data["train"][0]) < 20 or len(split_data["val"][0]) < 5:
        total_errors.append("Dataset insuficiente: se requieren al menos 20 imágenes train y 5 val.")
    if split_data["train"][1] < 50 or split_data["val"][1] < 10:
        total_errors.append("Etiquetas insuficientes: se requieren al menos 50 cajas train y 10 val.")
    if total_errors:
        print("\nVALIDACIÓN FALLIDA:")
        for error in total_errors:
            print("- " + error)
        sys.exit(1)
    print("\nVALIDACIÓN CORRECTA. Dataset apto estructuralmente para iniciar entrenamiento.")
    

if __name__ == "__main__":
    main()
