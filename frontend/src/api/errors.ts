export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '연결을 확인하고 다시 시도해 주세요.'
}
