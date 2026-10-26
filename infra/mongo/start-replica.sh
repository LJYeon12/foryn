#!/usr/bin/env bash
set -euo pipefail

# 비공개 명명 볼륨에서 생성하며 소스나 표준 출력에 복사하지 않는다.
replica_key=/data/configdb/replica-set.key
if [[ ! -s "$replica_key" ]]; then
  umask 077
  openssl rand -base64 512 > "$replica_key"
fi
chown mongodb:mongodb "$replica_key"
chmod 400 "$replica_key"

/bin/bash /opt/mongo/initialize-replica.sh &
exec /usr/local/bin/docker-entrypoint.sh "$@"
