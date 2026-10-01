# Quillo Phase 13 — Tier A Character & Paragraph Formatting (Web → Native)

Phase 13 ports the remaining "Tier A" web-editor formatting features to the
native Kotlin engine. These features already existed as fields on the native
document model (`TextRun.strike`, `TextRun.foreground`, `TextRun.highlight`,
`TextRun.vertical`, `ParagraphBlock.style`) and were persisted by
`DocumentJsonCodec`, but were not wired end-to-end through commands, the
toolbar, the on-canvas renderer, or the DOCX/PDF exporters. Phase 13 completes
that wiring.

## Features ported
- **Strikethrough** — run-level toggle.
- **Subscript / superscript** — run-level vertical alignment with reduced glyph
  size and baseline shift.
- **Text color (foreground)** — arbitrary hex color per run.
- **Highlight** — arbitrary hex background color per run.
- **Heading styles H1 / H2 / H3** (and a "Normal" / `¶` reset) — paragraph-level
  style that scales font size and applies bold.

## Changes by layer
- **Commands (`DocumentEngine.kt`)** — new `DocumentCommand` subtypes:
  `ToggleStrike`, `SetForeground`, `SetHighlight`, `SetVertical`,
  `SetParagraphStyle`.
- **Engine (`NativeDocumentEngine.kt`)** — `applyCommand` now handles the new
  commands (run transforms + paragraph style mutation, both undoable).
  `FormattingState` was extended with `strike`, `vertical`, `style`,
  `foreground`, and `highlight` so the toolbar can reflect caret state.
- **Shared metrics (`DocumentModel.kt`)** — new helpers
  `ParagraphStyle.headingScale()`, `isHeading()`, `TextRun.verticalScale()`,
  `effectiveSizePx(style)`, `effectiveBold(style)`. Layout, canvas rendering and
  the exporters all derive sizing from these so caret, selection and painted
  glyphs stay aligned.
- **Layout (`DocumentLayout.kt`)** — `LayoutParagraph` now carries `style`; line
  height grows with the paragraph's largest effective run size (normal 16px text
  is unchanged because the previous fixed line height remains a floor); the
  wrap/measurement pass measures runs at their effective (heading/vertical)
  size.
- **Canvas (`AndroidDocumentView.kt`)** — per-run drawing now paints highlight
  background rectangles, applies heading scale + bold, offsets and shrinks
  sub/superscript, and keeps strike/underline consistent with the effective
  size. `widthTo` (caret/selection geometry) uses the same effective sizing.
  New view helpers: `toggleStrike`, `setForeground`, `setHighlight`,
  `setVertical`, `setParagraphStyle`.
- **Toolbar (`NativeEditorHost.kt`)** — new buttons: `S`, `x²`, `x₂`, `Color`,
  `Highlight`, `H1`, `H2`, `H3`, `¶`. `Color`/`Highlight` open a preset swatch
  dialog (highlight supports a `None` to clear). The status line reflects the
  new attributes.
- **DOCX export (`NativeExporters.kt`)** — runs now emit `w:strike`, `w:color`,
  `w:shd` (highlight fill), and `w:vertAlign`; heading paragraphs scale run size,
  go bold, and emit `w:outlineLvl` so Word treats them as navigable headings
  (direct formatting; no external `styles.xml` dependency).
- **PDF export (`NativeExporters.kt`)** — heading paragraphs render at the scaled
  size and bold. (PDF remains a coarse line-level renderer: per-run color,
  highlight, and sub/superscript are **not** yet applied in PDF — see below.)
- **Web compatibility bridge (`MainActivity.kt`)** — the native↔web JSON now
  round-trips `vertical`; also fixed a pre-existing malformed `for` loop in the
  table-sync path that would not have compiled.

## What was and was not verified
- **Verified:** static/structural review only — edits were made against the
  existing code paths, the command `when` is exhaustive over the sealed
  `DocumentCommand`, and the new model helpers are shared by layout, canvas and
  exporters so sizing is consistent.
- **NOT verified:** this environment has no Android SDK, only JDK 11, and no
  network, so **no Gradle build or compile was run**. "Compiles" and runtime
  behavior must be confirmed in your Android build environment.

## Known limitations (carried forward)
- PDF export does not yet apply per-run color/highlight/sub-superscript (it draws
  concatenated line text); only heading size/bold is reflected. Full per-run PDF
  fidelity would require threading run spans into the PDF renderer.
- Tier B features (lists, indent/outdent, hyperlinks, horizontal rule, table
  border styling, per-section page-number format) are not part of Phase 13.

## Build note
As with prior phases, `gradle/wrapper/gradle-wrapper.jar` is bootstrapped by the
`gradlew` scripts on first run (needs internet + JDK 17 + SDK platform 34). Run
`./gradlew assembleDebug` on a connected machine to produce the APK.
