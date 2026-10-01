# Quillo Phase 4 — Android Native Document Surface

Phase 4 introduces the first real Android renderer for Quillo's document engine.

## Added
- `AndroidDocumentView`: Android `View`/`Canvas` paged renderer with paper/shadows, paragraph text/alignment, table grids, image bounds, zoom, and block hit-selection.
- `NativeEditorHost`: scrollable host for the native surface.
- `MainActivity` owns `NativeDocumentEngine` and the native host alongside the existing WebView compatibility editor.
- `AndroidNativeEditor` JavaScript bridge switches surfaces, refreshes native layout, and controls native zoom.

## Architecture
`QuilloDocument -> NativeDocumentEngine -> DocumentLayoutEngine -> AndroidDocumentView`

The native renderer has no HTML/CSS/DOM dependency. Before switching surfaces, `showNativeEditor()` serializes the Phase-2 compatibility model and `AndroidNativeEditor.syncModel()` converts paragraphs/runs, page breaks, images, and table cell text into the Kotlin `QuilloDocument`. The WebView remains the default editor so existing ribbon, DOCX/PDF import/export, image tools, tables, and persistence keep working while native editing reaches parity.

## Next migration boundary
Phase 4 now has one-way DOM-to-Kotlin synchronization at the renderer boundary. The next work is bidirectional synchronization, explicit page/run identity in render commands, native caret/range handles, Android IME editing, exact run-level typography, native image decoding, and full table-cell content rendering. After those are stable, the native surface can become authoritative and WebView can be reduced to compatibility/export duties.
