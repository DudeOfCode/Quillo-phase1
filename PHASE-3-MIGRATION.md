# Quillo Phase 3 — Native document layout architecture

Phase 3 moves physical pagination out of the browser-dependent editor layer.

## What was added

- `DocumentLayoutEngine`: converts `QuilloDocument` into deterministic physical pages.
- `LayoutDocument` / `LayoutPage`: explicit page geometry and content bounds.
- `LayoutParagraph`, `LayoutImage`, `LayoutTable`: renderer-neutral positioned blocks.
- `DocumentRenderer`: renderer contract independent of Android View/WebView.
- `DefaultDocumentRenderer`: converts layout into backend-neutral drawing commands.
- `NativeDocumentEngine.layout()` and `.render()`: entry points for native layout/rendering.

## Current compatibility strategy

The WebView remains the visible renderer in this phase. Existing HTML pagination is not removed yet. The new native layout path runs independently from the DOM and can therefore be tested and adopted incrementally.

## Layout rules

The phase-3 engine currently supports:

- section page dimensions and margins
- portrait/landscape page geometry
- paragraph line wrapping
- line spacing
- paragraph spacing
- page overflow and automatic page creation
- explicit page/section breaks
- image sizing while preserving aspect ratio
- table block sizing
- deterministic page/block coordinates

Text measurement currently uses a deterministic character-width approximation. Phase 4 should replace that approximation with Android font shaping/measurement (for example `TextPaint`/`StaticLayout` or a dedicated shaping layer) before native rendering becomes authoritative.

## Phase 4 target

The next step is to introduce an Android `View`/Canvas renderer backed by `LayoutDocument`, then make it the selectable/editable document surface while retaining the existing ribbon and surrounding UI. The WebView should remain available as a compatibility/import/export surface during the transition.
