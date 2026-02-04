# Spirals Project TODO List

This document tracks the necessary tasks to bring the Desktop version of Spirals to feature parity with the Android version.

## Migration Status

### Where We Were (Fully Functioning Android-Only App)
- **Monolithic Android Renderer**: The rendering logic was encapsulated in `SpiralRenderer` within the `:app` module, tightly coupled to Android's `GLES30`, `GLSurfaceView`, and `Context` (for asset loading).
- **Android-Specific Architecture**: The system relied on `SharedEGLContextFactory` for context sharing across multiple `SpiralSurfaceView` instances (main view + previews).
- **Direct UI Integration**: `MainActivity` and editor screens (like `MixerEditorScreen`) communicated directly with `SpiralRenderer` and `SpiralSurfaceView` to control parameters (`setRatio`, `getMixerParam`) and visual sources (`MandalaVisualSource`, `MandalaParams`).
- **Sophisticated Pipeline**: The renderer implemented a complex pipeline involving:
    - Shared texture pools for mixer slots.
    - Ping-pong FBOs for feedback effects (trails, decay).
    - specialized shader programs (Mandala, Feedback, Mixer, Trail).
    - `SimpleBlitHelper` for rendering shared textures in secondary contexts (previews).

### Where We Are Now (Partially Built Android + Linux App)
- **Common Rendering Interface**: We have established `ISpiralRenderer` as a common interface in `:common`. This interface abstracts the core rendering lifecycle (`onSurfaceCreated`, `onDrawFrame`, `setRatio`).
- **Platform-Specific Implementations**:
    - **Android**: `app/.../display/SpiralRenderer.kt` now implements `ISpiralRenderer`. It has been refactored to use `readShaderResource` from `:common` for loading shaders, removing some direct dependency on `Context` for assets. However, it is currently **disconnected** from the parameter update system (`MandalaParams`, `MixerPatch`) to satisfy compilation of the interface change. `SpiralSurfaceView` has commented-out methods that previously linked the UI to the renderer.
    - **Desktop**: `desktop/.../DesktopSpiralRenderer.kt` implements `ISpiralRenderer` using LWJGL (`GL30`). It also uses the common `readShaderResource`. It is currently a "headless" renderer implementation without a hosting window or UI loop.
- **Abstracted Shader Loading**: A common `expect/actual` function `readShaderResource` now handles loading GLSL files from assets (Android) or resources (Desktop).
- **Broken Integrations**:
    - `MainActivity` and other UI screens in `:app` are likely broken because `SpiralRenderer` no longer exposes methods like `getMixerParam`.
    - `SpiralSurfaceView` is essentially a hollow shell that initializes the renderer but cannot accept mixer state or parameter updates.
    - The "Shared Context" strategy on Android remains untouched but is currently isolated from the new common interface usage in the UI.

The immediate goal is to rebuild the UI layer ("Common UI for Rendering") to bridge the gap between the common `ISpiralRenderer` and the platform-specific views (`AndroidView`, Desktop equivalent), and to re-integrate the parameter/modulation system (`MandalaVisualSource`) in a cross-platform way.

---

## 1. Rendering System Abstraction

The goal is to move the rendering logic to `:common` and have platform-specific implementations in `:app` and `:desktop`.

-   [x] **Create `expect/actual` Renderer Interface**:
    -   [x] Define an `expect interface ISpiralRenderer` in `commonMain`. This interface should contain all essential rendering methods like `onSurfaceCreated()`, `onSurfaceChanged(width, height)`, `onDrawFrame()`, `setRatio()`, and `clearFeedback()`.
    -   [x] Move the existing `ISpiralRenderer.kt` from `:app` to `common/src/commonMain/kotlin/llm/slop/spirals/display/`.

-   [x] **Refactor Android Renderer**:
    -   [x] Modify `app/src/main/java/llm/slop/spirals/display/SpiralRenderer.kt` to be an `actual` implementation of the common `ISpiralRenderer`.
    -   [x] Update `SpiralSurfaceView.kt` to work with the refactored renderer.

-   [x] **Refactor Desktop Renderer**:
    -   [x] Modify `desktop/src/jvmMain/kotlin/llm/slop/spirals/DesktopSpiralRenderer.kt` to be an `actual` implementation of the common `ISpiralRenderer`.

-   [x] **Abstract Shader Loading**:
    -   [x] The current `DesktopSpiralRenderer` reads shaders from resources using Java-specific methods. Create an `expect fun readShaderResource(path: String): String` in `commonMain` so each platform can implement its own resource loading.

