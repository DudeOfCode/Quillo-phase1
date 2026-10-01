# Quillo Phase 9 — Native-first transition

Phase 9 makes the Android native editor the primary surface after the compatibility Web document initializes. The WebView remains available through **Web** for mature import/export and legacy tools.

## Added
- Native-first startup after Web model synchronization.
- Selection-aware formatting status (bold/italic/underline, font size, alignment).
- Native image insertion and selected-image replacement through Android's document picker.
- Native header/footer editing per section.
- Portrait/landscape switching and uniform page-margin editing.
- Native page-break and section-break commands.
- Existing Phase 8 table-cell editor, bitmap rendering, IME composition and bidirectional Web projection remain available.

## Migration boundary
The native model is increasingly authoritative for editing. WebView is still initialized because its existing import/export and legacy feature stack is required. Phase 10 should move file/import/export services behind native-facing interfaces and remove that startup dependency.
