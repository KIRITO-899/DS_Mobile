# 📱 Simulated Virtual Phones: UI Screens & Benchmarking Data

> **Purpose:** Pre-packaged simulated hardware benchmarks and Android screen mockups for inclusion in project report, slides, and viva presentations.

---

## 1. 🚀 Phone Tier 1: High-End Flagship Smartphone

### Device Profile
* **Target Hardware:** Snapdragon 8 Gen 3 / Google Tensor G3 class SoC
* **RAM & Storage:** 12 GB LPDDR5X RAM, UFS 4.0 storage
* **Execution Provider:** ONNX Runtime CPU (4 threads, high-performance ARM Cortex-X4 cores)

### App Results Screen Representation (Flagship Mode)

```
┌─────────────────────────────────────────────────────────────┐
│ 📱 TARGET HARDWARE: High-End Flagship (Simulated)           │
├─────────────────────────────────────────────────────────────┤
│ COMPARISON TABLE                                            │
│ Metric              │ Conv-Small   │ Hybrid-Medium│ V2      │
├─────────────────────┼──────────────┼──────────────┼─────────┤
│ Model Size          │ 10.48 MB     │ 44.32 MB     │ 14.00 MB│
│ Load Time           │ 14.2 ms      │ 42.6 ms      │ 18.5 ms │
│ Avg Latency (P50)   │ 3.85 ms      │ 11.40 ms     │ 6.10 ms │
│ P90 Latency         │ 4.10 ms      │ 12.30 ms     │ 6.60 ms │
│ P95 Latency         │ 4.25 ms      │ 12.80 ms     │ 6.90 ms │
│ P99 Latency         │ 4.40 ms      │ 13.50 ms     │ 7.20 ms │
│ Jitter (StdDev)     │ ±0.18 ms     │ ±0.52 ms     │ ±0.28 ms│
│ Throughput          │ 259.7 img/s  │ 87.7 img/s   │ 163.9/s │
│ Top-1 Accuracy      │ 74.2%        │ 79.8%        │ 71.5%   │
├─────────────────────────────────────────────────────────────┤
│ ⚡ FASTEST: MobileNetV4-Conv-Small (3.85 ms)               │
│ 🎯 MOST ACCURATE: MobileNetV4-Hybrid-Medium (79.8%)         │
│ 📊 RECOMMENDATION: Hybrid-Medium is viable (<15ms SLA).     │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. ⚖️ Phone Tier 2: Mid-Range Smartphone

### Device Profile
* **Target Hardware:** Snapdragon 7 Gen 1 / MediaTek Dimensity 7000 class SoC
* **RAM & Storage:** 8 GB LPDDR4X RAM, UFS 3.1 storage
* **Execution Provider:** ONNX Runtime CPU (4 threads, balanced ARM Cortex-A78 cores)

### App Results Screen Representation (Mid-Range Mode)

```
┌─────────────────────────────────────────────────────────────┐
│ 📱 TARGET HARDWARE: Mid-Range Phone (Simulated)             │
├─────────────────────────────────────────────────────────────┤
│ COMPARISON TABLE                                            │
│ Metric              │ Conv-Small   │ Hybrid-Medium│ V2      │
├─────────────────────┼──────────────┼──────────────┼─────────┤
│ Model Size          │ 10.48 MB     │ 44.32 MB     │ 14.00 MB│
│ Load Time           │ 38.5 ms      │ 115.0 ms     │ 52.0 ms │
│ Avg Latency (P50)   │ 10.85 ms     │ 32.50 ms     │ 17.40 ms│
│ P90 Latency         │ 11.50 ms     │ 35.20 ms     │ 18.80 ms│
│ P95 Latency         │ 12.00 ms     │ 36.80 ms     │ 19.50 ms│
│ P99 Latency         │ 12.60 ms     │ 38.50 ms     │ 20.20 ms│
│ Jitter (StdDev)     │ ±0.45 ms     │ ±1.48 ms     │ ±0.78 ms│
│ Throughput          │ 92.2 img/s   │ 30.8 img/s   │ 57.5/s  │
│ Top-1 Accuracy      │ 74.2%        │ 79.8%        │ 71.5%   │
├─────────────────────────────────────────────────────────────┤
│ ⚡ FASTEST: MobileNetV4-Conv-Small (10.85 ms)              │
│ 🛡️ MOST STABLE: MobileNetV4-Conv-Small (±0.45 ms Jitter)  │
│ 📊 RECOMMENDATION: Conv-Small is optimal for mid-range.    │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 🐢 Phone Tier 3: Low-End Budget Smartphone

### Device Profile
* **Target Hardware:** Snapdragon 680 / MediaTek Helio G85 class SoC
* **RAM & Storage:** 4 GB LPDDR4X RAM, eMMC 5.1 storage
* **Execution Provider:** ONNX Runtime CPU (4 threads, efficiency ARM Cortex-A73/A53 cores)

### App Results Screen Representation (Budget Mode)

```
┌─────────────────────────────────────────────────────────────┐
│ 📱 TARGET HARDWARE: Low-End Budget Phone (Simulated)        │
├─────────────────────────────────────────────────────────────┤
│ COMPARISON TABLE                                            │
│ Metric              │ Conv-Small   │ Hybrid-Medium│ V2      │
├─────────────────────┼──────────────┼──────────────┼─────────┤
│ Model Size          │ 10.48 MB     │ 44.32 MB     │ 14.00 MB│
│ Load Time           │ 92.5 ms      │ 275.0 ms     │ 128.0 ms│
│ Avg Latency (P50)   │ 26.60 ms     │ 79.60 ms     │ 42.60 ms│
│ P90 Latency         │ 28.50 ms     │ 86.40 ms     │ 46.20 ms│
│ P95 Latency         │ 29.80 ms     │ 91.20 ms     │ 48.10 ms│
│ P99 Latency         │ 31.40 ms     │ 96.50 ms     │ 50.80 ms│
│ Jitter (StdDev)     │ ±1.12 ms     │ ±4.25 ms     │ ±1.85 ms│
│ Throughput          │ 37.6 img/s   │ 12.6 img/s   │ 23.5/s  │
│ Top-1 Accuracy      │ 74.2%        │ 79.8%        │ 71.5%   │
├─────────────────────────────────────────────────────────────┤
│ ⚡ FASTEST: MobileNetV4-Conv-Small (26.60 ms)              │
│ ⚠️ WARNING: Hybrid-Medium exceeds 75ms latency SLA.        │
│ 📊 RECOMMENDATION: Conv-Small mandatory on budget phones.   │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 📊 Multi-Phone Comparative Chart Summary

```
AVERAGE LATENCY COMPARISON (ms - Lower is Better)

High-End Flagship :  [Conv-Small: 3.8ms] [V2: 6.1ms]   [Hybrid-Medium: 11.4ms]
Mid-Range Phone   :  [Conv-Small: 10.8ms]    [V2: 17.4ms]      [Hybrid-Medium: 32.5ms]
Low-End Budget    :  [Conv-Small: 26.6ms]           [V2: 42.6ms]             [Hybrid-Medium: 79.6ms]
```
