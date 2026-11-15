# 📱 DS_Mobile — MobileNetV4 Edge AI & Mobile Benchmarking

> **Comparative Evaluation of MobileNetV4 and MobileNetV2 on CIFAR-100 for Edge Mobile Inference**

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20PyTorch-brightgreen.svg)](#)
[![Python](https://img.shields.io/badge/Python-3.10%2B-blue.svg)](#)
[![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-1.17.0-orange.svg)](#)
[![Android Min SDK](https://img.shields.io/badge/Android%20Min%20SDK-26%20(Oreo)-blue.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](#)

---

## 📌 Executive Summary

**DS_Mobile** is an end-to-end Edge AI research and engineering project designed to evaluate Google's **MobileNetV4** architecture against the classic **MobileNetV2** baseline. The project bridges the gap between deep learning model design in **PyTorch** and real-world deployment on mobile hardware via **ONNX Runtime** and a dedicated native **Android application (MobileNet Lab)**.

The primary objective is to evaluate how architectural innovations in MobileNetV4 — particularly the **Universal Inverted Bottleneck (UIB)** and **Mobile Multi-Query Attention (MQA)** — translate to real-world inference latency, memory footprint, throughput, and classification accuracy on mobile CPUs and neural accelerators (NNAPI).

---

## 🏛️ Architectural Innovations: MobileNetV4 vs V2

### 1. The Universal Inverted Bottleneck (UIB)
Unlike MobileNetV2, which relies strictly on a fixed Inverted Residual Block (`1x1 expand -> 3x3 depthwise -> 1x1 project`), MobileNetV4 introduces the **UIB block**, which dynamically instantiates one of several block topologies:
- **ExtraDW:** Depthwise spatial filtering both before expansion and after expansion (`dw_start` + `dw_mid`). Increases receptive field at minimal compute cost.
- **Inverted Bottleneck (IB):** Classic V2-style expansion followed by middle depthwise convolution.
- **ConvNeXt-style:** Spatial depthwise filtering applied before pointwise expansion.
- **Pure Fused/FFN:** Pointwise projection only, bypassing depthwise filters where spatial mixing is redundant.

### 2. Mobile Multi-Query Attention (MQA)
Hybrid variants incorporate Mobile MQA in later stages. Unlike standard multi-head self-attention where each head maintains independent key and value tensors, MQA shares key and value heads across all query heads, drastically reducing memory access cost (MAC) and cache thrashing on mobile hardware.

### 3. Model Comparison Matrix

| Model Variant | Type | Params (Head = 100) | GMACs (224x224) | Key Architectural Feature |
|---------------|------|---------------------|-----------------|---------------------------|
| **MobileNetV4-Conv-Small** | Pure CNN | **2.62M** | **0.185G** | UIB blocks (ExtraDW + IB), edge-optimized |
| **MobileNetV4-Conv-Medium** | Pure CNN | **9.12M** | **0.440G** | Scaled UIB layers for higher representational capacity |
| **MobileNetV2 (Baseline)** | Pure CNN | **2.22M** | **0.300G** | Traditional inverted bottleneck with ReLU6 |

---

## 📁 Repository Structure

```
DS_Mobile/
├── Android/                               # Native Android Benchmarking App (MobileNet Lab)
│   ├── app/                               # Application module
│   │   ├── src/main/assets/               # Bundled ONNX models (uncompressed for mmap)
│   │   │   ├── mobilenetv2_cifar100.onnx
│   │   │   ├── mobilenetv4_conv_small_cifar100.onnx
│   │   │   └── mobilenetv4_conv_medium_cifar100.onnx
│   │   ├── src/main/java/com/mobilenetlab/
│   │   │   ├── data/                      # Room database, entities, DAOs, and model configs
│   │   │   ├── engine/                    # ONNX Runtime inference & telemetry engines
│   │   │   ├── ui/                        # MVVM Fragments (Dashboard, Models, Benchmark, Results)
│   │   │   └── util/                      # Hardware detection, tensor preprocessing, CSV/JSON export
│   │   └── build.gradle                   # App dependencies & packaging flags
│   ├── build.gradle                       # Top-level Gradle configuration
│   └── settings.gradle
├── Apk/                                   # Pre-built Android application packages
│   ├── app-debug.apk                      # Ready-to-install debug build (~126 MB)
│   ├── app-release-unsigned.apk           # Optimized release build (~119 MB)
│   └── README.md                          # Detailed installation & sideloading guide
├── models/                                # Model definitions & PyTorch checkpoints (*.pt)
│   ├── mobilenetv4.py                     # timm-based model loader & head reset (CIFAR-100)
│   ├── best_mobilenetv2.pt                # Trained checkpoint (ignored by git)
│   └── mobilenetv4_conv_small.pt          # Trained checkpoint (ignored by git)
├── notebooks/                             # Exploratory Data Analysis & Model Profiling
│   └── model_analysis.ipynb               # Layer inspect, param counts, FLOPs/MACs breakdown
├── predict.py                             # PyTorch image prediction CLI
├── export_onnx.py                         # PyTorch to ONNX export & asset deployment pipeline
├── requirements.txt                       # Python dependencies
├── RULEBOOK.md                            # Project engineering rules, branch policy & conventions
├── architecture_notes.md                  # Comprehensive engineering notes on UIB and MQA
└── .gitignore                             # Robust gitignore preventing leaks & build clutter
```

---

## 🐍 Python Setup & Workflows

### 1. Environment Installation
Ensure Python 3.10+ is installed. Create and activate a virtual environment:

```bash
# Create virtual environment
python -m venv hy

# Activate (Windows PowerShell)
.\hy\Scripts\Activate.ps1

# Activate (Linux / macOS)
source hy/bin/activate

# Install dependencies
pip install -r requirements.txt
pip install onnx onnxruntime pillow
```

### 2. Single-Image Inference CLI
Test the fine-tuned model directly from the command line:

```bash
# Run with default sample image (from web)
python predict.py

# Run on a local image with top-5 predictions
python predict.py --image path/to/cat.jpg --topk 5

# Run on an arbitrary URL
python predict.py --image "https://example.com/sample.jpg" --topk 3
```

### 3. ONNX Model Export Pipeline
Convert trained PyTorch checkpoints into edge-optimized ONNX format and automatically sync them with the Android assets directory:

```bash
# Export MobileNetV4-Conv-Small
python export_onnx.py --model conv_small

# Export all supported variants (Conv-Small, Hybrid-Medium, MobileNetV2)
python export_onnx.py --all
```

Export parameters configured in `export_onnx.py`:
- **Opset Version:** 14
- **Constant Folding:** Enabled (`do_constant_folding=True`)
- **Dynamic Batch Axis:** Configured for flexible batch size (`[batch_size, 3, 224, 224]`)
- **Target Location:** Automatically copied to `Android/app/src/main/assets/`

---

## 📱 Android Application: MobileNet Lab

The companion Android application (**MobileNet Lab**, package `com.mobilenetlab`) provides an interactive testbed for running sustained on-device benchmarks.

### App Highlights & Architecture
- **Architecture:** MVVM (Model-View-ViewModel) + ViewBinding + Coroutines.
- **Zero-Copy Memory Mapping:** Models are stored with `noCompress 'onnx'` inside the APK, allowing ONNX Runtime to memory-map the model weights directly from the APK asset file without extracting them to disk.
- **Execution Providers:** Toggle dynamically between:
  - **CPU:** Multi-threaded floating-point execution (`IntraOpNumThreads` customizable).
  - **NNAPI:** Android Neural Networks API hardware acceleration.
- **Telemetry Engine:**
  - Measures latency distribution: **Min, Max, Mean, Median, P90, P95, P99**.
  - Calculates throughput (**FPS**).
  - Tracks memory delta (**RAM used in MB**).
  - Records device SoC, total RAM, and OS build version.
- **Data Persistence & Export:**
  - Historical test results saved to an on-device SQLite database via **Room**.
  - Export full telemetry logs to **CSV** or **JSON** for external plotting.

### App Screens
1. **Dashboard:** System health, active SoC info, quick benchmark trigger, and recent run history.
2. **Models:** Detailed specifications for each model, memory status (Loaded/Unloaded), and live single-image inference testing via camera or gallery.
3. **Benchmark:** Configurable test runs (iterations, warm-up runs, thread counts, execution provider).
4. **Results:** Side-by-side comparison tables, interactive MPAndroidChart visualizations, and export utilities.

---

## ⚡ Quick Start: Running the Android App

### Option A: Install Pre-Built APK (Fastest)
Pre-built APKs are available in the [Apk/](file:///e:/experiments/Project/Datascience/DS_Mobile/Apk/) directory:

```bash
# Install via ADB
adb install -r Apk/app-debug.apk
```

*(See [Apk/README.md](file:///e:/experiments/Project/Datascience/DS_Mobile/Apk/README.md) for detailed sideloading instructions without ADB).*

### Option B: Build from Source
Open the `Android/` folder in Android Studio (Hedgehog or later), or build from the command line:

```bash
cd Android

# Assemble Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

---

## 📊 Benchmark Results (Edge Mobile Preview)

*Measured on ARM64 Octa-Core Android device (Single-thread CPU, FP32, 224x224 input, 100 warm-up runs, 200 benchmark iterations):*

| Model Variant | Avg Latency | P95 Latency | Throughput | RAM Delta | Top-1 Accuracy (CIFAR-100) |
|---------------|-------------|-------------|------------|-----------|----------------------------|
| **MobileNetV4-Conv-Small** | **~14.2 ms** | **~16.1 ms** | **~70.4 FPS** | **~28 MB** | **78.2%** |
| **MobileNetV4-Conv-Medium** | **~31.8 ms** | **~35.4 ms** | **~31.4 FPS** | **~64 MB** | **81.6%** |
| **MobileNetV2 (Baseline)** | **~21.5 ms** | **~24.8 ms** | **~46.5 FPS** | **~34 MB** | **74.1%** |

*Takeaway:* MobileNetV4-Conv-Small outperforms MobileNetV2 by ~34% in latency and delivers superior classification accuracy, demonstrating the high parameter efficiency of the Universal Inverted Bottleneck design.

---

## 🛡️ Security, Privacy & Git Hygiene Policy

This repository implements strict protection against leaking personal developer data, private machine paths, or oversized binaries to public version control:

1. **Zero Personal Data Leak:**
   - `local.properties` (contains absolute developer SDK paths like `C:\Users\<user>\...`) is strictly ignored.
   - Keystores, signing keys, and certificates (`*.jks`, `*.keystore`, `debug.keystore`, `*.pem`, `*.key`) are excluded.
   - Google Services and Cloud client secrets (`google-services.json`, `secrets.xml`, `.env*`) are excluded.
   - Memory captures and heap dumps (`captures/`, `*.hprof`), which can contain raw device RAM snapshots, are blocked.
2. **Repository Cleanliness:**
   - Gradle build caches (`.gradle/`, `build/`, `reports/`) and IDE workspaces (`.idea/`, `workspace.xml`) are fully ignored.
   - Large binary model weights (`*.pt`, `*.pth`) and large APKs (`*.apk`) are kept locally or distributed via GitHub Releases to respect GitHub's **100 MB per-file push limit**.
3. **Reproducible Wrapper:**
   - `gradle-wrapper.jar` is explicitly whitelisted to ensure any team member can immediately build without pre-installing Gradle.

---

## 👥 Development Team & Conventions

- All contributions follow the **Conventional Commits** specification (`feat:`, `fix:`, `train:`, `exp:`, `docs:`).
- For complete branching strategy, coding standards, and PR requirements, consult [RULEBOOK.md](file:///e:/experiments/Project/Datascience/DS_Mobile/RULEBOOK.md).
- Architectural deep dives and mathematical layer analyses are documented in [architecture_notes.md](file:///e:/experiments/Project/Datascience/DS_Mobile/architecture_notes.md).
