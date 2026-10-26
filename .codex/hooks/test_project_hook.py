import importlib.util
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("project_hook.py")
spec = importlib.util.spec_from_file_location("project_hook", MODULE_PATH)
project_hook = importlib.util.module_from_spec(spec)
sys.modules["project_hook"] = project_hook
spec.loader.exec_module(project_hook)


class ProjectHookPolicyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / ".omx" / "state").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def write_state(self, **overrides):
        state = {
            "version": 1,
            "active": True,
            "topic": "sample",
            "test_command": "python3 -c 'raise SystemExit(1)'",
            "red_confirmed": True,
            "red_confirmed_at": "2026-05-10T00:00:00Z",
            "red_exit_code": 1,
            "red_output_excerpt": "expected red",
            "expected_pattern": None,
            "expires_at": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
        }
        state.update(overrides)
        path = self.root / ".omx" / "state" / "tdd-guard.json"
        path.write_text(json.dumps(state), encoding="utf-8")
        return path

    def patch_event(self, patch):
        return {
            "hook_event_name": "PreToolUse",
            "tool_name": "apply_patch",
            "tool_input": {"command": patch},
        }

    def bash_event(self, command):
        return {
            "hook_event_name": "PreToolUse",
            "tool_name": "Bash",
            "tool_input": {"command": command},
        }

    def assert_denied(self, result):
        self.assertFalse(result.allowed)
        self.assertIn("deny", json.dumps(result.output))

    def assert_allowed(self, result):
        self.assertTrue(result.allowed, result.reason)
        self.assertEqual({}, result.output)

    def test_src_main_patch_denied_without_token(self):
        patch = "*** Begin Patch\n*** Update File: src/main/java/Foo.java\n@@\n+class Foo {}\n*** End Patch\n"
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))

    def test_test_docs_and_config_paths_allowed_without_token(self):
        for path in [
            "src/test/java/FooTest.java",
            "src/integrationTest/java/FooIT.java",
            "src/archTest/java/ArchTest.java",
            "docs/readme.md",
            ".codex/hooks/project_hook.py",
            ".codex/hooks.json",
            "scripts/tdd-guard",
            "build.gradle",
            "settings.gradle",
            "config/checkstyle/checkstyle.xml",
            ".github/workflows/ci.yml",
            "AGENTS.md",
            "README.md",
        ]:
            with self.subTest(path=path):
                patch = f"*** Begin Patch\n*** Update File: {path}\n@@\n+ok\n*** End Patch\n"
                self.assert_allowed(project_hook.evaluate_event(self.patch_event(patch), self.root))

    def test_valid_token_allows_src_main_patch(self):
        self.write_state()
        patch = "*** Begin Patch\n*** Update File: src/main/java/Foo.java\n@@\n+class Foo {}\n*** End Patch\n"
        self.assert_allowed(project_hook.evaluate_event(self.patch_event(patch), self.root))

    def test_expired_green_malformed_state_denies_src_main_patch(self):
        patch = "*** Begin Patch\n*** Update File: src/main/java/Foo.java\n@@\n+class Foo {}\n*** End Patch\n"
        self.write_state(expires_at=(datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat().replace("+00:00", "Z"))
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))
        self.write_state(active=False)
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))
        (self.root / ".omx" / "state" / "tdd-guard.json").write_text("not-json", encoding="utf-8")
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))

    def test_direct_state_file_edit_denied(self):
        patch = "*** Begin Patch\n*** Update File: .omx/state/tdd-guard.json\n@@\n+{}\n*** End Patch\n"
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))
        self.assert_denied(project_hook.evaluate_event(self.bash_event("echo '{}' > .omx/state/tdd-guard.json"), self.root))

    def test_sanctioned_tdd_guard_command_allowed(self):
        result = project_hook.evaluate_event(self.bash_event("scripts/tdd-guard check -- python3 -c 'raise SystemExit(1)'"), self.root)
        self.assert_allowed(result)

    def test_bash_write_to_src_main_denied_without_token(self):
        for cmd in [
            "echo hi > src/main/java/Foo.java",
            "tee src/main/java/Foo.java",
            "cp Foo.java src/main/java/Foo.java",
            "mv Foo.java src/main/java/Foo.java",
            "rm src/main/java/Foo.java",
            "sed -i s/a/b/ src/main/java/Foo.java",
            "sed -i.bak s/a/b/ src/main/java/Foo.java",
            "sed -ibak s/a/b/ src/main/java/Foo.java",
            "sed --in-place s/a/b/ src/main/java/Foo.java",
            "sed --in-place=.bak s/a/b/ src/main/java/Foo.java",
        ]:
            with self.subTest(cmd=cmd):
                self.assert_denied(project_hook.evaluate_event(self.bash_event(cmd), self.root))

    def test_matched_unparseable_write_event_fails_closed(self):
        event = {"hook_event_name": "PreToolUse", "tool_name": "apply_patch", "tool_input": {}}
        self.assert_denied(project_hook.evaluate_event(event, self.root))

    def test_unrelated_unsupported_event_fails_open(self):
        event = {"hook_event_name": "PreToolUse", "tool_name": "mcp__fs__read", "tool_input": {"path": "src/main/java/Foo.java"}}
        self.assert_allowed(project_hook.evaluate_event(event, self.root))

    def test_python_command_mentioning_tdd_guard_does_not_bypass_state_write(self):
        cmd = "python3 -c \"open('.omx/state/tdd-guard.json','w').write('{}')\" scripts/tdd-guard"
        self.assert_denied(project_hook.evaluate_event(self.bash_event(cmd), self.root))

    def test_tdd_guard_redirection_to_state_file_denied(self):
        cmd = "scripts/tdd-guard status > .omx/state/tdd-guard.json"
        self.assert_denied(project_hook.evaluate_event(self.bash_event(cmd), self.root))

    def test_read_only_state_file_commands_allowed(self):
        for cmd in ["cat .omx/state/tdd-guard.json", "grep red_confirmed .omx/state/tdd-guard.json"]:
            with self.subTest(cmd=cmd):
                self.assert_allowed(project_hook.evaluate_event(self.bash_event(cmd), self.root))

    def test_wrong_version_and_unconfirmed_state_deny_src_main_patch(self):
        patch = "*** Begin Patch\n*** Update File: src/main/java/Foo.java\n@@\n+class Foo {}\n*** End Patch\n"
        self.write_state(version=2)
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))
        self.write_state(red_confirmed=False)
        self.assert_denied(project_hook.evaluate_event(self.patch_event(patch), self.root))


if __name__ == "__main__":
    unittest.main()
