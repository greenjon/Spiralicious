# Spirals Rendering System Documentation

This document describes the advanced rendering techniques used in the Spirals application, focusing on two core technical approaches:

1. **Shared OpenGL Context Strategy**: How multiple GLSurfaceView instances efficiently share textures
2. **Feedback Rendering System**: The ping-pong FBO technique for creating visual trails and effects

## Table of Contents

1. [Rendering Architecture Overview](#rendering-architecture-overview)
2. [The Rendering Process](#the-rendering-process)
   - [Frame-by-Frame Pipeline](#frame-by-frame-pipeline)
   - [Parameter Update System](#parameter-update-system)
   - [Render Target Management](#render-target-management)
3. [Shader Programs and Their Purposes](#shader-programs-and-their-purposes)
   - [Main Shader Programs](#main-shader-programs)
   - [Uniform Parameters](#uniform-parameters)
   - [Specialized Visual Effects](#specialized-visual-effects)
4. [Texture Management](#texture-management)
   - [Texture Pool](#texture-pool)
   - [Texture Creation and Parameters](#texture-creation-and-parameters)
   - [Resolution Strategy](#resolution-strategy)
5. [Frame Buffer Objects and Their Usage](#frame-buffer-objects-and-their-usage)
   - [FBO Architecture](#fbo-architecture)
   - [Ping-Pong Technique](#ping-pong-technique)
   - [FBO Lifecycle](#fbo-lifecycle)
   - [Render-to-Texture Pipeline](#render-to-texture-pipeline)
6. [The Feedback Mechanism and Visual Effects](#the-feedback-mechanism-and-visual-effects)
   - [Feedback Architecture](#feedback-architecture)
   - [Effect Parameters](#effect-parameters)
   - [Visual Transformations](#visual-transformations)
   - [Compositing Techniques](#compositing-techniques)
7. [Multi-Context Rendering for UI Previews](#multi-context-rendering-for-ui-previews)
   - [Context Sharing Mechanics](#context-sharing-mechanics)
   - [SimpleBlitHelper Implementation](#simpleblithelper-implementation) 
   - [Preview Performance Optimizations](#preview-performance-optimizations)
8. [Shared OpenGL Context Strategy](#shared-opengl-context-strategy)
   - [Implementation](#shared-context-implementation)
   - [Resource Sharing Rules](#resource-sharing-rules)
   - [Reference Counting](#reference-counting)
9. [Complete Rendering Pipeline](#complete-rendering-pipeline)
   - [Mixer Mode Rendering](#mixer-mode-rendering)
   - [Single Mandala Mode](#single-mandala-mode)
10. [Performance Considerations](#performance-considerations)
11. [Debugging and Troubleshooting](#debugging-and-troubleshooting)

## Rendering Architecture Overview

The Spirals app uses OpenGL ES 3.0 for real-time rendering of generative visuals. The system is built around several key components:

- **SpiralRenderer**: The main renderer class that handles drawing mandalas, applying effects, and compositing multiple visual sources
- **SpiralSurfaceView**: A custom GLSurfaceView implementation that uses shared EGL contexts
- **SharedEGLContextFactory**: Manages the creation and sharing of OpenGL contexts
- **SimpleBlitHelper**: A utility class for rendering shared textures in secondary contexts

The rendering pipeline supports two main modes:
1. **Single Mandala Mode**: Renders a single mandala with optional feedback effects
2. **Mixer Mode**: Combines multiple visual sources with a complex mixing and effects pipeline

## The Rendering Process

### Frame-by-Frame Pipeline

The frame rendering process in `SpiralRenderer.onDrawFrame()` follows this sequence:

1. **Initialization Check**: Verifies that GL resources are properly initialized
   ```kotlin
   if (program == 0 || screenWidth == 0) return
   ```

2. **Feedback Buffer Clearing** (when needed): Clears feedback buffers when sources change
   ```kotlin
   if (clearFeedbackNextFrame) {
       // Clear per-slot and final feedback buffers
       for (slot in 0..3) {
           for (i in 0..1) {
               GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, slotFBFramebuffers[slot][i])
               GLES30.glClearColor(0f,0f,0f,0f)
               GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
           }
       }
       // Clear final buffers (similar code)
       clearFeedbackNextFrame = false
   }
   ```

3. **Parameter Update**: Evaluates all modulatable parameters for the current frame
   ```kotlin
   visualSource?.update() // For single-mandala mode
   for (i in 0..3) slotSources[i].update() // For the 4 mixer slots
   mixerParams.values.forEach { it.evaluate() } // Evaluates all mixers
   ```

4. **Mode Selection**: Determines whether to render in mixer or single mandala mode
   ```kotlin
   if (p != null) {
       // Mixer mode rendering path
   } else {
       // Single mandala mode rendering path
   }
   ```

5. **Rendering Execution**: Performs the actual rendering (details in subsequent sections)

6. **Frame Completion**: Ensures all GL commands are submitted and displayed
   ```kotlin
   // Submit all rendering commands to GPU
   GLES30.glFlush()
   
   // Blit the selected texture to the screen
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
   GLES30.glViewport(0, 0, screenWidth, screenHeight)
   // ... drawing code ...
   ```

### Parameter Update System

The parameter update system uses `ModulatableParameter` objects that can be influenced by:

1. **Base Values**: Static values set directly by the user interface
2. **Modulators**: Dynamic inputs from CV (Control Voltage) sources like:
   - Beat-synchronized oscillators
   - Audio amplitude extractors
   - LFOs (Low Frequency Oscillators)

Each frame, all parameters are evaluated by:

```kotlin
// In SpiralRenderer.onDrawFrame()
visualSource?.update()
for (i in 0..3) slotSources[i].update()
mixerParams.values.forEach { it.evaluate() }

// In ModulatableParameter.evaluate()
override fun evaluate(): Float {
    // Start with base value
    var result = baseValue
    
    // Apply all active modulators
    for (modulator in modulators) {
        val modulationValue = modulator.source.getValue() // Get CV signal value
        result = modulator.apply(result, modulationValue) // Apply modulation logic
    }
    
    // Cache the result
    _value = result.coerceIn(0f, 1f)
    return _value
}
```

### Render Target Management

The renderer uses a systematic approach to manage render targets:

1. **Fixed Resolution**: All rendering occurs at a consistent 1920×1080 target resolution
   ```kotlin
   // In SpiralRenderer class
   const val TARGET_WIDTH = 1920
   const val TARGET_HEIGHT = 1080
   ```

2. **Viewport Configuration**: The viewport is adjusted to maintain consistent aspect ratio
   ```kotlin
   GLES30.glViewport(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
   ```

3. **Target Binding**: Before drawing, the appropriate framebuffer is bound
   ```kotlin
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
   ```

4. **Result Handling**: After drawing, results are copied to the appropriate display texture
   ```kotlin
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[targetFboIdx])
   // Drawing code to copy from feedback buffer to master texture
   ```

## Shader Programs and Their Purposes

The Spirals app employs five specialized shader programs, each with a distinct purpose in the rendering pipeline.

### Main Shader Programs

1. **Mandala Shader** (`program`): Renders the primary mandala geometry
   ```kotlin
   program = ShaderHelper.buildProgram(context, R.raw.mandala_vertex, R.raw.mandala_fragment)
   ```
   - **Purpose**: Generates the base geometric patterns with parametric control
   - **Input**: Triangle strip vertices representing the mandala geometry
   - **Output**: Colored mandala patterns with HSV color mapping
   - **Key Features**: 
     - Perceptual depth modeling with radius-based shading
     - Hue sweep for rainbow color patterns
     - Adjustable alpha for transparency

2. **Feedback Shader** (`feedbackProgram`): Processes the feedback effect
   ```kotlin
   feedbackProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.feedback_fragment)
   ```
   - **Purpose**: Creates persistent echo/trail effects by combining current and previous frames
   - **Input**: Two textures - current frame and previous feedback frame
   - **Output**: Combined frame with applied transformations
   - **Key Features**:
     - Zoom and rotation transformation of history texture
     - Optional blur with configurable radius
     - Hue shift for psychedelic color cycling
     - Gain control for feedback intensity

3. **Mixer Shader** (`mixerProgram`): Combines multiple visual sources
   ```kotlin
   mixerProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.mixer_fragment)
   ```
   - **Purpose**: Implements various blending modes between two textures
   - **Input**: Two source textures to be mixed
   - **Output**: Combined texture with applied blend mode
   - **Key Features**:
     - Multiple blend modes (ADD, SCREEN, MULT, MAX, XFADE)
     - Balance control for weighted blending
     - Master alpha for overall opacity

4. **Trail Shader** (`trailProgram`): Renders a texture with alpha control
   ```kotlin
   trailProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.trail_fragment)
   ```
   - **Purpose**: Simple passthrough shader with alpha control for trails
   - **Input**: A texture to be rendered
   - **Output**: The texture with applied alpha
   - **Key Features**: Controlled transparency for layering effects

5. **Blit Shader** (`blitProgram`): Simple texture copy
   ```kotlin
   blitProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.passthrough_fragment)
   ```
   - **Purpose**: Efficiently copies a texture to the current target
   - **Input**: Source texture
   - **Output**: Direct copy of the source texture
   - **Key Features**: Minimal overhead for fast texture display

### Uniform Parameters

The shader programs use a wide range of uniform parameters to control their behavior:

1. **Mandala Shader Uniforms**:
   ```kotlin
   uHueOffsetLocation = GLES30.glGetUniformLocation(program, "uHueOffset")
   uHueSweepLocation = GLES30.glGetUniformLocation(program, "uHueSweep")
   uAlphaLocation = GLES30.glGetUniformLocation(program, "uAlpha")
   uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
   uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")
   uGlobalScaleLocation = GLES30.glGetUniformLocation(program, "uGlobalScale")
   // ...many more for controlling mandala shape
   ```

2. **Feedback Shader Uniforms**:
   ```kotlin
   uFBDecayLoc = GLES30.glGetUniformLocation(feedbackProgram, "uDecay")
   uFBGainLoc = GLES30.glGetUniformLocation(feedbackProgram, "uGain")
   uFBZoomLoc = GLES30.glGetUniformLocation(feedbackProgram, "uZoom")
   uFBRotateLoc = GLES30.glGetUniformLocation(feedbackProgram, "uRotate")
   uFBShiftLoc = GLES30.glGetUniformLocation(feedbackProgram, "uHueShift")
   uFBBlurLoc = GLES30.glGetUniformLocation(feedbackProgram, "uBlur")
   uFBTextureLiveLoc = GLES30.glGetUniformLocation(feedbackProgram, "uTextureLive")
   uFBTextureHistoryLoc = GLES30.glGetUniformLocation(feedbackProgram, "uTextureHistory")
   ```

3. **Mixer Shader Uniforms**:
   ```kotlin
   uMixTex1Loc = GLES30.glGetUniformLocation(mixerProgram, "uTex1")
   uMixTex2Loc = GLES30.glGetUniformLocation(mixerProgram, "uTex2")
   uMixModeLoc = GLES30.glGetUniformLocation(mixerProgram, "uMode")
   uMixBalLoc = GLES30.glGetUniformLocation(mixerProgram, "uBalance")
   uMixAlphaLoc = GLES30.glGetUniformLocation(mixerProgram, "uAlpha")
   ```

### Specialized Visual Effects

The shader programs implement several specialized effects:

1. **HSV Color Transformation**: The mandala shader uses HSV color space for natural color sweeps
   ```glsl
   // Robust HSV to RGB
   vec3 hsv2rgb(vec3 c) {
       vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
       vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
       return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
   }
   
   // Usage:
   float hue = fract(uHueOffset + (vPhase * uHueSweep));
   vec3 rgb = hsv2rgb(vec3(hue, 0.8, V));
   ```

2. **Perceptual Depth**: The mandala shader implements a perceptual curve for depth
   ```glsl
   float rNorm = clamp((r - uMinR) / max(0.001, uMaxR - uMinR), 0.0, 1.0);
   float f = pow(rNorm, 0.7); // Gamma correction for perceptual curve
   float V = 0.85 * (1.0 - uDepth + uDepth * f);
   ```

3. **Advanced Blending Modes**: The mixer shader implements multiple blend equations
   ```glsl
   if (uMode == 0) { // ADD
       result = s1 + s2;
   } else if (uMode == 1) { // SCREEN
       result = 1.0 - (1.0 - s1) * (1.0 - s2);
   } else if (uMode == 2) { // MULT
       result = s1 * s2;
   } else if (uMode == 3) { // MAX
       result = max(s1, s2);
   } else { // XFADE
       result = mix(s1, s2, uBalance);
   }
   ```

4. **Optimized Gaussian Blur**: The feedback shader uses a 5-tap sampling pattern for efficient blur
   ```glsl
   if (uBlur > 0.001) {
       float b = uBlur * 0.004;
       history = texture(uTextureHistory, historyCoord) * 0.4;
       history += texture(uTextureHistory, historyCoord + vec2(b, 0.0)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(-b, 0.0)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(0.0, b)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(0.0, -b)) * 0.15;
   }
   ```

## Texture Management

### Texture Pool

The SpiralRenderer maintains a structured pool of textures that serve different purposes:

1. **Master Textures Array**: Holds the main output textures for all sources
   ```kotlin
   // [0-3]: Output textures for the 4 slots
   // [4]: Output texture for Mixer A (slots 1+2)
   // [5]: Output texture for Mixer B (slots 3+4)
   // [6]: Final output texture (Mixer F)
   private val masterTextures = IntArray(7)
   private val masterFramebuffers = IntArray(7)
   ```

2. **Feedback Textures**: Used for ping-pong rendering of feedback effects
   ```kotlin
   // Per-slot feedback textures (2 per slot)
   private val slotFBTextures = Array(4) { IntArray(2) }
   private val slotFBFramebuffers = Array(4) { IntArray(2) }
   
   // Final output feedback textures (2 textures)
   private val finalFBTextures = IntArray(2)
   private val finalFBFramebuffers = IntArray(2)
   ```

3. **Temporary Frame Texture**: Used as an intermediate buffer in the rendering process
   ```kotlin
   private var currentFrameFBO: Int = 0
   private var currentFrameTexture: Int = 0
   ```

### Texture Creation and Parameters

Textures are created with specific parameters to ensure optimal quality and performance:

```kotlin
private fun setupFBOs() {
    fun cT(w: Int, h: Int): Int {
        val a = IntArray(1)
        GLES30.glGenTextures(1, a, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a[0])
        
        // Create texture with RGBA8 format
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,    // Target
            0,                       // Mip level
            GLES30.GL_RGBA,          // Internal format
            w, h,                    // Width and height
            0,                       // Border
            GLES30.GL_RGBA,          // Format
            GLES30.GL_UNSIGNED_BYTE, // Type
            null                     // Data (null = allocate only)
        )
        
        // Set texture parameters for optimal filtering and wrapping
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        return a[0]
    }
}
```

Key texture parameters include:

1. **Format**: RGBA8 (8 bits per channel) is used for all textures
2. **Filtering**: Linear filtering for both minification and magnification
3. **Wrapping**: CLAMP_TO_EDGE to prevent artifacts at boundaries
4. **No Mipmaps**: All textures use only the base level (0) without mipmaps

### Resolution Strategy

Texture resolution is managed with these principles:

1. **Fixed High Resolution**: All textures are created at TARGET_WIDTH × TARGET_HEIGHT (1920×1080)
   ```kotlin
   // Master output textures
   for (i in 0..6) {
       masterTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT)
       masterFramebuffers[i] = cF(masterTextures[i])
   }
   ```

2. **Aspect Ratio Preservation**: Aspect ratio is maintained throughout the pipeline
   ```kotlin
   // In onSurfaceChanged
   aspectRatio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT.toFloat()
   
   // In renderSource
   GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)
   ```

3. **Scaling for Display**: Final output is scaled to fit the actual display size
   ```kotlin
   // In onDrawFrame
   GLES30.glViewport(0, 0, screenWidth, screenHeight)
   ```

## Frame Buffer Objects and Their Usage

### FBO Architecture

The application uses a hierarchical structure of framebuffer objects (FBOs):

1. **Master Framebuffers**: The primary output targets for each rendering stage
   ```kotlin
   private val masterFramebuffers = IntArray(7) // One for each master texture
   ```

2. **Slot Feedback Framebuffers**: Used for per-slot ping-pong feedback rendering
   ```kotlin
   private val slotFBFramebuffers = Array(4) { IntArray(2) } // 2 per slot
   ```

3. **Final Feedback Framebuffers**: Used for final output ping-pong feedback
   ```kotlin
   private val finalFBFramebuffers = IntArray(2)
   ```

4. **Temporary Framebuffer**: Used as an intermediate step in the feedback process
   ```kotlin
   private var currentFrameFBO: Int = 0
   ```

Each framebuffer is created with a single color attachment (no depth or stencil):

```kotlin
fun cF(t: Int): Int {
    val a = IntArray(1)
    GLES30.glGenFramebuffers(1, a, 0)
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, a[0])
    
    // Attach the texture as the color attachment
    GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,         // Target
        GLES30.GL_COLOR_ATTACHMENT0,   // Attachment point
        GLES30.GL_TEXTURE_2D,          // Texture target
        t,                            // Texture ID
        0                             // Mipmap level
    )
    
    return a[0]
}
```

### Ping-Pong Technique

The ping-pong technique alternates between two framebuffers each frame:

1. **Index Tracking**: Each feedback system has its own index pointer
   ```kotlin
   private val slotFBIndex = IntArray(4) { 0 } // Per-slot indices
   private var finalFBIndex = 0               // Final output index
   ```

2. **Swap Logic**: The active index is swapped after each frame
   ```kotlin
   val fbIdx = fbIndexRef()       // Get current index
   val nextIdx = (fbIdx + 1) % 2  // Calculate next index
   
   // After rendering:
   fbIndexSet(nextIdx)  // Update the index for next frame
   ```

3. **Read/Write Separation**: The technique ensures we never read from and write to the same texture
   ```kotlin
   // Bind the next framebuffer in the pair as the target
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbFramebuffers[nextIdx])
   
   // Read from the current/previous framebuffer
   GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
   GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbTextures[fbIdx])
   ```

### FBO Lifecycle

Framebuffer objects follow this lifecycle:

1. **Creation**: All FBOs are created during `setupFBOs()` which is called from `onSurfaceChanged()`
   ```kotlin
   private fun setupFBOs() {
       // Create all master, slot feedback, and final feedback FBOs
       // ...
   }
   ```

2. **Usage**: During rendering, FBOs are bound before drawing
   ```kotlin
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
   GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
   ```

3. **Cleanup**: FBOs are implicitly destroyed when the GL context is destroyed
   - OpenGL ES automatically cleans up resources when contexts are destroyed
   - The app does not explicitly delete FBOs since they're recreated on context reset

4. **Reset**: When the GL context is lost (e.g., app is backgrounded), all FBO handles are reset
   ```kotlin
   override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
       // Reset all GL resource handles
       currentFrameFBO = 0
       // Reset all other FBO handles
       // ...
   }
   ```

### Render-to-Texture Pipeline

The app uses a sophisticated render-to-texture pipeline:

1. **Multi-Pass Rendering**: Content is rendered through multiple passes
   ```kotlin
   // First pass: Render mandala to temporary frame buffer
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameFBO)
   GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
   render() // Draw the mandala
   
   // Second pass: Apply feedback using the feedback shader
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbFramebuffers[nextIdx])
   GLES30.glUseProgram(feedbackProgram)
   // Combine current frame with feedback history
   
   // Third pass: Copy result to master texture
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[targetFboIdx])
   // Copy from feedback buffer to master texture
   ```

2. **Explicit Attachment Management**: Each FBO is explicitly attached to its texture
   ```kotlin
   GLES30.glFramebufferTexture2D(
       GLES30.GL_FRAMEBUFFER,
       GLES30.GL_COLOR_ATTACHMENT0,
       GLES30.GL_TEXTURE_2D,
       texture,
       0
   )
   ```

3. **Viewport Consistency**: Viewport is properly set for each rendering pass
   ```kotlin
   // Set fixed resolution for consistent rendering
   GLES30.glViewport(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
   ```

4. **Clear State Management**: Each FBO is properly cleared before use
   ```kotlin
   GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
   GLES30.glClearColor(0f, 0f, 0f, 0f)
   GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
   ```

## The Feedback Mechanism and Visual Effects

### Feedback Architecture

The feedback system in Spirals creates visual trails and echo effects through a sophisticated multi-stage process:

1. **Dual-Level Feedback**: The app implements feedback at two levels:
   - **Per-slot feedback**: Each mixer slot (1-4) has its own independent feedback system
   - **Final mixer feedback**: The final output has a separate feedback system

2. **Implementation Strategy**: The `compositeWithFeedback` function encapsulates the core feedback logic:
   ```kotlin
   private fun compositeWithFeedback(
       render: () -> Unit,             // Function that renders the current frame
       fx: Map<String, Float>,         // Effect parameters
       fbTextures: IntArray,           // Ping-pong texture pair
       fbFramebuffers: IntArray,       // Ping-pong FBO pair
       fbIndexRef: () -> Int,          // Function to get current index
       fbIndexSet: (Int) -> Unit,      // Function to set next index
       targetFboIdx: Int               // Index of destination in master texture array
   ) {
       // Implementation details...
   }
   ```

3. **Rendering Flow**: The feedback loop follows these steps:
   1. Render current frame to temporary buffer
   2. Apply feedback shader to combine current frame with feedback history
   3. Swap ping-pong indices
   4. Copy result to the appropriate master texture

4. **Gain Control**: The feedback intensity is controlled by the gain parameter
   ```kotlin
   // Only apply feedback when gain is above threshold
   if (gain > 0.01f) {
       // Feedback processing
       // ...
   }
   ```

### Effect Parameters

The feedback system supports various effect parameters:

1. **Feedback Gain**: Controls the intensity of the feedback effect
   ```kotlin
   val mappedGain = if (gain <= 0.01f) {
       // Special case for "off" position
       0.0f
   } else {
       // Convert 0.01-1.0 range to 1.1-1.25 range for effective feedback
       1.1f + (gain * 0.15f)
   }
   GLES30.glUniform1f(uFBGainLoc, mappedGain)
   ```

2. **Zoom Effect**: Scales the feedback texture
   ```kotlin
   GLES30.glUniform1f(uFBZoomLoc, ((fx["FB_ZOOM"] ?: 0.5f) - 0.5f) * 0.1f)
   ```

3. **Rotation Effect**: Spins the feedback texture
   ```kotlin
   GLES30.glUniform1f(uFBRotateLoc, ((fx["FB_ROTATE"] ?: 0.5f) - 0.5f) * 10f * (PI.toFloat() / 180f))
   ```

4. **Hue Shift**: Creates psychedelic color cycling
   ```kotlin
   GLES30.glUniform1f(uFBShiftLoc, fx["FB_SHIFT"] ?: 0f)
   ```

5. **Blur**: Applies a diffusion effect to the feedback
   ```kotlin
   GLES30.glUniform1f(uFBBlurLoc, fx["FB_BLUR"] ?: 0f)
   ```

### Visual Transformations

The feedback shader (`feedback_fragment.glsl`) applies several visual transformations:

1. **Coordinate Transformation**: Applies zoom and rotation
   ```glsl
   // Transform History Coordinates
   vec2 uv = vTexCoord - 0.5;
   uv *= (1.0 - uZoom);
   float s = sin(uRotate);
   float c = cos(uRotate);
   uv = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);
   vec2 historyCoord = uv + 0.5;
   ```

2. **Blur Effect**: Uses a 5-tap sampling pattern
   ```glsl
   if (uBlur > 0.001) {
       float b = uBlur * 0.004;
       history = texture(uTextureHistory, historyCoord) * 0.4;
       history += texture(uTextureHistory, historyCoord + vec2(b, 0.0)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(-b, 0.0)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(0.0, b)) * 0.15;
       history += texture(uTextureHistory, historyCoord + vec2(0.0, -b)) * 0.15;
   }
   ```

3. **Hue Shift**: Changes colors over time
   ```glsl
   if (uHueShift != 0.0 && history.a > 0.01) {
       vec3 hsv = rgb2hsv(history.rgb);
       hsv.x = fract(hsv.x + uHueShift);
       history.rgb = hsv2rgb(hsv);
   }
   ```

4. **Energy-Preserving Blending**: Prevents energy loss with proper gain compensation
   ```glsl
   // Apply gain to feedback to compensate for energy loss
   vec3 feedbackPart = history.rgb * uGain; // Gain is 1.1-1.25 range
   
   // Blend between live and feedback
   float blendFactor = uGain > 0.01 ? 0.8 : 0.0;
   vec3 composite = mix(live.rgb, max(live.rgb, feedbackPart), blendFactor);
   ```

### Compositing Techniques

The app uses several compositing techniques for visual effects:

1. **Mixer Blending Modes**: The mixer shader supports multiple blend modes
   ```glsl
   if (uMode == 0) { // ADD
       result = s1 + s2;
   } else if (uMode == 1) { // SCREEN
       result = 1.0 - (1.0 - s1) * (1.0 - s2);
   } else if (uMode == 2) { // MULT
       result = s1 * s2;
   } else if (uMode == 3) { // MAX
       result = max(s1, s2);
   } else { // XFADE
       result = mix(s1, s2, uBalance);
   }
   ```

2. **Multi-Level Mixing**: The mixer stages are arranged in a tree structure
   ```kotlin
   // First-level mixing (slots 1+2 → A, slots 3+4 → B)
   renderMixerGroup(4, masterTextures[0], masterTextures[1], "A") 
   renderMixerGroup(5, masterTextures[2], masterTextures[3], "B")
   
   // Second-level mixing (A+B → Final)
   compositeFinalMixer(masterTextures[4], masterTextures[5])
   ```

3. **Alpha Premultiplication**: Ensures correct transparency handling
   ```glsl
   // Pre-multiplied alpha for blending consistency
   fragColor = vec4(rgb * uAlpha, uAlpha);
   ```

4. **Blend Mode Control**: OpenGL blend state is managed for different operations
   ```kotlin
   GLES30.glEnable(GLES30.GL_BLEND)
   GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
   
   // For operations that require no blending:
   GLES30.glDisable(GLES30.GL_BLEND)
   // ... drawing code ...
   GLES30.glEnable(GLES30.GL_BLEND)
   ```

## Multi-Context Rendering for UI Previews

### Preview Component Implementation

The UI preview system uses a modular approach for showing GL content in the interface:

1. **SpiralPreview Composable**: A Compose UI component that creates a GLSurfaceView
   ```kotlin
   @Composable
   fun SpiralPreview(sourceId: String, mainRenderer: SpiralRenderer?, modifier: Modifier = Modifier) {
       // Create GLSurfaceView with shared context
       // ...
   }
   ```

2. **StripPreview Composable**: Similar component used in the mixer interface
   ```kotlin
   @Composable
   fun StripPreview(monitorSource: String, patch: MixerPatch, mainRenderer: SpiralRenderer?) {
       // Create GLSurfaceView with shared context
       // ...
   }
   ```

3. **Renderer Configuration**: Each preview has its own renderer that uses the shared context
   ```kotlin
   AndroidView(
       factory = { ctx ->
           GLSurfaceView(ctx).apply {
               setEGLContextClientVersion(3)
               // Bridge Context
               setEGLContextFactory(SharedEGLContextFactory())
               setRenderer(object : GLSurfaceView.Renderer {
                   // Preview-specific renderer implementation
                   // ...
               })
           }
       },
       // ...
   )
   ```

### SimpleBlitHelper Implementation

The `SimpleBlitHelper` class handles the rendering of shared textures in secondary contexts:

```kotlin
class SimpleBlitHelper {
    private val program: Int
    private val vao: Int
    private val vbo: Int
    private val uTextureLocation: Int

    init {
        // Compile shaders and create program
        // Create quad geometry and VAO
        // ...
    }

    fun drawTexture(textureId: Int) {
        android.opengl.GLES30.glUseProgram(program)
        android.opengl.GLES30.glBindVertexArray(vao)
        android.opengl.GLES30.glActiveTexture(android.opengl.GLES30.GL_TEXTURE0)
        android.opengl.GLES30.glBindTexture(android.opengl.GLES30.GL_TEXTURE_2D, textureId)
        android.opengl.GLES30.glUniform1i(uTextureLocation, 0)
        android.opengl.GLES30.glDrawArrays(android.opengl.GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
}
```

Key features of SimpleBlitHelper:

1. **Self-Contained Resources**: Creates its own shader program, VAO, and VBO for rendering
2. **Minimal Shader**: Uses a simple pass-through shader for efficient texture display
3. **Direct Texture Access**: Directly renders textures from the shared context
4. **Simple API**: Provides a clean interface for drawing textures with a single method call

### Preview Performance Optimizations

The preview system includes several optimizations for efficient rendering:

1. **Throttled Rendering**: Previews request frames at a reduced rate (30 FPS)
   ```kotlin
   LaunchedEffect(sourceId) {
       while (true) {
           delay(33) // Throttled sample (approx. 30 FPS)
           view.value?.requestRender()
       }
   }
   ```

2. **On-Demand Rendering**: Preview GLSurfaceViews use RENDERMODE_WHEN_DIRTY
   ```kotlin
   renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
   ```

3. **Initialization Delay**: Previews wait for the main context to initialize
   ```kotlin
   var isReady by remember { mutableStateOf(false) }
   LaunchedEffect(Unit) {
       delay(100) // Give main renderer time to initialize
       isReady = true
   }
   ```

4. **Minimal Drawing**: Only renders a simple textured quad
   ```kotlin
   // Sample from main renderer's master pool
   val textureId = mainRenderer.getTextureForSource(sourceId)
   if (textureId != 0 && blitHelper != null) {
       blitHelper?.drawTexture(textureId)
   }
   ```

## Shared OpenGL Context Strategy

### Shared Context Implementation

The app implements texture sharing across multiple GLSurfaceView instances using OpenGL's context sharing capabilities:

```kotlin
object SharedContextManager {
    @Volatile
    var mainContext: EGLContext? = null
    var refCount = 0
}

class SharedEGLContextFactory : GLSurfaceView.EGLContextFactory {
    override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
        val attribList = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
        
        synchronized(SharedContextManager) {
            // If no main context exists, create primary context
            if (SharedContextManager.mainContext == null) {
                val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
                // Store as main context
                SharedContextManager.mainContext = context
                SharedContextManager.refCount++
                return context
            }
            
            // Otherwise create a shared context
            val mainCtx = SharedContextManager.mainContext!!
            val context = egl.eglCreateContext(display, config, mainCtx, attribList)
            
            // Fallback to unshared context if sharing fails
            if (context == EGL10.EGL_NO_CONTEXT) {
                return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
            }
            
            SharedContextManager.refCount++
            return context
        }
    }
    
    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        synchronized(SharedContextManager) {
            SharedContextManager.refCount--
            // Preserve primary context if other contexts still exist
            if (context == SharedContextManager.mainContext && SharedContextManager.refCount > 0) {
                return
            }
            // Clear primary context reference if it's being destroyed
            if (context == SharedContextManager.mainContext) {
                SharedContextManager.mainContext = null
            }
            egl.eglDestroyContext(display, context)
        }
    }
}
```

### Resource Sharing Rules

In OpenGL ES, context sharing follows specific rules:

**Shared resources include:**
- Textures and texture data
- Vertex buffer objects (VBOs)
- Pixel buffer objects (PBOs)
- Framebuffer objects (FBOs) (but not their attachment state)
- Renderbuffer objects
- Shader objects and programs

**Non-shared resources:**
- Vertex array objects (VAOs)
- Current state (blend mode, active texture unit, etc.)
- Default framebuffer (screen)
- Individual attachments of framebuffers

**Synchronization Requirements:**
- Shared resources must be explicitly synchronized
- Changes made in one context are not guaranteed to be visible in another until:
  - The context making changes calls `glFlush()` or equivalent
  - The context that needs to see changes ensures proper synchronization

### Reference Counting

The reference counting system ensures proper resource lifecycle management:

1. **Increment on Creation**:
   ```kotlin
   // When creating a context
   SharedContextManager.refCount++
   ```

2. **Decrement on Destruction**:
   ```kotlin
   // When destroying a context
   SharedContextManager.refCount--
   ```

3. **Primary Context Preservation**:
   ```kotlin
   // Don't actually destroy the primary context if other contexts exist
   if (context == SharedContextManager.mainContext && SharedContextManager.refCount > 0) {
       return
   }
   ```

4. **Resource Cleanup**:
   ```kotlin
   // Only actually destroy primary context when refCount = 0
   if (context == SharedContextManager.mainContext) {
       SharedContextManager.mainContext = null
   }
   egl.eglDestroyContext(display, context)
   ```

## Complete Rendering Pipeline

### Mixer Mode Rendering

In mixer mode, the rendering pipeline follows this sequence:

1. **Slot Rendering**: 
   - Each enabled slot (1-4) is rendered independently
   - Slots with mandalas, sets, or random sets use their own feedback systems
   - Output textures are stored in `masterTextures[0-3]`

2. **First-level Mixing**:
   - Mixer A combines slots 1 & 2 (`masterTextures[0]`, `masterTextures[1]`)
   - Output is written to `masterTextures[4]`
   - Mixer B combines slots 3 & 4 (`masterTextures[2]`, `masterTextures[3]`)
   - Output is written to `masterTextures[5]`

3. **Final Mixing**:
   - Final mixer combines outputs from Mixer A and B (`masterTextures[4]`, `masterTextures[5]`)
   - This stage has its own feedback system using ping-pong FBOs
   - Final output is written to `masterTextures[6]`

4. **Display**:
   - The texture selected by `monitorSource` is displayed in the main view
   - Preview components sample from the appropriate textures in the shared pool

### Single Mandala Mode

In single mandala mode, the pipeline is simplified:

1. **Mandala Rendering**:
   - The current mandala is rendered directly
   - A single feedback system (using the final FBO pair) is applied
   - Output is written to `masterTextures[6]`

## Performance Considerations

The shared context and ping-pong feedback systems have been optimized for performance:

1. **Resource Sharing**:
   - Textures are shared between contexts to reduce memory usage
   - Only shader programs and VAOs are duplicated across contexts

2. **Preview Throttling**:
   - Preview rendering is throttled to 30 FPS to reduce power consumption
   - The main rendering continues at full frame rate for smooth performance

3. **Efficient Feedback**:
   - Feedback effects use a gain threshold to disable processing when not in use
   - Blur is only applied when the blur parameter is above a threshold

4. **Render Target Resolution**:
   - All rendering is done at a consistent 1920x1080 resolution for predictable performance
   - Preview components scale this resolution to fit their display size

## Debugging and Troubleshooting

The rendering system includes extensive debugging features:

1. **Detailed Logging**:
   - SharedEGLContextFactory logs context creation, sharing, and destruction
   - Error states and fallback behaviors are documented in logs

2. **Fallback Mechanisms**:
   - Context creation has a fallback path to create unshared contexts if sharing fails
   - This maintains basic functionality even if optimal sharing is not available

3. **Texture Pool Validation**:
   - The system validates texture IDs before use to prevent rendering with invalid textures
   - Zero checks prevent attempting to render with uninitialized textures

4. **Clear on Source Change**:
   - Feedback buffers are cleared when visual sources change
   - This prevents visual artifacts from previous content