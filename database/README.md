# 数据库模块

本目录用于单独调试城市副本数据库环境。

## 启动方式

只启动 PostGIS：

```bash
cd database
docker compose up -d
```

根目录完整依赖环境：

```bash
docker compose up -d
```

两个 compose 文件都使用同一个项目名 `urban-sidequest`，数据库服务名、库名、用户名和端口保持一致。不要同时启动两套 compose。

## 默认配置

- 数据库：`urban_sidequest`
- 用户名：`urban_sidequest`
- 本地端口：`5432`
- 坐标字段：统一保存 GCJ-02 经度、纬度，并用 `location_gcj02` 建 PostGIS 空间索引。

## 迁移脚本

迁移脚本放在 `database/migrations/`。Spring Boot 后端默认从该目录读取 Flyway 迁移。