-   [ ] **Create Common UI for Rendering**:
    -   [ ] **Move Android Rendering Classes to Common**: Move `SpiralRenderer.kt`, `SpiralSurfaceView.kt`, `ShaderHelper.kt`, `SharedEGLContextFactory.kt` from `:app` to `:common/src/androidMain/...`. This allows the common module to implement the `actual` composable using these views.
    -   [ ] **Define Common Surface Composable**: Create `expect @Composable fun SpiralSurface(modifier: Modifier)` in `commonMain`.
    -   [ ] **Implement Android Surface**: Implement `actual fun SpiralSurface` in `androidMain`. It should wrap `SpiralSurfaceView` using `AndroidView`.
    -   [ ] **Implement Desktop Surface**: Implement `actual fun SpiralSurface` in `desktopMain`. This requires setting up an LWJGL `GLCanvas` (or `GLJPanel`) inside a `SwingPanel` (Compose for Desktop interop) and attaching the `DesktopSpiralRenderer`.
    -   [ ] **Expand Renderer Interface**: Add missing methods to `ISpiralRenderer` needed by the UI: `getMixerParam(name: String): ModulatableParameter?`, `setVisualSource(source: VisualSource)`.
    -   [ ] **Re-enable Parameter System**: Uncomment `MandalaVisualSource` and related classes in `common`. Ensure the renderer's `onDrawFrame` calls `visualSource.update()` and applies parameters to uniforms.

## 2. Audio System Abstraction

The goal is to abstract the audio input and analysis engine so that it can be used on both Android and Desktop.

-   [ ] **Create `expect/actual` Audio Engine Interface**:
    -   [ ] Define an `expect interface IAudioEngine` in `commonMain`. It should include methods like `start()`, `stop()`, and a way to access modulation signals (e.g., `getAmplitudeCv()`, `getBeatClock()`).
    -   [ ] Move platform-agnostic logic from `AudioEngine.kt` (like the CV signal generation) into a common base class or helper.

-   [ ] **Refactor Android Audio Engine**:
    -   [ ] Modify `app/src/main/java/llm/slop/spirals/cv/processors/AudioEngine.kt` to be an `actual` implementation of the common `IAudioEngine`. It will continue to use `AudioRecord` and other Android-specific APIs.

-   [ ] **Implement Desktop Audio Engine**:
    -   [ ] Create a new `actual class DesktopAudioEngine` in `desktopMain`.
    -   [ ] Use a JVM-compatible library like `javax.sound.sampled` to capture microphone input.
    -   [ ] Implement the same audio analysis (FFT, onset detection) as the Android version to provide consistent audio-reactive behavior.

## 3. General Desktop Parity

-   [ ] **Implement `AppConfig` for Desktop**:
    -   The current desktop implementation of `AppConfig` has a `TODO`.
    -   **File**: `common/src/desktopMain/kotlin/llm/slop/spirals/platform/AppConfig.kt`
    -   **Task**: Implement `saveNavStack` and `loadNavStack` using a file-based approach (e.g., storing the JSON in a settings file in the user's home directory).

-   [ ] **Desktop UI Shell**:
    -   [ ] Share the Compose UI from `:app` in the `:desktop` module. `MainActivity.kt` can serve as a template for `desktop/Main.kt`.
    -   [ ] Implement window management (sizing, fullscreen).
    -   [ ] Handle keyboard input for shortcuts (some of this is already in `MainActivity`).

-   [ ] **Verify All Editor Screens**:
    -   [ ] Ensure every editor screen (`Mandala`, `Set`, `Mixer`, `Show`, `RandomSet`) is fully functional on the desktop target.

## 4. TODOs from Code Comments

-   [ ] **Implement `AppConfig` for Desktop**
    -   **Location**: `common/src/desktopMain/kotlin/llm/slop/spirals/platform/AppConfig.kt`
    -   **Comment**: `// TODO: Implement for Desktop`
    -   **Task**: This is the same task as listed under "General Desktop Parity". It involves implementing file-based storage for the navigation stack and startup mode.

-   [ ] **Review Data Backup Rules**
    -   **Location**: `app/src/main/res/xml/data_extraction_rules.xml`
    -   **Comment**: `<!-- TODO: Use <include> and <exclude> to control what is backed up. -->`
    -   **Task**: This is an Android-specific task to configure the Auto Backup feature correctly.
