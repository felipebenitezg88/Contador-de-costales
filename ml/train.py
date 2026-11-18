"""Entrena, valida y exporta el detector CONSEDE para pruebas."""
from pathlib import Path
import json
import subprocess
import sys

from ultralytics import YOLO

ROOT = Path(__file__).resolve().parents[1]
DATA_YAML = ROOT / "ml/data.yaml"
RUNS = ROOT / "runs"
RUN_NAME = "conseDe_costales"


def main():
    subprocess.run([sys.executable, str(ROOT / "ml/validate_dataset.py")], cwd=ROOT, check=True)
    model = YOLO("yolo11n.pt")
    model.train(
        data=str(DATA_YAML), epochs=100, imgsz=640, batch=8, patience=20,
        project=str(RUNS), name=RUN_NAME, exist_ok=True, pretrained=True,
        seed=42, deterministic=True, plots=True, save=True, device="cpu",
    )
    best = RUNS / RUN_NAME / "weights/best.pt"
    if not best.is_file():
        raise SystemExit(f"No se encontró el mejor modelo: {best}")
    trained = YOLO(str(best))
    metrics = trained.val(data=str(DATA_YAML), imgsz=640, plots=True)
    report = {
        "model": "YOLO11n",
        "weights": str(best.relative_to(ROOT)),
        "map50": float(metrics.box.map50),
        "map50_95": float(metrics.box.map),
        "precision": float(metrics.box.mp),
        "recall": float(metrics.box.mr),
        "dataset": "ml/dataset/costales",
        "note": "Las métricas no sustituyen la prueba en videos reales ni la evaluación del conteo temporal.",
    }
    (RUNS / RUN_NAME / "metrics.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))
    trained.export(format="tflite", imgsz=640, int8=False)


if __name__ == "__main__":
    main()
