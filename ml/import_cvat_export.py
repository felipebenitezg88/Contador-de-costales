#!/usr/bin/env python3
"""Importa etiquetas YOLO exportadas desde CVAT y aplica el split por video del manifest."""
from __future__ import annotations
import argparse
import csv
import shutil
import sys
from pathlib import Path

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def index_files(folder: Path, extensions: set[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    duplicates: list[str] = []
    for path in folder.rglob("*"):
        if path.is_file() and path.suffix.lower() in extensions:
            if path.name in result:
                duplicates.append(path.name)
            result[path.name] = path
    if duplicates:
        raise ValueError("Nombres duplicados en exportación: " + ", ".join(sorted(set(duplicates))))
    return result


def validate_label(path: Path) -> tuple[bool, str]:
    for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        fields = line.split()
        if len(fields) != 5:
            return False, f"{path.name}:{number}: se esperan 5 campos YOLO"
        try:
            cls = int(fields[0])
            x, y, w, h = map(float, fields[1:])
        except ValueError:
            return False, f"{path.name}:{number}: valores no numéricos"
        if cls != 0:
            return False, f"{path.name}:{number}: clase {cls}; debe ser 0 (costal)"
        if not all(0.0 <= v <= 1.0 for v in (x, y, w, h)) or w <= 0 or h <= 0:
            return False, f"{path.name}:{number}: caja fuera de rango o sin área"
    return True, ""


def import_dataset(manifest: Path, source_images: Path, source_labels: Path, output: Path) -> int:
    with manifest.open(newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))
    required = {"image", "source_video", "proposed_split"}
    if not rows or not required.issubset(rows[0]):
        raise ValueError("El manifest debe incluir image, source_video y proposed_split.")
    names = [row["image"] for row in rows]
    if len(names) != len(set(names)):
        raise ValueError("El manifest contiene nombres de imagen duplicados.")
    image_map = index_files(source_images, IMAGE_EXTS)
    label_map = index_files(source_labels, {".txt"})
    missing_images = sorted(set(names) - image_map.keys())
    missing_labels = sorted({Path(n).stem + ".txt" for n in names} - label_map.keys())
    if missing_images or missing_labels:
        if missing_images:
            print("Faltan imágenes: " + ", ".join(missing_images[:20]))
        if missing_labels:
            print("Faltan etiquetas (incluye .txt vacíos para imágenes sin costales): " + ", ".join(missing_labels[:20]))
        return 2

    videos_by_split: dict[str, set[str]] = {"train": set(), "val": set()}
    for row in rows:
        split = row["proposed_split"].strip()
        if split not in videos_by_split:
            raise ValueError(f"Split no permitido para {row['image']}: {split}")
        videos_by_split[split].add(row["source_video"].strip())
        ok, error = validate_label(label_map[Path(row["image"]).stem + ".txt"])
        if not ok:
            print("Etiqueta inválida: " + error)
            return 2
    overlap = videos_by_split["train"] & videos_by_split["val"]
    if overlap:
        raise ValueError("Fuga de videos entre train y val: " + ", ".join(sorted(overlap)))
    if output.exists() and any(output.rglob("*")):
        raise ValueError(f"El destino {output} no está vacío. Haz una copia y limpia el destino antes de importar para evitar mezclar etiquetas antiguas.")

    for row in rows:
        split = row["proposed_split"].strip()
        image_src = image_map[row["image"]]
        label_src = label_map[Path(row["image"]).stem + ".txt"]
        image_dst = output / "images" / split / row["image"]
        label_dst = output / "labels" / split / (Path(row["image"]).stem + ".txt")
        image_dst.parent.mkdir(parents=True, exist_ok=True)
        label_dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(image_src, image_dst)
        shutil.copy2(label_src, label_dst)

    print(f"Importación completada: {len(rows)} imágenes con etiqueta YOLO.")
    print(f"Train: {sum(r['proposed_split'].strip() == 'train' for r in rows)} imágenes; videos: {len(videos_by_split['train'])}")
    print(f"Val: {sum(r['proposed_split'].strip() == 'val' for r in rows)} imágenes; videos: {len(videos_by_split['val'])}")
    print(f"Dataset: {output}")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--images", type=Path, required=True, help="Carpeta de imágenes exportadas desde CVAT")
    parser.add_argument("--labels", type=Path, required=True, help="Carpeta de etiquetas .txt exportadas desde CVAT")
    parser.add_argument("--output", type=Path, required=True, help="Destino ml/dataset/costales")
    args = parser.parse_args()
    try:
        code = import_dataset(args.manifest, args.images, args.labels, args.output)
    except (OSError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        code = 2
    raise SystemExit(code)


if __name__ == "__main__":
    main()
