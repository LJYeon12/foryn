#!/usr/bin/env python3
"""로컬 실험의 안전 경계와 리포트 집계를 검증한다."""
import json
import io
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import report_fault


class ReportFaultRunnerTest(unittest.TestCase):
    def test_failed_setup_keeps_run_record_in_report_experiments(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(report_fault, "ROOT", root), \
                    patch.object(report_fault, "require_local_engine", side_effect=RuntimeError("setup stopped")), \
                    patch.object(report_fault, "docker") as docker, redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, "setup stopped"):
                    report_fault.execute(None)
            records = list((root / ".local/report/experiments/fault-runs").glob("*/run.json"))
            self.assertEqual(1, len(records))
            self.assertEqual(1, json.loads(records[0].read_text(encoding="utf-8"))["exitCode"])
            self.assertFalse((root / ".local/docs").exists())
            docker.assert_not_called()

    def test_diagnosis_is_current_zero_delay_only_without_network_by_default(self):
        output = io.StringIO()
        with redirect_stdout(output), patch.object(report_fault, "execute") as execute:
            self.assertEqual(0, report_fault.main(["--diagnose", "--repetitions", "10"]))
            execute.assert_not_called()
        plan = json.loads(output.getvalue())
        self.assertEqual(1, plan["variants"])
        self.assertEqual([0], plan["delayConditions"])

    def test_remote_docker_engine_is_rejected_before_any_command(self):
        with patch.dict(report_fault.os.environ, {"DOCKER_HOST": "tcp://remote:2375"}):
            with patch.object(report_fault, "docker") as docker:
                with self.assertRaises(ValueError):
                    report_fault.require_local_engine()
                docker.assert_not_called()

    def test_default_is_dry_run_without_docker(self):
        with patch.object(report_fault, "execute") as execute:
            self.assertEqual(0, report_fault.main([]))
            execute.assert_not_called()

    def test_rejects_unrelated_container_before_inspection(self):
        with patch.object(report_fault, "docker") as docker:
            with self.assertRaises(ValueError):
                report_fault.stop_owned("foryn-local-db-1")
            docker.assert_not_called()

    def test_rejects_wrong_container_owner_and_nonloopback_port(self):
        with patch.object(report_fault, "docker", return_value=json.dumps([
                {"Config": {"Labels": {"foryn.purpose": "other"}}}])):
            with self.assertRaises(ValueError):
                report_fault.stop_owned("foryn-report-fault-1234abcd-mysql")
        with patch.object(report_fault, "docker", return_value=json.dumps([
                {"NetworkSettings": {"Ports": {"3306/tcp": [
                    {"HostIp": "0.0.0.0", "HostPort": "13306"}]}}}])):
            with self.assertRaises(ValueError):
                report_fault.local_port("test", "3306/tcp")

    def test_report_pools_raw_samples_instead_of_averaging_percentiles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gates.json").write_text("[]", encoding="utf-8")
            (root / "query-counts.json").write_text("[]", encoding="utf-8")
            (root / "summary.json").write_text("[]", encoding="utf-8")
            (root / "environment.json").write_text("{}", encoding="utf-8")
            samples = [{"case": "mix-test", "variant": "D_BATCH_NO_TX", "delayMs": 150, "kind": "mysql",
                        "status": 200, "clientMs": n, "scheduleLagMs": 0} for n in range(1, 101)]
            samples[-1]["status"] = 500
            (root / "clients.ndjson").write_text("\n".join(map(json.dumps, samples)), encoding="utf-8")
            (root / "requests.ndjson").write_text("", encoding="utf-8")
            report_fault.write_comparison(root)
            text = (root / "comparison.md").read_text(encoding="utf-8")
            self.assertIn("95.00", text)
            self.assertIn("1/100", text)


if __name__ == "__main__":
    unittest.main()
