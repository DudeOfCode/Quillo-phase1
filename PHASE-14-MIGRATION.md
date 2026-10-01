# Quillo Phase 14 — Revisions, Links, Lists and Document Properties

Phase 14 extends the native Quillo document model while keeping the Phase 13 editor architecture intact.

## Added
- First-class document metadata (`DocumentProperties`).
- Bookmarks stored as logical document ranges.
- Hyperlink targets on rich-text runs.
- Paragraph list metadata for bullets and numbering.
- Revision records for insertion/deletion ranges, with engine accept/reject operations.
- `.qlo` format version 14 persistence for all Phase 14 structures; older documents remain readable through defaults.
- Native DOCX package parts for numbering and document properties.
- DOCX hyperlink relationships, bookmark markers and tracked-change markup.

## Architecture
These features live in `QuilloDocument`; they are not DOCX-only state. The DOCX exporter is an adapter from the native model to OOXML.

## Compatibility
The WebView compatibility editor remains available. The Phase 14 work intentionally does not redesign the UI.
