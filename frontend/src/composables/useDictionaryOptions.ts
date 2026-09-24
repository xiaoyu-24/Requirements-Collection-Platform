import { ref } from 'vue'
import { api } from '../api'

export type DictionaryCategory = 'DEPARTMENT' | 'REQUIREMENT_TYPE'

export interface DictionaryItem {
  id: number
  category: DictionaryCategory
  name: string
  disabled: boolean
  recordVersion: number
}

const departments = ref<DictionaryItem[]>([])
const requirementTypes = ref<DictionaryItem[]>([])
let loadingPromise: Promise<void> | null = null

const loadDictionaryOptions = async (force = false) => {
  if (loadingPromise && !force) return loadingPromise

  const request = Promise.all([
    api.get<DictionaryItem[]>('/dictionaries/active', { params: { category: 'DEPARTMENT' } }),
    api.get<DictionaryItem[]>('/dictionaries/active', { params: { category: 'REQUIREMENT_TYPE' } }),
  ]).then(([departmentResponse, typeResponse]) => {
    departments.value = Array.isArray(departmentResponse.data) ? departmentResponse.data : []
    requirementTypes.value = Array.isArray(typeResponse.data) ? typeResponse.data : []
  })

  loadingPromise = request
  try {
    await request
  } finally {
    if (loadingPromise === request) loadingPromise = null
  }
}

export const useDictionaryOptions = () => ({
  departments,
  requirementTypes,
  loadDictionaryOptions,
})
