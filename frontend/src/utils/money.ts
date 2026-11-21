const number = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 4 })

export function money(value: number | null) {
  if (value === null) return '확인 필요'
  return value === 0 ? '0원' : value < 10000 ? number.format(value) + '원' : number.format(value / 10000) + '만 원'
}

export function won(value: string) {
  if (!/^\d+(\.\d{1,4})?$/.test(value)) return null
  const amount = Math.round(Number(value) * 10000)
  return Number.isSafeInteger(amount) ? amount : null
}
