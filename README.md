# 🌙 SMBX World Station

[SMBX World Station](https://station.smbx.world) 为 [SMBX World 中文社区](https://smbx.world) 的附属地图仓库 & 图床网站。

此仓库为 SMBX World Station 的源码，基于 Spring Boot 3（Kotlin）、Vue 3 和 S3-compatible 对象存储开发。

## S3-compatible 存储配置

应用通过服务端流式上传文件，支持 AWS S3、Cloudflare R2、MinIO、Wasabi 等兼容 S3 API 的存储服务。存储桶及公开读取/CDN 需由部署方预先配置。

| 环境变量 | 说明 |
|---|---|
| `S3_ENDPOINT` | S3 API endpoint，例如 `https://s3.example.com` |
| `S3_REGION` | 签名 region；部分服务使用 `auto` |
| `S3_BUCKET` | 存储桶名称 |
| `S3_PUBLIC_BASE_URL` | 文件公开访问基址或 CDN 域名，不包含对象键 |
| `S3_PATH_STYLE_ACCESS` | 是否强制 path-style 访问，MinIO 通常设为 `true` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 静态凭据；同时留空时使用 AWS 默认凭据链 |
| `S3_SESSION_TOKEN` | 可选的临时凭据 token |
| `S3_WORLDMAP_PREFIX` | 地图对象键前缀，默认 `station` |
| `S3_PICBED_PREFIX` | 图床对象键前缀，默认 `picbed` |

上传接口位于 `/api/storage/**`。`docker-compose.yml` 只连接外部 S3-compatible 服务，不会创建存储桶或修改其公开访问策略。
