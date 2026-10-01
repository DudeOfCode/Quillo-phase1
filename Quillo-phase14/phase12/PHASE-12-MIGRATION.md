# Quillo Phase 12 — Native Fidelity and Workflow Independence

Phase 12 moves ordinary Quillo export off the compatibility WebView.

## Added
- Native DOCX is now the default Word export path from the native toolbar.
- Native PDF is now the default PDF export path.
- DOCX packages embedded data-URI PNG/JPEG images as `word/media/*` and emits drawing relationships.
- DOCX emits per-section header/footer parts and PAGE fields in footers.
- Table cells now carry `columnSpan` and `verticalMerge` metadata; JSON persistence preserves both and DOCX emits `w:gridSpan` / `w:vMerge`.
- Table cell background fill is emitted to DOCX.
- Native PDF exporter uses the actual `LayoutPage.widthPx/heightPx` and `LayoutTable.width` APIs and renders header/footer/page number text.
- The Web workspace remains an explicit compatibility/import surface; it is no longer used by the native Word/PDF toolbar actions.

## Remaining fidelity work
Floating/anchored Word drawing positions, complex nested-table borders, tracked changes, comments, footnotes/endnotes, advanced fields, and exact Word font metrics are not yet modeled. Images are emitted as inline drawings.

## Build note
The supplied project still does not include `gradle/wrapper/gradle-wrapper.jar`; full Gradle/APK verification therefore requires restoring the wrapper or opening the project in an Android build environment that can regenerate it.
