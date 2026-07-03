# 后端模块

城市副本后端使用 Java + Spring Boot，负责用户、路线生成请求、路线结果、收藏、打卡、反馈、我的页和后台管理。

## 本地启动

后端默认配置 `src/main/resources/application.yml` 只保留环境变量占位符，可提交到仓库。
本地真实值放在 `src/main/resources/application-dev.yml`，该文件不提交。

先启动本地依赖：

```bash
cd /Users/qinzeyu/study/docker-database-common
docker compose up -d

cd /Users/qinzeyu/study/mix-java-python/urban-sidequest
docker compose run --rm postgres-init
URBAN_MINIO_PASSWORD=urban_sidequest_dev_password docker compose run --rm minio-init
```

再启动后端：

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

## 分层约定

后端按以下链路组织：

```text
Controller -> Service -> Manage / API / Mapper -> Domain
```

当前骨架只放入最小健康检查链路。后续新增业务时按包职责扩展：

- `controller`：HTTP 入口，只做参数接收和调用 service。
- `service` / `service.impl`：业务编排。
- `domain.param`：前端请求参数。
- `domain.vo`：返回给前端的视图对象。
- `domain.po`：数据库持久化对象。
- `api`：高德 Web 服务、天气、LLM 等第三方客户端。
- `config`：Redis、HTTP 客户端、线程池、鉴权等配置。

## 环境变量

- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `NEW_API_KEY`
- `ROUTE_LLM_BASE_URL`
- `ROUTE_LLM_COMPLETIONS_PATH`
- `ROUTE_LLM_MODEL`
- `ROUTE_LLM_TEMPERATURE`
- `ROUTE_SCORING_CONFIG_PATH`
- `ROUTE_PREF_MINIO_ENDPOINT`
- `ROUTE_PREF_MINIO_BUCKET`
- `ROUTE_PREF_MINIO_ACCESS_KEY`
- `ROUTE_PREF_MINIO_SECRET_KEY`
- `ROUTE_PREF_MINIO_SECURE`
- `ROUTE_PREF_MINIO_PREFIX`
- `ROUTE_PREF_MINIO_WRITE_ENABLED`
- `AMAP_WEB_KEY`
- `AMAP_WEB_KEYS`
- `AMAP_WEB_BASE_URL`
- `AMAP_WEB_CONNECT_TIMEOUT`
- `AMAP_WEB_READ_TIMEOUT`
- `AMAP_WEB_KEY_QPS`
- `BAIDU_MAP_AK`
- `BAIDU_MAP_BASE_URL`
- `BAIDU_MAP_CONNECT_TIMEOUT`
- `BAIDU_MAP_READ_TIMEOUT`
- `POI_SEARCH_PROVIDER`
- `POI_SEARCH_MIX_AMAP_WEIGHT`
- `POI_SEARCH_MIX_BAIDU_WEIGHT`
- `AUTH_JWT_ISSUER`
- `AUTH_JWT_SECRET`
- `AUTH_JWT_ACCESS_TOKEN_VALIDITY_SECONDS`
- `AUTH_DEV_VERIFICATION_CODE`

本地宿主机直接运行后端时，推荐把真实值写入被忽略的 `application-dev.yml`。
Docker 或未来 CI 不使用 `application-dev.yml`，只注入同名环境变量。

## 路线评分配置

路线评分权重和阈值属于私有训练/策略资产，不提交到 GitHub，也不拆成 `.env` 里的零散变量。

`application.yml` 只声明路径占位符：

```yaml
route:
  scoring:
    config-path: ${ROUTE_SCORING_CONFIG_PATH:}
```

本地 `application-dev.yml` 直接配置被忽略的私有文件路径：

```text
backend/config/private/route-scoring.yml
```

线上 Docker 或未来 CI/CD 通过环境变量指定容器内挂载路径：

```bash
ROUTE_SCORING_CONFIG_PATH=/app/config/route-scoring.yml
```

后端代码只读取 Spring 已绑定的 `route.scoring.config-path`，不直接读取 `.env`、系统属性或硬编码相对路径。
