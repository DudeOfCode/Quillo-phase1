package com.quillo.app.document

/** Stable editor contract. UI code should depend on this contract, not a rendering backend. */
interface DocumentEngine {
    fun document(): QuilloDocument
    fun selection(): DocumentSelection?
    fun setSelection(selection: DocumentSelection?)
    fun apply(command: DocumentCommand)
    fun undo()
    fun redo()
}

sealed interface DocumentCommand {
    data class ToggleBold(val selection: DocumentSelection) : DocumentCommand
    data class ToggleItalic(val selection: DocumentSelection) : DocumentCommand
    data class ToggleUnderline(val selection: DocumentSelection) : DocumentCommand
    data class SetFont(val selection: DocumentSelection, val family: String) : DocumentCommand
    data class SetFontSize(val selection: DocumentSelection, val sizePx: Float) : DocumentCommand
    data class SetAlignment(val block: Int, val alignment: Alignment) : DocumentCommand
    data class InsertText(val selection: DocumentSelection, val text: String) : DocumentCommand
    data class DeleteBackward(val selection: DocumentSelection) : DocumentCommand
    data class InsertPageBreak(val block: Int) : DocumentCommand
    data class InsertSectionBreak(val block: Int) : DocumentCommand
    data class DeleteSelection(val selection: DocumentSelection) : DocumentCommand
    // Phase 13 (Tier A) character + paragraph formatting ported from the web editor.
    data class ToggleStrike(val selection: DocumentSelection) : DocumentCommand
    data class SetForeground(val selection: DocumentSelection, val color: String) : DocumentCommand
    data class SetHighlight(val selection: DocumentSelection, val color: String?) : DocumentCommand
    data class SetVertical(val selection: DocumentSelection, val vertical: VerticalText) : DocumentCommand
    data class SetParagraphStyle(val block: Int, val style: ParagraphStyle) : DocumentCommand
    // Phase 14 (Tier B) lists, indent/outdent, hyperlinks, table borders.
    data class SetListType(val block: Int, val listType: ListType) : DocumentCommand
    data class Indent(val block: Int) : DocumentCommand
    data class Outdent(val block: Int) : DocumentCommand
    data class SetLink(val selection: DocumentSelection, val url: String?) : DocumentCommand
    data class SetTableBorder(val block: Int, val border: TableBorder) : DocumentCommand
}
