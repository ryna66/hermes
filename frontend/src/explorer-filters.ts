import { categories, statuses } from './challenge-options'

export function explorerFilters(params: URLSearchParams) {
  const page = Number(params.get('page') ?? 0)
  return {district: params.get('district')?.trim() || undefined, category: categories.find(c => c === params.get('category')), status: statuses.find(s => s === params.get('status')), page: Number.isSafeInteger(page) && page >= 0 ? page : 0, size: 20}
}
