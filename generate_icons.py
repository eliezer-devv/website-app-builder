from pathlib import Path
from PIL import Image, ImageOps

root = Path(__file__).resolve().parents[1]
source = root / "webapp-icon.png"
if not source.exists():
    print("No webapp-icon.png uploaded; keeping the template icon.")
    raise SystemExit(0)
image = Image.open(source).convert("RGBA")
# Square-crop, then resize to Android launcher density dimensions.
image = ImageOps.fit(image, (1024, 1024), method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, size in sizes.items():
    folder = root / "app" / "src" / "main" / "res" / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    resized = image.resize((size, size), Image.Resampling.LANCZOS)
    for name in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"):
        resized.save(folder / name, format="PNG", optimize=True)
print("Generated launcher icons from webapp-icon.png")
