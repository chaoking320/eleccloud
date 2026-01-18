<template>
  <div class="task-monitor">
    <el-card>
      <div class="card-header">
        <span>任务监控</span>
      </div>
      <div class="content">
        <p>任务监控页面 - 待实现</p>
        <p>功能包括：</p>
        <ul>
          <li>任务统计Dashboard（图表展示）</li>
          <li>任务列表页面（分页、筛选、搜索）</li>
          <li>任务详情弹窗（包含重试历史时间线）</li>
          <li>手动重试按钮</li>
        </ul>
      </div>
    </el-card>
  </div>
</template>

<script setup>
// 任务监控页面 - 将在子任务11.3中实现
</script>

<style scoped>
.task-monitor {
  padding: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 20px;
}

.content {
  padding: 20px;
}

.content ul {
  margin-left: 20px;
  margin-top: 10px;
}

.content li {
  margin-bottom: 5px;
}
</style>