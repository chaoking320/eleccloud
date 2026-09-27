/**
 * 统一通用格式化工具函数
 */

/**
 * 时间戳/时间字符串格式化
 */
export function formatTime(timeVal) {
  if (!timeVal) return '-'
  const date = new Date(timeVal)
  if (isNaN(date.getTime())) return '-'
  return date.toLocaleString()
}

/**
 * 格式化 JSON 字符串为易读的美化格式
 */
export function formatJson(jsonStr) {
  if (!jsonStr) return '{}'
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return String(jsonStr)
  }
}

/**
 * 格式化错误信息（处理 URL 编码）
 */
export function formatErrorMessage(msg) {
  if (!msg) return ''
  try {
    return decodeURIComponent(msg.replace(/\+/g, ' '))
  } catch (e) {
    return msg
  }
}
