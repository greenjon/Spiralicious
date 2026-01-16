# Spirals - Design Documentation

**NOTE TO FUTURE AI/DEVELOPERS:** This document captures the architecture and design decisions for this project. **Please add to this document** as you learn more about the system, discover patterns, or make architectural changes. The more we document, the easier it is to maintain consistency.

---

## Project Overview

Spirals is an Android app for creating audio-reactive visual art. It's designed for live VJ performances with a focus on fast workflow and minimal UI.

**Core Philosophy:**
- **Immediate feedback** - Changes reflect in real-time
- **Hierarchical composition** - Build complex visuals from simple building blocks
- **Performance-oriented** - Designed for live use, not studio production

---

## Architecture: The Hierarchy System

The app uses a **4-level hierarchy** for organizing visual content:

```
Show (playlist of mixers)
  └─> Mixer (4 video slots + effects)
       └─> Set (collection of Mandalas)
            └─> Mandala (single parametric visual)
```

### Level Details:

1. **Mandala** - The atomic unit
   - Single parametric visual based on a "recipe" (frequency ratios: a,b,c,d)
   - Has modulatable parameters (L1-L8, R1-R8, etc.)
   - Can be modulated by CV (Control Voltage) signals from audio

2. **Set** - Collection of Mandalas
   - Ordered list of Mandala names
   - Has a selection policy (SEQUENTIAL, RANDOM, etc.)
   - Used in Mixer slots for variety

3. **Mixer** - 4-slot video mixer with effects
   - Each slot can hold a Set OR a Mandala
   - Has A/B/F mixer stages with different blend modes
   - Includes feedback effects (trails, kaleidoscope, etc.)

4. **Show** - Playlist of Mixers
   - List of Mixer names in performance order
   - Has PREV/NEXT triggers for live switching
   - Top level for organizing a complete performance

---

## Navigation & Editor System

### NavStack Architecture

The app uses a **navigation stack** (`List<NavLayer>`) to manage the editing context:

```kotlin
data class NavLayer(
    val id: String,              // Unique ID
    val name: String,            // Display name
    val type: LayerType,         // SHOW, MIXER, SET, or MANDALA
    val isDirty: Boolean,        // Has unsaved changes
    val data: LayerContent?,     // The actual patch data
    val parentSlotIndex: Int?,   // Which Mixer slot this belongs to
    val createdFromParent: Boolean  // Should auto-link on breadcrumb navigation
)
```

**Key Insight:** The stack represents your "editing path" through the hierarchy. Example:
- `[Show1]` - Editing Show1
- `[Show1, Mix001]` - Editing Mix001 within Show1 context
- `[Show1, Mix001, Set001, Man001]` - Deep editing path

### Breadcrumb Cascade System

**The Workflow:**
1. User is in Show1
2. Clicks "Create new..." on a Mixer slot
3. System creates Mix001, saves it immediately, and pushes it to stack
4. User edits Mix001, then clicks "Show1" in breadcrumb
5. **Cascade happens:** Mix001 is saved AND linked to Show1's mixer list

**Implementation:**
- `createAndPushLayer()` sets `createdFromParent = true` and `parentSlotIndex` (for Mixers)
- `popToLayer(index, save = true)` walks from current layer down to target, saving and linking each
- `linkChildToParent()` adds the child to parent's collection (mixer slot, set list, etc.)
- Prevents duplicates by checking if already present

**Why this design?**
- **Auto-save:** Never lose work (saves immediately on creation)
- **Auto-link:** Breadcrumb navigation completes the workflow naturally
- **No popups:** Fast, gestural workflow for live performance

---

## Data Persistence Strategy

### Hybrid Auto-Save Approach

**Decision:** Items are saved immediately upon creation and on every change, but only **linked** to their parent when navigating via breadcrumb.

**Why?**
- Traditional "Save" button → can lose work, have to remember
- Full auto-link → would clutter parent collections with half-finished experiments
- Hybrid → Best of both worlds: never lose work, but explicit linking via breadcrumb gesture

**Implications:**
- Database may contain "orphaned" patches (saved but not linked anywhere)
- Use "Manage" overlay to browse/delete orphans
- Renaming must update BOTH `layer.name` AND the data inside (`patch.name`, etc.)

### How Saving and Linking Work: A Detailed Guide

#### Scenario 1: Opening from Menu

**User Action:** "Open Mandala" → selects "Man042"

