export const urgencyOptions = [
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
]

export const urgencyMeta = (urgency: string | null | undefined) => ({
  HIGH: { label: '高', color: 'red' },
  MEDIUM: { label: '中', color: 'orange' },
  LOW: { label: '低', color: 'default' },
}[urgency ?? 'MEDIUM'] ?? { label: '中', color: 'orange' })
