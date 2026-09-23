import { createI18n } from 'vue-i18n'
import zh from './zh.js'
import en from './en.js'

// Detect language from URL query (?lang=en/zh) or localStorage, defaulting to 'zh'
export function getDefaultLocale() {
  if (typeof window !== 'undefined') {
    const urlParams = new URLSearchParams(window.location.search)
    const paramLang = urlParams.get('lang')
    if (paramLang && (paramLang === 'en' || paramLang === 'zh')) {
      localStorage.setItem('eleccloud_lang', paramLang)
      return paramLang
    }
    const saved = localStorage.getItem('eleccloud_lang')
    if (saved && (saved === 'en' || saved === 'zh')) {
      return saved
    }
  }
  return 'zh'
}

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: getDefaultLocale(),
  fallbackLocale: 'zh',
  messages: {
    zh,
    en
  }
})

export default i18n
