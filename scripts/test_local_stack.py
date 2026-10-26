#!/usr/bin/env python3
"""Docker 데몬 없이 로컬 Compose의 격리와 데이터 보존을 검사한다."""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
PASSWORD_KEYS = (
    "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD",
    "MONGO_INITDB_ROOT_PASSWORD", "GRAFANA_ADMIN_PASSWORD",
)


def render_compose(missing: str | None = None) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    for key in PASSWORD_KEYS:
        env[key] = "configuration-test-only"
    if missing:
        env[missing] = ""
    return subprocess.run(
        ["docker", "compose", "--env-file", ".env.example", "--profile", "load",
         "config", "--format", "json"],
        cwd=ROOT, env=env, capture_output=True, text=True, encoding="utf-8",
        timeout=30, check=False,
    )


class LocalStackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        result = render_compose()
        if result.returncode:
            raise AssertionError("Compose validation failed: " + result.stderr)
        cls.stack = json.loads(result.stdout)

    def test_published_ports_are_loopback_only(self) -> None:
        ports = [port for service in self.stack["services"].values()
                 for port in service.get("ports", [])]
        self.assertTrue(ports, "Expected local database and dashboard ports")
        for port in ports:
            self.assertEqual("127.0.0.1", port["host_ip"])

    def test_state_survives_container_recreation(self) -> None:
        for name, target in (("db", "/var/lib/mysql"), ("mongo", "/data/db"),
                             ("prometheus", "/prometheus"), ("grafana", "/var/lib/grafana")):
            with self.subTest(service=name):
                volumes = self.stack["services"][name]["volumes"]
                self.assertTrue(any(v["type"] == "volume" and v["target"] == target
                                    for v in volumes))

    def test_mongo_transactions_have_authenticated_replica_set(self) -> None:
        mongo = self.stack["services"]["mongo"]
        self.assertIn("--replSet", mongo.get("command") or [])
        self.assertIn("--keyFile", mongo.get("command") or [])
        self.assertTrue(any(v["type"] == "volume" and v["target"] == "/data/configdb"
                            for v in mongo["volumes"]))
        self.assertTrue(any(v["type"] == "bind" and v["target"] == "/opt/mongo"
                            and v["read_only"] for v in mongo["volumes"]))

    def test_load_is_opt_in_and_metrics_reach_prometheus(self) -> None:
        k6 = self.stack["services"]["k6"]
        self.assertIn("load", k6["profiles"])
        self.assertEqual("no", k6["restart"])
        self.assertEqual("http://prometheus:9090/api/v1/write",
                         k6["environment"]["K6_PROMETHEUS_RW_SERVER_URL"])
        self.assertIn("--web.enable-remote-write-receiver",
                      self.stack["services"]["prometheus"]["command"])

    def test_missing_passwords_fail_before_startup(self) -> None:
        for key in PASSWORD_KEYS:
            with self.subTest(key=key):
                result = render_compose(missing=key)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(key, result.stderr)

    def test_mounted_configuration_exists_and_is_read_only(self) -> None:
        for service in self.stack["services"].values():
            for volume in service.get("volumes", []):
                if volume["type"] == "bind":
                    self.assertTrue(volume["read_only"])
                    self.assertTrue(Path(volume["source"]).exists(), volume["target"])


if __name__ == "__main__":
    unittest.main()
