# Quotes Widget — Design Rules and Constraints

## Overview

The quotes widget displays user-configured quotes rendered as styled text
bitmaps. Tap to cycle to the next quote (Fisher-Yates shuffle, no repeats
until the list is exhausted). Auto-refreshes hourly via `WorkManager`. No
animation, no foreground service.

## Architecture

### State-Manager-Owns-All-Refresh

`QuotesWidgetStateManager` is the single source of truth for widget refresh.
All paths that update widget content must route through it:

1. **`scheduleRefresh(context, appWidgetId)`** — calls `refreshWidget()` then
   enqueues `WorkManager` periodic refresh (60 min). Called from `onUpdate`,
   `onReceive` (user tap), and `QuotesConfigureActivity.onDestroy`.
2. **`refreshWidget(context, appWidgetId)`** — picks a random quote via
   `QuotesStore.pickRandomQuote()`, bumps generation + invalidates PNG cache
   (if a quote exists), then calls `updateAppWidget` with new `RemoteViews`.

### Data Flow

```
User tap / onUpdate / config done
  → QuotesWidgetStateManager.scheduleRefresh()
    → QuotesStore.pickRandomQuote()          // Fisher-Yates shuffle, persisted
    → BaseWidgetImageProvider.nextGeneration() // force URI re-resolution
    → BaseWidgetImageProvider.invalidateCache() // clear stale PNGs
    → QuotesWidgetViews.createViews()          // RemoteViews with setImageViewUri
    → AppWidgetManager.updateAppWidget()
```

### View Factory

`QuotesWidgetViews` produces two `RemoteViews`:
- `createSyncViews()` — shows `ic_sync` drawable (used during resize)
- `createViews(context, appWidgetId, quote)` — full layout with quote content
- `applyQuote()` — sets `content_image` via `setImageViewUri` (URI from
  `QuotesImageProvider`) when a quote exists, or `setImageViewResource` with
  `ic_gear` when null (no quotes configured)

`setTapIntent()` creates a `PendingIntent` for `ACTION_TAP` on
`content_container` with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.

## Data Persistence (QuotesStore)

### Storage

`SharedPreferences` (`quotes_widget_data`) per-widget key isolation:

| Key suffix | Purpose |
|---|---|
| `quotes_<id>` | JSON array of `{text, author}` objects |
| `quotes_gen_<id>` | Incremented each save, used for shuffle invalidation |
| `current_text_<id>` / `current_author_<id>` | Currently displayed quote |
| `shuffle_list_<id>` | JSON array of indices (Fisher-Yates order) |
| `shuffle_cursor_<id>` | Current position in shuffle list |
| `shuffle_gen_<id>` | Generation at shuffle creation |

### Fisher-Yates Shuffle Algorithm

`pickRandomQuote()` implements a deterministic shuffle to prevent repeats:
- On first call or when the quotes list changes (`quotesGen` mismatch), a new
  shuffle is generated via Fisher-Yates (Durstenfeld variant).
- The last index from the previous generation's shuffle is avoided at position
  0 of the new shuffle (prevents repeating the same quote across generations).
- When the cursor exceeds the shuffle size, a fresh shuffle is generated and
  the cursor resets.
- Single-quote lists short-circuit to always return the same quote.

### Cleanup

`removeQuotesData()` removes all keys for a given widget ID on widget
deletion.

## Rendering (QuotesImageProvider)

Extends `BaseWidgetImageProvider` with:
- `authoritySuffix = ".quoteswidgetimages"`
- `cacheKeyPrefix = "quote"`
- `getWidgetDimensions()` — delegates to `WidgetUtils.getSizePx()` (non-square:
  uses width and height from system options)
- `renderContent()` — delegates to top-level `renderQuoteBitmap()`

### Bitmap Rendering Pipeline

1. Loads current quote from `QuotesStore`
2. Creates themed context (`Theme_Material3Expressive_DynamicColors_DayNight`)
3. Resolves `colorOnSurface` (text), `colorPrimary` (author pill bg),
   `colorOnPrimary` (author text)
