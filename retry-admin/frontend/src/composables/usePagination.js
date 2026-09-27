import { reactive } from 'vue'

/**
 * 通用分页组合式函数
 * @param {Function} fetchCallback 分页或页码变更时触发的数据拉取回调
 * @param {number} initialPageSize 初始每页条数
 */
export function usePagination(fetchCallback, initialPageSize = 10) {
  const pagination = reactive({
    pageNum: 1,
    pageSize: initialPageSize,
    total: 0
  })

  const handleSizeChange = (val) => {
    pagination.pageSize = val
    if (typeof fetchCallback === 'function') {
      fetchCallback()
    }
  }

  const handleCurrentChange = (val) => {
    pagination.pageNum = val
    if (typeof fetchCallback === 'function') {
      fetchCallback()
    }
  }

  const resetPage = () => {
    pagination.pageNum = 1
  }

  return {
    pagination,
    handleSizeChange,
    handleCurrentChange,
    resetPage
  }
}
