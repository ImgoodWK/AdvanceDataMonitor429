from typing import Any

from ..config import SENSITIVE_KEY_RE


def _is_sensitive_key(key: str) -> bool:
    k = key.lower()
    return any(s in k for s in SENSITIVE_KEY_RE)


def mask_value(value: Any) -> Any:
    if isinstance(value, str):
        if len(value) <= 8:
            return "****"
        return value[:2] + "*" * (len(value) - 4) + value[-2:]
    return "****"


def mask_config(obj: Any) -> Any:
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if _is_sensitive_key(str(k)) and not isinstance(v, (dict, list)):
                out[k] = mask_value(v)
            else:
                out[k] = mask_config(v)
        return out
    if isinstance(obj, list):
        return [mask_config(x) for x in obj]
    return obj
