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
- **MandalaViewModel.kt**: Primary view model that coordinates data flow between UI and model/database classes.
- **SpiralSurfaceView.kt**: Custom view for rendering the mandala visualizations.
- **SpiralRenderer.kt**: OpenGL renderer that handles the actual drawing of mandalas.
- **SharedEGLContextFactory.kt**: Manages shared OpenGL contexts for efficient rendering across multiple surfaces.
- **ShaderHelper.kt**: Utility for compiling and managing shaders used in visualization.
- **MandalaVisualSource.kt**: Defines the source of mandala visuals and how they're generated.
- **VisualSource.kt**: Abstract interface for different types of visual sources.
- **MandalaLibrary.kt**: Contains predefined mandala ratios and templates used throughout the app.
- **Mandala4Arm.kt**: Implementation of a specific mandala type with 4 symmetrical arms.

## Model Classes

Files that define the data models and entities for the app:

- **MandalaParams.kt**: Parameters that define the appearance and behavior of a mandala.
- **MandalaRatio.kt**: Defines ratio settings for mandala symmetry and proportions.
- **RandomSetModels.kt**: Data models related to randomized mandala sets.
- **MixerModels.kt**: Models for the mixer functionality that combines visual sources.
- **ShowModels.kt**: Data structures for organizing shows/performances.
- **LayerContent.kt**: Defines content types for different layers in the app.
- **LayerType.kt**: Enum of available layer types in the application.
- **MandalaSet.kt**: Represents a collection of mandalas that can be sequenced.
- **NavLayer.kt**: Model representing a navigation layer in the app's hierarchy.

## Database

Files related to data persistence:

- **MandalaDatabase.kt**: Room database implementation for persisting mandala designs.
- **MandalaPatch.kt**: Defines a saved mandala configuration (a "patch").
- **MandalaPatchEntity.kt**: Database entity for storing mandala patches.
- **MandalaSetEntity.kt**: Database entity for storing mandala sets.
- **RandomSetEntity.kt**: Database entity for storing randomized sets.
- **ShowPatchEntity.kt**: Database entity for storing show configurations.
- **MixerPatchEntity.kt**: Database entity for storing mixer configurations.
- **MandalaTag.kt**: Defines tags for organizing mandalas.
- **MandalaTagDao.kt**: Data Access Object for mandala tags.

## UI Components

User interface screens and components:

- **MandalaSetEditorScreen.kt**: UI for editing mandala sets.
- **MixerEditorScreen.kt**: UI for editing mixer configurations.
- **ShowEditorScreen.kt**: UI for organizing and editing shows.
- **RandomSetEditorScreen.kt**: UI for creating and editing random set templates.
- **InstrumentEditorScreen.kt**: UI for editing instrument/control mappings.
- **CvLabScreen.kt**: UI for experimenting with control voltage signals.

### UI Components Subpackage

- **components/EditorBreadcrumbs.kt**: Navigation breadcrumb UI component.
- **components/KnobView.kt**: Rotary knob control for parameter editing.
- **components/KnobModifier.kt**: Compose modifier for knob behaviors.
- **components/RotaryKnobMath.kt**: Mathematical functions for rotary knob calculations.
- **components/MandalaPicker.kt**: UI component for selecting mandalas.
- **components/MixerComponents.kt**: UI components specific to the mixer editor.
- **components/ParameterMatrix.kt**: Matrix layout for parameter controls.
- **components/PatchManagerOverlay.kt**: Overlay UI for managing saved patches.
- **components/RecipePickerDialog.kt**: Dialog for selecting mandala recipes.
- **components/SetChipList.kt**: Component for displaying a list of sets as chips.
- **components/OscilloscopeView.kt**: Visualization component for audio signals.

### Theme

- **theme/Color.kt**: Color definitions for the app theme.
- **theme/Theme.kt**: Theme configuration for Compose UI.
- **theme/Type.kt**: Typography definitions for the app theme.

## Control Voltage (CV) System

Files related to the modulation and control voltage system:

- **cv/Modulation.kt**: Base class for modulation sources.
- **cv/CvRegistry.kt**: Registry for available CV sources in the app.
- **cv/CvSignal.kt**: Interface for CV signal generators.
- **cv/CvClock.kt**: Clock signal generator for rhythmic modulation.
- **cv/BeatClock.kt**: Beat-synchronized clock for rhythmic parameters.
- **cv/AmplitudeCv.kt**: CV source based on audio amplitude.

### Audio Processing

- **cv/audio/AudioEngine.kt**: Core audio processing system.
- **cv/audio/AudioSourceManager.kt**: Manages different audio input sources.
- **cv/audio/Filters.kt**: Audio filtering utilities.
- **cv/audio/EnvelopeFollower.kt**: Extracts amplitude envelope from audio.
- **cv/audio/AmplitudeExtractor.kt**: Extracts amplitude data from audio signals.

