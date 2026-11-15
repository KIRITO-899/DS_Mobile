# 📱 MobileNet Lab — Android Application Package (APK)

This folder contains pre-built Android installation packages (**APK**) for **MobileNet Lab**, a benchmarking and inference application for MobileNetV4 and MobileNetV2 models powered by ONNX Runtime Mobile.

---

## 📦 Available Builds

| File | Build Type | Size | Description |
|------|------------|------|-------------|
| `app-debug.apk` | **Debug** | ~126 MB | Built with debugging, full logging, and uncompressed ONNX model assets for direct memory mapping. |
| `app-release-unsigned.apk` | **Release (Unsigned)** | ~119 MB | Production-optimized APK without debug overhead. Must be signed before Google Play distribution. |

> **Why is the APK size ~120 MB?**  
> 1. The APK bundles **three complete ONNX models** (`mobilenetv4_conv_small`, `mobilenetv4_conv_medium`, `mobilenetv2`).  
> 2. `noCompress 'onnx'` is enabled in Gradle so models can be memory-mapped directly into RAM without temporary extraction.  
> 3. Native C++ ONNX Runtime runtime shared libraries (`libonnxruntime4j_jni.so`) are included across multiple architectures (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).

---

## 🛠️ System Requirements

- **Operating System:** Android 8.0 (Oreo / API Level 26) or higher
- **Target OS:** Android 14 (API Level 34)
- **Architecture:** ARM64 (`arm64-v8a`) recommended for best neural network performance; x86/x86_64 supported for emulators.
- **RAM:** Minimum 2 GB RAM (3 GB+ recommended for running sustained benchmarks).

---

## 🚀 Installation Guide

### Method 1: Via ADB (Android Debug Bridge) — Recommended
Connect your Android phone with **USB Debugging** enabled:

```bash
# Verify device connection
adb devices

# Install Debug APK
adb install -r Apk/app-debug.apk
```

If targeting Android 12+:
```bash
adb install --bypass-low-target-sdk-block Apk/app-debug.apk
```

### Method 2: Direct Sideloading
1. Transfer `app-debug.apk` to your phone via USB cable, Google Drive, or local file sharing.
2. Open your phone's file manager and locate the APK.
3. Tap on the APK and choose **Install** (allow *"Install unknown apps"* permission if prompted).
4. Launch **MobileNet Lab** from your app drawer.

---

## ✨ Application Features

- **Dashboard:** Instant overview of available models, SoC / CPU specs, and past benchmark history.
- **Models Catalog:** Inspect model parameters, input resolution (224x224), output classes (100 CIFAR-100 classes), and test single-image inference via gallery or camera.
- **Benchmark Suite:**
  - Configurable iterations (10 to 500+ runs)
  - Configurable warm-up cycles (to eliminate cold-start bias)
  - Execution Provider toggle: **CPU** vs **NNAPI (Hardware Neural Accelerator)**
  - Thread count tuning
- **Real-Time Telemetry:**
  - Latency: Min, Max, Mean, Median, P90, P95, P99
  - Throughput: Frames Per Second (FPS)
  - Memory consumption delta (RAM in MB)
- **Results & Historical Analytics:**
  - Interactive comparison bar charts
  - Local SQLite database persistence (Room Database)
  - Export run logs to **CSV** or **JSON**

---

## 🛡️ Version Control & Distribution Notice

Because standard Git and GitHub impose a strict **100 MB per-file upload limit**, APK binary files (`*.apk`) are ignored in `.gitignore` to prevent repository corruption and push rejection. 

For continuous integration and remote distribution:
- Download the latest APK directly from the **GitHub Releases** tab of this repository.
- Team members building locally can build fresh APKs at any time:
  ```bash
  cd Android
  ./gradlew assembleDebug
  ```
