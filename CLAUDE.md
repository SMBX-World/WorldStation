# CLAUDE.md — WorldStation

## 项目概述

[SMBX World Station](https://station.smbx.world) 是 [SMBX World 中文社区](https://smbx.world) 的附属地图仓库 & 图床网站。用户可通过 OAuth2 登录后上传 SMBX 游戏地图文件或图片，获取直链用于社区帖子。

- **基础包名**: `ink.chyk.worldstation`
- **许可证**: MIT

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5.0 + Kotlin 1.9.25 |
| JDK | Java 21 |
| 构建工具 | Gradle (Kotlin DSL) |
| 数据库 | PostgreSQL 15 |
| ORM | Exposed 1.0.0-beta-2（JetBrains 官方 Kotlin ORM） |
| 认证 | Spring Security + OAuth2 Client（授权码模式） |
| 文件存储 | AList v3.45.0（通过 REST API 流式转发） |
| 前端 | Vue 3.5 + Vite 6 + Pinia |
| 容器化 | Docker 多阶段构建 + docker-compose |

## 目录结构

```
WorldStation/
├── build.gradle.kts              # Gradle 构建配置（依赖、插件）
├── settings.gradle.kts           # Gradle 设置（项目名）
├── docker-compose.yml            # 编排 PostgreSQL + AList + App
├── Dockerfile                    # 多阶段构建（Node → Gradle → JDK）
├── sql/                          # 数据库 DDL 脚本
│   ├── worldmap.sql              # maps 表建表语句
│   └── title_index.sql           # pg_trgm 模糊搜索索引
├── scripts/
│   ├── docker-entrypoint.sh      # 容器入口（envsubst 渲染配置 → 启动 jar）
│   └── import_maps.js            # 地图数据导入脚本
├── development_docs/
│   └── oauth2_principal_format.json  # OAuth2 用户信息响应格式参考
│
├── src/main/kotlin/ink/chyk/worldstation/
│   ├── WorldStationApplication.kt    # Spring Boot 入口
│   ├── configuration/
│   │   ├── SecurityConfig.kt         # 安全配置（CSRF、OAuth2 登录、授权规则）
│   │   ├── ApplicationConfig.kt      # RestTemplate Bean（30 分钟超时）
│   │   ├── AsyncConfig.kt            # 异步请求超时 2 小时
│   │   ├── OneDriveConfig.kt         # AList 连接配置（@ConfigurationProperties）
│   │   └── AdminConfig.kt            # 管理员用户 ID 列表配置
│   ├── controller/
│   │   ├── WorldMapController.kt     # 地图 CRUD API
│   │   ├── OneDriveController.kt     # 流式文件上传 API
│   │   ├── UserController.kt         # 当前用户信息 API
│   │   ├── MotdController.kt         # 每日消息 API
│   │   ├── VersionsController.kt     # 游戏版本枚举 API
│   │   └── SpaForwardController.kt   # SPA 前端路由回退
│   ├── dto/
│   │   ├── ApiResponseDTO.kt         # 统一响应格式 {code, message, data}
│   │   ├── WorldMapDTO.kt            # 地图 DTO（含 fromEntity 映射）
│   │   ├── UserDTO.kt                # 用户 DTO（含 isAdmin 字段）
│   │   └── MotdDTO.kt                # 每日消息 DTO
│   ├── entity/
│   │   ├── WorldMap.kt               # maps 表定义（Exposed Table）
│   │   └── Motd.kt                   # motd 表定义
│   ├── enum/
│   │   ├── GameVersion.kt            # 游戏版本枚举（7 个值）
│   │   ├── UploadFileKind.kt         # 上传类型：WORLDMAP / PICBED
│   │   └── DownloadProvider.kt       # 下载方式：DIRECT_LINK / THIRD_PARTY / UNKNOWN
│   ├── repository/
│   │   ├── WorldMapRepository.kt     # 地图数据库操作（Exposed transaction）
│   │   └── MotdRepository.kt         # 每日消息查询
│   ├── service/
│   │   └── OneDriveService.kt        # AList 文件上传/删除核心逻辑
│   └── util/
│       └── ContentTypeUtils.kt       # 文件类型检测与 MIME 映射
│
└── web/                              # 前端项目
    ├── package.json                  # Vue 3 + Vite + Pinia + @vueuse/core + marked
    ├── vite.config.js                # Vite 配置（代理、代码分割、SW 压缩）
    ├── index.html                    # HTML 入口（注册 Service Worker）
    ├── static-bundle.py              # 打包静态资源为 .bundle 文件
    ├── public/sw.js                  # Service Worker（资源包提取与缓存）
    └── src/
        ├── main.js                   # Vue 应用入口
        ├── App.vue                   # 根组件（背景+头部+路由+页脚+Spring）
        ├── utils.js                  # 工具函数（版本/下载信息映射、上传函数）
        ├── css/style.css             # 全局样式（暗色模式、响应式、工具类）
        ├── stores/
        │   ├── router.js             # 自研 SPA 路由（Pinia + history.pushState）
        │   ├── userId.js             # 用户登录状态
        │   └── url.js                # OAuth2 登录 URL
        ├── views/
        │   ├── MapListView.vue       # 首页：地图列表 + 筛选
        │   ├── UploadMapView.vue     # 上传地图页面
        │   ├── UploadImageView.vue   # 上传图片页面
        │   ├── EditMapView.vue       # 编辑地图信息页面
        │   ├── RouterView.vue        # 动态路由出口
        │   └── NotFoundView.vue      # 404 页面
        └── components/
            ├── Header.vue            # 顶部导航（Logo + 用户头像 + 登出按钮）
            ├── Footer.vue            # 页脚
            ├── ScrollingBackground.vue # 滚动背景（自适应暗色/亮色）
            ├── Spring.vue            # 回到顶部按钮（Spring Boot 双关梗）
            ├── Semisolid.vue         # SMBX 风格卡片容器（白/蓝配色）
            ├── WorldMapList.vue      # 无限滚动地图列表 + 客户端缓存
            ├── WorldMapItem.vue      # 单个地图条目
            ├── FilterBar.vue         # 地图筛选栏（标题/版本/排序/上传者，登录态感知）
            ├── UploadBox.vue         # 拖拽上传组件
            ├── ProgressBar.vue       # SMBX 风格进度条（马力欧行走动画）
            ├── InputBox.vue          # 带下划线样式的输入框
            ├── CopyUrl.vue           # 复制链接按钮（useClipboard）
            ├── UserAvatar.vue        # 用户头像（自动获取 /api/user）
            └── Motd.vue              # 每日消息（Markdown 渲染）
```

## 后端架构要点

### API 路由

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/worldmaps` | 搜索地图（分页、筛选、排序） | 公开 |
| GET | `/api/worldmaps/worldmap/{id}` | 获取单个地图 | 需要登录 |
| POST | `/api/worldmaps` | 新增地图记录 | 需要登录 |
| PUT | `/api/worldmaps` | 更新地图信息 | 需要登录（上传者或管理员） |
| DELETE | `/api/worldmaps/worldmap/{id}` | 删除地图 | 需要登录（上传者或管理员） |
| PUT | `/api/onedrive/upload` | 流式上传文件 | 需要登录 |
| GET | `/api/user` | 获取当前用户信息 | 302 重定向到登录 |
| POST | `/api/logout` | 登出（清除 session 和 cookies） | 需要登录 |
| GET | `/api/motd` | 获取启用的每日消息 | 公开 |
| GET | `/api/versions` | 获取游戏版本列表 | 公开 |
| `/{path}` | SPA 路由回退 | 前端路由 | 公开 |

### 安全模型

- **CSRF**: 启用 CookieCsrfTokenRepository（`XSRF-TOKEN` cookie），前端读取后通过 `X-XSRF-TOKEN` 头发送
- **OAuth2 认证**: smbx.world 作为 OAuth2 Provider，授权码模式，scope 为 `user.read`，`user-name-attribute` 为 `username`
- **授权**: 地图增删改操作校验 `principal.id == worldMap.uploader || isAdmin`，非上传者且非管理员返回 403
- **管理员**: 通过 `worldstation.admin.ids` 配置管理员用户 ID 列表，管理员可编辑/删除任意地图
- **登出**: `POST /api/logout` 清除 session、authentication 和 cookies，前端通过 `fetch` + `keepalive` 调用
- **Forward Headers**: `server.forward-headers-strategy: native`，适配反向代理场景

### 文件上传流程

1. 前端通过 XMLHttpRequest 流式上传（带进度回调）
2. `OneDriveController` 接收流并转发到 AList 的 `/api/fs/put` 端点
3. `OneDriveService` 处理：
   - 根据 `UploadFileKind` 确定存储路径
   - 世界地图按标题首字母分类（A-Z / 0-9 / Others）
   - 图床按用户 ID 分类（`picbed_{userId}`）
   - 自动检测 MIME 类型，浏览器报告 `application/octet-stream` 时根据扩展名纠正
   - 上传后刷新 AList 文件系统缓存
4. 返回 AList 直链 URL 给前端
5. 前端再 POST 地图元信息到 `/api/worldmaps`

### 上传限制

- 整体 multipart 上传最大 1024MB
- 图床单文件限制 30MB
- 地图支持格式：zip / gz / zst / rar / 7z / exe
- 图床支持格式：jpg / jpeg / png / gif / bmp / webp / svg
- 异步请求超时 2 小时（适配 2GB 文件 + 4MB/s 上传速度）

### 数据库

- **maps 表**: id, title, title_lower, author, uploader, game_version (enum), download_provider (enum), download_url
  - `title_lower` 列建有 `pg_trgm` GIN 索引，支持模糊搜索
  - 搜索时将用户输入的空格替换为 `%` 进行 LIKE 匹配
  - 排序支持：默认按 id 降序（最新优先），`sort=title` 时按标题升序
- **motd 表**: id, content, enabled

## 前端架构要点

### 自研 SPA 路由

项目**不使用 vue-router**，而是基于 Pinia store 自研了一个轻量路由器（`stores/router.js`）：
- 路由表硬编码在 `routes` 对象中
- 使用 `defineAsyncComponent` 懒加载视图组件
- 通过 `window.history.pushState` + `popstate` 事件实现导航
- 未匹配路由回退到 `/404`

### 静态资源打包

用 Python 脚本 `static-bundle.py` 将所有静态资源打包成单个 `.bundle` 文件：
- 文件头魔数：`sMbXwRlD`
- 结构：Magic(8) + IndexLength(4) + Index(JSON) + TotalLength(4) + Data
- 文件名含内容 MD5 哈希（如 `static-a1b2c3d4.bundle`），打包后自动更新 `sw.js` 中的占位符 URL 并清理旧 bundle
- Service Worker 拦截 `/static/*` 请求，从 bundle 中提取对应文件并缓存；bundle 中找不到的文件回退到网络请求
- Service Worker 激活时自动清理旧版缓存
- 首次访问只需下载一个 bundle 文件，后续访问完全离线可用

### 无限滚动与客户端缓存

`WorldMapList.vue` 实现了：
- 滚动到底部自动加载下一页（监听 `getBoundingClientRect`）
- 筛选条件变更时缓存当前结果到内存（`worldMapCache`），最多缓存 10 组
- 切换回之前的筛选条件时直接从缓存恢复，无需重新请求

### 地图列表页公开访问控制

- 地图列表 API（`GET /api/worldmaps`）完全公开，不再限制游客只能查看前 N 页
- 筛选栏（FilterBar）对所有用户可见，但上传按钮、排序选项中的"仅显示我上传的地图"仅在登录后显示

## 部署

### Docker Compose（推荐）

```bash
docker compose up -d
```

启动三个服务：
- **db**: PostgreSQL 15，端口 15432
- **alist**: AList v3.45.0，端口 5244
- **app**: Spring Boot，端口 8080

### 环境变量

所有配置通过环境变量注入（容器入口 `docker-entrypoint.sh` 使用 `envsubst` 渲染模板）：

| 变量 | 说明 |
|------|------|
| `SPRING_DATASOURCE_URL` | JDBC 连接串 |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 |
| `OAUTH2_CLIENT_ID` | OAuth2 客户端 ID |
| `OAUTH2_CLIENT_SECRET` | OAuth2 客户端密钥 |
| `SWAGGER_UI_ENABLED` | 是否启用 Swagger UI（`/docs`） |
| `ONEDRIVE_ALIST` | AList 服务 URL |
| `ONEDRIVE_ALIST_TOKEN` | AList API Token |

### 本地开发

```bash
# 后端（需要先启动 PostgreSQL 和 AList）
./gradlew bootRun

# 前端（开发服务器，自动代理 API 到 localhost:8080）
cd web && npm run dev
```

Vite 开发服务器已配置代理：`/api`、`/login`、`/oauth2` → `http://localhost:8080`

## 代码风格约定

- **Kotlin**: 使用 Kotlin 惯用写法，优先使用 `when` 表达式、`?.let`、`apply` 等
- **Vue**: 使用 `<script setup>` 语法，Composition API
- **注释**: 代码中偶有中文注释和幽默梗（如"高端万兆企业级路由器 powered by 威优易(TM)"）
- **DTO 映射**: 在 companion object 中定义 `fromEntity(ResultRow)` 工厂方法
- **API 响应**: 统一使用 `ApiResponseDTO<T>(code, message, data)`，成功时 code=0
- **数据库操作**: 所有数据库操作包裹在 `transaction {}` 块中
