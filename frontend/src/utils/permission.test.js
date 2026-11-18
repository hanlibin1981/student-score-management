import { describe, expect, it } from 'vitest'
import { canAccess } from './permission'

describe('canAccess', () => {
  it('allows any role when no roles are required', () => {
    expect(canAccess('STUDENT', undefined)).toBe(true)
    expect(canAccess('STUDENT', [])).toBe(true)
  })

  it('allows a role included in the requirement', () => {
    expect(canAccess('ADMIN', ['ADMIN', 'TEACHER'])).toBe(true)
    expect(canAccess('TEACHER', ['ADMIN', 'TEACHER'])).toBe(true)
  })

  it('denies a role not included in the requirement', () => {
    expect(canAccess('STUDENT', ['ADMIN'])).toBe(false)
  })
})
