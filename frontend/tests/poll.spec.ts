import { describe, expect, it, vi } from 'vitest'
import { waitForMatch } from '../src/poll.js'

describe('waitForMatch', () => {
  it('returns the first matching item', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ id: 1, activityId: 9 }])
    const hit = await waitForMatch(load, (o: { activityId: number }) => o.activityId === 9, { tries: 3, delayMs: 1 })
    expect(hit?.id).toBe(1)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('does not fall back to the first order', async () => {
    const load = vi.fn().mockResolvedValue([{ id: 99, activityId: 1 }])
    const hit = await waitForMatch(load, (o: { activityId: number }) => o.activityId === 2, { tries: 2, delayMs: 1 })
    expect(hit).toBeNull()
  })
})
