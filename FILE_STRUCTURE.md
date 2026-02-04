# Spirals Project File Structure (Post-Refactoring)

This document provides a comprehensive overview of the file structure in the Spirals application after the refactoring for multiplatform (Android/Desktop) support. This will help developers (and AI assistants) understand the current codebase organization.

## Project Modules

The project is now organized into three main modules:

-   `:app` - The Android-specific application. Contains Android entry points, UI, and platform-specific implementations.
-   `:common` - The shared module. Contains platform-agnostic business logic, data models, database definitions, and ViewModels.
-   `:desktop` - The Desktop (Linux) application. Contains the desktop entry point and platform-specific implementations.

---

## `:common` Module Structure

This module contains the core logic of the application, shared between Android and Desktop.

### Core Logic & ViewModels (`commonMain`)

-   **`viewmodel/SpiralsViewModel.kt`**: **[Partially Implemented]** A central ViewModel that holds the main application state and business logic. It was refactored from the old `MandalaViewModel` but still has significant responsibilities that could be further delegated. It handles the navigation stack, data persistence, and high-level state management.
-   **`navigation/NavigationViewModel.kt`**: **[Fully Implemented]** Manages the navigation stack (`navStack`) and the breadcrumb cascade logic. This was successfully extracted from the old `MandalaViewModel`.
-   **`navigation/NavLayer.kt`**: **[Fully Implemented]** Data class representing a single layer in the navigation stack (e.g., a Mixer editor, a Set editor).
-   **`viewmodels/*.kt`**: **[Fully Implemented]** Editor-specific ViewModels (`MandalaEditorViewModel`, `MixerEditorViewModel`, etc.) that manage the state and logic for their respective editor screens. They interact with the `NavigationViewModel` and `Repositories`.

### Data Models (`commonMain`)

Located in `models/`, these data classes represent the core entities of the application.

-   **`models/mandala/MandalaRatio.kt`**: **[Fully Implemented]** Defines the core parameters for a single visual, including its arms and feedback properties.
-   **`models/set/MandalaSet.kt`**: **[Fully Implemented]** Represents a collection of Mandalas.
-   **`models/MixerPatch.kt`**: **[Fully Implemented]** Represents a 4-slot mixer configuration.
-   **`models/ShowPatch.kt`**: **[Fully Implemented]** Represents a sequence of Mixers for a performance.
-   **`models/RandomSet.kt`**: **[Fully Implemented]** Defines the constraints for generating randomized Mandalas.
-   **`Layers.kt`**: **[Fully Implemented]** Defines the `LayerType` enum and `LayerContent` sealed interface, crucial for the navigation system.

### Database (`commonMain`)

The entire Room database definition is now located in `commonMain`, leveraging Room's multiplatform support.

-   **`database/MandalaDatabase.kt`**: **[Fully Implemented]** The abstract RoomDatabase class. Defines the entities and provides access to the DAOs.
-   **`database/DatabaseDriver.kt`**: **[Fully Implemented]** The `expect`/`actual` mechanism for creating a platform-specific database instance (`Room.databaseBuilder`). This is a key part of the multiplatform architecture.
-   **`database/daos/*.kt`**: **[Fully Implemented]** Data Access Objects (DAOs) for each entity. These are now concrete interfaces directly in `commonMain`.
-   **`database/entities/*.kt`**: **[Fully Implemented]** Room `@Entity` classes. These are now concrete data classes directly in `commonMain`.

### Platform-Specific Implementations (`androidMain`, `desktopMain`)

-   **`platform/AppConfig.kt`**: **[Partially Implemented]** `expect` class for handling platform-specific configurations like loading/saving the navigation stack. The Android version is implemented using SharedPreferences, but the Desktop version is likely a placeholder.
-   **`database/DatabaseDriver.kt`**: **[Fully Implemented]** The `actual` implementations for creating the database on Android (using `ApplicationContext`) and Desktop (using a file in the user's home directory).

---

## `:app` Module Structure

This module contains everything required to run the Spirals application on Android.

-   **`MainActivity.kt`**: **[Fully Implemented]** The main Android entry point. Its main responsibility is to host the Jetpack Compose UI, initialize the `SpiralsViewModel`, and handle Android-specific lifecycle events and permissions.
-   **`SpiralSurfaceView.kt` & `display/SpiralRenderer.kt`**: **[Partially Implemented for Multiplatform]** The Android-specific OpenGL rendering surface and renderer. The core rendering logic is here, but for full Linux support, this will need to be abstracted into `:common` with a platform-agnostic interface.
-   **`cv/processors/AudioEngine.kt`**: **[Android-Only Implementation]** The audio analysis engine is currently specific to Android. For Linux support, this needs to be abstracted behind an `expect` interface in `:common`.
-   **`ui/screens/*.kt`**: **[Fully Implemented]** The Jetpack Compose UI for each of the editor screens. These are now in their own files and are mostly platform-agnostic, making them easy to reuse in the `:desktop` module.
-   **`ui/components/*.kt`**: **[Fully Implemented]** Reusable UI components like knobs, dialogs, and breadcrumbs.
-   **`database/repositories/*.kt`**: **[Fully Implemented]** Repository classes that mediate between the ViewModels and the DAOs. These are located in the `:app` module but are written in a platform-agnostic way and could potentially be moved to `:common`.

---

## `:desktop` Module Structure

This module is the entry point for the Linux desktop application.

-   **`Main.kt`**: **[Placeholder/Minimal Implementation]** The `main` function that starts the desktop application. It sets up the Compose window and initializes the desktop-specific components.
-   **`DesktopSpiralRenderer.kt`**: **[Partially Implemented]** A desktop-specific implementation of the `ISpiralRenderer` interface (which should be in `:common`). It uses LWJGL for OpenGL rendering. This is the desktop equivalent of the Android `SpiralRenderer`.
-   **Platform Implementations**: This module contains the `actual` implementations for any `expect` classes in `:common`, such as `AppConfig`.

## Current State & Path to Full Linux Support

-   **Core Logic & Data:** **Excellent.** The business logic, data models, and database are almost entirely in `:common`. This is the biggest and most important part of the refactoring.
-   **UI:** **Good.** The UI screens and components are written in Compose and are largely portable. They can be reused in the `:desktop` module with minimal changes.
-   **Rendering:** **Needs Abstraction.** The rendering logic is duplicated between `SpiralRenderer.kt` (Android) and `DesktopSpiralRenderer.kt` (Desktop). An `expect`/`actual` `ISpiralRenderer` interface in `:common` is the next logical step.
-   **Audio Input:** **Needs Abstraction.** The `AudioEngine` is Android-specific. This needs to be placed behind an `expect` interface in `:common` so that a desktop version (e.g., using `javax.sound`) can be created.

This structure provides a strong foundation for achieving full feature parity on Linux. The next development steps should focus on abstracting the platform-specific Rendering and Audio systems.
