/*
 * Quillo Document Engine - Phase 2
 *
 * The document model and logical selection now sit above the current DOM renderer.
 * The DOM remains a compatibility projection so existing tables, images, pagination,
 * DOCX import/export and UI can continue to work while the native renderer is built.
 */
(function (global) {
  'use strict';

  const BLOCK_SELECTOR = 'p,div,h1,h2,h3,h4,h5,h6,li,blockquote,pre';

  function clamp(n, min, max) { return Math.max(min, Math.min(max, n)); }
  function isBlock(el) { return !!el && el.nodeType === 1 && el.matches(BLOCK_SELECTOR); }
  function closestBlock(node, root) {
    let el = node && node.nodeType === 1 ? node : node && node.parentElement;
    while (el && el !== root) {
      if (isBlock(el)) return el;
      el = el.parentElement;
    }
    return root;
  }
  function textOffset(root, node, offset) {
    const r = document.createRange();
    try {
      r.selectNodeContents(root);
      r.setEnd(node, offset);
      return r.toString().length;
    } catch (_) { return 0; }
  }
  function pointAtTextOffset(root, target) {
    target = Math.max(0, target | 0);
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let n, consumed = 0;
    while ((n = walker.nextNode())) {
      const len = n.nodeValue ? n.nodeValue.length : 0;
      if (target <= consumed + len) return { node: n, offset: target - consumed };
      consumed += len;
    }
    return { node: root, offset: root.childNodes.length };
  }

  class LogicalSelection {
    constructor(editor) {
      this.editor = editor;
      this.value = null;
      this.range = null;
    }

    capture() {
      const s = global.getSelection && global.getSelection();
      if (!s || !s.rangeCount || !this.editor.contains(s.anchorNode)) return false;
      const r = s.getRangeAt(0);
      const startBlock = closestBlock(r.startContainer, this.editor);
      const endBlock = closestBlock(r.endContainer, this.editor);
      if (!startBlock || startBlock === this.editor) return false;
      this.value = {
        start: { blockPath: this.pathOf(startBlock), offset: textOffset(startBlock, r.startContainer, r.startOffset) },
        end: { blockPath: this.pathOf(endBlock), offset: textOffset(endBlock, r.endContainer, r.endOffset) }
      };
      this.range = r.cloneRange();
      return true;
    }

    pathOf(block) {
      const blocks = [...this.editor.querySelectorAll(BLOCK_SELECTOR)];
      return Math.max(0, blocks.indexOf(block));
    }

    resolve(path) {
      const blocks = [...this.editor.querySelectorAll(BLOCK_SELECTOR)];
      return blocks[clamp(path | 0, 0, Math.max(0, blocks.length - 1))] || null;
    }

    restore() {
      if (this.value) {
        try {
          const a = this.resolve(this.value.start.blockPath);
          const b = this.resolve(this.value.end.blockPath);
          if (a && b) {
            const ap = pointAtTextOffset(a, this.value.start.offset);
            const bp = pointAtTextOffset(b, this.value.end.offset);
            const r = document.createRange();
            r.setStart(ap.node, ap.offset);
            r.setEnd(bp.node, bp.offset);
            this.editor.focus({ preventScroll: true });
            const s = global.getSelection();
            s.removeAllRanges(); s.addRange(r);
            this.range = r.cloneRange();
            return true;
          }
        } catch (_) {}
      }
      if (this.range) {
        try {
          this.editor.focus({ preventScroll: true });
          const s = global.getSelection();
          s.removeAllRanges(); s.addRange(this.range.cloneRange());
          return true;
        } catch (_) {}
      }
      return false;
    }

    clear() { this.value = null; this.range = null; }
    get nativeRange() { return this.range; }
    get logical() { return this.value; }
  }

  function readRuns(el) {
    const out = [];
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      if (!n.nodeValue) continue;
      const parent = n.parentElement;
      const cs = parent ? getComputedStyle(parent) : null;
      out.push({
        text: n.nodeValue,
        fontFamily: cs ? cs.fontFamily.replace(/['"]/g, '') : 'Calibri',
        fontSizePx: cs ? parseFloat(cs.fontSize) || 16 : 16,
        bold: !!(parent && (parent.closest('b,strong') || (cs && parseInt(cs.fontWeight, 10) >= 600))),
        italic: !!(parent && (parent.closest('i,em') || (cs && cs.fontStyle === 'italic'))),
        underline: !!(parent && parent.closest('u')),
        strike: !!(parent && parent.closest('s,del,strike')),
        foreground: cs ? cs.color : '#000000',
        highlight: cs && cs.backgroundColor !== 'rgba(0, 0, 0, 0)' ? cs.backgroundColor : null
      });
    }
    if (!out.length) out.push({ text: '', fontFamily: 'Calibri', fontSizePx: 16, bold: false, italic: false, underline: false, strike: false, foreground: '#000000', highlight: null });
    return out;
  }

  class DomDocumentAdapter {
    constructor(editor) {
      this.editor = editor;
      this.selection = new LogicalSelection(editor);
      this.model = { version: 2, sections: [] };
      this.undoStack = [];
      this.redoStack = [];
      this.transactionDepth = 0;
      this.syncModelFromDom();
    }

    notify() { if (typeof this.onChange === 'function') this.onChange(this.model); }

    syncModelFromDom() {
      const sections = [];
      let blocks = [];
      const flush = () => { sections.push({ page: {}, blocks }); blocks = []; };
      [...this.editor.children].forEach(el => {
        if (el.classList.contains('sb')) { flush(); return; }
        if (el.classList.contains('pb')) { blocks.push({ type: 'pageBreak' }); return; }
        if (el.matches('table')) {
          blocks.push({ type: 'table', rows: [...el.rows].map(row => [...row.cells].map(cell => ({ type: 'cell', html: cell.innerHTML, text: cell.innerText || '' }))) });
          return;
        }
        if (el.matches('img')) { blocks.push({ type: 'image', src: el.src || '', width: el.offsetWidth || 100, height: el.offsetHeight || 100 }); return; }
        if (isBlock(el)) {
          const cs = getComputedStyle(el);
          blocks.push({
            type: 'paragraph',
            tag: el.tagName.toLowerCase(),
            alignment: cs.textAlign || 'left',
            lineSpacing: cs.lineHeight,
            html: el.innerHTML,
            text: el.innerText || '',
            runs: readRuns(el)
          });
        }
      });
      if (!blocks.length) blocks.push({ type: 'paragraph', tag: 'p', alignment: 'left', lineSpacing: 'normal', html: '<br>', text: '', runs: readRuns(this.editor) });
      flush();
      this.model = { version: 2, sections };
      return this.model;
    }

    snapshot() {
      const clone = this.editor.cloneNode(true);
      clone.querySelectorAll('.pgbrk,.pl').forEach(n => n.remove());
      clone.querySelectorAll('*').forEach(n => {
        if (n.style) n.style.marginTop = '';
        if (n.classList) n.classList.remove('sel');
      });
      return clone.innerHTML;
    }

    restoreSnapshot(html, record) {
      if (record !== false) this.beginTransaction('restore');
      this.editor.innerHTML = html || '<p><br></p>';
      this.selection.clear();
      this.syncModelFromDom();
      if (record !== false) this.commitTransaction();
      this.notify();
    }

    beginTransaction(label) {
      if (this.transactionDepth++) return;
      this._tx = { label: label || 'edit', before: this.snapshot() };
    }

    commitTransaction() {
      if (!this.transactionDepth) return;
      if (--this.transactionDepth) return;
      const after = this.snapshot();
      if (this._tx && this._tx.before !== after) {
        this.undoStack.push({ label: this._tx.label, before: this._tx.before, after });
        if (this.undoStack.length > 100) this.undoStack.shift();
        this.redoStack = [];
      }
      this._tx = null;
    }

    transact(label, fn) {
      this.beginTransaction(label);
      try { return fn(); } finally { this.syncModelFromDom(); this.commitTransaction(); this.notify(); }
    }

    exec(command, value) {
      return this.transact(command, () => {
        this.selection.restore();
        const ok = document.execCommand(command, false, value);
        this.selection.capture();
        return ok;
      });
    }

    setFontSize(px) {
      return this.transact('fontSize', () => {
        if (!this.selection.restore()) return false;
        document.execCommand('fontSize', false, 7);
        this.editor.querySelectorAll('[style*="xxx-large"],font[size="7"]').forEach(f => {
          const span = document.createElement('span');
          span.style.fontSize = px + 'px'; span.innerHTML = f.innerHTML; f.replaceWith(span);
        });
        this.selection.capture(); return true;
      });
    }

    undo() {
      const tx = this.undoStack.pop(); if (!tx) return false;
      this.redoStack.push(tx);
      this.restoreSnapshot(tx.before, false);
      return true;
    }

    redo() {
      const tx = this.redoStack.pop(); if (!tx) return false;
      this.undoStack.push(tx);
      this.restoreSnapshot(tx.after, false);
      return true;
    }

    canUndo() { return this.undoStack.length > 0; }
    canRedo() { return this.redoStack.length > 0; }
  }

  class QuilloDocumentEngine {
    constructor(editor) {
      this.editor = editor;
      this.adapter = new DomDocumentAdapter(editor);
      this.selection = this.adapter.selection;
      this.version = 2;
    }
    captureSelection() { return this.selection.capture(); }
    restoreSelection() { return this.selection.restore(); }
    clearSelection() { this.selection.clear(); }
    snapshot() { return this.adapter.snapshot(); }
    restoreSnapshot(html) { return this.adapter.restoreSnapshot(html); }
    command(command, value) { return this.adapter.exec(command, value); }
    setFontSize(px) { return this.adapter.setFontSize(px); }
    syncModel() { return this.adapter.syncModelFromDom(); }
    documentModel() { return this.adapter.model; }
    logicalSelection() { return this.selection.logical; }
    undo() { return this.adapter.undo(); }
    redo() { return this.adapter.redo(); }
    canUndo() { return this.adapter.canUndo(); }
    canRedo() { return this.adapter.canRedo(); }
    beginTransaction(label) { return this.adapter.beginTransaction(label); }
    commitTransaction() { return this.adapter.commitTransaction(); }
    getDocumentModel() { return this.adapter.model; }
  }

  global.QuilloDocumentEngine = QuilloDocumentEngine;
})(window);
