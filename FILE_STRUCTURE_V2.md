> **Note:** This document describes the file structure of the Spirals application *before* the major refactoring to support Linux and consolidate the architecture into the `:common` module. It is preserved here for historical reference.

# Spirals Project File Structure Documentation

This document provides a comprehensive overview of the file structure in the Spirals application, explaining the purpose and responsibility of each key file. This will help developers (and AI assistants) better understand how the codebase is organized.

## Table of Contents

1. [Core Components](#core-components)
2. [Model Classes](#model-classes)
3. [Database](#database)
4. [UI Components](#ui-components)
5. [Control Voltage (CV) System](#control-voltage-cv-system)
6. [Settings and Configuration](#settings-and-configuration)
7. [Utility Classes](#utility-classes)
8. [Resources](#resources)
9. [Build Configuration](#build-configuration)
10. [File Organization Strategy](#file-organization-strategy)
11. [Notable Architecture Patterns](#notable-architecture-patterns)
12. [Refactoring Roadmap](#refactoring-roadmap)

---

## Core Components

These files represent the core functionality of the application:

- **MainActivity.kt**: Entry point for the application, sets up navigation and the main UI container.
- **MandalaViewModel.kt**: Primary view model that coordinates data flow between UI and model/database classes. (Being refactored in Phase 3)
- **navigation/NavigationViewModel.kt**: Dedicated ViewModel that manages navigation stack and breadcrumb cascade logic.
- **navigation/NavLayer.kt**: Model representing a navigation layer in the app's hierarchy.

### Editor-specific ViewModels

- **viewmodels/MandalaEditorViewModel.kt**: ViewModel for the Mandala Editor screen.
- **viewmodels/SetEditorViewModel.kt**: ViewModel for the Set Editor screen.
- **viewmodels/MixerEditorViewModel.kt**: ViewModel for the Mixer Editor screen.
- **viewmodels/ShowEditorViewModel.kt**: ViewModel for the Show Editor screen.
- **viewmodels/RandomSetEditorViewModel.kt**: ViewModel for the Random Set Editor screen.

### Rendering System

- **SpiralSurfaceView.kt**: Custom view for rendering the mandala visualizations.
- **display/SpiralRenderer.kt**: OpenGL renderer that handles the actual drawing of mandalas.
- **display/SharedEGLContextFactory.kt**: Manages shared OpenGL contexts for efficient rendering across multiple surfaces.
- **display/ShaderHelper.kt**: Utility for compiling and managing shaders used in visualization.

### Core Visuals

- **MandalaVisualSource.kt**: Defines the source of mandala visuals and how they're generated, including the VisualSource interface.
- **models/mandala/MandalaLibrary.kt**: Contains predefined mandala ratios and templates used throughout the app.

## Model Classes

Files that define the data models and entities for the app, organized by domain:

### Mandala Models
- **models/mandala/MandalaParams.kt**: Parameters that define the appearance and behavior of a mandala.
- **models/mandala/MandalaRatio.kt**: Defines ratio settings for mandala symmetry and proportions.
- **models/mandala/Mandala4Arm.kt**: Implementation of a specific mandala type with 4 symmetrical arms.

### Set Models
- **models/set/MandalaSet.kt**: Represents a collection of mandalas that can be sequenced.
- **models/set/SelectionPolicy.kt**: Rules for selecting items from collections, specific to Mandala Sets.

### Mixer Models
- **models/MixerModels.kt**: Models for the mixer functionality that combines visual sources.

### Show Models
- **models/ShowModels.kt**: Data structures for organizing shows/performances.

### Randomization Models
- **models/RandomizationModels.kt**: Data models related to randomized mandala sets.

### Core Models
- **Layers.kt**: Defines layer types and content types for the application's hierarchical navigation system.

## Database

Files related to data persistence:

- **MandalaDatabase.kt**: Room database implementation for persisting mandala designs.

### Database Entities
- **database/entities/MandalaPatch.kt**: Defines a saved mandala configuration (a "patch").
- **database/entities/MandalaPatchEntity.kt**: Room Entity for storing mandala patches.
- **database/entities/MandalaSetEntity.kt**: Room Entity for storing mandala sets.
- **database/entities/RandomSetEntity.kt**: Room Entity for storing randomized sets.
- **database/entities/ShowPatchEntity.kt**: Room Entity for storing show configurations.
- **database/entities/MixerPatchEntity.kt**: Room Entity for storing mixer configurations.
- **database/entities/MandalaTag.kt**: Room Entity for defining tags for organizing mandalas.

### Database DAOs
- **database/daos/MandalaPatchDao.kt**: Data Access Object for mandala patches.
- **database/daos/MandalaSetDao.kt**: Data Access Object for mandala sets.
- **database/daos/RandomSetDao.kt**: Data Access Object for random sets.
- **database/daos/ShowPatchDao.kt**: Data Access Object for show patches.
- **database/daos/MixerPatchDao.kt**: Data Access Object for mixer patches.
- **database/daos/MandalaTagDao.kt**: Data Access Object for mandala tags.

### Repository Classes

- **database/repositories/Repository.kt**: Base interface for all repositories.
- **database/repositories/MandalaRepository.kt**: Repository for managing mandala patches.
- **database/repositories/SetRepository.kt**: Repository for managing mandala sets.
- **database/repositories/MixerRepository.kt**: Repository for managing mixer patches.
- **database/repositories/ShowRepository.kt**: Repository for managing show patches.
- **database/repositories/RandomSetRepository.kt**: Repository for managing random sets.
- **database/repositories/TagRepository.kt**: Repository for managing mandala tags.

## UI Components

User interface screens and components:

### UI Screens

- **ui/screens/MandalaEditorScreen.kt**: UI for editing individual mandalas.
- **ui/screens/MandalaSetEditorScreen.kt**: UI for editing mandala sets.
- **ui/screens/MixerEditorScreen.kt**: UI for editing mixer configurations.
- **ui/screens/ShowEditorScreen.kt**: UI for organizing and editing shows.
- **ui/screens/RandomSetEditorScreen.kt**: UI for creating and editing random set templates.
- **ui/screens/InstrumentEditorScreen.kt**: UI for editing instrument/control mappings.
- **ui/screens/CvLabScreen.kt**: UI for experimenting with control voltage signals.

### UI Components Subpackage

- **ui/components/EditorBreadcrumbs.kt**: Navigation breadcrumb UI component.
- **ui/components/KnobView.kt**: Rotary knob control for parameter editing.
- **ui/components/KnobModifier.kt**: Compose modifier for knob behaviors.
- **ui/components/RotaryKnobMath.kt**: Mathematical functions for rotary knob calculations.
- **ui/components/MandalaPicker.kt**: UI component for selecting mandalas.
- **ui/components/MixerComponents.kt**: UI components specific to the mixer editor.
- **ui/components/ParameterMatrix.kt**: Matrix layout for parameter controls.
- **ui/components/PatchManagerOverlay.kt**: Overlay UI for managing saved patches.
- **ui/components/RecipePickerDialog.kt**: Dialog for selecting mandala recipes.
- **ui/components/SetChipList.kt**: Component for displaying a list of sets as chips.
- **ui/components/OscilloscopeView.kt**: Visualization component for audio signals.
- **ui/components/SettingsOverlay.kt**: Overlay for application settings.
- **ui/components/RenamePatchDialog.kt**: Dialog for renaming patches.
- **ui/components/OpenPatchDialog.kt**: Dialog for opening saved patches.

### Theme

- **ui/theme/AppTheme.kt**: Consolidated theme configuration including colors, typography, and theme composable.

## Control Voltage (CV) System

Files related to the modulation and control voltage system:

### CV Core
- **cv/core/Modulation.kt**: Base class for modulation sources, `ModulatableParameter`, and related enums.
- **cv/core/ModulationRegistry.kt**: Central registry for all Control Voltage signals, managing global state and synchronization.
- **cv/core/CvSignal.kt**: Interface for all CV signal generators, and `ConstantCv` implementation.

### CV Sources
- **cv/sources/CvClock.kt**: A clock that maintains CV time, decoupled from system time.
- **cv/sources/BeatClock.kt**: Logic for tracking beat phase based on BPM, and `BeatPhaseCv` implementation.
- **cv/sources/AmplitudeCv.kt**: A CV signal that provides smoothed linear energy from an audio stream.
- **cv/sources/SampleAndHoldCv.kt**: A CV signal that generates new random values on a clock trigger and glides to it.

### CV Processors
- **cv/processors/AudioEngine.kt**: Core audio analysis engine for splitting audio, onset detection, and BPM estimation.
- **cv/processors/AudioSourceManager.kt**: Manages audio source configuration and MediaProjection flow.
- **cv/processors/Filters.kt**: Simple IIR Filter implementations (BiquadFilter) for frequency splitting.
- **cv/processors/EnvelopeFollower.kt**: Applies attack and release smoothing to a signal.
- **cv/processors/AmplitudeExtractor.kt**: Extracts raw RMS amplitude from PCM data.
- **cv/processors/CvModifiers.kt**: Composite and individual CV signal modifiers including Power, Clip, Offset, and Gain.

### CV Visualizers
- **cv/visualizers/CvHistoryBuffer.kt**: A ring buffer to store the last N samples of a CV signal for visualization.

## Settings and Configuration

Files related to application settings:

- **defaults/DefaultsConfig.kt**: Central configuration for default parameter values.
- **defaults/RandomSetDefaults.kt**: Default settings for random set generation.
- **settings/GlobalDefaultsScreen.kt**: UI for editing global default settings.
- **AppConfig.kt**: Application-wide configuration settings.

## Utility Classes

Utility and helper classes:

- **NamingUtils.kt**: Utilities for generating and managing names.
- **PatchData.kt**: Utilities for handling patch data.
- **PatchMapper.kt**: Maps between different patch formats/versions.
- **RandomSetGenerator.kt**: Generates random mandalas based on constraints.
- **RecipeTagManager.kt**: Manages tags and recipes for mandalas.

## Resources

Resource files for the application:

- **res/drawable/ic_wave_*.xml**: Icons for different waveform types.
- **res/drawable/ic_launcher_*.xml**: App icon resources.
- **res/values/strings.xml**: String resources.
- **res/values/colors.xml**: Color resources.
- **res/values/themes.xml**: Theme resources.
- **AndroidManifest.xml**: Android app manifest file.

## Build Configuration

Build and configuration files:

- **build.gradle.kts**: Gradle build configuration for the app.

---

## File Organization Strategy

The Spirals project follows these organizational principles:

1. **Feature-based packaging**: Related files are grouped by feature rather than by type.
2. **Separation of concerns**: UI components are separate from business logic and data models.
3. **Clear dependencies**: Higher-level components depend on lower-level ones, not vice versa.
4. **Composability**: Components are designed to be composed together for complex functionality.

## Notable Architecture Patterns

1. **MVVM Pattern**: Separates UI (Screens) from business logic (ViewModels) and data (Models).
2. **Repository Pattern**: Database access is abstracted through repository classes.
3. **Entity-Component System**: Mandalas are composed of various components with different behaviors.
4. **Factory Pattern**: Used for creating complex objects like random mandalas.
5. **Observer Pattern**: Used for reactive UI updates based on data changes.

This file structure documentation should be kept up to date as the codebase evolves.

---

## Refactoring Roadmap

**Executive Summary:** A thorough analysis of the codebase, particularly `MainActivity.kt` (1000+ lines) and `MandalaViewModel.kt` (850+ lines), confirms the critical need for the refactoring outlined below. These two files have become "God Objects," concentrating too much responsibility in one place.

The highest-priority tasks are:
1.  **Dismantling `MandalaViewModel.kt`** into a dedicated `NavigationViewModel`, domain-specific `Repository` classes, and editor-specific ViewModels.
2.  **Extracting all screen composables** from `MainActivity.kt` into their own files to improve modularity and enable Compose previews.

The following roadmap has been updated to reflect a more detailed, phased approach. It now includes new phases for architectural improvements like dependency injection and a more organized project structure. Following this roadmap will lead to a more scalable, maintainable, and testable codebase.

---

Below is a phased approach to improving the codebase organization. These changes should be implemented gradually to minimize disruption and ensure everything continues to work between phases.

### Phase 1: Foundational Cleanup (Low-hanging Fruit)

1.  **Align Core Component Names**:
   - [x] Rename `cv/CvRegistry.kt` → `cv/ModulationRegistry.kt` (align with terminology in `Modulation.kt`)
   - [x] Rename `RandomSetModels.kt` → `RandomizationModels.kt` (more descriptive of functionality)

2.  **Clean Up Test Files**:
    - [x] Remove template test files (`ExampleInstrumentedTest.kt`, `ExampleUnitTest.kt`) if unused.
    - [ ] Create a proper test plan and structure for actual testing.

### Phase 2: File Consolidation
   - [x] Consolidate `LayerContent.kt` and `LayerType.kt` into `Layers.kt`
   - [x] Merge `VisualSource.kt` and `MandalaVisualSource.kt` (interface and primary implementation)

2.  **Simplify Theme Structure**:
   - [x] Consolidate theme files (`Color.kt`, `Theme.kt`, `Type.kt`) into a single `AppTheme.kt`

3.  **Optimize CV Modifier Structure**:
   - [x] Group related CV modifiers into logical files (e.g., `AmplitudeModifiers.kt`, `TimeModifiers.kt`)
   - [x] Consider consolidating smaller modifier classes into a unified structure

### Phase 3: Critical Deconstruction - Splitting God Objects

1.  **Dismantle `MandalaViewModel.kt` (Highest Priority)**:
    - [x] Create a dedicated `NavigationViewModel.kt` to manage the `navStack` and breadcrumb cascade logic.
    - [x] Create domain-specific `Repository` classes (e.g., `MandalaRepository`, `SetRepository`) to abstract all database operations, using the existing DAOs.
    - [x] Create individual ViewModels for each editor screen (e.g., `MandalaEditorViewModel`, `MixerEditorViewModel`). These will handle screen-specific state and logic, interacting with the repositories and the `NavigationViewModel`.

2.  **Extract Editor Composables from `MainActivity.kt`**:
    - [x] Move each primary screen composable (`ShowEditorScreen`, `MixerEditorScreen`, `MandalaSetEditorScreen`, `MandalaEditorScreen`, `RandomSetEditorScreen`, `CvLabScreen`) into its own file within a new `ui/screens` package.
    - [x] Move dialogs and overlays (`SettingsOverlay`, `RenamePatchDialog`) into their own files within a `ui/components` package.
    - [x] Reduce `MainActivity.kt` to its core responsibility: setting up the theme, navigation host, and top-level state management.


### Phase 4: Directory Structure Improvements

1.  **Establish a Standard `ui` Package**:
    - [x] Create a top-level `ui` package and organize UI files into `ui/screens`, `ui/components`, and `ui/theme`.

2.  **Create Specialized Packages**:
    - [x] Move `SpiralRenderer.kt`, `SharedEGLContextFactory.kt`, `ShaderHelper.kt` into a `display` subpackage.

3.  **Reorganize models by domain**:
    - [x] Create `models/mandala`, `models/mixer`, `models/set`, `models/show` subpackages and move relevant model files.
    - [x] Separate into `database/entities` and `database/daos` subpackages and move relevant entity/DAO files.

4.  **Restructure CV System**:
   - [x] Reorganize into `cv/sources`, `cv/processors`, `cv/visualizers`
   - [x] Group related files into these new subpackages

### Phase 5: Final Refinements

1.  **Normalize Naming Conventions**:
   - [ ] Ensure consistent naming patterns across the codebase
   - [ ] Address any remaining inconsistencies between "Spiral" and "Mandala" terminology
   - [ ] Standardize abbreviation usage (CV vs Control vs Modulation)

2.  **Documentation Updates**:
   - [ ] Update this FILE_STRUCTURE.md document to reflect the new organization
   - [ ] Add code comments explaining the rationale behind the new structure

3.  **Review and Testing**:
   - [ ] Perform full application testing after each phase
   - [ ] Review for any missed refactoring opportunities
   - [ ] Ensure all features continue to function correctly

### Phase 6: Advanced Architectural Improvements

1.  **Integrate Dependency Injection**:
    - [ ] Add the Hilt library to the project.
    - [ ] Annotate the Application class with `@HiltAndroidApp`.
    - [ ] Provide dependencies like Repositories, ViewModels, and the `AudioEngine` using Hilt modules.
    - [ ] Inject dependencies into `MainActivity` and other Android components using `@AndroidEntryPoint`.

2.  **Formalize State Management**:
    - [ ] Review the state management approach in the new, smaller ViewModels.
    - [ ] Consider adopting a formal MVI (Model-View-Intent) pattern to ensure a unidirectional data flow and predictable state changes.
    - [ ] This will make the application easier to debug and more robust, especially with its highly reactive nature.

This roadmap provides a structured approach to improving the codebase organization without changing functionality. Each phase builds on the previous one, ensuring that the codebase remains functional throughout the refactoring process.
