"""
轻量级指标中心（Prometheus 文本格式）
不依赖 prometheus-client，自实现 Counter / Histogram，暴露 GET /metrics 供
Prometheus / Grafana 抓取。企业级可观测性的最小闭环：QPS、耗时分布、
Token 消耗、工具调用次数、护栏拦截次数，全部可量化。
"""
import math
import threading
import time
from collections import defaultdict


class _Counter:
    def __init__(self):
        self._lock = threading.Lock()
        self._values = defaultdict(float)

    def inc(self, name: str, labels: str = "", value: float = 1.0):
        with self._lock:
            self._values[f"{name}|{labels}"] += value

    def render(self) -> str:
        lines = []
        grouped = defaultdict(dict)
        with self._lock:
            for key, val in self._values.items():
                name, labels = key.split("|", 1)
                grouped[name][labels] = val
        for name, series in grouped.items():
            lines.append(f"# TYPE {name} counter")
            for labels, val in series.items():
                label_str = "{" + labels + "}" if labels else ""
                lines.append(f"{name}{label_str} {val}")
        return "\n".join(lines)


class _Histogram:
    """固定桶直方图（秒），默认桶覆盖 5ms ~ 10s"""

    DEFAULT_BUCKETS = (0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)

    def __init__(self):
        self._lock = threading.Lock()
        self._data = defaultdict(lambda: {"buckets": defaultdict(int), "sum": 0.0, "count": 0})

    def observe(self, name: str, value: float, labels: str = ""):
        with self._lock:
            d = self._data[f"{name}|{labels}"]
            for upper in self.DEFAULT_BUCKETS:
                if value <= upper:
                    d["buckets"][upper] += 1
            d["buckets"][math.inf] += 1
            d["sum"] += value
            d["count"] += 1

    def render(self) -> str:
        lines = []
        with self._lock:
            keys = list(self._data.keys())
        for key in keys:
            with self._lock:
                name, labels = key.split("|", 1)
                snapshot = {
                    "buckets": dict(self._data[key]["buckets"]),
                    "sum": self._data[key]["sum"],
                    "count": self._data[key]["count"],
                }
            label_prefix = (labels.rstrip("}") + ",") if labels else ""
            lines.append(f"# TYPE {name} histogram")
            for upper, cum in snapshot["buckets"].items():
                u = "+Inf" if math.isinf(upper) else str(upper)
                lines.append(f'{name}_bucket{{{label_prefix}le="{u}"}} {cum}')
            lines.append(f'{name}_sum{{{label_prefix}}} {snapshot["sum"]}')
            lines.append(f'{name}_count{{{label_prefix}}} {snapshot["count"]}')
        return "\n".join(lines)


class Metrics:
    """全局指标注册中心（单例使用模块级 metrics 实例）"""

    def __init__(self):
        self.counter = _Counter()
        self.histogram = _Histogram()

    # ---- 语义化封装，业务代码只调用这些方法 ----
    def observe_request(self, method: str, path: str, status: int, cost: float):
        labels = f'method="{method}",path="{path}",status="{status}"'
        self.counter.inc("agent_http_requests_total", labels)
        self.histogram.observe("agent_http_request_seconds", cost,
                               f'method="{method}",path="{path}"')

    def observe_llm(self, model: str, cost: float, prompt_tokens: int, completion_tokens: int, ok: bool):
        self.counter.inc("agent_llm_calls_total", f'model="{model}",ok="{ok}"')
        self.histogram.observe("agent_llm_latency_seconds", cost, f'model="{model}"')
        if ok:
            self.counter.inc("agent_llm_prompt_tokens_total", f'model="{model}"', prompt_tokens)
            self.counter.inc("agent_llm_completion_tokens_total", f'model="{model}"', completion_tokens)

    def observe_tool(self, tool: str, ok: bool, cost: float):
        self.counter.inc("agent_tool_calls_total", f'tool="{tool}",ok="{ok}"')
        self.histogram.observe("agent_tool_latency_seconds", cost, f'tool="{tool}"')

    def observe_guardrail(self, kind: str):
        self.counter.inc("agent_guardrail_hits_total", f'type="{kind}"')

    def render(self) -> str:
        body = [self.counter.render(), self.histogram.render(),
                "# TYPE agent_uptime_seconds gauge",
                f"agent_uptime_seconds {time.time() - _START_TIME}"]
        return "\n".join(filter(None, body)) + "\n"


_START_TIME = time.time()
metrics = Metrics()
