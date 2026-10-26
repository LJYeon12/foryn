#!/usr/bin/env bash
set -euo pipefail
: "${MONGO_INITDB_ROOT_USERNAME:?Set MongoDB root username}"
: "${MONGO_INITDB_ROOT_PASSWORD:?Set MongoDB root password}"

# 기본 진입점은 먼저 임시 단독 서버를 시작한다.
# 최종 서버에서 인증과 복제 세트 명령이 모두 준비될 때까지 기다린다.
for attempt in {1..90}; do
  if mongosh 'mongodb://127.0.0.1:27017/?directConnection=true' --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin --eval '
        try {
          rs.status();
        } catch (error) {
          if (error.code === 94) {
            rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]});
          } else { quit(1); }
        }
        quit(db.hello().isWritablePrimary ? 0 : 1);
      ' >/dev/null 2>&1; then
    exit 0
  fi
  sleep 2
done
echo 'MongoDB replica-set initialization did not finish; inspect container state.' >&2
exit 1
