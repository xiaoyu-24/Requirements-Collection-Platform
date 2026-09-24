<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Alert, Button, Card, Form, FormItem, Input, InputPassword, Modal, message } from 'ant-design-vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const { login, changePassword, isLoggedIn, mustChangePassword } = useAuth()
const route = useRoute()
const router = useRouter()

// 首次登录或管理员重置密码后，服务端会要求先改密才能进入系统。
// 初始化时也读取全局状态，避免用户刷新页面后遗漏改密弹窗。
const changePasswordOpen = ref(mustChangePassword.value)
const submitting = ref(false)
const errorMessage = ref('')

const loginForm = reactive({ username: '', password: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

watch(mustChangePassword, (required) => {
  if (required) changePasswordOpen.value = true
})

const passwordMismatch = computed(
  () => passwordForm.confirmPassword.length > 0 && passwordForm.newPassword !== passwordForm.confirmPassword,
)

const extractMessage = (error: unknown, fallback: string) => {
  const data = (error as { response?: { data?: { message?: string } } })?.response?.data
  return data?.message && data.message.trim().length > 0 ? data.message : fallback
}

const submitLogin = async () => {
  if (!loginForm.username.trim() || !loginForm.password) {
    errorMessage.value = '请输入账号和密码'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    const user = await login(loginForm.username.trim(), loginForm.password)
    if (user.mustChangePassword) {
      passwordForm.currentPassword = loginForm.password
      changePasswordOpen.value = true
    }
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : { name: 'dashboard' }
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = extractMessage(error, '登录失败，请重试')
  } finally {
    submitting.value = false
  }
}

const submitPasswordChange = async () => {
  if (!passwordForm.currentPassword) {
    errorMessage.value = '请输入当前密码'
    return
  }
  if (passwordForm.newPassword.length < 8) {
    errorMessage.value = '新密码长度至少 8 位'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    errorMessage.value = '两次输入的新密码不一致'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    // 服务端改密后会清除全部会话，因此这里改完需要用新密码重新登录。
    await changePassword(passwordForm.currentPassword, passwordForm.newPassword)
    message.success('密码已修改，请使用新密码登录')
    await router.replace({ name: 'login' })
    changePasswordOpen.value = false
    loginForm.password = ''
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (error) {
    errorMessage.value = extractMessage(error, '修改密码失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="!isLoggedIn" class="login-page">
    <Card class="login-card" data-test="login-card">
      <div class="login-brand">
        <AppstoreOutlined class="login-brand-icon" />
        <span class="login-brand-title">需求收集平台</span>
      </div>

      <p class="login-hint">请使用管理员分配的账号登录。</p>
      <Alert v-if="errorMessage" type="error" :message="errorMessage" show-icon class="login-alert" />
      <Form layout="vertical" @submit.prevent="submitLogin">
        <FormItem label="账号">
          <Input
            v-model:value="loginForm.username"
            data-test="login-username"
            placeholder="请输入账号"
            autocomplete="username"
            size="large"
            @press-enter="submitLogin"
          />
        </FormItem>
        <FormItem label="密码">
          <InputPassword
            v-model:value="loginForm.password"
            data-test="login-password"
            placeholder="请输入密码"
            autocomplete="current-password"
            size="large"
            @press-enter="submitLogin"
          />
        </FormItem>
        <Button
          type="primary"
          block
          size="large"
          data-test="login-submit"
          :loading="submitting"
          @click="submitLogin"
        >
          登录
        </Button>
      </Form>
      <p class="login-footer">忘记密码请联系系统管理员重置。</p>
    </Card>
  </div>

  <Modal
    v-if="isLoggedIn && mustChangePassword"
    v-model:open="changePasswordOpen"
    title="首次登录，请修改密码"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :footer="null"
    width="420px"
    data-test="change-password-modal"
  >
    <p class="login-hint">您已成功登录。为保障账号安全，请先修改初始密码。</p>
    <Alert v-if="errorMessage" type="error" :message="errorMessage" show-icon class="login-alert" />
    <Form layout="vertical" @submit.prevent="submitPasswordChange">
      <FormItem label="当前密码">
        <InputPassword
          v-model:value="passwordForm.currentPassword"
          data-test="change-password-current"
          placeholder="请输入当前密码"
          autocomplete="current-password"
          size="large"
        />
      </FormItem>
      <FormItem label="新密码">
        <InputPassword
          v-model:value="passwordForm.newPassword"
          data-test="change-password-new"
          placeholder="至少 8 位"
          autocomplete="new-password"
          size="large"
        />
      </FormItem>
      <FormItem
        label="确认新密码"
        :validate-status="passwordMismatch ? 'error' : undefined"
        :help="passwordMismatch ? '两次输入的新密码不一致' : undefined"
      >
        <InputPassword
          v-model:value="passwordForm.confirmPassword"
          data-test="change-password-confirm"
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          size="large"
          @press-enter="submitPasswordChange"
        />
      </FormItem>
      <Button
        type="primary"
        block
        size="large"
        data-test="change-password-submit"
        :loading="submitting"
        @click="submitPasswordChange"
      >
        修改并重新登录
      </Button>
    </Form>
  </Modal>
</template>
