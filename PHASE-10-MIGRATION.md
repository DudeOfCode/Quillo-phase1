# Quillo Phase 10 — Native Document Lifecycle

Phase 10 removes WebView initialization from Quillo's primary document lifecycle.

## What changed
- Native editor starts immediately from an app-private autosave (or a new native document).
- `.quillo` is now a persistent native JSON format represented by `DocumentJsonCodec`.
- `DocumentRepository` stores a debounced app-private autosave and restores it on launch.
- `NativeDocumentEngine` exposes a mutation callback used by lifecycle persistence.
- Native toolbar exposes Open, Save, Word export, and PDF export commands.
- Open/Save use Android Storage Access Framework and do not require WebView.
- DOCX/PDF export is now invoked as a compatibility service: native model is projected into the hidden Web editor immediately before export.
- Web remains an explicit compatibility workspace and is no longer required to initialize before native editing begins.

## Architectural boundary
Native document ownership:
`NativeDocumentEngine -> QuilloDocument -> DocumentJsonCodec -> DocumentRepository / .quillo`

Compatibility export:
`QuilloDocument -> compatibility projection -> Web DOCX/PDF exporter`

The next phase can replace compatibility import/export one service at a time without changing the authoritative document model.
