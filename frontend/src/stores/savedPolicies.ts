import { sessionKey } from '../api/firsthome'

export type SavedPolicy = { policyKey: string; name: string; amountWon: number | null; assessmentDate: string }

const key = (id: string) => sessionKey + ':saved-policies:' + id

export function readSavedPolicies(id: string): SavedPolicy[] {
  const raw = sessionStorage.getItem(key(id))
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(item => item && typeof item.policyKey === 'string' &&
      typeof item.name === 'string' && typeof item.assessmentDate === 'string' &&
      (item.amountWon === null || Number.isSafeInteger(item.amountWon))) : []
  } catch {
    return []
  }
}

export function writeSavedPolicies(id: string, policies: SavedPolicy[]) {
  sessionStorage.setItem(key(id), JSON.stringify(policies))
}
