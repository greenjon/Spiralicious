# Spirals

An Android app for creating audio-reactive parametric visuals for live VJ performances.

## Quick Overview

Spirals lets you build complex audio-reactive visuals by composing simple building blocks in a 4-level hierarchy:

```
Show (playlist)
  └─ Mixer (4-slot video mixer + effects)
      └─ Set (collection of Mandalas)
          └─ Mandala (single parametric visual)
```

**Key Features:**
- Audio-reactive visuals using frequency analysis
- CV (Control Voltage) modulation system
- Real-time parameter control
- Hierarchical composition for complex visuals
- Fast workflow optimized for live performance

## Architecture

See **[DESIGN.md](DESIGN.md)** for comprehensive documentation including:
- System architecture and design decisions
- Navigation and breadcrumb cascade system
- Data persistence strategy
- Recipe tagging workflow
- Code organization

For detailed information about the rendering system, see **[RENDERING.md](RENDERING.md)** which covers:
- Shared OpenGL context architecture
- Ping-pong feedback rendering system
- Complete rendering pipeline
- Performance considerations

For detailed information about the modulation system, see **[MODULATION.md](MODULATION.md)** which covers:
- CV (Control Voltage) signal sources
- Parameter modulation mechanisms
- Audio analysis and signal processing
- Signal flow from audio input to visual parameters

For architectural diagrams and system interactions, see **[ARCHITECTURE_DIAGRAMS.md](ARCHITECTURE_DIAGRAMS.md)** which provides:
- Component-level architecture diagrams of both systems
- Sequence diagrams showing data flow and interactions
- Guidelines for creating visual representations

## Tech Stack

- Kotlin + Jetpack Compose
- OpenGL ES for rendering
- Room database for persistence
- Audio analysis engine
- Custom CV modulation system

## For Developers (and Future AI)

**Starting point for understanding the codebase:**
1. Read [DESIGN.md](DESIGN.md) first - it explains the architecture
2. Key files:
   - `MandalaViewModel.kt` - Navigation and business logic
   - `MainActivity.kt` - Main activity with all editor composables
   - `NavLayer.kt` - Navigation data structures
   - `models/` - Data models for patches

**Adding Documentation:**
This project values inline documentation. When you learn something new about the system:
- Add comments to complex functions
- Update DESIGN.md with architectural insights
- Document edge cases and gotchas
- **Your contributions to documentation are welcome and encouraged!**

## Project Status

Active development. This is a performance tool designed for live use.

---
## Desktop Migration

This project is being refactored to support both Android and Desktop (Linux).

**Phase 1: Project Restructuring (Complete)**
- The project has been restructured into `:app`, `:common`, and `:desktop` modules.

**Phase 2: Code Migration (In Progress)**
- Moving platform-agnostic code from `:app` to `:common`.

**Note:** This README provides a quick overview. For detailed architectural documentation, design decisions, and development notes, see [DESIGN.md](DESIGN.md).
