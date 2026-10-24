/**
 * 写作页锚定工具（wangEditor v5 / Slate）
 *
 * 统一约定：正文纯文本 = 顶层块文本按 \n 连接。
 * 所有偏移计算、引文定位、高亮都基于这一约定，保证前后一致。
 */

const HIGHLIGHT_NAME = 'ai-suggestion'

function nodeText(node) {
  if (typeof node?.text === 'string') return node.text
  if (Array.isArray(node?.children)) return node.children.map(nodeText).join('')
  return ''
}

/** 顶层块文本，与 editor.children 一一对应 */
export function getBlockTexts(editor) {
  if (!editor?.children) return []
  return editor.children.map(nodeText)
}

/** 正文纯文本（块之间以 \n 分隔） */
export function getPlainText(editor) {
  return getBlockTexts(editor).join('\n')
}

/** Slate point → 纯文本偏移 */
export function pointToOffset(editor, point) {
  if (!editor?.children || !point?.path) return null
  const target = point.path
  let offset = 0
  let found = null

  const walk = (node, path) => {
    if (found !== null) return
    if (typeof node?.text === 'string') {
      if (path.length === target.length && path.every((v, i) => v === target[i])) {
        found = offset + Math.min(point.offset, node.text.length)
      } else {
        offset += node.text.length
      }
      return
    }
    const children = node?.children || []
    for (let i = 0; i < children.length; i++) {
      walk(children[i], [...path, i])
      if (found !== null) return
    }
  }

  for (let i = 0; i < editor.children.length; i++) {
    if (found !== null) break
    if (i > 0) offset += 1
    walk(editor.children[i], [i])
  }
  return found
}

/** 纯文本偏移 → Slate point */
export function offsetToPoint(editor, target) {
  if (!editor?.children || target < 0) return null
  let offset = 0
  let result = null

  const walk = (node, path) => {
    if (result) return
    if (typeof node?.text === 'string') {
      const len = node.text.length
      if (target <= offset + len) {
        result = { path, offset: target - offset }
      } else {
        offset += len
      }
      return
    }
    const children = node?.children || []
    for (let i = 0; i < children.length; i++) {
      walk(children[i], [...path, i])
      if (result) return
    }
  }

  for (let i = 0; i < editor.children.length; i++) {
    if (result) break
    if (i > 0) offset += 1
    walk(editor.children[i], [i])
  }
  return result
}

/** 纯文本偏移区间 → Slate Range */
export function rangeFromOffsets(editor, start, end) {
  if (start == null || end == null || end <= start) return null
  const anchor = offsetToPoint(editor, start)
  const focus = offsetToPoint(editor, end)
  if (!anchor || !focus) return null
  return { anchor, focus }
}

/** 当前选区信息；未选中或空选区返回 null */
export function getSelectionInfo(editor) {
  const selection = editor?.selection
  if (!selection) return null
  const anchorOffset = pointToOffset(editor, selection.anchor)
  const focusOffset = pointToOffset(editor, selection.focus)
  if (anchorOffset == null || focusOffset == null || anchorOffset === focusOffset) return null
  const start = Math.min(anchorOffset, focusOffset)
  const end = Math.max(anchorOffset, focusOffset)
  const plainText = getPlainText(editor)
  return {
    start,
    end,
    selectedText: plainText.slice(start, end),
    plainText,
    range: selection
  }
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 在最新正文中定位引文。
 * 先精确匹配；失败再按空白容错匹配（要求唯一）。
 */
export function locateQuote(plainText, quote) {
  const q = (quote || '').trim()
  if (!q || !plainText) return null

  const exact = plainText.indexOf(q)
  if (exact >= 0 && plainText.indexOf(q, exact + 1) === -1) {
    return { start: exact, end: exact + q.length }
  }

  const parts = q.split(/\s+/).filter(Boolean).map(escapeRegExp)
  if (!parts.length) return null
  const matches = [...plainText.matchAll(new RegExp(parts.join('\\s*'), 'g'))]
  if (matches.length !== 1) return null
  const m = matches[0]
  return { start: m.index, end: m.index + m[0].length }
}

/** 按纯文本偏移替换正文片段（会移动编辑器选区到片段末尾） */
export function replaceOffsets(editor, start, end, text) {
  if (!editor) return false
  const range = rangeFromOffsets(editor, start, end)
  if (!range) return false
  editor.select(range)
  editor.restoreSelection?.()
  editor.deleteFragment()
  if (text) editor.insertText(text)
  editor.restoreSelection?.()
  return true
}

function blockPosition(blocks, offset) {
  let acc = 0
  for (let i = 0; i < blocks.length; i++) {
    const len = blocks[i].length
    if (offset <= acc + len) return { index: i, local: offset - acc }
    acc += len + 1
  }
  return null
}

function domRangeInBlock(el, startLocal, endLocal) {
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT)
  let offset = 0
  let startNode = null
  let startOffset = 0
  let endNode = null
  let endOffset = 0
  let node = walker.nextNode()
  while (node) {
    const len = node.textContent.length
    if (!startNode && startLocal <= offset + len) {
      startNode = node
      startOffset = startLocal - offset
    }
    if (!endNode && endLocal <= offset + len) {
      endNode = node
      endOffset = endLocal - offset
    }
    if (startNode && endNode) break
    offset += len
    node = walker.nextNode()
  }
  if (!startNode || !endNode) return null
  const range = document.createRange()
  range.setStart(startNode, startOffset)
  range.setEnd(endNode, endOffset)
  return range
}

/** 非侵入高亮多条引文（CSS Custom Highlight API，不支持时静默降级） */
export function highlightQuotes(editor, ranges) {
  if (!editor || typeof CSS === 'undefined' || !CSS.highlights || typeof Highlight === 'undefined') {
    return false
  }
  const container = editor.getEditableContainer?.()
  if (!container) return false

  const blocks = getBlockTexts(editor)
  const domBlocks = Array.from(container.children || [])
  if (!blocks.length || blocks.length !== domBlocks.length) return false

  const highlight = new Highlight()
  let added = false
  for (const item of ranges || []) {
    if (!item || item.end <= item.start) continue
    const start = blockPosition(blocks, item.start)
    const end = blockPosition(blocks, item.end)
    if (!start || !end || start.index !== end.index) continue
    const range = domRangeInBlock(domBlocks[start.index], start.local, end.local)
    if (!range) continue
    highlight.add(range)
    added = true
  }
  if (!added) return false
  CSS.highlights.set(HIGHLIGHT_NAME, highlight)
  return true
}

export function clearQuoteHighlights() {
  if (typeof CSS !== 'undefined' && CSS.highlights) {
    CSS.highlights.delete(HIGHLIGHT_NAME)
  }
}
