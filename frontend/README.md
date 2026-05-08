# frontend

一个用于和 `paper-rag-server` 本地联调的 Vue 3 + Element Plus Demo。

## 启动

先启动后端（默认 `http://localhost:8080`），再在本目录运行：

```bash
npm install
npm run dev
```

## 可选环境变量

- `VITE_BACKEND_URL`：Vite 代理目标，默认 `http://localhost:8080`
- `VITE_API_PREFIX`：前端请求前缀，默认空字符串；如果你希望前端统一走 `/api`，可设为 `/api`

## 已实现联调能力

- 文档上传
- 文档列表分页查询
- 文档详情与分片查看
- RAG 问答与引用展示