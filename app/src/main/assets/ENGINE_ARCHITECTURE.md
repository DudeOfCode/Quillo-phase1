# Quillo Document Engine Migration

Phase 1 introduces `document-engine.js` as the editor/document boundary.

## Current backend

`QuilloDocumentEngine` currently delegates to `DomDocumentAdapter`. This preserves the existing UI, pagination, table system, image editor, DOCX importer/exporter and PDF exporter.

## Contract

UI commands should use:

- `documentEngine.captureSelection()`
- `documentEngine.restoreSelection()`
- `documentEngine.command(command, value)`
- `documentEngine.setFontSize(px)`
- `documentEngine.setBlockStyle(property, value)`
- `documentEngine.snapshot()`
- `documentEngine.restoreSnapshot(html)`

## Phase 2 target

Replace `DomDocumentAdapter` with a structured document model:

`Document -> Section -> Block -> TextRun/Image/Table`

Selection should become model-based rather than browser `Range` based. Pagination and rendering can then consume the model without changing the ribbon/UI contract.
