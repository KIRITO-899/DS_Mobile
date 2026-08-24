"""
mobilenetv4.py
Member 1 deliverable — MobileNetV4-Small model loader.

Usage (for Member 2 / training pipeline):
    from mobilenetv4 import get_model
    model = get_model(num_classes=100)
"""

import timm


def get_model(num_classes: int = 100, pretrained: bool = True):
    """
    Load MobileNetV4-Small with a modified classification head.

    Args:
        num_classes: number of output classes (100 for CIFAR-100).
        pretrained: whether to load ImageNet-pretrained weights (True for fine-tuning).

    Returns:
        A timm MobileNetV4-Small model with the classifier head resized to num_classes.
        Expected input size: 224x224 (use timm.data.resolve_model_data_config(model)
        to get the exact normalization/transform config for the DataLoader).
    """
    model = timm.create_model(
        'mobilenetv4_conv_small.e2400_r224_in1k',
        pretrained=pretrained
    )
    model.reset_classifier(num_classes=num_classes)
    return model


if __name__ == "__main__":
    # quick sanity check when run directly: python mobilenetv4.py
    import torch

    model = get_model(num_classes=100)
    model.eval()

    total_params = sum(p.numel() for p in model.parameters()) / 1e6
    print(f"Model loaded — {total_params:.2f}M parameters")
    print("Classifier head:", model.get_classifier())

    dummy_input = torch.randn(1, 3, 224, 224)
    with torch.no_grad():
        output = model(dummy_input)
    print("Output shape:", output.shape)
