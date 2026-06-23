from __future__ import annotations

from .config import BackendConfig
from .http_json import post_json


class BackendClient:
    def __init__(self, config: BackendConfig):
        self._config = config
        self._token = config.auth_token

    def ensure_token(self) -> str:
        if self._token:
            return self._token
        if not self._config.login_phone or not self._config.login_code:
            raise ValueError("缺少 backend.authToken，且 backend.login.phone/code 未配置")
        response = post_json(
            self._url("/api/auth/login"),
            {
                "phone": self._config.login_phone,
                "code": self._config.login_code,
            },
            timeout=self._config.timeout_seconds,
        )
        token_type = response.get("tokenType") or "Bearer"
        access_token = response.get("accessToken")
        if not access_token:
            raise RuntimeError("登录响应缺少 accessToken")
        self._token = f"{token_type} {access_token}"
        return self._token

    def generate_route(self, route_request: dict) -> dict:
        return post_json(
            self._url("/api/routes/requests"),
            route_request,
            headers={"Authorization": self.ensure_token()},
            timeout=self._config.timeout_seconds,
        )

    def save_judgment(self, payload: dict) -> dict:
        return post_json(
            self._url("/api/route-preferences/judgments"),
            payload,
            headers={"Authorization": self.ensure_token()},
            timeout=self._config.timeout_seconds,
        )

    def _url(self, path: str) -> str:
        return self._config.base_url + path
