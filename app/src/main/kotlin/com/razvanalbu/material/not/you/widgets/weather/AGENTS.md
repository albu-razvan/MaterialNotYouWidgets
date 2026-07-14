# Weather Widget — Design Rules and Constraints

## Overview

The weather pill widget shows temperature + weather icon inside a pill-shaped
container. On user tap it plays a 3-phase morph animation (pill→cookie→pill)
while new weather data is fetched. Content and state management are
centralized in `WeatherWidgetStateManager`.

---

## Architecture

### State-Manager-Owns-All-Widget-Updates

`WeatherWidgetStateManager` is the single source of truth for widget content
state. All `AppWidgetManager.updateAppWidget` calls that touch content must
route through the state manager. The three update paths are:

1. **`applyNow`** — immediate apply when no animation is running
2. **`enqueueDuringAnimation`** — queues the update; flushed at MORPH_OUT start
3. **`reapplyState`** — final apply after animation completes

Services (`BasePumpService` / `FramePumpService`) **never** set content
state directly — they read from the state manager and delegate rendering.

### Animation State Machine

The animation lives entirely in `WeatherWidgetStateManager`. Services only
render frames and apply `TickResult` values.

**Phase flow** (mandatory, never skipped):
```
MORPH_IN → ROTATE → MORPH_OUT → Completed
```

- MORPH_IN: pill → cookie morph (500ms)
- ROTATE: cookie rotates (minimum 1000ms, controlled by `MIN_ROTATE_NS`)
- MORPH_OUT: cookie → pill morph + content fades in (500ms)

**Transition rules** (in `tickAnimation`):
- MORPH_IN → ROTATE: automatic when morph fraction >= 1.0
- ROTATE → MORPH_OUT: requires BOTH `appWidgetId in animContentReady`
  AND `elapsed >= MIN_ROTATE_NS` (1000ms).
  `animContentReady` is set by `requestMorphOut()`, which is called
  **only for user-initiated refreshes**.
- MORPH_OUT → Completed: automatic when elapsed >= MORPH_DURATION_NS

`TickResult` sealed class drives the service:
- `None` — continue current phase
- `ToRotate` — switch from MORPH_IN to ROTATE (service updates rotation)
- `ToMorphOut(startRotation, targetRotation)` — switch to MORPH_OUT;
  service calls `onBeforeMorphOut()` then renders first MORPH_OUT frame
- `Completed` — animation done; service calls `onAnimationComplete()` then
  stops itself

---

## Content Image (`content_image`) Constraints

### When content_image CAN change

- **Animation IDLE** (no morph running): any state change is applied
  immediately via `applyNow`.
- **User-initiated refresh, at MORPH_OUT start**: the pending update is
  flushed by `flushContentDuringMorphOut()` → `onPushFrameHook` detects
  the state change and embeds content in the same RemoteViews as the
  morph frame.

### When content_image MUST NOT change

- **During MORPH_IN or ROTATE**: never. Changes during these phases are
  queued by `enqueueDuringAnimation` and flushed at MORPH_OUT start.
- **System-triggered update during animation**: the update is queued but
  `requestMorphOut` is **not** called, so the animation stays in ROTATE
  until content readiness + 1000ms elapse.
- **`onAppWidgetOptionsChanged` during animation**: returns early.

### Delivery Mechanism

1. **`setImageViewBitmap` (cached PNG)** — used at MORPH_OUT start via
   `applyContentStateBitmap`. The cached PNG is decoded to a `Bitmap` and
   sent directly via IPC. This is instant and survives the widget host
   layout rebuild that happens when `morph_image` changes from animation
   bitmap to static drawable.

2. **`setImageViewUri` (ContentProvider)** — used for the final state by
   `reapplyState`. Theme-aware and survives widget recreation. Produces
   exactly one `openFile` call at animation end (harmless — no animation
   frames running).

### IPC Anti-Patterns (Critical!)

- **Never call `updateAppWidget` twice on the same frame.** The second
  IPC can override or coalesce with the first, dropping actions.
- **Never call `setImageViewUri` on every animation frame.** Each call
  triggers the widget host to re-resolve the URI, producing redundant
  `openFile` calls, pipe creations, thread spawns, and PNG decodings
  (observed: 28 calls, causing visible frame drops).
- **Content source must be in the same `RemoteViews` as the morph
  bitmap.** Set content source in `onPushFrameHook` so it travels with
  the single `updateAppWidget` IPC that also carries the morph bitmap.

---

## WidgetImageProvider (ContentProvider)

### URI Format

```
content://<packageName>.widgetimages/render/<widgetId>/content
```

Stable URI — no `g` (generation) query parameter. The generation is
stored in `generationMap[widgetId]` and read at `openFile` call time.

### Cache Key Format

```
${widgetId}_content_${nightMode shl 4}_g${generation}
```

- `nightMode`: `Configuration.UI_MODE_NIGHT_YES` (32) or
  `Configuration.UI_MODE_NIGHT_NO` (16), shifted left by 4 to match
  what `precache()` stores.
- `generation`: incremented by `nextGeneration()` before each `precache()`.

### Cache Lifecycle

```
nextGeneration(widgetId)     → increment counter
invalidateCache(widgetId)    → remove all entries for widget
precache(context, widgetId, temp, iconRes)  → render both night modes
getCachedBitmap(context, widgetId)  → decode cached PNG to Bitmap (or null)
```

`precache()` runs on the background fetch thread **before**
`applyState()` is posted to the main handler. By the time MORPH_OUT
starts, the cache is populated.

### `openFile` Flow

1. Read generation from `generationMap`
2. Read night mode from current configuration
3. Get weather state (from memory or SharedPreferences)
4. `getOrCreateImage` — `computeIfAbsent` on cache (re-renders on miss)
5. `precacheOppositeTheme` — ensures other night mode is cached
6. `createPipe` — creates pipe + spawns thread to write PNG bytes

