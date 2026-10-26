#!/usr/bin/env python3
"""프로젝트 Codex 훅: 명령 실행 주기별 TDD 가드.

로컬 작업 절차를 보호하는 장치이며 보안 경계는 아니다.
`scripts/tdd-guard check`가 테스트 실패 상태를 기록할 때까지
프로덕션 코드를 대상으로 하는 알려진 Codex 쓰기 이벤트를 차단한다.
"""
from __future__ import annotations

import json
import os
import re
import shlex
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable

STATE_RELATIVE_PATH = ".omx/state/tdd-guard.json"
GUARDED_WRITE_TOOLS = {"apply_patch", "Write", "Edit", "MultiEdit"}

ALLOW_PREFIXES = (
    "src/test/",
    "src/integrationTest/",
    "src/archTest/",
    "docs/",
    ".codex/hooks/",
    "scripts/",
    "config/",
    ".github/",
)
ALLOW_EXACT = {
    ".codex/hooks.json",
    "build.gradle",
    "settings.gradle",
    "AGENTS.md",
    "README.md",
}
WRITE_COMMANDS = {"tee", "cp", "mv", "rm", "touch", "install", "truncate"}


@dataclass(frozen=True)
class EvaluationResult:
    allowed: bool
    reason: str = ""
    output: dict[str, Any] | None = None

    def __post_init__(self) -> None:
        if self.output is None:
            object.__setattr__(self, "output", {})


def repo_root_from_cwd() -> Path:
    return Path(os.environ.get("GIT_WORK_TREE") or os.getcwd()).resolve()


def normalize_repo_path(path: str) -> str | None:
    if not path or path == "/dev/null":
        return None
    path = path.strip().strip('"').strip("'")
    if path.startswith("a/") or path.startswith("b/"):
        path = path[2:]
    path = path.replace("\\", "/")
    pure = PurePosixPath(path)
    if pure.is_absolute() or ".." in pure.parts:
        return None
    normalized = str(pure)
    if normalized == ".":
        return None
    return normalized


def is_allowed_path(path: str) -> bool:
    normalized = normalize_repo_path(path)
    if normalized is None:
        return False
    if normalized == STATE_RELATIVE_PATH:
        return False
    if normalized in ALLOW_EXACT:
        return True
    if normalized.startswith(".omx/"):
        return True
    return any(normalized.startswith(prefix) for prefix in ALLOW_PREFIXES)


def is_production_path(path: str) -> bool:
    normalized = normalize_repo_path(path)
    return bool(normalized and normalized.startswith("src/main/"))


def parse_iso_utc(value: str) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        if value.endswith("Z"):
            value = value[:-1] + "+00:00"
        dt = datetime.fromisoformat(value)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except ValueError:
        return None


def state_path(repo_root: Path) -> Path:
    return repo_root / STATE_RELATIVE_PATH


