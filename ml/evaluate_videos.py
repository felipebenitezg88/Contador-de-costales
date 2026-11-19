#!/usr/bin/env python3
"""Evalúa detección y conteo por cruce de línea en vídeos completos con YOLO + ByteTrack."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import time

import cv2
from ultralytics import YOLO


def evaluate_video(weights: Path, video: Path, output_dir: Path, line_percent: float, conf: float, imgsz: int) -> dict:
    model = YOLO(str(weights))
    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        raise RuntimeError(f"No se pudo abrir el vídeo: {video}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    output_dir.mkdir(parents=True, exist_ok=True)
    output_video = output_dir / f"{video.stem}_evaluado.mp4"
    writer = cv2.VideoWriter(str(output_video), cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    if not writer.isOpened():
        cap.release()
        raise RuntimeError(f"No se pudo crear el vídeo anotado: {output_video}")

    line_x = int(width * line_percent / 100.0)
    previous_side: dict[int, bool] = {}
    counted: set[int] = set()
    frames = 0
    detections = 0
    started = time.perf_counter()
    try:
        for result in model.track(source=str(video), stream=True, persist=True, tracker="bytetrack.yaml",
                                  classes=[0], conf=conf, imgsz=imgsz, verbose=False):
            frames += 1
            boxes = result.boxes
            if boxes is not None and boxes.id is not None:
                ids = boxes.id.int().cpu().tolist()
                xyxy = boxes.xyxy.cpu().tolist()
                detections += len(ids)
                for track_id, coords in zip(ids, xyxy):
                    center_x = (coords[0] + coords[2]) / 2.0
                    side = center_x >= line_x
                    old_side = previous_side.get(track_id)
                    if old_side is not None and old_side != side and track_id not in counted:
                        counted.add(track_id)
                    previous_side[track_id] = side
            annotated = result.plot()
            cv2.line(annotated, (line_x, 0), (line_x, height - 1), (0, 255, 255), 2)
            cv2.putText(annotated, f"Conteo: {len(counted)}", (20, 40),
                        cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
            writer.write(annotated)
    finally:
        cap.release()
        writer.release()

    elapsed = time.perf_counter() - started
    return {
        "video": str(video),
        "frames": frames,
        "detections_with_track_id": detections,
        "counted_unique_tracks": len(counted),
        "line_x_percent": line_percent,
        "confidence": conf,
        "inference_fps": round(frames / elapsed, 2) if elapsed else None,
        "elapsed_seconds": round(elapsed, 2),
        "annotated_video": str(output_video),
        "note": "Revisar manualmente el vídeo anotado; el conteo por IDs no equivale todavía a una verdad-terreno.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", type=Path, required=True, help="Ruta a best.pt")
    parser.add_argument("--videos", type=Path, nargs="+", required=True, help="Vídeos completos de evaluación")
    parser.add_argument("--output", type=Path, default=Path("runs/video_evaluation"))
    parser.add_argument("--line", type=float, default=50.0, help="Posición de línea vertical en porcentaje (0-100)")
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--imgsz", type=int, default=640)
    args = parser.parse_args()
    if not 0 < args.line < 100:
        parser.error("--line debe estar entre 0 y 100")
    if not 0 < args.conf < 1:
        parser.error("--conf debe estar entre 0 y 1")
    reports = []
    for video in args.videos:
        reports.append(evaluate_video(args.weights, video, args.output, args.line, args.conf, args.imgsz))
    report_path = args.output / "video_evaluation.json"
    report_path.write_text(json.dumps(reports, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(reports, indent=2, ensure_ascii=False))
    print(f"Reporte: {report_path}")


if __name__ == "__main__":
    main()
