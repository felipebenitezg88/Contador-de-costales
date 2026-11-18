"""Extract review frames from raw conveyor videos for manual YOLO annotation.

Usage: python ml/extract_frames.py
Samples one frame every N seconds, capped per video. It does NOT create labels.
"""
from pathlib import Path
import cv2

ROOT = Path(__file__).resolve().parents[1]
VIDEO_DIR = ROOT / "ml/dataset/costales/videos/raw"
OUT_DIR = ROOT / "ml/dataset/costales/frames/review"
INTERVAL_SECONDS = 2.0
MAX_FRAMES_PER_VIDEO = 150
VIDEO_EXTS = {".mp4", ".mov", ".m4v", ".avi"}


def main():
    videos = sorted(p for p in VIDEO_DIR.glob("*") if p.suffix.lower() in VIDEO_EXTS)
    if not videos:
        raise SystemExit(f"No se encontraron videos en {VIDEO_DIR}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for video in videos:
        cap = cv2.VideoCapture(str(video))
        if not cap.isOpened():
            print(f"AVISO: no se pudo abrir {video.name}")
            continue
        fps = cap.get(cv2.CAP_PROP_FPS) or 0
        frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        duration = frame_count / fps if fps > 0 else 0
        if duration <= 0:
            print(f"AVISO: duración no disponible: {video.name}")
            cap.release()
            continue

        step = max(1, round(fps * INTERVAL_SECONDS))
        saved = 0
        for frame_no in range(0, frame_count, step):
            if saved >= MAX_FRAMES_PER_VIDEO:
                break
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_no)
            ok, frame = cap.read()
            if not ok:
                continue
            # Resize large frames while preserving aspect ratio for efficient annotation.
            h, w = frame.shape[:2]
            scale = min(1.0, 1280 / max(w, h))
            if scale < 1.0:
                frame = cv2.resize(frame, (round(w * scale), round(h * scale)))
            timestamp = frame_no / fps
            name = f"{video.stem}_t{int(timestamp*1000):09d}.jpg"
            if cv2.imwrite(str(OUT_DIR / name), frame, [cv2.IMWRITE_JPEG_QUALITY, 92]):
                saved += 1
        cap.release()
        total += saved
        print(f"{video.name}: {saved} fotogramas (duración aprox. {duration:.1f}s)")
    print(f"Total: {total} fotogramas en {OUT_DIR}")


if __name__ == "__main__":
    main()