**What Happens:**
1. `createAndResetStack(LayerType.MANDALA, openedFromMenu = true)` creates new layer
2. Layer data populated with Man042's patch data
3. `openedFromMenu = true` flag triggers Manage overlay to show
4. User edits parameters, changes recipe, etc.
5. Each edit triggers `LaunchedEffect` → `vm.updateLayerData()` → **updates in-memory layer**
6. ⚠️ **NOT saved to database yet!** Changes only in memory
7. User navigates away (breadcrumb, switch editor) → `popToLayer()` → **NOW it saves**
8. OR user stays on layer indefinitely → changes persist in workspace if "Last Workspace" enabled

**Key Point:** Opening from menu does NOT set `createdFromParent = true`, so no auto-linking happens (no parent to link to anyway).

#### Scenario 2: Creating from Parent

**User Action:** In Mixer, clicks "Create new" on Slot 2

**What Happens:**
1. `createAndPushLayer(LayerType.SET, parentSlotIndex = 2)` called
2. Generates name "Set003", creates empty set data
3. Sets `createdFromParent = true` and `parentSlotIndex = 2`
4. **IMMEDIATELY saves to database** via `saveLayer(newLayer)`
5. Pushes to stack: `[Mixer001, Set003]`
6. User adds mandalas to set, edits, etc.
7. Each edit → `updateLayerData()` → in-memory only
8. User clicks "Mixer001" breadcrumb → triggers cascade!

**The Cascade (most important part):**
```
popToLayer(index = 0, save = true)  // Pop to Mixer001
  ↓
  Loop from current (Set003) down to Mixer001+1:
    1. Save Set003 to database (with all edits)
    2. Check if Set003.createdFromParent == true (YES!)
    3. Call linkChildToParent(Set003, Mixer001)
       ↓
       Find parent Mixer001, get Slot 2 (from parentSlotIndex)
       Set mixer.slots[2].mandalaSetId = Set003.id
       Set mixer.slots[2].sourceType = MANDALA_SET
       Save updated Mixer001
  ↓
  Pop Set003 from stack
  Final stack: [Mixer001]
```

**Result:** Set003 is now **permanently linked** to Mixer Slot 2!

#### Scenario 3: Rename Edge Case

**The Problem:** Linking uses `data.name` (e.g., `mixer.name`, `set.name`), not `layer.name`.

**Why Both Exist:**
- `layer.name` - What breadcrumb displays (updated first during rename)
- `data.*.name` - The actual patch name in the data structure (used for linking)

**Rename Process:**
1. User renames "Set003" → "MySuperSet"
2. `renameLayer()` called
3. **Deletes old database entry** (by old name/id)
4. **Updates data:** `SetLayerContent(set.copy(name = "MySuperSet"))`
5. **Updates layer:** `layer.copy(name = "MySuperSet", data = updatedData)`
6. **Saves with new name** (creates new database entry)
7. Both `layer.name` AND `data.set.name` now have new name

**Critical:** If we only updated `layer.name`, the breadcrumb would show "MySuperSet" but when cascade linking happens, it would try to link "Set003" (from `data.set.name`) to the parent, and the item would vanish!

#### When Does Saving Actually Happen?

**Immediate Save (Database Write):**
- ✅ `createAndPushLayer()` - Creates and saves new item immediately
- ✅ `popToLayer()` with save=true - Cascade saves all layers
- ✅ Manual "Save" button in menu (if present)
- ✅ `renameLayer()` - Deletes old, saves new
- ✅ `cloneLayer()` - Saves the clone immediately

**In-Memory Only (No Database Write):**
- ❌ Every edit/parameter change - Just updates `NavLayer.data` in memory
- ❌ Loading from Manage overlay - Just updates current layer data
- ❌ Switching between patches in Manage - Rapid preview changes

**Workspace Persistence (SharedPreferences):**
- 🔄 Entire nav stack saved on any navigation change (if "Last Workspace" mode enabled)
- 🔄 Survives app restart, but NOT app uninstall
- 🔄 Separate from database - workspace is "session state", database is "saved content"

#### Linking Rules by Parent Type

**Show → Mixer:**
```kotlin
show.mixerNames += mixer.name  // Add to list if not present
```

**Mixer → Set (to specific slot):**
```kotlin
mixer.slots[parentSlotIndex].mandalaSetId = set.id
mixer.slots[parentSlotIndex].sourceType = MANDALA_SET
```

