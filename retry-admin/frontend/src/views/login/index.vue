<template>
  <div class="login-container">
    <div class="login-bg-glow"></div>
    <div class="login-lang-switch">
      <el-button size="small" round @click="toggleLang">
        🌐 {{ locale === 'en' ? '简体中文' : 'English' }}
      </el-button>
    </div>
    <div class="login-box">
      <div class="login-header">
        <div class="logo-wrapper">
          <div class="logo-badge">⚡</div>
          <div class="logo-title">{{ $t('login.title') }}</div>
        </div>
        <p class="login-subtitle">{{ $t('login.subtitle') }}</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="$t('login.usernamePlaceholder')"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="$t('login.passwordPlaceholder')"
            size="large"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <div class="login-options">
          <el-checkbox v-model="rememberMe">{{ $t('login.rememberMe') }}</el-checkbox>
          <el-button link type="primary" size="small" @click="fillDemoAccount">
            {{ $t('login.fillDemo') }}
          </el-button>
        </div>

        <el-button
          :loading="loading"
          type="primary"
          size="large"
          class="login-btn"
          @click="handleLogin"
        >
          {{ loading ? $t('login.loggingIn') : $t('login.loginBtn') }}
        </el-button>
      </el-form>

      <div class="login-footer">
        <div class="footer-badge">
          <span class="pulse-dot"></span>
          <span>{{ $t('login.systemOnline') }}</span>
        </div>
        <div class="footer-copyright">
          ElecCloud Distributed Retry Platform v1.0.0
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { locale, t } = useI18n()

const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [{ required: true, message: 'Password is required', trigger: 'blur' }]
}

const toggleLang = () => {
  const next = locale.value === 'en' ? 'zh' : 'en'
  locale.value = next
  localStorage.setItem('eleccloud_lang', next)
}

const fillDemoAccount = () => {
  loginForm.username = 'admin'
  loginForm.password = 'admin123'
  ElMessage.success(locale.value === 'en' ? 'Demo credentials filled (admin / admin123)' : '已自动填入演示账号 (admin / admin123)')
}

onMounted(() => {
  if (route.query.auto === '1' || route.query.demo === '1') {
    authStore.login('admin', 'admin123').then(() => {
      const redirect = route.query.redirect || '/dashboard'
      router.push(redirect)
    })
  }
})

const handleLogin = () => {
  if (!loginFormRef.value) return
  loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login(loginForm.username, loginForm.password)
      ElMessage.success(t('login.loginSuccess'))
      const redirect = route.query.redirect || '/dashboard'
      router.push(redirect)
    } catch (err) {
      ElMessage.error(err.message || t('login.loginFailed'))
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b1329 0%, #111e42 50%, #192756 100%);
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.login-lang-switch {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 20;
}

.login-bg-glow {
  position: absolute;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(56, 139, 253, 0.15) 0%, rgba(188, 140, 255, 0.05) 50%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.login-box {
  width: 420px;
  padding: 40px 36px;
  background: rgba(18, 26, 47, 0.85);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(88, 166, 255, 0.2);
  border-radius: 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4), 0 0 25px rgba(56, 139, 253, 0.1);
  position: relative;
  z-index: 10;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}

.logo-badge {
  font-size: 24px;
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #1f6feb, #388bfd);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(31, 111, 235, 0.4);
}

.logo-title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #ffffff 40%, #79c0ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.login-subtitle {
  color: #8b949e;
  font-size: 13px;
  margin-top: 4px;
}

.login-form {
  margin-top: 20px;
}

.login-form :deep(.el-input__wrapper) {
  background-color: rgba(13, 17, 23, 0.7);
  border: 1px solid #30363d;
  box-shadow: none;
  border-radius: 8px;
  transition: all 0.25s;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: #388bfd;
  box-shadow: 0 0 0 1px #388bfd, 0 0 12px rgba(56, 139, 253, 0.3);
}

.login-form :deep(.el-input__inner) {
  color: #e6edf3;
}

.login-form :deep(.el-input__prefix) {
  color: #58a6ff;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
  font-size: 13px;
}

.login-options :deep(.el-checkbox__label) {
  color: #8b949e;
  font-size: 12px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  background: linear-gradient(135deg, #1f6feb 0%, #238636 100%);
  border: none;
  box-shadow: 0 4px 14px rgba(31, 111, 235, 0.3);
  transition: all 0.25s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(35, 134, 54, 0.4);
  background: linear-gradient(135deg, #388bfd 0%, #2ea043 100%);
}

.login-footer {
  margin-top: 28px;
  text-align: center;
  border-top: 1px solid rgba(48, 54, 61, 0.6);
  padding-top: 18px;
}

.footer-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #3fb950;
  margin-bottom: 6px;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3fb950;
  box-shadow: 0 0 8px #3fb950;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.2); opacity: 1; }
  100% { transform: scale(0.9); opacity: 0.7; }
}

.footer-copyright {
  font-size: 11px;
  color: #6e7681;
}
</style>
