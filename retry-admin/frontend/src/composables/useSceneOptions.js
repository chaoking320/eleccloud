import { ref } from 'vue'
import { sceneApi } from '@/api'

// 全局内存缓存，避免页面路由切换时重复调用 API
const sceneOptionsCache = ref([])
const sceneMapCache = ref({})
let isLoaded = false

/**
 * 统一场景字典与名称解析 Composable
 */
export function useSceneOptions() {
  const sceneOptions = ref(sceneOptionsCache.value)
  const sceneMap = ref(sceneMapCache.value)

  const loadScenes = async (force = false) => {
    if (isLoaded && !force && sceneOptionsCache.value.length > 0) {
      sceneOptions.value = sceneOptionsCache.value
      sceneMap.value = sceneMapCache.value
      return
    }
    try {
      const res = await sceneApi.getSceneList()
      if (res && res.data) {
        sceneOptionsCache.value = res.data
        const map = {}
        res.data.forEach((scene) => {
          map[scene.sceneType] = scene.sceneName
        })
        sceneMapCache.value = map
        sceneOptions.value = res.data
        sceneMap.value = map
        isLoaded = true
      }
    } catch (e) {
      console.error('Failed to load scenes', e)
    }
  }

  /**
   * 统一清理场景名称中的后缀括号编码或 [Demo] 前缀
   */
  const cleanSceneName = (sceneType) => {
    const name = sceneMap.value[sceneType] || sceneMapCache.value[sceneType]
    if (!name) return `场景 #${sceneType}`
    return name.replace(/\s*\(\d+\)$/, '').replace(/^\[Demo\]\s*/, '')
  }

  const getSceneName = (sceneType) => {
    const name = cleanSceneName(sceneType)
    return `${name} (#${sceneType})`
  }

  return {
    sceneOptions,
    sceneMap,
    loadScenes,
    cleanSceneName,
    getSceneName
  }
}
