# Quillo Phase 13 — Advanced Word Structures

Phase 13 builds on the corrected, GitHub-buildable Phase 12 baseline.

## Added
- Native document-level comments with author/text and run-level comment ranges.
- Native footnotes and endnotes with run references.
- Native Word fields: PAGE, NUMPAGES, DATE, TIME and FILENAME.
- Floating/anchored image metadata: inline vs anchored placement, X/Y position and wrap mode.
- DOCX OOXML export for comments, footnotes, endnotes and fields.
- DOCX `wp:anchor` output for floating images, including square/tight/top-bottom/behind/in-front wrapping metadata.
- `.qlo` format version 13 persistence for all Phase 13 structures.
- Undoable engine APIs for adding comments/notes/fields and changing image anchoring.

## Compatibility
Older `.qlo` documents remain readable because every new property has a default. The WebView remains a compatibility/import surface; the new structures live in the native model and native DOCX exporter.

## Validation performed
- Renderer-independent Kotlin engine compilation.
- Comment/note/field engine regression test.
- JSON codec Kotlin syntax/type compilation against API stubs.
- Native exporter Kotlin syntax/type compilation against Android API stubs.
- Generated advanced DOCX ZIP integrity test and verification of comments/footnotes package parts.

## Remaining advanced fidelity work
Tracked changes, bookmarks/cross-references, equations, content controls, citations/bibliographies, and complete Word-compatible floating-object layout are not yet authoritative in the native editor.
