# 后端模块

城市副本后端使用 Java + Spring Boot，负责用户、路线生成请求、路线结果、收藏、打卡、反馈、我的页和后台管理。

## 本地启动

先启动本地依赖：

```bash
docker compose up -d
```

再启动后端：

```bash
cd backend
mvn spring-boot:run
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

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `AMAP_WEB_KEY`

