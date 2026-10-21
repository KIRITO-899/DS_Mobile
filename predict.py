"""
predict.py
Run inference with the fine-tuned MobileNetV4-Conv-Small (CIFAR-100) model.

Usage:
    python predict.py                          # uses the default Pexels image URL
    python predict.py --image path/to/img.jpg  # local file
    python predict.py --image https://...      # any URL
    python predict.py --topk 10                # show top-10 predictions
"""

import argparse
import sys
from io import BytesIO
from pathlib import Path
from urllib.request import urlopen, Request

import torch
import timm
from timm.data import resolve_model_data_config, create_transform
from PIL import Image

# ── CIFAR-100 class labels (in the standard torchvision order) ──────────────
CIFAR100_LABELS = [
    "apple", "aquarium_fish", "baby", "bear", "beaver",
    "bed", "bee", "beetle", "bicycle", "bottle",
    "bowl", "boy", "bridge", "bus", "butterfly",
    "camel", "can", "castle", "caterpillar", "cattle",
    "chair", "chimpanzee", "clock", "cloud", "cockroach",
    "couch", "crab", "crocodile", "cup", "dinosaur",
    "dolphin", "elephant", "flatfish", "forest", "fox",
    "girl", "hamster", "house", "kangaroo", "keyboard",
    "lamp", "lawn_mower", "leopard", "lion", "lizard",
    "lobster", "man", "maple_tree", "motorcycle", "mountain",
    "mouse", "mushroom", "oak_tree", "orange", "orchid",
    "otter", "palm_tree", "pear", "pickup_truck", "pine_tree",
    "plain", "plate", "poppy", "porcupine", "possum",
    "rabbit", "raccoon", "ray", "road", "rocket",
    "rose", "sea", "seal", "shark", "shrew",
    "skunk", "skyscraper", "snail", "snake", "spider",
    "squirrel", "streetcar", "sunflower", "sweet_pepper", "table",
    "tank", "telephone", "television", "tiger", "tractor",
    "train", "trout", "tulip", "turtle", "wardrobe",
    "whale", "willow_tree", "wolf", "woman", "worm",
]

# ── Paths ───────────────────────────────────────────────────────────────────
MODEL_DIR = Path(__file__).resolve().parent / "models"
CHECKPOINT = MODEL_DIR / "best_conv_small.pt"

DEFAULT_IMAGE_URL = (
    "https://images.pexels.com/photos/4781925/pexels-photo-4781925.jpeg"
)


def load_model(checkpoint_path: Path, device: torch.device) -> torch.nn.Module:
    """Build MobileNetV4-Conv-Small with 100-class head and load trained weights."""
    # Build the same architecture used during training (pretrained=False since
    # we're loading our own checkpoint, not the ImageNet weights).
    model = timm.create_model(
        "mobilenetv4_conv_small.e2400_r224_in1k",
        pretrained=False,
    )
    model.reset_classifier(num_classes=100)

    # Load checkpoint
    state = torch.load(checkpoint_path, map_location=device, weights_only=True)

    # Handle common checkpoint formats: plain state_dict or wrapped dict
    if isinstance(state, dict) and "model_state_dict" in state:
        state = state["model_state_dict"]
    elif isinstance(state, dict) and "state_dict" in state:
        state = state["state_dict"]

    model.load_state_dict(state)
    model.to(device).eval()
    return model


def load_image(source: str) -> Image.Image:
    """Load an image from a local path or URL."""
    if source.startswith(("http://", "https://")):
        print(f"[+] Downloading image from URL ...")
        req = Request(source, headers={"User-Agent": "Mozilla/5.0"})
        try:
            data = urlopen(req).read()
        except Exception:
            # Fallback: skip SSL verification (common on Windows with
            # expired/missing certificates in the Python cert store).
            import ssl
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            data = urlopen(req, context=ctx).read()
        img = Image.open(BytesIO(data)).convert("RGB")
    else:
        path = Path(source)
        if not path.exists():
            sys.exit(f"❌ File not found: {path}")
        img = Image.open(path).convert("RGB")
    return img


def predict(
    model: torch.nn.Module,
    img: Image.Image,
    device: torch.device,
    topk: int = 5,
) -> list[tuple[str, float]]:
    """Return top-k (label, probability) pairs for a single image."""
    # Build the exact same transform pipeline the model expects (224×224,
    # ImageNet normalization, bicubic interpolation, etc.)
    data_config = resolve_model_data_config(model)
    transform = create_transform(**data_config, is_training=False)

    tensor = transform(img).unsqueeze(0).to(device)  # (1, 3, 224, 224)

    with torch.no_grad():
        logits = model(tensor)                        # (1, 100)
        probs = torch.softmax(logits, dim=1)

    topk_probs, topk_indices = probs.topk(topk, dim=1)
    results = []
    for prob, idx in zip(topk_probs.squeeze(), topk_indices.squeeze()):
        label = CIFAR100_LABELS[idx.item()]
        results.append((label, prob.item()))
    return results


def main():
    parser = argparse.ArgumentParser(
        description="MobileNetV4-Conv-Small — CIFAR-100 inference"
    )
    parser.add_argument(
        "--image",
        type=str,
        default=DEFAULT_IMAGE_URL,
        help="Path or URL to the input image (default: Pexels sample)",
    )
    parser.add_argument(
        "--topk",
        type=int,
        default=5,
        help="Number of top predictions to show (default: 5)",
    )
    parser.add_argument(
        "--checkpoint",
        type=str,
        default=str(CHECKPOINT),
        help="Path to the .pt checkpoint file",
    )
    args = parser.parse_args()

    # ── Device ──────────────────────────────────────────────────────────────
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Device: {device}")

    # ── Load model ──────────────────────────────────────────────────────────
    ckpt = Path(args.checkpoint)
    if not ckpt.exists():
        sys.exit(f"[!] Checkpoint not found: {ckpt}")
    print(f"[+] Loading checkpoint: {ckpt.name}")
    model = load_model(ckpt, device)
    print("[+] Model loaded successfully\n")

    # ── Load image ──────────────────────────────────────────────────────────
    img = load_image(args.image)
    print(f"[+] Image size: {img.size[0]}x{img.size[1]}\n")

    # ── Predict ─────────────────────────────────────────────────────────────
    results = predict(model, img, device, topk=args.topk)

    print(f"{'Rank':<6} {'Class':<20} {'Confidence':>10}")
    print("-" * 38)
    for rank, (label, prob) in enumerate(results, 1):
        bar = "#" * int(prob * 30)
        print(f"  {rank:<4} {label:<20} {prob:>8.2%}  {bar}")

    print()


if __name__ == "__main__":
    main()
