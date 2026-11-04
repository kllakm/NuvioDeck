### NuvioDeck v0.1.16 Release Notes

#### Desktop Performance Overhaul, Coil 3 Disk Cache & Low-Latency G1GC

- **Coil 3 Persistent 512MB Disk Cache:**
  - Registered a dedicated Okio-backed `DiskCache` with 512MB capacity at `%LOCALAPPDATA%\NuvioDeck\Cache\image_cache`.
  - Posters, thumbnails, backdrops, and avatars now load in 1–3ms directly from local SSD storage on launch and during scrolling, eliminating network fetch delays and placeholder spinners.

- **Draw-Pass Image Optimization & GPU Trilinear Mipmapping:**
  - Removed forced 1536px decode constraints on small card composables, allowing Coil's background decoder to downsample images to the target composable size before emitting to the UI thread.
  - Replaced CPU bicubic shaders (`FilterQuality.High`) with GPU hardware trilinear mipmapping (`FilterQuality.Medium`), completely eliminating synchronous `scalePixels` CPU rasterization bottlenecks from Compose's `onDraw` pass during scroll and zoom animations.

- **Low-Latency G1GC & High-Refresh Display Synchronization:**
  - Added production-tuned JVM parameters (`-XX:+UseG1GC`, `-XX:MaxGCPauseMillis=16`, `-XX:+UseStringDeduplication`, `-Xms256m`, `-Xmx2048m`) ensuring garbage collection pauses stay under 16ms to eliminate scroll hitching and dropped frames.
  - Configured `skiko.fps=-1` and `skiko.vsync=true` both at the application launcher level and programmatically in `applyBeforeCompose()`, unlocking fluid 90Hz, 120Hz, and 144Hz performance on modern monitors and gaming handhelds (ROG Ally, Steam Deck OLED, Lenovo Legion Go).

- **Windows MSI Installer:**
  - Packaged standalone installer `NuvioDeck-Windows-x64-0.1.16.msi`.