def has_valid_red_token(repo_root: Path, *, now: datetime | None = None) -> bool:
    now = now or datetime.now(timezone.utc)
    try:
        data = json.loads(state_path(repo_root).read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return False
    expires_at = parse_iso_utc(data.get("expires_at"))
    return (
        data.get("version") == 1
        and data.get("active") is True
        and data.get("red_confirmed") is True
        and expires_at is not None
        and expires_at > now
    )


def extract_apply_patch_paths(command: str) -> tuple[set[str], bool]:
    paths: set[str] = set()
    if not command:
        return paths, False
    lines = command.splitlines()
    saw_patch_marker = False
    for line in lines:
        if line.startswith("*** Begin Patch") or line.startswith("*** End Patch"):
            saw_patch_marker = True
        for prefix in ("*** Update File: ", "*** Add File: ", "*** Delete File: "):
            if line.startswith(prefix):
                normalized = normalize_repo_path(line[len(prefix) :])
                if normalized:
                    paths.add(normalized)
        if line.startswith("*** Move to: "):
            normalized = normalize_repo_path(line[len("*** Move to: ") :])
            if normalized:
                paths.add(normalized)
    return paths, saw_patch_marker


def extract_generic_tool_paths(tool_input: Any) -> set[str]:
    paths: set[str] = set()
    if not isinstance(tool_input, dict):
        return paths
    for key in ("path", "file_path", "filename"):
        value = tool_input.get(key)
        if isinstance(value, str):
            normalized = normalize_repo_path(value)
            if normalized:
                paths.add(normalized)
    for key in ("paths", "files"):
        value = tool_input.get(key)
        if isinstance(value, list):
            for item in value:
                if isinstance(item, str):
                    normalized = normalize_repo_path(item)
                    if normalized:
                        paths.add(normalized)
    return paths


def shell_tokens(command: str) -> list[str]:
    try:
        return shlex.split(command, posix=True)
    except ValueError:
        return command.split()


def is_tdd_guard_script_token(token: str) -> bool:
    normalized = normalize_repo_path(token)
    return normalized == "scripts/tdd-guard"


def is_python_token(token: str) -> bool:
    return Path(token).name in {"python", "python3"}


def is_sed_in_place_option(token: str) -> bool:
    return (
        token == "-i"
        or token.startswith("-i")
        or token == "--in-place"
        or token.startswith("--in-place=")
    )


def is_sanctioned_tdd_guard_command(command: str) -> bool:
    tokens = shell_tokens(command)
    if not tokens:
        return False
    if is_tdd_guard_script_token(tokens[0]):
        return True
    if len(tokens) >= 2 and is_python_token(tokens[0]) and is_tdd_guard_script_token(tokens[1]):
        return True
    return False


def bash_write_targets(command: str) -> tuple[set[str], bool]:
    tokens = shell_tokens(command)
    targets: set[str] = set()
    write_like = False

    # 셸 리디렉션: > path, >> path, 2> path, &> path.
    redir_pattern = re.compile(r"(?:^|\s)(?:\d?>|>>|&>)\s*([^\s;&|]+)")
    for match in redir_pattern.finditer(command):
        normalized = normalize_repo_path(match.group(1))
        if normalized:
            targets.add(normalized)
            write_like = True

    for idx, token in enumerate(tokens):
        base = Path(token).name
        if base in WRITE_COMMANDS:
            write_like = True
            for later in tokens[idx + 1 :]:
                if later.startswith("-"):
                    continue
                normalized = normalize_repo_path(later)
                if normalized:
                    targets.add(normalized)
        elif base == "sed" and any(
            is_sed_in_place_option(later) for later in tokens[idx + 1 : idx + 4]
        ):
            write_like = True
            for later in tokens[idx + 1 :]:
                if later.startswith("-") or "/" not in later:
                    continue
                normalized = normalize_repo_path(later)
                if normalized:
                    targets.add(normalized)

    # 보호된 상태 파일을 쓰기 모드로 여는 Python 코드는 쓰기 작업으로 분류한다.
    # cat/grep처럼 읽기만 하는 참조는 계속 허용한다.
    open_pattern = re.compile(
        r"open\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"][^'\"]*[wax+][^'\"]*['\"]"
    )
    for match in open_pattern.finditer(command):
        normalized = normalize_repo_path(match.group(1))
        if normalized:
            targets.add(normalized)
            write_like = True

    return targets, write_like


def deny(reason: str) -> EvaluationResult:
    return EvaluationResult(
        allowed=False,
        reason=reason,
        output={
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        },
    )


def allow() -> EvaluationResult:
    return EvaluationResult(allowed=True, output={})


def classify_paths(paths: Iterable[str], repo_root: Path) -> EvaluationResult:
    normalized_paths = {p for p in (normalize_repo_path(path) for path in paths) if p}
    if not normalized_paths:
        return deny("TDD guard could not classify this guarded write; refusing to fail open.")
    if any(path == STATE_RELATIVE_PATH for path in normalized_paths):
        return deny("Direct edits to .omx/state/tdd-guard.json are blocked; use scripts/tdd-guard instead.")
    production_paths = {path for path in normalized_paths if is_production_path(path)}
    if not production_paths:
        if all(is_allowed_path(path) for path in normalized_paths):
            return allow()
        # PRD 범위를 넘어 차단하지 않도록 분류되지 않은 비프로덕션 경로는 허용한다.
        return allow()
    if has_valid_red_token(repo_root):
        return allow()
    return deny(
        "Production edit blocked by TDD guard: add/modify a relevant test, run "
        "scripts/tdd-guard check -- <test command> to confirm red, then retry."
    )


def evaluate_event(event: dict[str, Any], repo_root: Path | str | None = None) -> EvaluationResult:
    repo = Path(repo_root).resolve() if repo_root is not None else repo_root_from_cwd()
    event_name = event.get("hook_event_name") or event.get("hookEventName") or event.get("event")
    if event_name != "PreToolUse":
        return allow()

    tool_name = event.get("tool_name") or event.get("toolName")
    tool_input = event.get("tool_input") or event.get("toolInput") or {}
    command = tool_input.get("command") if isinstance(tool_input, dict) else None

    if tool_name == "Bash":
        if not isinstance(command, str):
            return allow()
        targets, write_like = bash_write_targets(command)
        if write_like and targets:
            return classify_paths(targets, repo)
        if is_sanctioned_tdd_guard_command(command):
            return allow()
        return allow()

    if tool_name in GUARDED_WRITE_TOOLS:
        paths: set[str] = set()
        saw_patch = False
        if isinstance(command, str):
            paths, saw_patch = extract_apply_patch_paths(command)
        if not paths:
            paths = extract_generic_tool_paths(tool_input)
        if not paths and (tool_name in GUARDED_WRITE_TOOLS or saw_patch):
            return deny("TDD guard could not parse paths from a guarded write event; refusing to fail open.")
        return classify_paths(paths, repo)

    return allow()


def main() -> int:
    try:
        raw = sys.stdin.read()
        event = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        # 정책 검사 대상이 아닌 훅의 입력 오류로 시작을 막지 않는다.
        # 잘못된 PreToolUse 페이로드는 식별할 수 없으므로 여기서는 허용한다.
        return 0
    result = evaluate_event(event, repo_root_from_cwd())
    if result.output:
        print(json.dumps(result.output, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
