#!/usr/bin/env python3
"""격리된 채팅 복구 실험을 실행한다. 기본값은 읽기 전용 계획이며 Docker를 호출하지 않는다."""
from __future__ import annotations

import argparse
import hashlib
from collections import defaultdict
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PURPOSE = "chat-local-fault-check"
NAME = re.compile(r"foryn-chat-fault-[a-f0-9]{8}-(mysql|mongo)\Z")
EXPECTED = {}
for condition in ("NORMAL_PROFILE", "MYSQL_SUCCESS_ACK_LOST", "MONGO_PUBLISH_SUCCESS_ACK_LOST",
                  "PUBLISH_OUTAGE_RECOVERED", "RECOVERY_WORKER_RACE", "SQL_COMMIT_RECOVERY_RACE",
                  "COMPLETION_RECORD_RETRY", "LEGACY_MISSING_VERSION", "NICKNAME_UPDATE_RACE",
                  "JVM_CRASH_AFTER_SQL", "JVM_CRASH_AFTER_PUBLISH"):
    EXPECTED[condition] = (True, "BIRTH_YEAR", 2, "COMPLETED")
for condition in ("MYSQL_UPDATE_REJECT", "MYSQL_ROLLBACK_BEFORE_COMMIT", "MESSAGE_1_GENDER", "MESSAGE_2_GENDER",
                  "SESSION_WRITE_REJECT", "MONGO_STAGE_SUCCESS_ACK_LOST", "COMPENSATION_OUTAGE_RECOVERED",
                  "OLD_WRITER_FENCED", "FORCE_INITIALIZE_ROLLBACK", "JVM_CRASH_PREPARING", "JVM_CRASH_BEFORE_SQL",
                  "JVM_CRASH_AFTER_STAGE", "JVM_CRASH_DURING_CANCEL"):
    EXPECTED[condition] = (False, "GENDER", 0, "CANCELLED")
for condition in ("MESSAGE_1_ACTION_FEEDBACK", "MESSAGE_2_ACTION_FEEDBACK"):
    EXPECTED[condition] = (False, "ACTION_FEEDBACK", 0, "CANCELLED")
EXPECTED.update(NORMAL_MONGO_ONLY=(False, "CHAT_END", 2, "COMPLETED"),
                INITIALIZE=(False, "GENDER", 1, "COMPLETED"), FORCE_INITIALIZE=(False, "GENDER", 1, "COMPLETED"),
                DELETE_OLD_WRITER_FENCED=(False, "DELETED", 0, "COMPLETED"),
                PROFILE_RESET_RACE=(False, "DELETED", 0, "COMPLETED"),
                FIVE_PROFILE_STEPS=(True, "EMOTION_SELECT", 10, "COMPLETED"))


def source_hashes() -> dict:
    paths = [ROOT / "build.gradle", ROOT / "scripts/chat_fault.py"]
    for source in ("src/main/java", "src/faultTest/java"):
        paths += sorted((ROOT / source).rglob("*.java"))
    paths += sorted((ROOT / "src/main/resources/db/migration").glob("*.sql"))
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}


