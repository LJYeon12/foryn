import importlib.machinery
import importlib.util
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("tdd-guard")
loader = importlib.machinery.SourceFileLoader("tdd_guard_cli", str(SCRIPT_PATH))
spec = importlib.util.spec_from_loader("tdd_guard_cli", loader)
tdd_guard = importlib.util.module_from_spec(spec)
loader.exec_module(tdd_guard)


class TddGuardCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / ".omx" / "state").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def read_state(self):
        return json.loads((self.root / ".omx" / "state" / "tdd-guard.json").read_text(encoding="utf-8"))

    def write_state(self, state):
        (self.root / ".omx" / "state" / "tdd-guard.json").write_text(json.dumps(state), encoding="utf-8")

    def run_cli(self, args):
        return tdd_guard.main(args, repo_root=self.root)

    def parse_iso(self, value):
        if value.endswith("Z"):
            value = value[:-1] + "+00:00"
        return datetime.fromisoformat(value).astimezone(timezone.utc)

    def test_start_creates_unconfirmed_state(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        state = self.read_state()
        self.assertTrue(state["active"])
        self.assertFalse(state["red_confirmed"])
        self.assertEqual("sample", state["topic"])

    def test_red_alias_does_not_grant_red_confirmation(self):
        self.assertEqual(0, self.run_cli(["red", "--topic", "sample"] ))
        self.assertFalse(self.read_state()["red_confirmed"])

    def test_check_records_red_on_failing_command(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        code = self.run_cli(["check", "--", "python3", "-c", "import sys; print('expected red'); sys.exit(1)"])
        self.assertEqual(0, code)
        state = self.read_state()
        self.assertTrue(state["red_confirmed"])
        self.assertEqual(1, state["red_exit_code"])
        self.assertIn("expected red", state["red_output_excerpt"])

    def test_check_refreshes_expired_token_on_red_confirmation(self):
        expired_at = datetime.now(timezone.utc) - timedelta(minutes=5)
        check_started_at = datetime.now(timezone.utc)
        self.write_state({
            "version": 1,
            "active": True,
            "red_confirmed": False,
            "expires_at": tdd_guard.iso(expired_at),
        })

        code = self.run_cli(["check", "--", "python3", "-c", "import sys; sys.exit(1)"])

        state = self.read_state()
        refreshed_expires_at = self.parse_iso(state["expires_at"])
        self.assertEqual(0, code)
        self.assertTrue(state["red_confirmed"])
        self.assertGreater(refreshed_expires_at, expired_at)
        self.assertGreater(refreshed_expires_at, check_started_at)

    def test_check_refreshes_expiration_using_configured_ttl(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample", "--ttl-minutes", "1"]))
        state = self.read_state()
        self.assertEqual(1, state["ttl_minutes"])

        check_started_at = datetime.now(timezone.utc)
        code = self.run_cli(["check", "--", "python3", "-c", "import sys; sys.exit(1)"])

        state = self.read_state()
        refreshed_expires_at = self.parse_iso(state["expires_at"])
        refreshed_window = refreshed_expires_at - check_started_at
        self.assertEqual(0, code)
        self.assertTrue(state["red_confirmed"])
        self.assertEqual(1, state["ttl_minutes"])
        self.assertGreater(refreshed_window, timedelta(seconds=0))
        self.assertLess(refreshed_window, timedelta(minutes=2))

    def test_check_requires_optional_regex_match(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        code = self.run_cli(["check", "--expect-regex", "needle", "--", "python3", "-c", "import sys; print('other'); sys.exit(1)"])
        self.assertNotEqual(0, code)
        self.assertFalse(self.read_state()["red_confirmed"])

    def test_successful_check_does_not_grant_red(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        code = self.run_cli(["check", "--", "python3", "-c", "print('green')"])
        self.assertNotEqual(0, code)
        self.assertFalse(self.read_state()["red_confirmed"])

    def test_green_clears_state(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        self.assertEqual(0, self.run_cli(["green"] ))
        state = self.read_state()
        self.assertFalse(state["active"])
        self.assertFalse(state["red_confirmed"])
        self.assertIsNotNone(state["completed_at"])

    def test_status_prints_without_error(self):
        self.assertEqual(0, self.run_cli(["start", "--topic", "sample"] ))
        self.assertEqual(0, self.run_cli(["status"] ))


if __name__ == "__main__":
    unittest.main()