---

## FramePumpService Hooks

### `onPushFrameHook(views)`

Called on every animation frame — this is where morph bitmap + content
source + animation effects are combined into a single `RemoteViews`:

```kotlin
override fun onPushFrameHook(views: RemoteViews) {
    // Only apply content when state changes (guarded by lastContentState)
    val state = WeatherWidgetStateManager.getContentState(widgetId) ?: SUCCESS
    if (state != lastContentState) {
        lastContentState = state
        if (phase == MORPH_OUT) {
            // Use cached bitmap for instant display at morph-out start
            WeatherWidgetViews.applyContentStateBitmap(views, this, widgetId, state)
        } else {
            // MORPH_IN / ROTATE: use standard drawable/URI for old state
            WeatherWidgetViews.applyContentState(views, this, widgetId, state)
        }
    }
    // Animation effects (alpha, scale on content_image and container)
    onPushFrameView?.invoke(views)
}
```

### `onBeforeMorphOut()`

Called when `ToMorphOut` is returned. Flushes pending content updates:

```kotlin
WeatherWidgetStateManager.flushContentDuringMorphOut(this, widgetId)
```

This updates `currentContentState` and persists weather state but does
**not** call `updateAppWidget` — the content source will be picked up by
the next `onPushFrameHook` call on the same frame.

### `onAnimationComplete()`

Called when `Completed` is returned. Applies the final widget state:

```kotlin
WeatherWidgetStateManager.reapplyState(this, widgetId)
```

`reapplyState` uses `createViews` → `applyContentState` → URI content.
This is the only `openFile` call after animation. Harmless (no animation
running).

---

## WeatherWidgetViews Methods

| Method | Purpose |
|---|---|
| `createViews(context, appWidgetId, state)` | Full RemoteViews: tap intent, morph_image, content_image |
| `createResetViews(context, appWidgetId)` | Only tap intent + morph_image (no content image) |
| `applyContentState(views, context, appWidgetId, state)` | Sets content_image on existing RemoteViews (drawable or URI) |
| `applyContentStateBitmap(views, context, appWidgetId, state)` | Sets content_image using cached Bitmap for SUCCESS; falls back to `applyContentState` on cache miss |

`morph_image` drawable is resolved dynamically via
`BasePumpService.getMorphShapeRes(appWidgetId)` — not hardcoded.

---

## Animation Effects (onPushFrameView)

Defined in `WeatherPillAnimSpec.kt` per phase:

| Phase | Container Scale | Info Scale | Alpha (→clamped [0,1]) | Interpolator |
|---|---|---|---|---|
| MORPH_IN | 1→0.6 | 1→0.4 | 1→-1 (invisible) | Path(0.9,0,0.3,1) |
| ROTATE | 0.6 | 0.4 | 0 | Linear |
| MORPH_OUT | 0.6→1 | 0.4→1 | -1→1 (fade in) | Path(0.4,0,0.2,1) |

---

## Content State Transitions

```kotlin
enum class ContentState {
    REQUIRES_CONFIG,  // → ic_gear
    UPDATING,         // → ic_sync
    NO_INTERNET,      // → ic_no_internet
    ERROR,            // → ic_error
    SUCCESS,          // → cached bitmap (during MORPH_OUT) or URI (final)
}
```

- SUCCESS uses `setImageViewBitmap` from cached PNG at MORPH_OUT start
- SUCCESS uses `setImageViewUri` in `reapplyState` (final state, theme-aware)
- All other states use `setImageViewResource` with static drawables

---

## User-Initiated vs System-Initiated

### User-Initiated (tap refresh)

1. Start `FramePumpService` (foreground service)
2. `WeatherWidgetStateManager.startAnimation()` → phase = MORPH_IN
3. Background fetch → `precache` → `applyState` (→ enqueued) → `requestMorphOut`
4. Animation plays MORPH_IN → ROTATE → MORPH_OUT (with content flush)

### System-Initiated (periodic worker, onUpdate)

1. `WeatherWidgetStateManager.applyState(context, appWidgetId, UPDATING)` — shown immediately
2. Background fetch → `precache` → `applyState` → `applyNow` (IDLE phase)
3. No service started, no animation
4. `requestMorphOut` is NOT called

---

## State Persistence

- Weather state persisted to `SharedPreferences` via `persistWeatherState`
- Restored by `getOrRestoreWeatherState` on widget recreation / cold cache
- ContentProvider reads persisted state when `pngCache` is empty

---

## Morph Shape Resolution

`BasePumpService.getMorphShapeRes(widgetId)` returns the current shape
drawable. Checks `activeInstance` and animation phase; falls back to
`R.drawable.pill_shape`. Used by `createResetViews` for the morph_image
drawable in all widget views.

---

## Key Files

| File | Purpose |
|---|---|
| `WeatherWidgetStateManager.kt` | State machine, content state tracking, pending updates, persistence |
| `BasePumpService.kt` | Generic animation service (phase rendering, frame callback, pushFrame) |
| `FramePumpService.kt` | Weather-specific override: hooks, content flush, animation completion |
| `WeatherWidgetViews.kt` | View factories: createViews, applyContentState, applyContentStateBitmap |
| `WeatherImageProvider.kt` | ContentProvider: URI serving, PNG cache, precache, render |
| `WeatherPillWidget.kt` | AppWidgetProvider: onReceive, refreshAndAnimate, user/system dispatch |
| `WeatherRefreshWorker.kt` | WorkManager worker for periodic background refresh |
| `WeatherPillAnimSpec.kt` | Per-phase animation specs (scale, alpha, interpolator) |
