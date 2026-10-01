# Quillo Phase 14 — Tier B Lists, Links & Table Borders (Web → Native)

Phase 14 ports the "Tier B" web-editor features to the native Kotlin engine:
bullet / numbered lists with nested indenting, run-level hyperlinks, and
table border styling. Unlike Tier A (Phase 13), these features did **not**
already exist on the native document model — Phase 14 adds the model fields,
commands, layout/metrics, on-canvas rendering, DOCX/PDF export, JSON
persistence (schema v11), and the web↔native bridge mapping end-to-end.

## Features ported
- **Bullet list** (`• List`) and **numbered list** (`1. List`) — paragraph-level
  list membership. Pressing the same list button again toggles the paragraph
  back to a plain paragraph.
- **Indent / outdent** (`⇥` / `⇤`) — nesting level 0–8, shared by list items and
  plain paragraphs. Nested bullet lists cycle through • ◦ ▪ glyphs; nested
  numbered lists keep an independent counter per level.
- **Hyperlink** (`Link`) — run-level link target applied over the current
  selection; rendered blue + underlined. A dialog lets you set, replace, or
  remove the link.
- **Table borders** (`Borders`) — per-table border style (None / Single /
  Double / Dashed / Dotted), width (1–8 px) and color, applied to the selected
  table via a 3-step dialog.

## Changes by layer
- **Model (`DocumentModel.kt`)** — `ParagraphBlock` gains `listType: ListType`
  and `indentLevel: Int`; `TextRun` gains `link: String?`; `TableBlock` gains
  `border: TableBorder`. New enums `ListType { NONE, BULLET, NUMBER }` and
  `BorderStyle { NONE, SINGLE, DOUBLE, DASHED, DOTTED }`, new `TableBorder`
  data class. New constants `INDENT_STEP_PX = 36f`, `LIST_MARKER_GUTTER_PX =
  28f`, `MAX_INDENT_LEVEL = 8`, and helpers `ParagraphBlock.baseIndentPx()` /
  `textIndentPx()` (base indent plus a marker gutter when the paragraph is a
  list item).
- **Commands (`DocumentEngine.kt`)** — new `DocumentCommand` subtypes
  `SetListType`, `Indent`, `Outdent`, `SetLink`, `SetTableBorder`.
- **Engine (`NativeDocumentEngine.kt`)** — `applyCommand` handles the new
  commands (list toggle, indent clamp 0…MAX, run link transform, table border
  copy), all through the existing undoable mutation path. `FormattingState`
  extended with `listType`, `indentLevel`, `link` so the toolbar reflects caret
  state. `deepCopy` now copies `TableBorder` (`border.copy()`); the Enter/split
  path inherits the current paragraph's `listType` + `indentLevel` so a new
  line continues the list.
- **Layout (`DocumentLayout.kt`)** — `LayoutParagraph` gains `marker`,
  `markerX`, `listType`, `indentLevel`. The layout bakes the text indent into
  `LayoutParagraph.x` (so caret / hit-testing / selection math stay correct
  with no further changes) and computes the list marker separately at
  `markerX`. Ordered-list counters are tracked per nesting level across
  pages/sections and reset when interrupted by a shallower level or a
  non-paragraph block.
- **Canvas (`AndroidDocumentView.kt`)** — draws the list marker at `markerX`;
  renders link runs blue (`#1A56DB`) + underlined; draws the table border grid
  using the table's `TableBorder` (stroke width, color, dash/dot path effect;
  DOUBLE approximated as 2× width). New view API: `setListType`,
  `indentParagraph`, `outdentParagraph`, `setLink`, `setTableBorder`,
  `selectedTableBorder`, `selectedLink`.
- **Exporters (`NativeExporters.kt`)**
  - DOCX: emits a `numbering.xml` part (wired into `[Content_Types].xml` and
    the document rels) **only when the document contains lists**; list
    paragraphs get `<w:numPr>` (bullet numId 1 / decimal numId 2) and plain
    indented paragraphs get `<w:ind w:left>`. Hyperlinks use a self-contained
    `HYPERLINK` field (begin/instrText/separate/end) requiring no external
    relationship — matching the existing `PAGE` field pattern. Table borders
    emit `<w:tblBorders>` for all six edges.
  - PDF: draws the list marker + indent and the table border grid (style,
    width, color).
- **Persistence (`DocumentJsonCodec.kt`)** — schema `VERSION` bumped to **11**;
  serializes/deserializes `listType`, `indentLevel`, run `link`, and the table
  `border` object (older documents default cleanly: `listType=NONE`,
  `indentLevel=0`, `link=null`, `border=SINGLE`).
- **Web bridge (`MainActivity.kt`)** — the native→web and web→native JSON
  mappers now carry `listType`, `indentLevel`, run `link`, and table `border`
  so switching between the legacy web editor and the native editor preserves
  these fields where the web model supports them.

## Verified
- Static source inspection only: field/enum/constructor ordering is consistent
  across model → commands → engine → layout → canvas → exporters → JSON codec
  → web bridge (e.g. `ParagraphBlock(runs, alignment, lineSpacing, style,
  listType, indentLevel)` and `TableBlock(rows, border)` match every call
  site). Required imports are present (`android.graphics.*` for
  `DashPathEffect`; `android.widget.*` for `EditText`/`Toast`).
- JSON round-trip ordering (serialize vs. deserialize) matches for the new
  fields; unknown/missing keys fall back to safe defaults.
- DOCX wiring is internally consistent: the `numbering.xml` part, its
  content-type override, and its relationship are all gated on the same
  `hasLists` flag.

## NOT verified
- **No compile / build was run.** This sandbox has no Android SDK, JDK 17 is
  unavailable (JDK 11 only), and network to Google/Maven is blocked, so
  `assembleDebug` cannot run here. Brace/type balance was checked by reading
  the source, which is **not** the same as a successful Kotlin compile.
- The generated `numbering.xml` was **not** opened in Microsoft Word /
  LibreOffice; list rendering fidelity (especially nested levels and the
  decimal `%n.` level text) is unverified against a real consumer.
- The self-contained `HYPERLINK` field was not validated in a real DOCX
  viewer.

## Known limitations
- **Web round-trip of lists/links/borders is partial by design.** The legacy
  web editor stores lists, links and borders as raw HTML (`innerHTML`), not as
  structured model fields. The native engine is now the primary editor, so a
  full lossless web→native→web round-trip of these specific features is out of
  scope; the bridge carries the structured fields but the HTML side may not
  reproduce every nuance.
- **PDF does not apply per-run link color.** As with the Phase 13 color /
  highlight limitation, the PDF exporter renders link text in the default
  color (links are still visible in the DOCX and in the on-screen native
  editor). Marker, indent and table borders *are* drawn in the PDF.
- **DOUBLE border is approximated** as a single stroke at 2× width on the
  canvas / PDF (DOCX uses the real `double` value).
- `numbering.xml` omits `<w:nsid>` / `<w:multiLevelType>`; most consumers
  tolerate this but it is unverified.

## Build note
Build on a machine with the Android SDK (platform 34) + JDK 17 + internet:

```
./gradlew assembleDebug
```

The Gradle wrapper self-bootstraps `gradle-wrapper.jar` (Gradle 8.7) on first
run. Debug output: `app/build/outputs/apk/debug/app-debug.apk`. A release build
additionally requires a signing keystore. Confirm the `FileProvider` authority
matches `"${packageName}.fileprovider"` for your package.