4. Loads three typefaces: `google_sans_flex_regular`, `google_sans_flex_quote`
   (italic), `google_sans_flex_bold_rounded`
5. **Quote text**: centered via `StaticLayout` with `BREAK_STRATEGY_BALANCED`;
   dynamic font sizing via `fitTextSize()` (binary search, 4f–startSize,
   max 20 iterations)
6. **Author name**: rendered in a rounded pill (`colorPrimary` bg,
   `colorOnPrimary` text), right-aligned below quote text
7. Output: transparent-background PNG via pipe-based `ParcelFileDescriptor`

### Constraints

| Property | Limit |
|---|---|
| Quote text | ≤175 chars, ≤4 lines |
| Author name | ≤20 chars, ≤1 line |
| Font size | 4f–startSize binary search, max 6 lines |

## Configuration Activity

`QuotesConfigureActivity` extends `BaseConfigureActivity` with layout
`activity_quotes_configue.xml`:

- **Quotes list**: `ListView` with `item_quote.xml` layout; each item shows
  quote text (italic) and author (bold rounded pill) with a delete button
- **Add mode**: `add_button` shows `ic_add_quote` icon; tapping inserts a new
  `Quote` (or replaces at `editingIndex`)
- **Edit mode**: tapping an existing quote fills the inputs and changes
  `add_button` to `ic_confirm_edit`; tapping the same quote again deselects
- **Validation**: `TextWatcher` enforces character/line limits; `error_text`
  shown on violation
- **Empty state**: `soft_boom` decorative shape with `ObjectAnimator` (30s
  infinite rotation)
- **On destroy**: if quotes exist, calls `scheduleRefresh()`; if empty,
  refreshes with no quotes (shows gear icon)

## Resize Handling

`onAppWidgetOptionsChanged` in `QuotesWidgetProvider`:
1. Loads current quote (if any)
2. Bumps generation + invalidates PNG cache
3. Shows sync icon immediately (`createSyncViews`)
4. Posts a delayed `Runnable` (150ms) to re-render content via `createViews`
   (debounces rapid resize events via `pendingResizes` map)

## Refresh (WorkManager)

`QuotesRefreshWorker` enqueues periodic refresh:

- Interval: 60 minutes (matching widget_info `updatePeriodMillis=0`)
- Initial delay: 60 minutes (first refresh happens on config save)
- Tag: `"quotes_refresh_$appWidgetId"` with `UPDATE` policy (replaces existing)
- Cancelled on widget deletion via `cancelPeriodicRefresh()`
- `onReceive(ACTION_BOOT_COMPLETED)` re-enqueues for all existing widgets

## Interaction

- **Tap with quotes**: `ACTION_TAP` → `scheduleRefresh()` → cycles to next
  quote in shuffle order
- **Tap without quotes**: `ACTION_TAP` → `openConfigActivity()` via
  `WidgetConfigProxyActivity`
- **Widget deletion**: cancel refresh → remove data → invalidate PNG cache

## Key Files

| File | Purpose |
|---|---|
| `QuotesWidgetProvider.kt` | AppWidgetProvider: onUpdate, onReceive, resize, tap dispatch |
| `QuotesWidgetStateManager.kt` | Refresh authority: refreshWidget, scheduleRefresh |
| `QuotesWidgetViews.kt` | View factories: createViews, createSyncViews, applyQuote, setTapIntent |
| `QuotesStore.kt` | SharedPreferences persistence, Fisher-Yates shuffle, per-widget isolation |
| `QuotesConfigureActivity.kt` | Config UI: add/edit/delete quotes, validation, IME handling |
| `QuotesRefreshWorker.kt` | WorkManager periodic worker (60 min) |
| `QuotesImageProvider.kt` | ContentProvider: text/author bitmap rendering, dynamic font sizing |
