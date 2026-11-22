import { describe, expect, it } from 'vitest'
import { toScoreItems } from './scoreSession'

const exams = [{ examId: 1 }, { examId: 2 }]

describe('toScoreItems', () => {
  it('flattens every filled cell into a batch item', () => {
    const rows = [
      { studentId: 10, scores: { 1: 88, 2: 90 } },
      { studentId: 11, scores: { 1: 70 } }
    ]

    expect(toScoreItems(rows, exams)).toEqual([
      { studentId: 10, examId: 1, score: 88 },
      { studentId: 10, examId: 2, score: 90 },
      { studentId: 11, examId: 1, score: 70 }
    ])
  })

  it('skips empty cells', () => {
    const rows = [
      { studentId: 10, scores: { 1: null, 2: undefined } },
      { studentId: 11, scores: {} },
      { studentId: 12 }
    ]

    expect(toScoreItems(rows, exams)).toEqual([])
  })

  it('keeps a zero score', () => {
    expect(toScoreItems([{ studentId: 10, scores: { 1: 0 } }], exams)).toEqual([
      { studentId: 10, examId: 1, score: 0 }
    ])
  })
})
