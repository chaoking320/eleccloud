# Retry Admin Frontend

重试平台管理后台前端项目，基于 Vue 3 + Vite + Element Plus 构建。

## 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Vite** - 下一代前端构建工具
- **Element Plus** - Vue 3 组件库
- **Vue Router** - Vue.js 官方路由管理器
- **Pinia** - Vue 状态管理库
- **Axios** - HTTP 客户端
- **ECharts** - 数据可视化图表库

## 项目结构

```
src/
├── layout/              # 布局组件
│   ├── Layout.vue      # 主布局
│   └── components/     # 布局子组件
├── views/              # 页面组件
│   ├── Dashboard.vue   # 仪表盘
│   ├── scene/         # 场景配置
│   ├── task/          # 任务监控
│   ├── failed/        # 失败任务
│   └── system/        # 系统配置
├── router/             # 路由配置
├── stores/             # 状态管理
├── utils/              # 工具函数
└── main.js            # 应用入口
```

## 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview

# 代码检查
npm run lint
```

## 开发说明

1. 开发服务器运行在 http://localhost:3000
2. API 请求会代理到后端服务 http://localhost:8081
3. 构建产物会输出到 `../src/main/resources/static` 目录，与 Spring Boot 集成

## 页面功能

- **仪表盘**: 任务统计概览和趋势图表
- **场景配置**: 重试场景的配置管理
- **任务监控**: 重试任务的监控和管理
- **失败任务**: 失败任务的查看和处理
- **系统配置**: 系统参数配置

## API 接口

所有 API 请求都通过 `/api` 前缀代理到后端服务，具体接口文档请参考后端 API 文档。