import { ref } from 'vue'

const createFormDirty = ref(false)

/** 仅用于路由离开确认与退出登录确认的填写页未保存状态。 */
export const useCreateFormState = () => {
  const setCreateFormDirty = (dirty: boolean) => {
    createFormDirty.value = dirty
  }

  return { createFormDirty, setCreateFormDirty }
}
