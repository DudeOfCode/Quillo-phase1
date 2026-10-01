# Quillo architecture migration — Phase 2

## What changed

Quillo now has a document-engine boundary instead of having the ribbon directly own all DOM editing behavior.

### Web editor boundary

`app/src/main/assets/document-engine.js`

`QuilloDocumentEngine` exposes selection, formatting, snapshots and document access. Its Phase-1 implementation is `DomDocumentAdapter`, which preserves the current HTML editor while removing direct `execCommand`/Range assumptions from UI commands.

### Native document contract

`app/src/main/java/com/quillo/app/document/DocumentModel.kt`

Defines the future structured model:

`Document -> Section -> Block -> TextRun/Image/Table`

`app/src/main/java/com/quillo/app/document/DocumentEngine.kt`

Defines the stable command/selection API that a native engine can implement later.

## What remains intentionally unchanged

The current pagination/layout algorithm, table UI, image editor, DOCX importer/exporter, PDF exporter, ribbon and Android file/save integration remain in place. This avoids a risky all-at-once rewrite.

## Phase 2 — model, selection and transactions

Phase 2 introduces a real model/selection/transaction layer while retaining the DOM as a compatibility projection.

### Browser-side engine

`app/src/main/assets/document-engine.js` now provides:

- logical selections expressed as block paths + text offsets
- restoration of selections after toolbar focus changes
- a structured document mirror (`sections -> blocks -> runs/tables/images`)
- transactional edit history with undo/redo
- model synchronization after edits
- a renderer-independent engine API

The existing HTML renderer remains active deliberately. This prevents Phase 2 from breaking the current pagination, table, image and DOCX features.

### Native engine

`app/src/main/java/com/quillo/app/document/NativeDocumentEngine.kt` implements the same `DocumentEngine` contract in memory. It is renderer-independent and provides model-level commands and bounded undo/redo history.

### Document model

`DocumentModel.kt` now includes `BreakBlock` so page/section break commands have a model representation.

## Phase 3

Make the structured model authoritative for pagination and selection, then introduce a native page layout/renderer while keeping the existing ribbon/controller API.

## Phase 4

Move DOCX/PDF conversion to operate directly from the structured model and remove the remaining DOM-only conversion dependencies.

## Phase 3

Replace the DOM renderer with a native page renderer while keeping the existing ribbon and controller API.

## Phase 4

Move DOCX/PDF conversion to operate from the structured model directly.

## Phase 3

Physical document layout is now represented independently of the DOM. `DocumentLayoutEngine` converts the structured document into pages and positioned blocks; `DocumentRenderer` converts those pages into backend-neutral render commands. The current WebView remains the compatibility renderer until the native Android renderer is introduced.


## Phase 4 — Native Android document surface

Phase 4 adds `AndroidDocumentView` and `NativeEditorHost`. The Android Canvas renderer consumes the Phase-3 native layout directly and has no DOM dependency. `MainActivity` now hosts both compatibility WebView and native renderer; `AndroidNativeEditor` exposes controlled switching/refresh/zoom during migration. See `PHASE-4-MIGRATION.md`.

## Phase 5
The native Android surface is now editable: touch maps to logical document positions, Android IME input mutates `NativeDocumentEngine`, range selections are rendered natively, and basic formatting/undo/redo are model commands. The WebView remains as a compatibility surface while bidirectional projection is completed.

## Phase 6
Native editing now preserves rich-text runs, supports cross-paragraph editing/clipboard/navigation, uses global block indices consistently, and projects the native model back into the WebView compatibility editor when switching surfaces. See `PHASE-6-MIGRATION.md`.

## Phase 7
Native layout now supports injected platform text measurement. The Android surface renders per-run rich text, supports vertical caret navigation and draggable selection handles, and exposes structured image/table block mutations. WebView remains the compatibility/export projection.

## Phase 8
Phase 8 adds composing-aware IME input, editable native table cells, embedded bitmap rendering and drag image resize, expanded native formatting controls, and section header/footer data through the model/layout/bridge.

## Phase 9
Native is now the primary editing surface after initial Web compatibility-model synchronization. Added state-aware native formatting feedback, Android image insertion/replacement, header/footer editing, page orientation/margins, and page/section break controls. Web remains an explicit compatibility workspace.

## Phase 10
Native lifecycle, .quillo persistence, autosave, and native-first startup are documented in `PHASE-10-MIGRATION.md`.

## Phase 12
Ordinary Word/PDF export now originates from `QuilloDocument` through native Kotlin exporters. The WebView remains a legacy/import compatibility surface, not the normal export owner. DOCX fidelity now includes embedded inline images, section header/footer parts, PAGE fields, table spans/vertical merges, and cell fills. Table merge metadata is persisted in the native JSON format.
