# Quillo Phase 7 — Native Editing Fidelity

Phase 7 advances the Android editor from model-correct editing toward a practical word-processing surface while retaining the Phase 6 WebView compatibility projection.

## Added
- Android `Paint`-backed text measurement is injected into the renderer-independent layout engine. Headless tests retain a deterministic fallback measurer.
- Rich text is drawn per `TextRun`, including font family, size, bold, italic, underline, strike and foreground color instead of styling an entire line from its first run.
- Caret hit-testing and selection geometry use measured run widths.
- Up/down caret navigation follows visual lines and preserves a preferred horizontal X position.
- Start/end selection handles can be dragged independently.
- Structured native image operations: proportional grow/shrink, 90° rotation metadata, deletion.
- Structured native table operations: add row, add column, deletion.
- Native toolbar exposes the new block operations while the existing WebView ribbon remains the compatibility UI.
- Native/WebView model payload version advanced to 7.

## Architecture
`DocumentLayoutEngine` remains Android-independent. It now accepts a `TextMeasurer` interface; `AndroidDocumentView` supplies the platform implementation. This keeps pagination testable without Android while allowing the native editor to use actual platform font metrics.

## Remaining migration work
Phase 8 should focus on editable table cells, actual bitmap/image rendering and resize handles, IME composing-region correctness, native formatting controls equivalent to the full ribbon, section/header/footer editing, and making native mode the default after feature parity is reached.
