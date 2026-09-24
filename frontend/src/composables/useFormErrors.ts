import { reactive } from 'vue'
import { apiErrorDetails, type ApiFieldErrors } from './useApiError'

export const useFormErrors = () => {
  const errors = reactive<ApiFieldErrors>({})

  const clearErrors = () => {
    Object.keys(errors).forEach((key) => delete errors[key])
  }

  const setError = (field: string, value: string) => {
    if (value.trim()) errors[field] = value
    else delete errors[field]
  }

  const applyServerErrors = (error: unknown) => {
    const details = apiErrorDetails(error)
    Object.assign(errors, details.fieldErrors)
    return details
  }

  return { errors, clearErrors, setError, applyServerErrors }
}
