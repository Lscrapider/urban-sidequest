# Urban Sidequest AI Python

这个模块承载 Urban Sidequest 的 Python AI 侧代码。

## 目录

- `src/urban_sidequest_ai/route_preference_judge/`：路线偏好 LLM 模拟用户评价流程。
- `src/urban_sidequest_ai/models/`：后续模型算法代码预留目录。

## 运行 judge

安装依赖：

```bash
python3 -m pip install -r ai-python/requirements.txt
```

从项目根目录执行：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge run --dry-run
```
