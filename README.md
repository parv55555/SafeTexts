# SafeTexts: On-Device AI for real time scam detection

![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-%23EE4C2C.svg?style=for-the-badge&logo=PyTorch&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)

An edge-computing security application that utilizes a quantized PyTorch MobileBERT NLP model to detect malicious intent and social engineering tactics in real-time. Engineered strictly for on-device inference to guarantee zero-latency execution and absolute data privacy.

## 🚀 System Architecture

Unlike traditional client-server AI applications, SafeTexts decouples inference from the cloud. The system pipeline is structured to execute entirely within the mobile hardware constraints:

`Message input through Notification listener` $\rightarrow$ `Stored into room DB` $\rightarrow$ `Fine tuned , 8 bit quantized SLM(A0908i.ptl)` $\rightarrow$ `UI Alert State Overlay`

### Why Edge ML? (Architectural Decisions)
* **Zero Latency:** Bypassing cloud API routing allows for instant intent analysis, critical for real-time threat detection.
* **Absolute Privacy:** User data never leaves the device. The NLP tensor constraints were optimized to run locally, eliminating server-side data harvesting risks.
* **Offline Resiliency:** The application maintains 100% core functionality in zero-connectivity environments.

## ⚙️ Performance Benchmarking
*Note: Benchmarks recorded via Android Studio Profiler on physical device hardware.*

* **Inference Latency:** ~200 ms per request
* **Peak Memory Footprint:** ~190 MB RAM allocated during active inference
* **Model Size:** INT 8 Quantized model (A0908i) `.ptl` payload optimized to 30 MB

## 💻 Technical Stack

* **Frontend & Core Engine:** Kotlin, Android SDK, XML
* **Language processing:** PyTorch Lite, MobileBERT (INT 8 Quantized for Edge Processing)
* **Concurrency:** Kotlin Coroutines (Main-thread unblocking during tensor operations)

## 🛣️ Engineering Roadmap
  **Phase 1:** Core UI deployment and native Android integration.
  **Phase 2:** PyTorch quantization and embedding of the NLP model for local edge inference.
  **Phase 3:** Development of a notification listener and room db for the storing the messages with autodelete in 14 days ,user's digital behaviour analyser for more accurate predictions & monitoring system for the installation of remote access apps that scammers use.

---
*Engineered & architected by Parv Khandelwal*
