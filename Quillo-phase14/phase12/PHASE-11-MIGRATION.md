# Phase 11 — Native Export & Recovery

Phase 11 removes WebView ownership from export for native documents.

- `NativeDocxExporter` emits a standards-based OOXML `.docx` package directly from `QuilloDocument`.
- `NativePdfExporter` renders the native layout into Android `PdfDocument` pages.
- `DocumentHistoryStore` keeps rotating native snapshots for version recovery.
- The Web compatibility editor remains available for legacy/import features.

Known boundary: the first native DOCX exporter preserves text, rich emphasis, alignment, tables, breaks and page geometry. Advanced Word constructs and exact image anchoring remain follow-up work.
