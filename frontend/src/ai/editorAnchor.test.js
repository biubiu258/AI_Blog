import { describe, expect, it } from 'vitest'
import {
  getPlainText,
  locateQuote,
  offsetToPoint,
  pointToOffset,
  rangeFromOffsets
} from './editorAnchor'

function editorWith(blocks) {
  return {
    children: blocks.map((text) => ({
      type: 'paragraph',
      children: [{ text }]
    }))
  }
}

describe('editorAnchor', () => {
  it('按块拼接纯文本', () => {
    const editor = editorWith(['第一段', '第二段'])
    expect(getPlainText(editor)).toBe('第一段\n第二段')
  })

  it('Slate point 与纯文本偏移互转', () => {
    const editor = {
      children: [
        { type: 'paragraph', children: [{ text: '黄昏时我' }, { text: '走到旧书摊前。' }] },
        { type: 'paragraph', children: [{ text: '摊主正在收拾。' }] }
      ]
    }
    const point = { path: [0, 1], offset: 2 }
    const offset = pointToOffset(editor, point)
    expect(offset).toBe(6)
    expect(offsetToPoint(editor, offset)).toEqual(point)
  })

  it('偏移区间可转换为 Slate Range', () => {
    const editor = editorWith(['黄昏时我走到旧书摊前。'])
    const range = rangeFromOffsets(editor, 3, 7)
    expect(range.anchor).toEqual({ path: [0, 0], offset: 3 })
    expect(range.focus).toEqual({ path: [0, 0], offset: 7 })
  })

  it('精确定位唯一引文', () => {
    const text = '黄昏时我走到旧书摊前。摊主正在收拾一摞旧杂志。'
    expect(locateQuote(text, '旧书摊前')).toEqual({ start: 6, end: 10 })
  })

  it('空白差异可容错定位', () => {
    const text = '黄昏时  我走到旧书摊前。'
    const result = locateQuote(text, '黄昏时 我走到旧书摊前。')
    expect(result).toEqual({ start: 0, end: text.length })
  })

  it('重复引文不定位', () => {
    const text = '旧书摊前的灯亮着。旧书摊前的灯亮着。'
    expect(locateQuote(text, '旧书摊前的灯亮着。')).toBeNull()
  })
})
