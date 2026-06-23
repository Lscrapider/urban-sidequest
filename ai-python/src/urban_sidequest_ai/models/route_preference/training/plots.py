from __future__ import annotations

from pathlib import Path
import json
import os


LOSS_KEYS = (
    "train/loss/total",
    "valid/loss/total",
    "train/loss/ranking",
    "valid/loss/ranking",
    "train/loss/goodness",
    "valid/loss/goodness",
    "train/loss/reason",
    "valid/loss/reason",
)

RANKING_KEYS = (
    "valid/pairwiseAccuracy",
    "valid/weightedPairwiseAccuracy",
    "valid/top1Accuracy",
    "valid/top2HitRate",
    "valid/ndcg@3",
)

GOODNESS_KEYS = (
    "valid/goodnessAuc",
    "valid/goodnessPrAuc",
    "valid/goodnessAccuracy@0.5",
)

REASON_KEYS = (
    "valid/reasonConditionalMicroF1@0.5",
    "valid/reasonConditionalMacroF1@0.5",
    "valid/reasonTopIssueHitRate",
    "valid/reasonConditionalPerCodeAucMacro",
    "valid/acceptedReasonFalsePositiveRate@issueThreshold",
)


def append_history(path: Path, record: dict[str, float | int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as file:
        json.dump(record, file, ensure_ascii=False, sort_keys=True)
        file.write("\n")


def load_history(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as file:
        return [json.loads(line) for line in file if line.strip()]


def render_history_plots(history_path: Path, output_dir: Path) -> list[Path]:
    history = load_history(history_path)
    return render_plots(history, output_dir)


def render_plots(history: list[dict], output_dir: Path) -> list[Path]:
    os.environ.setdefault("MPLCONFIGDIR", str(output_dir / ".matplotlib-cache"))
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    output_dir.mkdir(parents=True, exist_ok=True)
    created = [
        _plot_keys(plt, history, LOSS_KEYS, output_dir / "loss_curves.png", "Training and validation losses"),
        _plot_keys(plt, history, RANKING_KEYS, output_dir / "ranking_metrics.png", "Validation ranking metrics"),
        _plot_keys(plt, history, GOODNESS_KEYS, output_dir / "goodness_metrics.png", "Validation goodness metrics"),
        _plot_keys(plt, history, REASON_KEYS, output_dir / "reason_metrics.png", "Validation reason metrics"),
    ]
    return [path for path in created if path is not None]


def _plot_keys(plt, history: list[dict], keys: tuple[str, ...], output_path: Path, title: str) -> Path | None:
    epochs = [record["epoch"] for record in history]
    plotted = False
    figure, axis = plt.subplots(figsize=(10, 6))
    for key in keys:
        values = [record.get(key) for record in history]
        if all(value is None for value in values):
            continue
        plotted = True
        axis.plot(epochs, values, marker="o", label=key)
    if not plotted:
        plt.close(figure)
        return None
    axis.set_title(title)
    axis.set_xlabel("epoch")
    axis.grid(True, alpha=0.3)
    axis.legend()
    figure.tight_layout()
    figure.savefig(output_path, dpi=160)
    plt.close(figure)
    return output_path
