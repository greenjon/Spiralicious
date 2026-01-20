# Spirals Rendering System Documentation

This document describes the advanced rendering techniques used in the Spirals application, focusing on two core technical approaches:

1. **Shared OpenGL Context Strategy**: How multiple GLSurfaceView instances efficiently share textures
2. **Feedback Rendering System**: The ping-pong FBO technique for creating visual trails and effects

## Table of Contents

1. [Rendering Architecture Overview](#rendering-architecture-overview)
2. [Shared OpenGL Context Strategy](#shared-opengl-context-strategy)
   - [Implementation](#shared-context-implementation)
   - [Context Sharing Mechanics](#context-sharing-mechanics)
   - [Preview Architecture](#preview-architecture)
3. [Ping-Pong Feedback Rendering](#ping-pong-feedback-rendering)
   - [Technical Background](#technical-background)
   - [Implementation](#ping-pong-implementation)
   - [Feedback Shader](#feedback-shader)
4. [Complete Rendering Pipeline](#complete-rendering-pipeline)
   - [Mixer Mode Rendering](#mixer-mode-rendering)
   - [Single Mandala Mode](#single-mandala-mode)
5. [Performance Considerations](#performance-considerations)
6. [Debugging and Troubleshooting](#debugging-and-troubleshooting)

## Rendering Architecture Overview

The Spirals app uses OpenGL ES 3.0 for real-time rendering of generative visuals. The system is built around several key components:

- **SpiralRenderer**: The main renderer class that handles drawing mandalas, applying effects, and compositing multiple visual sources
- **SpiralSurfaceView**: A custom GLSurfaceView implementation that uses shared EGL contexts
- **SharedEGLContextFactory**: Manages the creation and sharing of OpenGL contexts
- **SimpleBlitHelper**: A utility class for rendering shared textures in secondary contexts

The rendering pipeline supports two main modes:
1. **Single Mandala Mode**: Renders a single mandala with optional feedback effects
2. **Mixer Mode**: Combines multiple visual sources with a complex mixing and effects pipeline

## Shared OpenGL Context Strategy

### Shared Context Implementation

The app implements texture sharing across multiple GLSurfaceView instances using OpenGL's context sharing capabilities. This approach enables efficient preview rendering without duplicating large texture resources.

Key classes involved:
- **SharedContextManager**: Singleton that tracks the primary OpenGL context and reference count
- **SharedEGLContextFactory**: Factory class that creates shared EGL contexts

```kotlin
object SharedContextManager {
    @Volatile
    var mainContext: EGLContext? = null
    var refCount = 0
}

class SharedEGLContextFactory : GLSurfaceView.EGLContextFactory {
    // Creates shared OpenGL contexts that share texture/buffer resources
}
```

### Context Sharing Mechanics

In OpenGL ES, context sharing allows multiple rendering contexts to access the same resource objects. 

**Shared resources include:**
- Textures
- Vertex buffers (VBOs)
- Framebuffer objects (FBOs)

**Non-shared resources:**
- Shader programs
- Vertex array objects (VAOs)
- Program state (active texture units, blend modes, etc.)

The implementation follows a primary/secondary context pattern:
1. **Primary context creation**:
   - The first renderer to initialize creates the primary context
   - This context becomes the reference for all shared contexts
   - It manages the master texture pool and performs the main rendering

2. **Secondary context creation**:
   - Secondary contexts are created with a reference to the primary context
   - They can read textures created by the primary context but must create their own shader programs and VAOs

3. **Reference counting**:
   - The SharedContextManager tracks how many contexts are sharing resources
   - The primary context is only destroyed when all secondary contexts have been destroyed

### Preview Architecture

The preview system uses shared contexts for efficient rendering of multiple views:

1. **Main Rendering Pipeline**:
   - Renders all visual sources into a pool of shared textures in the primary context
   - Stores output textures for slots 1-4, mixer A, mixer B, and the final output
   - Updates these textures continuously at full frame rate

2. **Preview Components**:
   - `SpiralPreview` and `StripPreview` components create GLSurfaceView instances with shared contexts
   - Each preview has its own SimpleBlitHelper with shaders and VAOs
   - Previews only sample from the shared textures created by the primary context
   - Preview rendering is throttled to 30 FPS for efficiency

This architecture allows for responsive UI with multiple live previews without duplicating expensive rendering work or texture data.

## Ping-Pong Feedback Rendering

### Technical Background

Feedback effects in video synthesis involve feeding the output of a renderer back into its own input, creating trails, echo effects, and other recursive visual patterns. In OpenGL, this requires a technique called "ping-pong rendering" using multiple framebuffers.

**Why ping-pong is necessary:**
- In OpenGL, you cannot read from and write to the same texture in a single render pass
- Two textures are used alternately as source and destination to create feedback loops

### Ping-Pong Implementation

The Spirals app implements ping-pong feedback rendering at two levels:

1. **Per-slot feedback**: Each of the four mixer slots has its own independent feedback system
2. **Final mixer feedback**: The final composite output has a separate feedback system

The implementation uses these key data structures:

```kotlin
// Per-slot feedback: 2 textures per slot (ping-pong)
private val slotFBTextures = Array(4) { IntArray(2) }
private val slotFBFramebuffers = Array(4) { IntArray(2) }
private val slotFBIndex = IntArray(4) { 0 }

// Final output feedback
private val finalFBTextures = IntArray(2)
private val finalFBFramebuffers = IntArray(2)
private var finalFBIndex = 0

// Temp rendering target
private var currentFrameFBO: Int = 0
private var currentFrameTexture: Int = 0
```

The feedback rendering process follows these steps:

1. Render the current frame to a temporary texture (`currentFrameTexture`)
2. Bind the next feedback framebuffer in the ping-pong pair
3. Use a special shader to blend the current frame with the previous feedback texture
4. Apply transformations (zoom, rotation, blur) to the feedback
5. Swap the ping-pong indices for the next frame
6. Copy the final result to the output texture

### Feedback Shader

The `feedback_fragment.glsl` shader handles the core of the feedback effect. It performs:

1. **Coordinate transformation**:
   - Scales the UV coordinates for zoom effects
   - Applies rotation matrix for spinning effects

2. **Optional blur**:
   - Applies a 5-tap blur filter when blur is enabled
   - Uses weighted samples to maintain energy

3. **Color shifting**:
   - Converts RGB to HSV
   - Applies hue shift
   - Converts back to RGB

4. **Blend Modes**:
   - Uses a max blend operation to prevent washout
   - Applies gain to compensate for energy loss from effects
   - Blends between live frame and feedback based on gain parameter

The most critical aspects of the shader are the coordinate transformations and the blending logic, which together create the persistent feedback effect while preventing either complete decay or white-out.

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