def docker(*arguments: str) -> str:
    result = subprocess.run(["docker", *arguments], cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=180, check=False)
    if result.returncode:
        raise RuntimeError(f"docker {arguments[0]} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def require_owned(info: dict, name: str, original_id: str) -> None:
    if (not NAME.fullmatch(name) or info.get("Name") != "/" + name or info.get("Id") != original_id
            or info.get("Config", {}).get("Labels", {}).get("foryn.purpose") != PURPOSE):
        raise ValueError("Refusing to mutate an unowned container")


def endpoint(info: dict, target: str) -> int:
    ports = info["NetworkSettings"]["Ports"][target]
    if len(ports) != 1 or ports[0]["HostIp"] != "127.0.0.1":
        raise ValueError("Only disposable loopback ports are allowed")
    return int(ports[0]["HostPort"])


def verify_observations(rows: list[dict]) -> dict:
    groups = defaultdict(lambda: {"observations": 0, "inconsistent": 0, "serviceCalls": 0, "serviceExceptions": 0,
                                  "recoverySamples": 0, "maxRecoveryMillis": 0.0, "duplicateMessages": 0})
    bad = []
    seen = set()
    for row in rows:
        key = (row["condition"], row.get("iteration"))
        supplied = (row["expectedProfileChanged"], row["expectedStep"], row["expectedMessages"], row["expectedJournalStatus"])
        valid = (key not in seen and supplied == EXPECTED.get(row["condition"])
                 and row["visibleMessages"] == row["expectedMessages"] == row["rawMessages"]
                 and row["pendingMessages"] == 0 and row["pendingTurns"] == 0
                 and row["profileChanged"] == row["expectedProfileChanged"]
                 and row["currentStep"] == row["expectedStep"] and not row["guardActive"]
                 and row["journalStatus"] == row["expectedJournalStatus"] and row.get("duplicateMessages", 0) == 0)
        seen.add(key)
        group = groups[row["condition"]]
        group["observations"] += 1
        group["inconsistent"] += not valid
        group["serviceCalls"] += row.get("serviceCalls", 0)
        group["serviceExceptions"] += row.get("serviceExceptions", 0)
        group["duplicateMessages"] += row.get("duplicateMessages", 0)
        if row.get("recoveryMillis") is not None:
            group["recoverySamples"] += 1
            group["maxRecoveryMillis"] = max(group["maxRecoveryMillis"], row["recoveryMillis"])
        if not valid:
            bad.append({"condition": row["condition"], "iteration": row.get("iteration")})
    if bad:
        raise AssertionError("Raw final state is inconsistent: " + json.dumps(bad, ensure_ascii=False))
    return dict(groups)


def ready(name: str, command: list[str]) -> None:
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            docker("exec", name, *command)
            return
        except RuntimeError:
            time.sleep(1)
    raise RuntimeError("Disposable database readiness deadline exceeded")


def run(repetitions: int, process_repetitions: int, test_filter: str | None = None) -> int:
    connection = json.loads(docker("context", "inspect"))[0]["Endpoints"]["docker"]["Host"]
    if not (connection.startswith("npipe://") or connection.startswith("unix://")):
        raise ValueError("Refusing a remote Docker endpoint")
    run_id = uuid.uuid4().hex[:8]
    evidence = ROOT / ".local/chat/experiments/fault-evidence" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-") + run_id)
    evidence.mkdir(parents=True)
    owned = []
    outcome = {"exitCode": 1, "cleanupErrors": [], "repetitions": repetitions,
               "processRepetitions": process_repetitions, "testFilter": test_filter}
    try:
        ports = {}
        images = {}
        for kind, image, target in (("mysql", "mysql:8.4", "3306/tcp"), ("mongo", "mongo:8.0", "27017/tcp")):
            name = f"foryn-chat-fault-{run_id}-{kind}"
            images[image] = json.loads(docker("image", "inspect", image))[0]["Id"]
            command = ["run", "--rm", "--detach", "--name", name, "--label", f"foryn.purpose={PURPOSE}",
                       "--publish", f"127.0.0.1:0:{target.split('/')[0]}"]
            if kind == "mysql":
                command += ["--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", image]
            else:
                command += [image, "--replSet", "chat-test", "--bind_ip_all", "--setParameter", "enableTestCommands=1"]
            original_id = docker(*command)
            owned.append((name, original_id))
            info = json.loads(docker("inspect", name))[0]
            require_owned(info, name, original_id)
            ports[kind] = endpoint(info, target)
            print(f"Started disposable {kind}", file=sys.stderr, flush=True)
        mysql, mongo = (entry[0] for entry in owned)
        ready(mysql, ["mysql", "--user=root", "--execute=SELECT 1"])
        docker("exec", mysql, "mysql", "--user=root", "--execute=CREATE DATABASE chat_fault_local")
        ready(mongo, ["mongosh", "--quiet", "--eval", "quit(db.adminCommand({ping:1}).ok?0:1)"])
        docker("exec", mongo, "mongosh", "--quiet", "--eval",
               "rs.initiate({_id:'chat-test',members:[{_id:0,host:'127.0.0.1:27017'}]})")
        ready(mongo, ["mongosh", "--quiet", "--eval", "quit(db.hello().isWritablePrimary?0:1)"])
        env = os.environ.copy()
        env.update(CHAT_FAULT_JDBC_URL=f"jdbc:mysql://127.0.0.1:{ports['mysql']}/chat_fault_local"
                   "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC",
                   CHAT_FAULT_MONGO_URI=f"mongodb://127.0.0.1:{ports['mongo']}/chat_fault_local"
                   "?replicaSet=chat-test&directConnection=true",
                   CHAT_FAULT_EVIDENCE=str(evidence), CHAT_FAULT_REPETITIONS=str(repetitions),
                   CHAT_FAULT_PROCESS_REPETITIONS=str(process_repetitions))
        hashes = source_hashes()
        metadata = {"startedUtc": datetime.now(timezone.utc).isoformat(), "images": images,
                    "ports": ports, "mongoReplicaSet": "chat-test", "syntheticOnly": True}
        (evidence / "source-hashes.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
        (evidence / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        command = [str(ROOT / "gradlew.bat"), "chatFaultTest", "--no-daemon", "--console=plain"]
        if test_filter:
            command += ["--tests", test_filter]
        if os.name != "nt":
            command[0] = str(ROOT / "gradlew")
        with (evidence / "gradle.log").open("w", encoding="utf-8") as log:
            with subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", errors="replace") as process:
                for line in process.stdout:
                    log.write(line)
                    log.flush()
                    if any(marker in line for marker in ("> Task", "CHAT_FAULT", "FAILED", "BUILD ", "error:")):
                        print(line.rstrip(), file=sys.stderr, flush=True)
                outcome["exitCode"] = process.wait()
        if source_hashes() != hashes:
            raise AssertionError("Source changed during the experiment; rerun before claiming final verification")
        raw = evidence / "observations.ndjson"
        if outcome["exitCode"] == 0 and raw.exists():
            rows = [json.loads(line) for line in raw.read_text(encoding="utf-8").splitlines() if line]
            summary = verify_observations(rows)
            if not test_filter:
                if set(summary) != set(EXPECTED):
                    raise AssertionError("Missing or unexpected experiment conditions")
                for condition, group in summary.items():
                    expected_count = process_repetitions if condition.startswith("JVM_CRASH_") else repetitions
                    if group["observations"] != expected_count:
                        raise AssertionError("Incorrect sample count for " + condition)
            (evidence / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            lines = ["# 채팅 저장·복구 장애 검사", "", "조건을 강제로 주입한 로컬 검사다. 운영 오류율이 아니다.",
                     "실제 DB와 Spring 트랜잭션을 사용한다. HTTP·실제 AI·푸시 연동의 성공률은 측정하지 않는다.",
                     "Mongo 저장 거부·응답 유실은 실제 쓰기 전/후의 검증용 예외다. SQL 커밋 응답 유실은 실제 JDBC commit 뒤 주입한다.",
                     "복구 시간은 저장소 복원 뒤 복구 호출부터 완료 확인까지 측정한다. JVM 검사는 새 복구 프로세스 기동 시간을 포함한다.", "",
                     "검사에서 lease·다음 시도 시각을 앞당긴다. 표시한 복구 시간에 운영 기본 300초 lease 대기와 backoff는 포함하지 않는다.", "",
                     "| 조건 | 최종 상태 관측 | 서비스 예외/호출 | 잔존 불일치 | 중복 메시지 | 최대 복구 ms |",
                     "| --- | ---: | ---: | ---: | ---: | ---: |"]
            for key, value in sorted(summary.items()):
                calls = f"{value['serviceExceptions']}/{value['serviceCalls']}" if value['serviceCalls'] else "미계측"
                elapsed = f"{value['maxRecoveryMillis']:.2f}" if value['recoverySamples'] else "미계측"
                lines.append(f"| {key} | {value['observations']} | {calls} | {value['inconsistent']} | "
                             f"{value['duplicateMessages']} | {elapsed} |")
            (evidence / "RESULTS.ko.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        elif outcome["exitCode"] == 0 and not test_filter:
            raise AssertionError("No raw final-state evidence")
    except BaseException as failure:
        outcome["exitCode"] = 1
        outcome["errorType"] = type(failure).__name__
        raise
    finally:
        for name, original_id in reversed(owned):
            try:
                require_owned(json.loads(docker("inspect", name))[0], name, original_id)
                docker("stop", "--timeout", "3", name)
            except (RuntimeError, ValueError) as failure:
                outcome["cleanupErrors"].append(str(failure))
        if outcome["cleanupErrors"]:
            outcome["exitCode"] = 1
        (evidence / "run.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"evidence": str(evidence), **outcome}, ensure_ascii=False), flush=True)
    return outcome["exitCode"] or bool(outcome["cleanupErrors"])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Create and later remove only labelled disposable containers")
    parser.add_argument("--repetitions", type=int, default=100)
    parser.add_argument("--process-repetitions", type=int, default=1)
    parser.add_argument("--tests", help="Optional Gradle test filter; filtered runs are not full fault verification")
    args = parser.parse_args()
    if not 1 <= args.repetitions <= 1000 or not 1 <= args.process_repetitions <= 100:
        parser.error("repetitions must be 1..1000; process-repetitions must be 1..100")
    if not args.execute:
        print(json.dumps({"mode": "dry-run", "scope": "disposable loopback MySQL + Mongo replica set",
                          "repetitions": args.repetitions, "processRepetitions": args.process_repetitions}))
        return 0
    return run(args.repetitions, args.process_repetitions, args.tests)


if __name__ == "__main__":
    raise SystemExit(main())
