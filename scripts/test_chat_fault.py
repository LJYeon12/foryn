#!/usr/bin/env python3
"""폐기 가능한 채팅 장애 실험의 안전성과 독립적인 결과를 검사한다."""
from __future__ import annotations

import importlib.util
import io
import json
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("chat_fault", ROOT / "scripts/chat_fault.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class ChatFaultRunnerTest(unittest.TestCase):
    def test_failed_setup_keeps_run_record_in_chat_experiments(self):
        connection = json.dumps([{"Endpoints": {"docker": {"Host": "npipe://local"}}}])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(runner, "ROOT", root), \
                    patch.object(runner, "docker", side_effect=[connection, RuntimeError("image unavailable")]), \
                    redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, "image unavailable"):
                    runner.run(1, 1)
            records = list((root / ".local/chat/experiments/fault-evidence").glob("*/run.json"))
            self.assertEqual(1, len(records))
            self.assertEqual("RuntimeError", json.loads(records[0].read_text(encoding="utf-8"))["errorType"])
            self.assertFalse((root / ".local/docs").exists())

    def info(self):
        return {"Id": "owned", "Name": "/foryn-chat-fault-1234abcd-mysql",
                "Config": {"Labels": {"foryn.purpose": runner.PURPOSE}},
                "NetworkSettings": {"Ports": {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "13307"}]}}}

    def test_cleanup_requires_name_label_and_original_id(self):
        runner.require_owned(self.info(), "foryn-chat-fault-1234abcd-mysql", "owned")
        for key in ("name", "label", "id"):
            info = self.info()
            if key == "name":
                info["Name"] = "/other"
            elif key == "label":
                info["Config"]["Labels"] = {}
            else:
                info["Id"] = "replaced"
            with self.subTest(key=key), self.assertRaises(ValueError):
                runner.require_owned(info, "foryn-chat-fault-1234abcd-mysql", "owned")

    def test_database_ports_must_be_loopback(self):
        self.assertEqual(13307, runner.endpoint(self.info(), "3306/tcp"))
        info = self.info()
        info["NetworkSettings"]["Ports"]["3306/tcp"][0]["HostIp"] = "0.0.0.0"
        with self.assertRaises(ValueError):
            runner.endpoint(info, "3306/tcp")

    def test_hidden_pending_is_not_a_success(self):
        row = {"condition": "MESSAGE_2_GENDER", "iteration": 0, "expectedMessages": 0, "visibleMessages": 0,
               "rawMessages": 1, "pendingMessages": 1, "pendingTurns": 0,
               "expectedProfileChanged": False, "profileChanged": False,
               "expectedStep": "GENDER", "currentStep": "GENDER", "guardActive": False,
               "expectedJournalStatus": "CANCELLED", "journalStatus": "CANCELLED"}
        with self.assertRaises(AssertionError):
            runner.verify_observations([row])
        row.update(rawMessages=0, pendingMessages=0)
        self.assertEqual(0, runner.verify_observations([row])["MESSAGE_2_GENDER"]["inconsistent"])
        with self.assertRaises(AssertionError):
            runner.verify_observations([row, row])


if __name__ == "__main__":
    unittest.main()
