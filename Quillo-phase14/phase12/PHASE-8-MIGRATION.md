# Quillo Phase 8 — Native Interaction Layer

Phase 8 advances the Android Canvas surface toward a practical native editor while retaining the WebView compatibility/export path.

## Added
- Stateful IME composing-region replacement for predictive/composition keyboards.
- Editable native table cells with undo/redo-backed model mutations.
- Embedded data-image bitmap decoding and Canvas rendering, including rotation/flips.
- Drag resize handle for selected images plus existing resize/rotate commands.
- Expanded native formatting toolbar: font sizes and paragraph alignment.
- Section header/footer fields in the native model, layout pages, renderer view and Android bridge payload.
- Richer native-to-Web projection for image transforms and table-cell backgrounds.

## Compatibility boundary
Native mode remains opt-in. WebView remains the compatibility projection for mature DOCX/PDF export and legacy editor features. Non-embedded image URI schemes currently render as placeholders on the native Canvas; embedded data images render as bitmaps.

## Verification
The renderer-independent Kotlin document/layout engine compiles, table-cell mutation/undo regression passes, and both document-engine.js and the inline index.html JavaScript pass Node syntax checking. A complete Android Gradle build is still unavailable because the supplied project does not include gradle/wrapper/gradle-wrapper.jar.
