# 数据库模块

本目录用于维护城市副本数据库初始化脚本和迁移 SQL。

## 启动方式

先启动通用数据库栈：

```bash
cd /Users/qinzeyu/study/docker-database-common
docker compose up -d
```

再执行项目数据库初始化：

```bash
cd /Users/qinzeyu/study/mix-java-python/urban-sidequest
docker compose run --rm postgres-init
```

如果需要本地路线偏好训练数据写入 MinIO，再初始化项目 bucket、policy 和项目用户：

```bash
URBAN_MINIO_PASSWORD=urban_sidequest_dev_password docker compose run --rm minio-init
```

根目录 compose 只包含项目资源初始化服务，不再启动 PostgreSQL、Redis、MinIO 等基础服务。

## 默认配置

- 数据库：`urban_sidequest`
- 用户名：`urban_sidequest`
- PostgreSQL：`common-postgres:5432`
- Redis：`common-redis:6379`
- MinIO：`common-minio:9000`
- 训练数据 bucket：`urban-sidequest-training`
- 坐标字段：统一保存 GCJ-02 经度、纬度，并用 `location_gcj02` 建 PostGIS 空间索引。

## 初始化脚本

迁移脚本放在 `database/migrations/`。本项目不使用 Flyway 自动迁移，本地数据库启动后手动执行：

```bash
cd database
python3 init_database.py
```

脚本默认在项目初始化镜像内执行，通过 `psql` 连接 `common-postgres:5432`，不依赖宿主机安装 `psql` 或 Python 数据库驱动。初始化会使用 common PostgreSQL root 账号创建项目数据库和项目账号，启用 `postgis`、`pgcrypto` 扩展，然后使用项目账号执行迁移 SQL。重复执行时，如果检测到 `users` 表已存在，会跳过迁移。

路线偏好预热训练数据已迁出产品 PostgreSQL。`V14__drop_route_preference_training_tables.sql` 会删除旧训练表；执行前应先用 Python 迁移脚本把旧数据导出到 MinIO dataset，并确认训练入口可以读取该 dataset。