**Mixer → Mandala (to specific slot):**
```kotlin
mixer.slots[parentSlotIndex].selectedMandalaId = mandala.name
mixer.slots[parentSlotIndex].sourceType = MANDALA
```

**Set → Mandala:**
```kotlin
set.orderedMandalaIds += mandala.name  // Append to end
```

**All linking checks for duplicates first!** Won't add the same item twice.

### Key Functions:

- `saveLayer(layer)` - Persists layer to Room database
- `updateLayerData(index, data, isDirty)` - Updates in-memory layer data (no save)
- `renameLayer(index, oldName, newName)` - Delete old, update data+layer, save new
- `linkChildToParent(child, parentIndex)` - Adds child to parent's collection based on parent type
- `popToLayer(index, save)` - Cascade walk: save each layer, link if `createdFromParent = true`
- `createAndPushLayer(type, parentSlotIndex)` - Create new child WITH immediate save and linking flags set
- `createAndResetStack(type, openedFromMenu)` - Replace stack with single new layer (no parent context)

---

## Recipe System

**Recipes** define the visual structure of a Mandala using 4 frequency ratios (a, b, c, d).

Example: `3, 5, 7, 11` creates a specific spiral pattern with those harmonic relationships.

**Library:** `MandalaLibrary.MandalaRatios` contains ~hundreds of pre-calculated recipes with metadata:
- `petals` - Number of petals/arms
- `shapeRatio`, `dominanceRatio`, etc. - Aesthetic properties for sorting/filtering

**Recipe Tagging System** (added for workflow optimization):
- `RecipeTagManager` uses SharedPreferences to tag recipes
- Two tags: **Favorites** (star) and **Trash** (for deletion)
- UI: Star/trash buttons on LEFT of each recipe in picker dialog
- Icons are gold (star) or red (trash) when active, gray when inactive
- Sort modes "Favorites" and "To Delete" show tagged items at top
- Helps user curate the large library down to preferred patterns

---

## UI/UX Patterns

### Breadcrumb Design

**Constraints:**
- Mobile screen, limited horizontal space
- Can have deep paths: Show1>Mix001>Set001>Man001
- Need overflow menu always accessible

**Solution:**
- FlowRow with max 2 lines
- Each breadcrumb + arrow wrapped as single unit (no mid-item breaks)
- Individual names truncated at 100dp with ellipsis
- Reserved 48dp for menu button (never pushed off screen)
- Only shows names when `layer.data != null` (hides generic "Editor" labels)
- Name extracted from data, not layer (handles rename timing issues)

### Editor Consistency

All editors (Show, Mixer, Set, Mandala) follow same patterns:
- Preview area at top
- Controls below
- Breadcrumb header with overflow menu
- "Manage" overlay for browsing saved patches
- "Create new..." in picker dialogs

---

## Code Organization

**Key Files:**

- `MainActivity.kt` - Main activity, hosts all editors as composables
- `MandalaViewModel.kt` - Navigation stack, persistence, business logic
- `NavLayer.kt` / `LayerContent.kt` - Navigation data structures
- `SpiralRenderer.kt` - OpenGL rendering engine, feedback system, texture management
- `SharedEGLContextFactory.kt` - Creates child GL contexts that share textures
- `EditorBreadcrumbs.kt` - Breadcrumb UI component
- `RecipeTagManager.kt` - Recipe favorites/trash persistence
- `ui/components/MixerComponents.kt` - Mixer editor UI, includes `SimpleBlitHelper` for mini-monitors
- `ui/components/RecipePickerDialog.kt` - Lazy-loaded recipe picker with search/sort/tagging
- `models/ShowModels.kt`, `MixerModels.kt` - Data models
- `PatchMapper.kt` - Converts between visual source and patch data

**Patterns:**
- Each editor is a `@Composable` function in MainActivity or separate screen file
- ViewModel provides navigation primitives, editors call them
- LaunchedEffect hooks sync data back to ViewModel
- Remember current layer from stack, update data on changes

---

## Rendering Architecture

### OpenGL Context Sharing & Multi-Preview System

**The Challenge:** The app needs to display multiple simultaneous GL views:
- Main full-screen preview (mandala editor or mixer output)
- 7 mini-monitors in the Mixer editor (4 slots + A/B/F outputs)
- Individual mandala previews in Set editor

**Solution: Shared EGL Context + Per-Context Resources**

