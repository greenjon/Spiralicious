# Spirals User Manual

## Table of Contents

1. [Introduction](#introduction)
2. [Key Concepts](#key-concepts)
3. [Navigation System](#navigation-system)
4. [Editor Types](#editor-types)
   - [Mandala Editor](#mandala-editor)
   - [Set Editor](#set-editor)
   - [Mixer Editor](#mixer-editor)
   - [Random Set Editor](#random-set-editor)
   - [Show Editor](#show-editor)
5. [Randomization System](#randomization-system)
6. [Beat Division System](#beat-division-system)
7. [Settings](#settings)
   - [Global Defaults](#global-defaults)
8. [Workflow Guides](#workflow-guides)
9. [Troubleshooting](#troubleshooting)
10. [Tips & Techniques](#tips--techniques)

---

## Introduction

Spirals is a visual performance application focused on generative art and real-time audio-reactive visualizations. It enables you to create, combine, and perform with dynamic visual elements that respond to sound input.

The application is designed around a layered editing approach, where you can create and modify visual elements at different levels of abstraction - from individual mandalas to complex multi-layered shows.

### Design Philosophy

Spirals emphasizes:

- **Real-time performance**: All changes are visible immediately
- **Audio reactivity**: Visual elements can synchronize with and respond to audio input
- **Layered complexity**: Start simple and build up to complex visuals
- **Generative capabilities**: Define constraints and let the system generate variations

---

## Key Concepts

### Mandalas

Mandalas are the core visual elements in Spirals. These symmetrical, repeating patterns form the foundation of all visuals. Key characteristics:

- Based on mathematical "recipes" that determine the number of petals and overall shape
- Controlled by parameters such as arm lengths, rotation, and color settings
- Can be animated through modulation sources like beat detection and LFOs
- Saved as individual patches for reuse

### Sets

Sets are collections of mandalas that can be sequenced and arranged. Features include:

- Ordered list of mandalas that can be cycled through
- Selection policies for determining how mandalas transition
- Ability to organize related mandalas into thematic groups
- Time-based automation for live performances

### Mixers

Mixers combine multiple visual sources with customizable layouts. They can include:

- Up to four different input sources in a 2x2 grid
- Sources can be individual mandalas, sets, or random sets
- Each source can have custom size, position, and blending settings
- Global effects that apply to the combined output

### Random Sets

Random Sets are templates for generating infinite variations of mandalas based on constraints:

- Define parameter ranges instead of specific values
- Set probability distributions for various attributes
- Control the "creative bounds" of generated mandalas
- Useful for creating visuals that continually evolve while maintaining a consistent aesthetic

### Shows

Shows are higher-level containers for organizing performances:

- Contain multiple mixers that can be triggered in sequence
- Allow for preplanned performances with saved states
- Provide top-level controls for live adjustments
- Enable complex multi-scene performances

---

## Navigation System

Spirals uses a breadcrumb-based navigation system with automatic cascade saves and linking.

### Breadcrumbs Navigation

The top of the screen displays breadcrumbs showing your current position in the editing hierarchy:

```
Show1 > Mix001 > Set001 > Man001
```

Each level represents a different editing context, with the rightmost item being your current position.

#### How to Navigate

- **Click any breadcrumb** to navigate up to that level
- When you navigate upward, all changes in lower levels are automatically saved
- Changes are also automatically linked to parent elements (cascade system)

#### Cascade System

The cascade system is a key workflow enhancement:

1. When you click a parent breadcrumb, Spirals walks from the current layer back to the target
2. Each layer is saved automatically
3. Child elements are linked to their parents (e.g., adding a mandala to a set, or a set to a mixer slot)
4. This enables a fast workflow where you can create a child, edit it, and then click the parent breadcrumb to automatically save and link

#### Dirty State Indicator

Items with unsaved changes display an asterisk (*) after their name in the breadcrumbs:

```
Show1 > Mix001 > Set001* > Man001
```

This indicates that the item has been modified since it was last saved.

---

## Editor Types

### Mandala Editor

The Mandala Editor is where you create and modify individual mandala patches.

#### Interface Elements

- **Preview area**: Shows the current mandala visualization
- **Recipe selector**: Choose the mathematical basis for the mandala shape
- **Parameter matrix**: Grid of sliders for all available parameters
- **Oscilloscope view**: Visualizes the current value of the focused parameter
- **Modulation editor**: Configure how parameters change over time

#### Key Parameters

- **Recipe**: Determines the number of petals and basic shape
- **L1-L4**: Arm lengths for the four layers (outer to inner)
- **Rotation**: Controls how the mandala rotates
- **Hue Offset**: Controls color cycling
- **Hue Sweep**: Controls the color spread across petals
- **Feedback**: Controls visual effects like trails and echoes

#### Modulation Sources

Parameters can be modulated by:

- **Beat**: Synchronized with audio beat detection
- **LFO**: Independent low-frequency oscillators
- **Audio**: Direct audio input mapping
- **Custom CV**: Custom control voltage signals

#### Saving and Loading

- Patches are saved automatically when navigating via breadcrumbs
- Can be manually saved and loaded via the Library button
- Saved patches appear in the Set Editor for sequencing

### Set Editor

The Set Editor allows you to organize mandalas into sequences for performance.

#### Interface Elements

- **Preview area**: Shows the current mandala in the set
- **Timeline**: Visual representation of the mandalas in the set
- **Selection policy**: Controls how mandalas transition
- **Navigation controls**: Move between mandalas in the set

#### Selection Policies

- **Sequential**: Plays mandalas in order from first to last
- **Random**: Randomly selects the next mandala
- **Weighted**: Probability-based selection with user weights
- **Beat-triggered**: Changes on beat detection
- **Time-based**: Automatic transitions after specified durations

#### Adding Mandalas to Sets

- Use the "+" button to add existing mandalas
- Create new mandalas within the set context
- Reorder mandalas via drag and drop
- Remove mandalas using the delete button

### Mixer Editor

The Mixer Editor combines multiple visual sources into a cohesive output.

#### Interface Elements

- **Preview area**: Shows the combined output
- **Source slots**: 2x2 grid of input sources
- **Source selectors**: Choose what appears in each slot
- **Layout controls**: Adjust size and position of sources
- **Global effects**: Apply effects to the combined output

#### Source Types

Each slot can contain:

- Individual mandala
- Set of mandalas
- Random set (generative)
- External input (future)

#### Blend Modes

Control how sources blend together:

- Add: Brightest parts of each source are emphasized
- Multiply: Darkening blend useful for overlapping patterns
- Screen: Lightening blend useful for complex layering
- Difference: Creates high-contrast color inversions
- Many others: Overlay, Soft Light, Hard Light, etc.

### Random Set Editor

The Random Set Editor allows you to define templates for generating mandalas.

#### Interface Elements

- **Preview area**: Shows a sample generated mandala
- **Tabs**: Recipe, Arms, Motion, Color, FX
- **Regenerate button**: Creates a new variation
- **Constraint controls**: Set ranges for parameters

#### Recipe Tab

- **Recipe Filter**: Control which mandala recipes to use
- **Petal Count**: Specify exact petals or petal range
- **Favorites Only**: Limit to favorited recipes
- **Auto Hue Sweep**: Automatically set color based on petals

#### Arms Tab

Control constraints for L1-L4 parameters (arms):

- **Base Length Range**: Minimum and maximum length
- **Movement Sources**: Enable/disable Beat and LFO
- **Waveforms**: Enable/disable Sine, Triangle, Square
- **Weight Range**: How strongly modulation affects the arm
- **Beat Division Range**: Beat subdivision values to use
- **LFO Time Range**: Time range for LFO cycles

#### Motion Tab

Control rotation constraints:

- **Direction**: Enable clockwise and/or counter-clockwise
- **Speed Source**: Beat or LFO
- **Beat Division Range**: For beat-synced rotation
- **LFO Time Range**: For LFO-based rotation

#### Color Tab

Control hue offset (color cycling) constraints:

- **Direction**: Enable forward and/or reverse cycling
- **Speed Source**: Beat or LFO
- **Beat Division Range**: For beat-synced color changes
- **LFO Time Range**: For LFO-based color cycling

#### FX Tab

- **Feedback Mode**: None, Light, Medium, Heavy
- Controls trails, echoes, and persistence effects

### Show Editor

The Show Editor is the highest level of organization, allowing you to create complete performances.

#### Interface Elements

- **Preview area**: Shows the current mixer output
- **Mixer list**: Saved mixers available in the show
- **Playback controls**: Navigate between mixers
- **Global settings**: Control show-wide parameters

#### Creating Shows

1. Add multiple mixers to the show
2. Arrange them in performance order
3. Set transition types between mixers
4. Configure global parameters

#### Live Performance Mode

- Manual triggering of mixers
- Automatic sequencing based on time or beats
- Global parameter adjustments that affect all mixers
- Quick access to frequently used controls

---

## Randomization System

The Random Set feature provides powerful generative capabilities that leverage templates rather than specific instances.

### Philosophy

- **Templates over instances**: Define constraints rather than exact values
- **Bounded randomness**: Control the range of possible outcomes
- **Consistent aesthetic**: Maintain a recognizable style while allowing variation
- **Performance-ready**: Generate fresh content during live performances

### How Randomization Works

1. **Define constraints**: Set ranges for parameters instead of exact values
2. **Set probabilities**: Control how often certain options are chosen
3. **Generate instances**: Create specific mandalas that fit within constraints
4. **Iterate**: Regenerate to explore the possibility space

### Constraint Types

- **Range constraints**: Min/max values (e.g., arm length 20%-60%)
- **Inclusion/exclusion**: What options to include or exclude (e.g., waveforms)
- **Probability weights**: How likely different options are (e.g., direction)
- **Correlation rules**: How parameters relate to each other

### Using Random Sets Effectively

- Start with wide constraints and narrow as you find interesting regions
- Save particularly interesting generated mandalas as fixed patches
- Use different random sets for different aesthetic styles
- Combine with Sets and Mixers for complex generative compositions

---

## Beat Division System

The beat division system is crucial for creating rhythm-synchronized visuals throughout Spirals.

### Standard Beat Values

Spirals uses a standardized set of beat division values:

- Fractional: 1/16, 1/8, 1/4, 1/2
- Whole: 1, 2, 4, 8, 16, 32, 64, 128, 256

These values determine how parameters change in relation to the detected beat.

### How Beat Divisions Work

- **Smaller numbers** (1/16, 1/8, etc.) create faster, more frequent changes
- **Larger numbers** (32, 64, etc.) create slower, more gradual changes
- Beat divisions are visualized as fractions when less than 1 (e.g., "1/4")
- Whole numbers represent beats (e.g., "4" means "every 4 beats")

### Visual Examples

- **1/16 division**: Parameter completes 16 cycles per beat (very fast)
- **1/4 division**: Parameter completes 4 cycles per beat (fast)
- **1 division**: Parameter completes 1 cycle per beat (moderate)
- **4 division**: Parameter completes 1 cycle every 4 beats (slow)
- **16 division**: Parameter completes 1 cycle every 16 beats (very slow)

### Practical Applications

- Use fast divisions (1/16, 1/8) for subtle textures and details
- Use moderate divisions (1, 2, 4) for main visual elements
- Use slow divisions (16, 32, 64) for gradual evolution and pacing
- Combine different divisions to create complex polyrhythmic visuals

---

## Settings

Spirals offers various settings to customize your experience and workflow.

### Startup Mode

Control what appears when you launch Spirals:

- **Last Workspace**: Continue from previous session (default)
- **Mixer Editor**: Start with an empty Mixer
- **Set Editor**: Start with an empty Set
- **Mandala Editor**: Start with an empty Mandala
- **Show Editor**: Start with an empty Show

### Audio Settings

Configure audio input and processing:

- **Input Source**: Microphone, line in, or internal audio
- **Beat Detection Sensitivity**: How easily beats are detected
- **Frequency Ranges**: What audio frequencies affect which parameters
- **Smoothing**: How quickly audio changes affect visuals

### Performance Settings

Optimize for your hardware and needs:

- **Quality**: Balance between visual fidelity and performance
- **Resolution**: Output resolution for rendering
- **Frame Rate Cap**: Limit frame rate to save resources
- **Background Processing**: Enable/disable when not in focus

### Global Defaults

The Global Defaults settings control the default behavior when parameters are not explicitly configured. These settings are especially important for Random Sets, as they determine how "unconfigured" parameters behave.

#### Random Set Editor Defaults

##### Arms (L1-L4) Default Settings

- **Base Length Range**: The default range for arm lengths (0-100%)
- **Movement Source Probabilities**: How likely Beat vs LFO is chosen
  - **Beat**: Percentage chance of using beat synchronization
  - **LFO**: Percentage chance of using low-frequency oscillator
- **Beat Division Range**: The range of beat divisions to use
  - **Min**: Smallest division value (fastest, e.g., 1/16)
  - **Max**: Largest division value (slowest, e.g., 256)
- **Waveform Probabilities**: How likely each waveform type is chosen
  - **Sine**: Percentage chance of using smooth sine wave
  - **Triangle**: Percentage chance of using angular triangle wave
  - **Square**: Percentage chance of using sharp square wave
- **Weight Range**: How strongly modulation affects the parameter (-100% to +100%)
- **LFO Time Range**: Range of cycle times for LFO modulation (in seconds)

##### Rotation Default Settings

- **Direction Probabilities**: How likely each rotation direction is chosen
  - **Clockwise**: Percentage chance of rotating clockwise
  - **Counter-clockwise**: Percentage chance of rotating counter-clockwise
- **Speed Source Probabilities**: How likely each speed control is chosen
  - **Beat**: Percentage chance of beat-synchronized rotation
  - **LFO**: Percentage chance of LFO-controlled rotation
- **Beat Division Range**: The range of beat divisions for rotation
- **LFO Time Range**: Range of cycle times for rotation LFOs

##### Color Default Settings

- **Direction Probabilities**: How likely each color cycle direction is chosen
  - **Forward**: Percentage chance of cycling forward through colors
  - **Reverse**: Percentage chance of cycling backward through colors
- **Speed Source Probabilities**: How likely each speed control is chosen
- **Beat Division Range**: The range of beat divisions for color cycling
- **LFO Time Range**: Range of cycle times for color cycling LFOs

##### Effect of Default Settings

These defaults affect randomization in two key ways:

1. **When parameters are unconfigured**: Default values are used directly
2. **For probability-based selection**: Determines relative likelihood of options

Changing these defaults can dramatically shift the character of generated mandalas:

- **Rhythmic focus**: Higher beat probabilities create more beat-reactive visuals
- **Tempo character**: Beat division ranges affect the perceived speed
- **Motion character**: Waveform probabilities affect how motion feels
- **Color dynamics**: Color settings affect the mood and energy

---

## Workflow Guides

### Creating a Basic Mandala

1. Open the Mandala Editor
2. Choose a recipe from the selector
3. Adjust arm lengths (L1-L4) to create your basic shape
4. Add modulation to animate the arms
5. Adjust rotation and hue settings
6. Save the mandala

### Building a Set

1. Open the Set Editor
2. Click "+" to add existing mandalas or create new ones
3. Arrange mandalas in desired order
4. Set the selection policy
5. Test the sequence by navigating through mandalas
6. Save the set

### Creating a Random Set

1. Open the Random Set Editor
2. Configure recipe constraints (exact petals, range, or all)
3. Set arm constraints for L1-L4
4. Configure rotation and color constraints
5. Test by clicking the regenerate button
6. When satisfied with the range of outputs, save the random set

### Setting Up a Mixer

1. Open the Mixer Editor
2. Click on each source slot to assign content
3. Choose from mandalas, sets, or random sets
4. Adjust the layout and blend modes
5. Configure global effects
6. Save the mixer

### Preparing a Show

1. Open the Show Editor
2. Add existing mixers or create new ones
3. Set transition types and timing
4. Configure global parameters
5. Test the flow by stepping through mixers
6. Save the show for performance

### Live Performance Workflow

1. Load a saved show
2. Use keyboard shortcuts for rapid control
3. Make real-time adjustments as needed
4. Trigger transitions manually or automatically
5. Respond to audio input dynamically

---
