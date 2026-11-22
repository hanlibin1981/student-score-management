/**
 * Flatten the student × subject grid into the batch-save payload.
 * Empty cells are skipped so an untouched input never overwrites a stored score.
 * @param {Array<{ studentId: number, scores?: Record<string, number|null> }>} rows
 * @param {Array<{ examId: number }>} exams
 * @returns {Array<{ studentId: number, examId: number, score: number }>}
 */
export function toScoreItems(rows, exams) {
  return rows.flatMap(row =>
    exams
      .map(exam => ({
        studentId: row.studentId,
        examId: exam.examId,
        score: row.scores?.[exam.examId]
      }))
      .filter(item => item.score !== null && item.score !== undefined && item.score !== '')
  )
}