1. **SharedEGLContextFactory** creates child contexts that share textures with the main renderer
2. **Critical Insight:** Only textures and buffers are shared between GL contexts - VAOs and shader programs are NOT shared
3. Each secondary view needs its own shader programs and VAOs but can READ textures written by the main renderer

**Common Pitfall:** Calling `mainRenderer.drawTextureToCurrentBuffer()` from secondary contexts will fail because it tries to use the main context's shader program/VAO. Solution: Use `SimpleBlitHelper` which creates per-context resources.

**Memory Layout:**
- `masterTextures[0-3]` - Mixer slot outputs (sources 1-4)
- `masterTextures[4-5]` - Mixer A/B stage outputs
- `masterTextures[6]` - Final mixer output (F)
- `slotFeedback[0-7]` - Per-slot feedback ping-pong buffers (2 per slot)

### Feedback System Architecture

**Design Decision:** Per-slot persistent feedback WITHOUT ghost/trails system.

**Why?**
- Original design: 2 global feedback textures + 16 ghost textures = 152 MB VRAM
- Current design: 8 slot-specific feedback textures (2×4 slots for ping-pong) = 66 MB VRAM
- **Memory savings: 86 MB!** Critical for mid-range phones

**What Was Removed:**
- Ghost/snapshot trail system (Trails, Snap Count, Snap Mode, Snap Blend, Snap Trigger params)
- Requires 16 additional textures per slot (64 total) = unacceptable memory cost

**What Was Kept:**
- Persistent feedback accumulation (FB Decay, FB Gain)
- Feedback transformations (FB Zoom, FB Rotate, FB Shift, FB Blur)
- The "infinite kaleidoscope" effect that makes feedback visually interesting

**How It Works:**
1. Each mixer slot has 2 dedicated feedback textures that ping-pong
2. When a mandala is rendered into a slot, its feedback settings are applied using the slot's buffers
3. Feedback persists and accumulates over time within each slot independently
4. Slots can have different feedback characteristics simultaneously

**Key Functions:**
- `compositeWithFX()` - Applies feedback to a slot using its dedicated buffers
- `clearFeedbackNextFrame` flag - Resets all feedback buffers on demand

---

## Serialization & Type Safety

### LayerContent Sealed Interface (Critical Fix)

**The Problem:** Original code used `data: Any?` in `NavLayer`, which crashed during serialization because kotlinx.serialization can't handle polymorphic `Any`.

**Solution:** Created a sealed interface hierarchy for type-safe polymorphism:

```kotlin
@Serializable
sealed interface LayerContent

@Serializable data class MandalaLayerContent(val mandala: Mandala4Arm) : LayerContent
@Serializable data class SetLayerContent(val set: MandalaSet) : LayerContent
@Serializable data class MixerLayerContent(val mixer: MixerPatch) : LayerContent
@Serializable data class ShowLayerContent(val show: ShowPatch) : LayerContent
```

**Benefits:**
1. **Serialization works** - Kotlin's sealed interface serialization is built-in
2. **Type safety** - Eliminates unsafe casts and `!!` operators
3. **Exhaustive when expressions** - Compiler enforces handling all cases

**Pattern Throughout Codebase:**
- `layer.data as? MixerLayerContent` - Safe casting with null return
- `when (layer.data) { is MixerLayerContent -> ... }` - Exhaustive branching
- Always wrap/unwrap when setting/getting: `MixerLayerContent(patch)` / `(data as? MixerLayerContent)?.mixer`

**Critical Note:** If you add a new layer type, you must:
1. Create a new `*LayerContent` data class
2. Add it to the sealed interface
3. Update all `when` expressions (compiler will help!)

---

## Performance Optimizations

### Recipe Picker Laziness

**Problem:** `DropdownMenu` with ~350 recipes created all 350 composables eagerly → 2-3 second lag.

**Solution:** Replaced with Dialog + LazyColumn → only renders ~15 visible items → instant.

**Pattern:** Use `LazyColumn` for any list over ~50 items in Compose. Never use `DropdownMenu` with large datasets.

### Recipe Sorting & Metadata

**Recipe Properties Explained** (from original Python generation script):

- **Petals** - Number of lobes/arms in the pattern
- **Shape Ratio** - Aspect ratio (max_radius / min_radius), higher = more elongated
- **Arm Symmetry** (Multiplicity Class):
  - 1 = all arms distinct
  - 2 = arms pair-wise symmetric  
  - 3-4 = produces "heart-like" or butterfly symmetry
