"""Entrena el detector de una clase para Contador de Costales CONSEDE.

Ejecutar desde la raíz del repositorio:
    python ml/train.py

Este script no inventa ni genera etiquetas. Requiere imágenes y etiquetas YOLO
reales en dataset/costales antes de iniciar el entrenamiento.
"""
from pathlib import Path

from ultralytics import YOLO

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "ml" / "dataset" / "costales"
DATA_YAML = ROOT / "ml" / "data.yaml"


def count_images(folder: Path) -> int:
    if not folder.exists():
        return 0
    extensions = {".jpg", ".jpeg", ".png", ".webp"}
    return sum(1 for item in folder.rglob("*") if item.suffix.lower() in extensions)


def main() -> None:
    train_images = count_images(DATASET / "images" / "train")
    val_images = count_images(DATASET / "images" / "val")

    if train_images == 0 or val_images == 0:
        raise SystemExit(
            "Dataset incompleto: agrega imágenes reales a "
            "dataset/costales/images/train y images/val, y sus etiquetas "
            "YOLO correspondientes en labels/train y labels/val. "
            "No se inició ningún entrenamiento."
        )

    model = YOLO("yolo11n.pt")
    model.train(
        data=str(DATA_YAML),
        epochs=100,
        imgsz=640,
        batch=8,
        patience=20,
        project=str(ROOT / "runs"),
        name="conseDe_costales",
        exist_ok=True,
        pretrained=True,
        seed=42,
    )


if __name__ == "__main__":
    main()
