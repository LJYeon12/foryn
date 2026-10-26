#!/usr/bin/env python3
"""폐기 가능한 리포트 장애 실험을 실행한다. 기본값은 읽기 전용 계획이다."""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PURPOSE = "report-local-fault-check"
NAME = re.compile(r"foryn-report-fault-[a-f0-9]{8}-(mysql|mongo)\Z")


def docker(*arguments: str) -> str:
    result = subprocess.run(["docker", *arguments], cwd=ROOT, capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=180, check=False)
    if result.returncode:
        raise RuntimeError(f"docker {arguments[0]} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def local_port(name: str, target: str) -> int:
    info = json.loads(docker("inspect", name))[0]
    ports = info["NetworkSettings"]["Ports"][target]
    if len(ports) != 1 or ports[0]["HostIp"] != "127.0.0.1":
        raise ValueError("Experiment containers must publish only on loopback")
    port = int(ports[0]["HostPort"])
    if not 0 < port < 65536:
        raise ValueError("Invalid experiment port")
    return port


def stop_owned(name: str) -> None:
    if not NAME.fullmatch(name):
        raise ValueError("Refusing to stop an unrelated container name")
    info = json.loads(docker("inspect", name))[0]
    if info["Config"].get("Labels", {}).get("foryn.purpose") != PURPOSE:
        raise ValueError("Refusing to stop a container with a different ownership label")
    docker("stop", "--timeout", "3", name)


def require_local_engine() -> None:
    endpoint = os.environ.get("DOCKER_HOST")
    if not endpoint:
        endpoint = json.loads(docker("context", "inspect"))[0]["Endpoints"]["docker"]["Host"]
    if not endpoint.startswith(("npipe://", "unix://")):
        raise ValueError("Only a local Docker socket or named pipe is permitted; remote Docker engines are refused")


def wait_ready(name: str, kind: str) -> None:
    command = (["mysql", "--protocol=TCP", "--host=127.0.0.1", "--user=root",
                "report_fault_local", "--execute=SELECT 1"] if kind == "mysql" else
               ["mongosh", "--quiet", "--eval", "quit(db.adminCommand({ping:1}).ok ? 0 : 1)"])
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            docker("exec", name, *command)
            return
        except RuntimeError:
            time.sleep(1)
    raise RuntimeError(f"Disposable {kind} did not become ready in 120 seconds")


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def read_lines(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def percentile(values: list[float], q: float = 0.95) -> float:
    return sorted(values)[max(0, math.ceil(len(values) * q) - 1)] if values else 0.0


def write_comparison(evidence: Path) -> None:
    """실패 요청을 포함한 원시 관측값을 합산한다. 실행별 백분위수는 평균 내지 않는다."""
    requests = read_lines(evidence / "requests.ndjson")
    clients = read_lines(evidence / "clients.ndjson")
    environment = read_json(evidence / "environment.json")
    verification = read_json(evidence / "verification.json") if (evidence / "verification.json").exists() else {}
    lines = ["# 리포트 연결 풀 로컬 비교 결과", "",
             "실제 MySQL·MongoDB·JPA·Hikari와 루프백 HTTP를 사용한 통제 실험이다.",
             "개선 전은 과거 실행 기록에서 재구성했다. 운영 revision의 재실행은 아니다.",
             "Mongo 지연은 실제 읽기 명령 직전의 클라이언트 대기다. 서버·네트워크 지연 자체는 주입하지 않았다.",
             "HTTP 경로는 검증 전용이다. 운영 MVC·인증·OSIV·외부 서비스는 실행하지 않았다.", "",
             f"시작(UTC): {environment.get('startedUtc', 'unknown')}",
             f"검사 종료 코드: {verification.get('exitCode', '별도 run.json 참조')}", "",
             "## 연결 소유권 재현", "",
             "같은 배치 조회에서 첫 Mongo 명령을 정지하고 풀 크기만큼 리포트를 실행했다.", "",
             "| 비교군 | Mongo 대기 중 active | 일반 조회 HTTP | 일반 조회 ms | 획득 실패 |",
             "| --- | ---: | ---: | ---: | ---: |"]
    for row in read_json(evidence / "gates.json"):
        lines.append(f"| {row['variant']} | {row['activeDuringMongoPause']} | {row['mysqlHttpStatus']} | "
                     f"{row['mysqlClientMs']:.2f} | {row['mysqlAcquireFailures']} |")
    lines += ["", "## 단일 요청의 실제 실행 횟수", "",
              "워밍업 후 측정했다. 네 비교군의 전체 주간 응답이 같은지 자동 검사한다.",
              "SQL은 JDBC execute 호출, Mongo는 find/aggregate/count/distinct/getMore 시작 횟수다.",
              "SQL에는 ORM의 연관 엔티티 조회도 포함한다. 인증 쿼리와 풀 내부 SELECT 1 연결 확인은 포함하지 않는다.", "",
              "| 비교군 | SQL | Mongo 명령 |", "| --- | ---: | ---: |"]
    for row in read_json(evidence / "query-counts.json"):
        lines.append(f"| {row['case'].removeprefix('count-')} | {row['sqlExecutions']} | {row['mongoCommands']} |")
    lines += ["", "## 혼합 HTTP 부하", "",
              "A=반복+전체 트랜잭션, B=반복+트랜잭션 제거, C=배치+전체 트랜잭션, D=현재 배치+트랜잭션 제거.",
              "리포트 16회/초, 일반 조회 8회/초를 서로 독립된 일정으로 발행했다.",
              "p95는 반복 실행의 원본 표본을 합친 nearest-rank 값이다. 실패 응답 시간도 포함한다.",
              "부하 종료 후 진행 중 요청이 모두 끝날 때까지 수집했다. 실패가 빠르면 p95가 낮아질 수 있으므로 오류율과 함께 읽는다.", "",
              "| 비교군 | 명령당 지연 ms | 요청 | 실패/전체 | HTTP p95 ms | 성공 HTTP p95 ms | 획득 p95 ms | 점유 p95 ms | 획득 실패 | 발행 지연 p95 ms |",
              "| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    groups = defaultdict(list)
    by_case_kind = defaultdict(list)
    for row in requests:
        by_case_kind[(row["case"], row["kind"])].append(row)
    for row in clients:
        groups[(row["variant"], row["delayMs"], row["kind"])].append(row)
    for (variant, delay, kind), rows in sorted(groups.items()):
        cases = {row["case"] for row in rows}
        server = [sample for case in cases for sample in by_case_kind[(case, kind)]]
        acquire = [value for sample in server for value in sample["jdbcAcquireMs"]]
        usage = [value for sample in server for value in sample["jdbcUsageMs"]]
        failures = sum(sample["jdbcAcquireFailures"] for sample in server)
        errors = sum(row["status"] != 200 for row in rows)
        lines.append(f"| {variant} | {delay} | {kind} | {errors}/{len(rows)} | "
                     f"{percentile([r['clientMs'] for r in rows]):.2f} | "
                     f"{percentile([r['clientMs'] for r in rows if r['status'] == 200]):.2f} | {percentile(acquire):.2f} | "
                     f"{percentile(usage):.2f} | {failures} | "
                     f"{percentile([r['scheduleLagMs'] for r in rows]):.2f} |")
    current_errors = [row for row in read_json(evidence / "summary.json")
                      if row["variant"] == "D_BATCH_NO_TX" and row["errors"]]
    lines += ["", "## 현재 비교군의 오류 관측", "",
              "회귀 검사는 Mongo 대기 중 JDBC 비점유와 계측 정합성을 판정한다. 부하 시험 전체의 오류 0을 보장하지 않는다."]
    if current_errors:
        for row in current_errors:
            lines.append(f"- {row['case']} / {row['kind']}: {row['errors']}/{row['requests']} 실패. "
                         f"획득 실패 {row['jdbcAcquireFailures']}회. 원인 분류 {row.get('failureCauses', {})}.")
    else:
        lines.append("- 이 실행의 현재 비교군에서는 HTTP 실패가 관측되지 않았다.")
    (evidence / "observed-errors.json").write_text(json.dumps(current_errors, indent=2) + "\n", encoding="utf-8")
    lines += ["", "## 해석 범위", "",
              "- 풀 크기 4, 획득 제한 300ms, 합성 자료 14일·세션 28개·메시지 28개·복약 기록 14개다.",
              "- 일반 API의 대기·실패와 리포트 자체의 지연을 분리한다. 느린 Mongo가 정상화됐다는 의미는 아니다.",
              "- 실제 쿼리 횟수는 성공한 단일 요청으로 비교한다. 과부하에서 실패한 요청의 낮은 조회 횟수를 개선으로 해석하지 않는다.",
              "- OSIV는 모든 비교군에서 없다. 기본 애플리케이션 설정과 같은지 별도 확인해야 한다.",
              "- 풀별 순간 최대 active/pending, 실행별 값은 summary.json에 있다. 각 요청의 상세 값은 requests.ndjson에 있다.",
              "- HTTP 표본은 clients.ndjson, 연결·Mongo 대기 타임라인은 events.ndjson에 있다.",
              "- Hikari 기본 메트릭은 전체 실행의 누적값이다. 비교군별 귀속에는 요청별 계측을 사용한다.",
              "- 이 결과로 과거 장애의 단일 원인이나 운영 SLO·모든 부하에서의 무재발을 확정할 수 없다.", ""]
    (evidence / "comparison.md").write_text("\n".join(lines), encoding="utf-8")


def execute(args: argparse.Namespace) -> int:
    suffix = uuid.uuid4().hex[:8]
    created = []
    run_dir = ROOT / ".local/report/experiments/fault-runs" / suffix
    run_dir.mkdir(parents=True, exist_ok=False)
    evidence = None
    exit_code = 1
    cleanup_errors = []
    try:
        require_local_engine()
        docker("version", "--format", "{{.Server.Version}}")
        infrastructure = {"atUtc": datetime.now(timezone.utc).isoformat(), "images": {}}
        info = json.loads(docker("info", "--format", "{{json .}}"))
        infrastructure["docker"] = {key: info.get(key) for key in ("ServerVersion", "OSType", "NCPU", "MemTotal")}
        for kind, image, port in (("mysql", "mysql:8.4", 3306), ("mongo", "mongo:8.0", 27017)):
            image_info = json.loads(docker("image", "inspect", image))[0]
            infrastructure["images"][image] = image_info["Id"]
            name = f"foryn-report-fault-{suffix}-{kind}"
            command = ["run", "--rm", "--detach", "--name", name, "--label", f"foryn.purpose={PURPOSE}",
                       "--publish", f"127.0.0.1::{port}"]
            if kind == "mysql":
                command += ["--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", "--env", "MYSQL_DATABASE=report_fault_local"]
            command += [image]
            docker(*command)
            created.append(name)
            print(f"Started disposable {kind}", file=sys.stderr, flush=True)
        mysql, mongo = created
        wait_ready(mysql, "mysql")
        wait_ready(mongo, "mongo")
        env = os.environ.copy()
        env["REPORT_FAULT_JDBC_URL"] = (f"jdbc:mysql://127.0.0.1:{local_port(mysql, '3306/tcp')}/report_fault_local"
                                        "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC")
        env["REPORT_FAULT_MONGO_URI"] = f"mongodb://127.0.0.1:{local_port(mongo, '27017/tcp')}/report_fault_local"
        env.update(REPORT_FAULT_REPETITIONS=str(args.repetitions), REPORT_FAULT_SECONDS=str(args.seconds),
                   REPORT_FAULT_DELAY_MS=str(args.delay_ms))
        if args.diagnose:
            env["REPORT_FAULT_DIAGNOSE"] = "1"
        else:
            env.pop("REPORT_FAULT_DIAGNOSE", None)
        infrastructure.update(repetitions=args.repetitions, arrivalSeconds=args.seconds, delayMs=args.delay_ms)
        infrastructure["diagnosis"] = args.diagnose
        (run_dir / "infrastructure.json").write_text(json.dumps(infrastructure, indent=2) + "\n", encoding="utf-8")
        command = [str(ROOT / "gradlew.bat"), "reportFaultTest", "--no-daemon", "--console=plain"]
        if os.name == "nt":
            command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", *command]
        with (run_dir / "gradle.log").open("w", encoding="utf-8") as log:
            with subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", errors="replace") as process:
                for line in process.stdout:
                    log.write(line)
                    log.flush()
                    if "REPORT_FAULT_EVIDENCE " in line:
                        evidence = Path(line.split("REPORT_FAULT_EVIDENCE ", 1)[1].strip())
                    if any(word in line for word in ("REPORT_FAULT_", "> Task", "FAILED", "PASSED", "BUILD ")):
                        print(line.rstrip(), flush=True)
                exit_code = process.wait()
        if evidence is not None:
            (evidence / "verification.json").write_text(json.dumps({"exitCode": exit_code}) + "\n", encoding="utf-8")
            write_comparison(evidence)
            (evidence / "infrastructure.json").write_text(json.dumps(infrastructure, indent=2) + "\n", encoding="utf-8")
    finally:
        for name in reversed(created):
            try:
                stop_owned(name)
            except Exception as error:
                cleanup_errors.append(str(error))
        result = {"exitCode": exit_code, "evidence": str(evidence) if evidence else None,
                  "run": str(run_dir), "cleanupErrors": cleanup_errors}
        (run_dir / "run.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        if evidence is not None:
            (evidence / "verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, ensure_ascii=False), flush=True)
    return exit_code or (1 if cleanup_errors else 0)


def bounded(minimum: int, maximum: int):
    def parse(value: str) -> int:
        number = int(value)
        if not minimum <= number <= maximum:
            raise argparse.ArgumentTypeError(f"Expected {minimum}..{maximum}")
        return number
    return parse


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Create disposable containers and run the comparison")
    parser.add_argument("--diagnose", action="store_true", help="Current implementation, no Mongo delay, JDBC/JFR/server tracing")
    parser.add_argument("--repetitions", type=bounded(1, 20), default=3)
    parser.add_argument("--seconds", type=bounded(2, 30), default=6)
    parser.add_argument("--delay-ms", type=bounded(25, 500), default=150)
    args = parser.parse_args(argv)
    if not args.diagnose and args.repetitions > 5:
        parser.error("More than 5 repetitions requires --diagnose")
    if not args.execute:
        print(json.dumps({"dryRun": True, "containers": "disposable loopback MySQL + MongoDB, no volumes",
                          "variants": 1 if args.diagnose else 4, "delayConditions": [0] if args.diagnose else [0, args.delay_ms],
                          "repetitions": args.repetitions, "arrivalSeconds": args.seconds}))
        return 0
    return execute(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
