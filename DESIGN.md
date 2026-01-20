# Spirals Design Documentation

This document outlines the key design principles, architecture, and implementation details of the Spirals application.

## Table of Contents

1. [Introduction](#introduction)
2. [Core Concepts](#core-concepts)
3. [Navigation System](#navigation-system)
4. [Editor Layers](#editor-layers)
5. [Randomization System](#randomization-system)
6. [Global Defaults](#global-defaults)
7. [Beat Division System](#beat-division-system)
8. [File Structure](#file-structure)

---

## Introduction

Spirals is a visual performance application focused on generative art and real-time audio-reactive visualizations. It features a multi-layered editing system where users can create, combine, and perform with different visual elements.

---

## Core Concepts

- **Mandalas**: Core visual elements composed of symmetrical, repeating patterns
- **Sets**: Collections of mandalas that can be sequenced and arranged
- **Mixers**: Combine multiple visual sources with customizable layouts
- **Shows**: Higher-level containers for organizing performances
- **Random Sets**: Templates for generating infinite variations of mandalas based on constraints

---

## Navigation System

The app uses a breadcrumb-based navigation system with cascading saves and auto-linking:

- Each screen maintains its position in the editing hierarchy
- Clicking on a breadcrumb performs auto-save and auto-linking of children
- The VM handles linking appropriate child content to parent containers

---

## Editor Layers

Each editor type provides specific functionality:

1. **Mandala Editor**: Shape, motion, and color parameters for individual visual elements
2. **Set Editor**: Sequential arrangement of mandalas with transition controls
3. **Mixer Editor**: Spatial arrangement with 2D layout options
4. **Show Editor**: Top-level performance organization
5. **Random Set Editor**: Constraint-based template for generating mandalas

---

## Randomization System

The Random Set feature provides generative capabilities:

- **Templates over instances**: Users define constraints rather than specific values
- **Controlled randomization**: Parameters vary within user-defined ranges
- **Performance-ready**: Templates can be loaded into mixer slots for live generation

---

## Global Defaults

The Global Defaults system allows users to configure the default behavior of randomization and editors when no specific constraints are provided.

### Design Goals

1. **Centralization**: All default settings in one organized location
2. **Extensibility**: Support for multiple editors and parameter types
3. **Separation of Concerns**: Keep the main editor interfaces clean
4. **Persistence**: Save user preferences between sessions
5. **Factory Reset**: Ability to restore original defaults

### Implementation

#### Data Structure

The defaults system uses a hierarchical structure:

```
GlobalDefaults
├── RandomSetDefaults
│   ├── ArmDefaults
│   ├── RotationDefaults
│   └── HueOffsetDefaults
└── MandalaDefaults
    └── [Future implementation]
```

#### Settings Persistence

Default values are stored using SharedPreferences with a structured approach:
- Prefixed keys for organization (e.g., "defaults_randomset_arm_beatdiv_min")
- Type-specific accessor methods for different value types
- Fallback to hardcoded defaults when preferences not set

#### Integration with Generators

The RandomSetGenerator uses the stored defaults when no specific constraints are provided:
- Reads from DefaultsConfig when constraints are null
- Applies probability-based selection for options like movement sources and waveforms
- Maintains the discrete beat division values for rhythm-related parameters

### UI Organization

The Settings screen includes a dedicated section for Randomization Defaults:

1. **Random Set Editor**
   - **Arms (L1-L4)**
     - Base Length Range
     - Movement Sources (Beat/LFO probability)
     - Beat Division Range
     - Waveform Probabilities
     - Weight Range
   - **Rotation**
     - Direction Probabilities
     - Source Selection (Beat vs LFO probability)
     - Beat Division Range
     - LFO Time Range
   - **Color**
     - Direction Probabilities
     - Source Selection
     - Beat Division Range
     - LFO Time Range

2. **Mandala Editor** (Future implementation)

---

## Beat Division System

The beat division system manages rhythmic parameters throughout the application:

- **Standardized values**: Uses discrete values (1/16, 1/8, 1/4, 1/2, 1, 2, 4, 8, 16, 32, 64, 128, 256)
- **Equal probability**: When randomizing, each value has equal weight within the selected range
- **Consistent display**: Fractional values shown as fractions (e.g., "1/16" not "0.0625")
- **Dual range control**: UI uses RangeSliders for setting min/max values

---

## File Structure

For a comprehensive overview of the application's file structure and the purpose of each file, please refer to the dedicated [FILE_STRUCTURE.md](FILE_STRUCTURE.md) document.

## Additional Documentation

For detailed information about the rendering system implementation:
- [RENDERING.md](RENDERING.md) - Documentation on the shared OpenGL context and ping-pong feedback rendering systems
