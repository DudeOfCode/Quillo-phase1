# Quillo Phase 5 — Native Editing Core

Phase 5 changes the Android Canvas surface from a read-only renderer into the first model-backed native editor.

## Added
- Android IME/InputConnection integration for direct keyboard input.
- Logical caret placement from touch coordinates.
- Drag range selection and Canvas selection painting.
- Selection handles/caret marker.
- Model-backed text insertion, replacement, newline insertion and Backspace.
- Undo/redo for native edits.
- Native bold/italic/underline commands routed through `DocumentEngine`.
- Small native toolbar so essential commands remain available while the WebView surface is hidden.
- Offset-aware layout lines, allowing screen positions to map back to `DocumentPosition`.

## Compatibility boundary
The WebView remains the authoritative compatibility surface for advanced Quillo features that have not yet been ported (full ribbon, DOCX/PDF workflows, rich tables/images and advanced formatting). Web → native synchronization remains supported. Native → Web lossless synchronization is intentionally not declared complete yet; native edits stay authoritative in `NativeDocumentEngine` until the next bridge/export stage.

## Next stage
Phase 6 should add exact run splitting/formatting, cursor navigation, cross-paragraph deletion/insertion, clipboard/select-all, native table/image selection, and lossless native-model → compatibility-WebView projection.