- **Complexity** (Frequency Count) - Number of distinct frequencies (1-2 = simple, 3 = good baseline, 4+ = intricate woven patterns)
- **Beat Layering** (Hierarchy Depth) - Temporal structure/rhythm as you trace the curve (0 = flat, 3 = nested beats)
- **Roundness** (Dominance Ratio) - 1.0 = perfectly circular, high values = stretched/ovoid
- **Spikiness** (Radial Variance) - Low = smooth circular, high = angular/pointy star shapes

**Sorting Implementation:**
- Each sort mode returns a `List<MandalaRatio>` sorted by the property
- Tag-based sorts (Favorites, To Delete) show tagged items first, then rest
- Display shows both petals and sort value (except for Petals mode): `"14, 23, 26, 29   5P • 2.3"`

### Recipe Tagging System

**Purpose:** Curate the large recipe library (~350 items) down to preferred patterns.

**Implementation:**
- `RecipeTagManager` uses SharedPreferences for persistence (survives app restarts)
- Two independent tags: Favorites (star icon) and Trash (delete icon)
- Tags stored as `Set<String>` of recipe IDs

**UI Pattern:**
- Star/trash icons on LEFT of each recipe item (click to toggle)
- Icons update immediately via `refreshTrigger` state increment
- Sort modes "Favorites" and "To Delete" move tagged items to top of list
- Gold star when favorited, red trash when marked for deletion

**Navigation Integration:**
- Prev/next arrows in mandala editor respect current sort mode
- Sorting by Favorites lets you browse only your curated collection
- RecipePickerDialog receives/updates `recipeSortMode` to keep navigation and picker in sync

---

## Audio & CV System

(TODO: Document CV modulation system, audio engine, etc.)

**Note to future AI:** If you work on the audio/CV features, please document them here!

---

## Testing Notes

**Manual Testing Focus:**
- Deep navigation paths (Show→Mixer→Set→Mandala)
- Renaming at each level
- Breadcrumb navigation with cascade linking
- Discard changes vs Save
- Orphaned patch cleanup

**Known Edge Cases:**
- GL context creation timing: Secondary GLSurfaceViews must wait for main context to initialize (use small delay in AndroidView factory)
- Feedback buffer synchronization: Call `GLES30.glFinish()` after main rendering completes to ensure secondary contexts can read updated textures
- DropdownMenu performance: Any list over ~50 items should use Dialog + LazyColumn instead
- SharedPreferences limitations: Recipe tags stored as `Set<String>` has size limits, but unlikely to hit with ~350 recipes

---

## Future Improvements & TODOs

**Features mentioned but not yet implemented:**
- "Edit" buttons on Mixer slots (vs just "Create new")
- Edit tracking: `createdFromParent = false` for existing items
- Better orphan management in database
- More CV modulation sources
- MIDI support?
- Actual recipe deletion UI (currently tags recipes but doesn't remove from library)

**Potential Optimizations:**
- Lower-resolution feedback textures (currently 1920×1080, could use 1024×1024 for slots)
- Texture compression for feedback buffers
- Shader program caching to speed up context creation

**Notes to future AI:**
- When you add features, update this document!
- Add comments inline in code for complex logic
- If you discover bugs or edge cases, document them
- If you refactor, explain why in this doc
- The inline comments in `SpiralRenderer.kt`, `LayerContent.kt`, and `SimpleBlitHelper` explain critical architecture decisions

---

## Questions? Discoveries?

**If you're a future AI instance working on this:**
1. Read this doc first to understand the system
2. Add your learnings as you go
3. Update this doc when making architectural changes
4. Comment complex code inline
5. **Your contributions to documentation are valuable and welcome!**

**Common pitfalls to watch for:**
- Renaming must update data AND layer name
- Breadcrumb uses data.name, not layer.name
- parentSlotIndex only valid for Mixer children
- Check for duplicates before linking child to parent
- **GL Resources:** VAOs and shader programs are NOT shared between contexts (only textures/buffers)
- **Serialization:** Always wrap patch data in appropriate `*LayerContent` class before setting `layer.data`
- **Compose Performance:** Use `LazyColumn` for any list over 50 items, never eager `Column` or `DropdownMenu`
- **State Updates:** When toggling SharedPreferences data (like tags), increment a trigger state to force recomposition
