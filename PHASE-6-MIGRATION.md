# Quillo Phase 6 — Bidirectional Native Editing

Phase 6 makes the Kotlin document model a practical editing authority while retaining the WebView as a compatibility projection.

## Added
- Exact rich-text run splitting for range formatting and insertion/deletion.
- Global block indexing shared by layout, selection and editing (fixes paragraph targeting after images/tables/breaks).
- Cross-paragraph selection, deletion, paragraph split on Enter, and merge on Backspace at paragraph start.
- Native clipboard: Select All, Copy, Cut, Paste.
- Left/right/home/end keyboard navigation across paragraph boundaries.
- Native image/table block hit selection.
- Native -> WebView JSON projection when returning to Web mode, preserving paragraph runs, basic formatting, page breaks, images and table text.

## Compatibility boundary
The WebView remains responsible for mature DOCX/PDF export, advanced tables/images and the full ribbon. Native-to-Web synchronization is intentionally conservative: table cell rich formatting, complex DOM-only metadata, headers/footers and advanced image metadata are not yet represented by the Phase 6 model.

## Next
Phase 7 should promote structured tables/images, Android text shaping and per-run measurement, vertical caret movement, selection-handle dragging, and richer section/page metadata so the native surface can become the default authority.
