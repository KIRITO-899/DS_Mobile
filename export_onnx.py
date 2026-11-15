"""
export_onnx.py
Exports PyTorch checkpoints (Conv-Small, Hybrid-Medium, MobileNetV2) to ONNX format
and copies them directly into Android/app/src/main/assets/

Usage:
    python export_onnx.py --model conv_small
    python export_onnx.py --model hybrid_medium
    python export_onnx.py --all
"""

import argparse
import os
import shutil
import sys
from pathlib import Path

import torch
import timm

ROOT_DIR = Path(__file__).resolve().parent
MODELS_DIR = ROOT_DIR / "models"
ASSETS_DIR = ROOT_DIR / "Android" / "app" / "src" / "main" / "assets"


def export_conv_small(checkpoint_path: Path, output_path: Path):
    print(f"[*] Loading MobileNetV4-Conv-Small...")
    model = timm.create_model("mobilenetv4_conv_small.e2400_r224_in1k", pretrained=False)
    model.reset_classifier(num_classes=100)

    if checkpoint_path.exists():
        print(f"[+] Loading weights from {checkpoint_path}...")
        state = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
        if isinstance(state, dict) and "model_state_dict" in state:
            state = state["model_state_dict"]
        elif isinstance(state, dict) and "state_dict" in state:
            state = state["state_dict"]
        model.load_state_dict(state)
    else:
        print(f"[!] Checkpoint {checkpoint_path} not found. Exporting base architecture with initialized head.")

    model.eval()

    dummy_input = torch.randn(1, 3, 224, 224)
    print(f"[*] Exporting to {output_path}...")
    torch.onnx.export(
        model,
        dummy_input,
        str(output_path),
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
        dynamo=False,
    )
    print(f"[✓] Conv-Small exported successfully: {output_path.stat().st_size / (1024*1024):.2f} MB")


def export_hybrid_medium(output_path: Path):
    print(f"[*] Loading MobileNetV4-Hybrid-Medium...")
    try:
        model = timm.create_model("mobilenetv4_hybrid_medium.e500_r224_in1k", pretrained=True)
    except Exception:
        model = timm.create_model("mobilenetv4_hybrid_medium.ix_e550_r256_in1k", pretrained=True)

    model.reset_classifier(num_classes=100)
    model.eval()

    data_config = timm.data.resolve_model_data_config(model)
    in_size = data_config.get("input_size", (3, 224, 224))
    dummy_input = torch.randn(1, *in_size)

    print(f"[*] Exporting to {output_path} with input shape {in_size}...")
    torch.onnx.export(
        model,
        dummy_input,
        str(output_path),
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
        dynamo=False,
    )
    print(f"[✓] Hybrid-Medium exported successfully: {output_path.stat().st_size / (1024*1024):.2f} MB")


def main():
    parser = argparse.ArgumentParser(description="Export MobileNet models to ONNX")
    parser.add_argument("--model", choices=["conv_small", "hybrid_medium", "all"], default="all")
    args = parser.parse_args()

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    if args.model in ["conv_small", "all"]:
        ckpt = MODELS_DIR / "mobilenetv4_conv_small.pt"
        out = ASSETS_DIR / "mobilenetv4_conv_small_cifar100.onnx"
        export_conv_small(ckpt, out)

    if args.model in ["hybrid_medium", "all"]:
        out = ASSETS_DIR / "mobilenetv4_hybrid_medium_cifar100.onnx"
        export_hybrid_medium(out)


if __name__ == "__main__":
    main()