### CV Modifiers

- **cv/modifiers/ModifiedCv.kt**: Base class for CV signal modifiers.
- **cv/modifiers/ClipCv.kt**: Limits CV signal to a specific range.
- **cv/modifiers/GainCv.kt**: Amplifies CV signal by a factor.
- **cv/modifiers/OffsetCv.kt**: Adds offset to CV signal.
- **cv/modifiers/PowerCv.kt**: Applies power function to CV signal.

### CV UI Components

- **cv/ui/CvHistoryBuffer.kt**: Stores history of CV values for visualization.

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
- **SelectionPolicy.kt**: Rules for selecting items from collections.

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

Below is a phased approach to improving the codebase organization. These changes should be implemented gradually to minimize disruption and ensure everything continues to work between phases.

### Phase 1: File Renaming

1. **Align Core Component Names**:
   - [ ] Rename `MandalaLibrary.kt` → `MandalaTemplates.kt` (clearer purpose as template repository)
   - [ ] Rename `SpiralSurfaceView.kt` → `MandalaSurfaceView.kt` (consistency with "Mandala" naming convention)
   - [ ] Rename `SpiralRenderer.kt` → `MandalaRenderer.kt` (consistency with "Mandala" naming convention)
   - [ ] Update all references to these files in the codebase

2. **Clarify CV System Names**:
   - [ ] Rename `cv/CvRegistry.kt` → `cv/ModulationRegistry.kt` (align with terminology in Modulation.kt)
   - [ ] Rename `RandomSetModels.kt` → `RandomizationModels.kt` (more descriptive of functionality)

### Phase 2: File Consolidation

1. **Combine Related Model Files**:
   - [ ] Consolidate `MandalaParams.kt` and `MandalaRatio.kt` into `MandalaDefinition.kt`
   - [ ] Consolidate `LayerContent.kt` and `LayerType.kt` into `Layers.kt`
   - [ ] Merge `VisualSource.kt` and `MandalaVisualSource.kt` (interface and primary implementation)

2. **Simplify Theme Structure**:
   - [ ] Consolidate theme files (`Color.kt`, `Theme.kt`, `Type.kt`) into a single `AppTheme.kt`

3. **Optimize CV Modifier Structure**:
   - [ ] Group related CV modifiers into logical files (e.g., `AmplitudeModifiers.kt`, `TimeModifiers.kt`)
   - [ ] Consider consolidating smaller modifier classes into a unified structure

### Phase 3: File Splitting and Reorganization

1. **Separate Concerns in Key Components**:
   - [ ] Split `MandalaViewModel.kt` into `NavigationViewModel.kt` and editor-specific ViewModels
   - [ ] Extract editor composables from `MainActivity.kt` into their own dedicated files
   - [ ] Divide `MandalaDatabase.kt` into domain-specific repository classes if it's doing too much

2. **Clean Up Test Files**:
   - [ ] Remove template test files (`ExampleInstrumentedTest.kt`, `ExampleUnitTest.kt`) if unused
   - [ ] Create a proper test plan and structure for actual testing

3. **Optimize Configuration**:
   - [ ] Evaluate and potentially consolidate `AppConfig.kt` and `DefaultsConfig.kt`

### Phase 4: Directory Structure Improvements

1. **Create Specialized Packages**:
   - [ ] Create an `engine` package for rendering components
     - Move `MandalaRenderer.kt`, `SharedEGLContextFactory.kt`, `ShaderHelper.kt`
   - [ ] Reorganize models by domain
     - Create `models/mandala`, `models/mixer`, `models/set`, `models/show` subpackages

2. **Properly Organize Database Components**:
   - [ ] Create a dedicated `database` package
   - [ ] Separate into `database/entities` and `database/daos` subpackages
   - [ ] Add proper repository classes in `database/repositories`

3. **Restructure CV System**:
   - [ ] Reorganize into `cv/sources`, `cv/processors`, `cv/visualizers`
   - [ ] Group related files into these new subpackages

### Phase 5: Final Refinements

1. **Normalize Naming Conventions**:
   - [ ] Ensure consistent naming patterns across the codebase
   - [ ] Address any remaining inconsistencies between "Spiral" and "Mandala" terminology
   - [ ] Standardize abbreviation usage (CV vs Control vs Modulation)

2. **Documentation Updates**:
   - [ ] Update this FILE_STRUCTURE.md document to reflect the new organization
   - [ ] Update imports and references in all documentation files
   - [ ] Add code comments explaining the rationale behind the new structure

3. **Review and Testing**:
   - [ ] Perform full application testing after each phase
   - [ ] Review for any missed refactoring opportunities
   - [ ] Ensure all features continue to function correctly

This roadmap provides a structured approach to improving the codebase organization without changing functionality. Each phase builds on the previous one, ensuring that the codebase remains functional throughout the refactoring process.