# Spirals Architecture Diagrams

This document contains architecture diagrams for the Spirals application's core systems:

1. [CV (Control Voltage) System](#cv-control-voltage-system-architecture)
2. [Video Pipeline](#video-pipeline-architecture)

These diagrams illustrate the key components and their relationships in a format that can be used as a reference for creating visual representations.

## CV (Control Voltage) System Architecture

```
┌─────────────────────┐     ┌────────────────────────────────────────────┐
│    Audio Sources    │     │            CV Signal Generators             │
│                     │     │                                            │
│  ┌───────────────┐  │     │  ┌──────────────┐     ┌─────────────────┐  │
│  │  Microphone   │──┼─────┼─▶│ AudioEngine  │────▶│ AmplitudeCv     │  │
│  └───────────────┘  │     │  │              │     │ (amp, bass, mid,│  │
│  ┌───────────────┐  │     │  │ ┌──────────┐ │     │  high)          │  │
│  │Internal Audio │──┼─────┼─▶│ │  Filters │ │     └─────────────────┘  │
│  └───────────────┘  │     │  │ │(low/mid/ │ │     ┌─────────────────┐  │
│                     │     │  │ │  high)   │ │────▶│ Transient CVs   │  │
└─────────────────────┘     │  │ └──────────┘ │     │ (onset, accent, │  │
                            │  │ ┌──────────┐ │     │  bassFlux)      │  │
┌─────────────────────┐     │  │ │Amplitude │ │     └─────────────────┘  │
│  System Clock       │     │  │ │Extractor │ │                          │
│  ┌───────────────┐  │     │  │ └──────────┘ │     ┌─────────────────┐  │
│  │  CvClock      │──┼─────┼─▶│ BPM Detection│────▶│ BeatPhaseCv     │  │
│  └───────────────┘  │     │  └──────────────┘     └─────────────────┘  │
└─────────────────────┘     │                       ┌─────────────────┐  │
                            │                       │ SampleAndHoldCv │  │
                            │                       └─────────────────┘  │
                            │                                            │
                            └────────────────────────────────────────────┘
                                             │
                                             ▼
                            ┌────────────────────────────────────────────┐
                            │           ModulationRegistry               │
                            │                                            │
                            │  ┌─────────────────────────────┐           │
                            │  │ Raw Signal Storage          │           │
                            │  │ (ConcurrentHashMap)         │           │
                            │  └─────────────────────────────┘           │
                            │  ┌─────────────────────────────┐           │
                            │  │ Signal History Buffers      │           │
                            │  │ (for visualization)         │           │
                            │  └─────────────────────────────┘           │
                            │  ┌─────────────────────────────┐           │
                            │  │ Precision Beat Clock        │           │
                            │  │ (high-precision timing)     │           │
                            │  └─────────────────────────────┘           │
                            │                                            │
                            └────────────────────────────────────────────┘
                                             │
                                             ▼
┌─────────────────────┐     ┌────────────────────────────────────────────┐
│  Signal Modifiers   │     │        ModulatableParameter                │
│                     │     │                                            │
│  ┌───────────────┐  │     │  ┌─────────────────────────────┐           │
│  │   PowerCv     │◀─┼─────┼──│ CvModulator                 │           │
│  └───────────────┘  │     │  │ - Source ID reference       │           │
│  ┌───────────────┐  │     │  │ - Operator (ADD/MUL)        │           │
│  │   GainCv      │◀─┼─────┼──│ - Weight                    │           │
│  └───────────────┘  │     │  │ - Waveform settings         │           │
│  ┌───────────────┐  │     │  │ - LFO/Beat settings         │           │
│  │   OffsetCv    │◀─┼─────┼──└─────────────────────────────┘           │
│  └───────────────┘  │     │                                            │
│  ┌───────────────┐  │     │  ┌─────────────────────────────┐           │
│  │   ClipCv      │◀─┼─────┼──│ Base Value                  │           │
│  └───────────────┘  │     │  └─────────────────────────────┘           │
└─────────────────────┘     │                                            │
                            │  ┌─────────────────────────────┐           │
                            │  │ Evaluation Logic            │           │
                            │  │ (combines base + modulators)│           │
                            │  └─────────────────────────────┘           │
                            │                                            │
                            └────────────────────────────────────────────┘
                                             │
                                             ▼
                            ┌────────────────────────────────────────────┐
                            │             Rendering System                │
                            │                                            │
                            │  ┌─────────────────────────────┐           │
                            │  │ Shader Uniform Parameters   │           │
                            │  └─────────────────────────────┘           │
                            │  ┌─────────────────────────────┐           │
                            │  │ Visual Elements             │           │
                            │  │ (shape, color, motion)      │           │
                            │  └─────────────────────────────┘           │
                            │  ┌─────────────────────────────┐           │
                            │  │ Feedback Effects            │           │
                            │  │ (zoom, rotate, blur)        │           │
                            │  └─────────────────────────────┘           │
                            │                                            │
                            └────────────────────────────────────────────┘
```

### Key Components and Interactions:

1. **Audio Sources**:
   - Microphone and internal audio provide raw input signals
   - System clock provides timing reference

2. **CV Signal Generators**:
   - AudioEngine processes audio with filters and detects beats
   - Various CV sources generate control signals

3. **ModulationRegistry**:
   - Central repository for all CV signals
   - Maintains signal history and precision timing

4. **ModulatableParameter**:
   - Combines base value with multiple modulators
   - Signal modifiers process CV before application
   - Evaluation logic combines all inputs

5. **Rendering System**:
   - Receives final parameter values
   - Applies to visual elements and effects

## Video Pipeline Architecture

```
┌───────────────────────┐   ┌────────────────────────────────────────────────────┐
│     Visual Sources    │   │                 SpiralRenderer                      │
│                       │   │                                                    │
│  ┌─────────────────┐  │   │  ┌──────────────────────────────────────────────┐  │
│  │  MandalaSource  │──┼──▶│  │               Shader Programs                │  │
│  │  (Single Mode)  │  │   │  │                                              │  │
│  └─────────────────┘  │   │  │  ┌────────────┐  ┌────────────┐  ┌─────────┐ │  │
│                       │   │  │  │  program   │  │trailProgram│  │blitProgram│  │
│  ┌─────────────────┐  │   │  │  │ (Mandala   │  │ (Display   │  │(Simple   │  │
│  │  slotSources[4] │──┼──▶│  │  │  Renderer) │  │  Texture)  │  │ Copy)    │  │
│  │  (Mixer Mode)   │  │   │  │  └────────────┘  └────────────┘  └─────────┘ │  │
│  └─────────────────┘  │   │  │                                              │  │
│                       │   │  │  ┌────────────┐  ┌─────────────┐             │  │
└───────────────────────┘   │  │  │feedbackProg│  │mixerProgram │             │  │
                            │  │  │ (Feedback  │  │ (Combine    │             │  │
┌───────────────────────┐   │  │  │  Effects)  │  │  Sources)   │             │  │
│    Modulated Params   │   │  │  └────────────┘  └─────────────┘             │  │
│                       │   │  └──────────────────────────────────────────────┘  │
│  ┌─────────────────┐  │   │                                                    │
│  │  MandalaParams  │──┼──▶│  ┌──────────────────────────────────────────────┐  │
│  │  (from ModSys)  │  │   │  │              Texture Management              │  │
│  └─────────────────┘  │   │  │                                              │  │
│                       │   │  │   ┌─────────────────────┐  ┌──────────────┐  │  │
│  ┌─────────────────┐  │   │  │   │   masterTextures[7] │  │ Current Frame│  │  │
│  │  mixerParams    │──┼──▶│  │   │   (Output Textures) │  │  (Temp FBO)  │  │  │
│  │  (from ModSys)  │  │   │  │   └─────────────────────┘  └──────────────┘  │  │
│  └─────────────────┘  │   │  │                                              │  │
└───────────────────────┘   │  │   ┌─────────────────────┐                    │  │
                            │  │   │ slotFBTextures[4][2]│                    │  │
┌───────────────────────┐   │  │   │ (Per-slot Feedback) │                    │  │
│   Config/State        │   │  │   └─────────────────────┘                    │  │
│                       │   │  │                                              │  │
│  ┌─────────────────┐  │   │  │   ┌─────────────────────┐                    │  │
│  │  monitorSource  │──┼──▶│  │   │  finalFBTextures[2] │                    │  │
│  │  (view selector)│  │   │  │   │  (Final Feedback)   │                    │  │
│  └─────────────────┘  │   │  │   └─────────────────────┘                    │  │
│                       │   │  └──────────────────────────────────────────────┘  │
│  ┌─────────────────┐  │   │                                                    │
│  │  mixerPatch     │──┼──▶│  ┌──────────────────────────────────────────────┐  │
│  │  (mixer config) │  │   │  │         Rendering Pipeline Stages            │  │
│  └─────────────────┘  │   │  │                                              │  │
└───────────────────────┘   │  │  ┌────────────────┐  ┌────────────────────┐  │  │
                            │  │  │ Slot Rendering  │  │ Feedback Processing│  │  │
                            │  │  │ (4 slots)       │  │ (Ping-pong FBOs)   │  │  │
                            │  │  └────────────────┘  └────────────────────┘  │  │
                            │  │                                              │  │
                            │  │  ┌────────────────┐  ┌────────────────────┐  │  │
                            │  │  │ Mixer A/B      │  │ Final Mixer (F)    │  │  │
                            │  │  │ (First level)  │  │ (Second level)     │  │  │
                            │  │  └────────────────┘  └────────────────────┘  │  │
                            │  │                                              │  │
                            │  └──────────────────────────────────────────────┘  │
                            └────────────────────────────────────────────────────┘
                                                   │
                                                   ▼
┌───────────────────────┐   ┌────────────────────────────────────────────────────┐
│   Shared GL Context   │   │                Display Outputs                      │
│                       │   │                                                    │
│ ┌───────────────────┐ │   │  ┌──────────────────────┐  ┌───────────────────┐   │
│ │SharedContextManager│ │   │  │  Main GLSurfaceView  │  │ Preview Components│   │
│ │                   │ │   │  │  (Full-screen view)  │  │ (Thumbnails)      │   │
│ │ - mainContext     │ │   │  └──────────────────────┘  └───────────────────┘   │
│ │ - refCount        │◀┼───┼──┐                           ▲                     │
│ └───────────────────┘ │   │  │                           │                     │
│                       │   │  │                           │                     │
│ ┌───────────────────┐ │   │  │                           │                     │
│ │SharedEGLContext   │ │   │  │                           │                     │
│ │Factory            │ │   │  │                           │                     │
│ │                   │◀┼───┼──┘                           │                     │
│ └───────────────────┘ │   │                              │                     │
│                       │   │  ┌──────────────────────────────────────────────┐  │
│ ┌───────────────────┐ │   │  │         SimpleBlitHelper                     │  │
│ │ SimpleBlitHelper  │ │   │  │ (Preview-specific rendering)                 │  │
│ │ (Render shared    │◀┼───┼──│ - Duplicated for each preview                │  │
│ │  textures)        │ │   │  │ - Uses shared textures from main renderer    │  │
│ └───────────────────┘ │   │  └──────────────────────────────────────────────┘  │
└──────────────────���────┘   └────────────────────────────────────────────────────┘
```

### Key Components and Interactions:

1. **Visual Sources**:
   - MandalaSource for single mode
   - slotSources array for mixer mode
   - Modulated parameters from CV system

2. **SpiralRenderer**:
   - Multiple shader programs for different rendering tasks
   - Sophisticated texture management system
   - Multi-stage rendering pipeline

3. **Texture Management**:
   - masterTextures array for output textures
   - Ping-pong FBO pairs for feedback effects
   - Per-slot and final feedback textures

4. **Rendering Pipeline Stages**:
   - Slot rendering (4 independent sources)
   - Mixer A/B (first-level combination)
   - Final Mixer (second-level combination)
   - Feedback processing at multiple levels

5. **Shared GL Context**:
   - SharedContextManager tracks main context and reference count
   - SharedEGLContextFactory creates contexts that share resources
   - SimpleBlitHelper renders shared textures in preview contexts

6. **Display Outputs**:
   - Main GLSurfaceView for full-screen display
   - Preview components for thumbnails
   - Each preview has its own SimpleBlitHelper

### Notes for Visual Representation:

1. Use color coding to distinguish different types of components:
   - Input/sources (blue)
   - Processing components (green)
   - Storage components (orange)
   - Output components (purple)

2. Arrow types can indicate different kinds of relationships:
   - Solid arrows for direct data flow
   - Dashed arrows for reference/control relationships
   - Dotted arrows for creation/instantiation

3. For complex components like the renderer, consider using nested boxes to show internal structure.

4. Add a legend explaining the symbols and color coding used in the diagram.

## System Integration: CV System and Video Pipeline

The CV System and Video Pipeline are tightly integrated, with modulated parameters directly affecting the rendering process. The following diagram illustrates how these systems interact:

```
┌─────────────────────────────────┐           ┌─────────────────────────────────┐
│        CV System                │           │        Video Pipeline           │
│                                 │           │                                 │
│  ┌───────────────────────────┐  │           │  ┌───────────────────────────┐  │
│  │      Audio Engine         │  │           │  │      SpiralRenderer       │  │
│  │                           │  │           │  │                           │  │
│  │  - Audio Analysis         │  │           │  │  - Shader Programs        │  │
│  │  - Signal Extraction      │  │           │  │  - Texture Management     │  │
│  │  - BPM Detection          │  │           │  │  - Rendering Pipeline     │  │
│  └───────────────┬───────────┘  │           │  └───────────┬───────────────┘  │
│                  │              │           │              │                  │
│  ┌───────────────▼───────────┐  │           │  ┌───────────▼──────────��────┐  │
│  │    Modulation Registry    │  │           │  │    Parameter Application   │  │
│  │                           │  │           │  │                           │  │
│  │  - Signal Storage         │  │           │  │  - Shader Uniforms        │  │
│  │  - Beat Clock             │  │           │  │  - Feedback Parameters    │  │
│  │  - Signal History         │  │           │  │  - Mixer Parameters       │  │
│  └───────────────┬───────────┘  │           │  └───────────────────────────┘  │
│                  │              │           │              ▲                  │
│  ┌───────────────▼───────────┐  │           │              │                  │
│  │  Modulatable Parameters   │  │           │              │                  │
│  │                           │──┼───────────┼──────────────┘                  │
│  │  - Base Values            │  │           │                                 │
│  │  - Modulators             │  │           │                                 │
│  │  - Evaluation Logic       │  │           │                                 │
│  └───────────────────────────┘  │           │                                 │
│                                 │           │                                 │
└─────────────────────────────────┘           └─────────────────────────────────┘
```

## Sequence Diagrams

### Parameter Modulation and Application Sequence

The following sequence diagram illustrates how a parameter is modulated and applied to the visual rendering:

```
┌───────────┐  ┌─────────────────┐  ┌────────────────────┐  ┌────────────┐  ┌─────────────┐
│AudioEngine│  │ModulationRegistry│  │ModulatableParameter│  │Renderer    │  │GLSurfaceView│
└─────┬─────┘  └────────┬─────────┘  └──────────┬─────────┘  └─────┬──────┘  └──────┬──────┘
      │                 │                       │                  │                │
      │ process()       │                       │                  │                │
      ├────────────────▶│                       │                  │                │
      │                 │                       │                  │                │
      │                 │ update("amp", value)  │                  │                │
      │                 ├──────────────────────▶│                  │                │
      │                 │                       │                  │                │
      │                 │ update("bass", value) │                  │                │
      │                 ├──────────────────────▶│                  │                │
      │                 │                       │                  │                │
      │                 │                       │                  │ onDrawFrame()  │
      │                 │                       │                  │◀───────────────┤
      │                 │                       │                  │                │
      │                 │                       │                  │                │
      │                 │                       │ evaluate()       │                │
      │                 │                       │◀─────────────────┤                │
      │                 │                       │                  │                │
      │                 │ get("amp")            │                  │                │
      │                 │◀──────────────────────┤                  │                │
      │                 │                       │                  │                │
      │                 │ return value          │                  │                │
      │                 ├──────────────────────▶│                  │                │
      │                 │                       │                  │                │
      │                 │                       │ return modulated │                │
      │                 │                       │ value            │                │
      │                 │                       ├─────────────────▶│                │
      │                 │                       │                  │                │
      │                 │                       │                  │ glUniform1f()  │
      │                 │                       │                  ├───────────────▶│
      │                 │                       │                  │                │
      │                 │                       │                  │ drawArrays()   │
      │                 │                       │                  ├───────────────▶│
      │                 │                       │                  │                │
```

### Rendering Pipeline Sequence for Mixer Mode

```
┌─────────────┐  ┌─────────────────┐  ┌──────────────┐  ┌─────────────────┐  ┌──────────────┐
│SpiralRenderer│  │Slot Rendering   │  │Mixer A/B     │  │Final Mixer      │  │Display Output│
└──────┬──────┘  └────────┬────────┘  └──────┬───────┘  └────────┬────────┘  └──────┬───────┘
       │                  │                  │                   │                  │
       │ onDrawFrame()    │                  │                   │                  │
       ├─────────────────▶│                  │                   │                  │
       │                  │                  │                   │                  │
       │                  │ for each enabled │                  │                  │
       │                  │ slot:            │                  │                  │
       │                  ├────────────────┐ │                  │                  │
       │                  │                │ │                  │                  │
       │                  │ render source  │ │                  │                  │
       │                  │ to temp buffer │ │                  │                  │
       │                  │                │ │                  │                  │
       │                  │ apply ping-pong│ │                  │                  │
       │                  │ feedback       │ │                  │                  │
       │                  │                │ │                  │                  │
       │                  │ write to       │ │                  │                  │
       │                  │ masterTextures │ │                  │                  │
       │                  │◀───────────────┘ │                  │                  │
       │                  │                  │                  │                  │
       │                  │                  │                  │                  │
       │                  │ renderMixerGroup()                  │                  │
       │                  ├─────────────────▶│                  │                  │
       │                  │                  │                  │                  │
       │                  │                  │ Mix slots 1+2    │                  │
       │                  │                  │ to Mixer A       │                  │
       │                  │                  │                  │                  │
       │                  │                  │ Mix slots 3+4    │                  │
       │                  │                  │ to Mixer B       │                  │
       │                  │                  │                  │                  │
       │                  │                  │ compositeFinalMixer()               │
       │                  │                  ├──────────────────▶│                 │
       │                  │                  │                   │                 │
       │                  │                  │                   │ Mix A+B outputs │
       │                  │                  │                   │                 │
       │                  │                  │                   │ Apply feedback  │
       │                  │                  │                   │                 │
       │                  │                  │                   │ Apply effects   │
       │                  │                  │                   │                 │
       │                  │                  │                   │ drawTextureToCurrentBuffer()
       │                  │                  │                   ├────────────────▶│
       │                  │                  │                   │                 │
       │                  │                  │                   │                 │ Display selected
       │                  │                  │                   │                 │ texture
       │                  │                  │                   │                 │
```

## Converting to Visual Diagrams

To convert these ASCII diagrams to proper visual representations, you can use one of the following approaches:

1. **Diagram Editors**:
   - [draw.io](https://app.diagrams.net/) (free web-based and desktop app)
   - [Lucidchart](https://www.lucidchart.com/) (freemium web-based)
   - [Mermaid](https://mermaid-js.github.io/) (text-to-diagram tool, can be embedded in Markdown)

2. **UML Modeling Tools**:
   - [PlantUML](https://plantuml.com/) - text-based UML diagram creation
   - [StarUML](http://staruml.io/) - full-featured UML modeling tool
   - [Visual Paradigm](https://www.visual-paradigm.com/) - professional UML tool

3. **Online Whiteboarding Tools**:
   - [Miro](https://miro.com/) - collaborative online whiteboard
   - [Whimsical](https://whimsical.com/) - flowcharts and diagrams
   - [Excalidraw](https://excalidraw.com/) - hand-drawn style diagrams

### Recommended Approach

For Spirals documentation, I recommend using **draw.io** (also known as diagrams.net) for the following reasons:

1. It's free and open-source
2. Available as both web and desktop applications
3. Supports all diagram types needed for this documentation
4. Has a clean, professional appearance
5. Exports to PNG, SVG, PDF, and other formats
6. Can be integrated with version control systems

### Style Guidelines for Visual Diagrams

When creating the visual representations, consider these guidelines for consistency:

1. **Color Scheme**:
   - Use a consistent color palette that matches the application's theme
   - Use colors to distinguish between component types
   - Consider accessibility (avoid relying solely on color to convey information)

2. **Layout**:
   - Maintain a clear flow direction (generally left-to-right or top-to-bottom)
   - Group related components visually
   - Use consistent spacing and alignment

3. **Typography**:
   - Use a limited set of fonts (1-2 font families)
   - Ensure text is readable at different zoom levels
   - Use font size hierarchy to indicate importance

4. **Connectors**:
   - Use different line styles to indicate different relationship types
   - Add clear arrowheads to show direction
   - Consider orthogonal routing for cleaner appearance

5. **Iconography**:
   - Use simple, consistent icons if needed
   - Ensure icons have a consistent style
   - Include a legend for any specialized icons

### Integration with Documentation

Once the visual diagrams are created, they should be:

1. Saved in a dedicated directory (e.g., `/docs/images/`)
2. Referenced in the documentation with appropriate context and explanation
3. Versioned alongside the code to ensure they stay accurate as the code evolves