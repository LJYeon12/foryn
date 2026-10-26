#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle이 생성한 POSIX용 Gradle 시작 스크립트.
#
#   실행 시 주의사항:
#
#   (1) 이 스크립트를 실행하려면 POSIX 호환 셸이 필요하다. /bin/sh가
#       호환되지 않으면 ksh 또는 bash 같은 호환 셸을 사용한다.
#       이 경우 전체 명령 앞에 셸 이름을 입력한다.
#       명령 예시:
#
#           ksh Gradle
#
#       Busybox처럼 기능을 줄인 셸에서는 실행할 수 없다.
#       이 스크립트는 아래 POSIX 셸 기능을 모두 사용한다.
#         * 함수;
#         * 변수 확장 «$var», «${var}», «${var:-default}», «${var+SET}»,
#           접두사·접미사 제거 및 명령 치환 «${var#prefix}», «${var%suffix}», «$( cmd )»;
#         * 종료 상태를 검사할 수 있는 복합 명령, 특히 «case»;
#         * «command», «set», «ulimit» 등 여러 내장 명령.
#
#   수정 시 주의사항:
#
#   (2) 모든 POSIX 셸에서 실행하도록 Bash, Ksh 등의
#       확장 기능을 피하며, 특히 배열을 사용하지 않는다.
#
#       여러 인자를 공백으로 구분한 문자열 하나에 넣는 기존 방식은
#       버그와 보안 문제의 원인으로 잘 알려져 있다.
#       이 문제를 대부분 피하기 위해 옵션을 점차
#       "$@"에 모은 뒤 Java에 전달한다.
#
#       상속한 환경변수 DEFAULT_JVM_OPTS, JAVA_OPTS,
#       GRADLE_OPTS에 단어 분리가 필요하면 이를 명시적으로 수행한다.
#       자세한 내용은 해당 줄의 주석을 참고한다.
#
#       AIX, CygWin, Darwin, MinGW, NonStop 같은
#       특정 운영체제를 위한 보정도 포함한다.
#
#   (3) 이 스크립트는 아래 Groovy 템플릿으로 생성한다.
#       https://github.com/gradle/gradle/blob/HEAD/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       이 템플릿은 Gradle 프로젝트에 포함되어 있다.
#
#       Gradle 저장소: https://github.com/gradle/gradle/.
#
##############################################################################

# APP_HOME 설정 시도

# 링크 해석: $0이 링크일 수 있다.
app_path=$0

# 여러 단계로 연결된 심볼릭 링크를 처리한다.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # 끝의 /를 남긴다. 앞에 경로가 없으면 빈 값이다.
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# 보통 사용하지 않는다.
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# CDPATH 설정 시 cd의 표준 출력을 버린다. (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# 사용 가능한 최댓값을 사용한다. MAX_FD가 -1이 아니면 지정한 값을 사용한다.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# 운영체제별 지원 여부. 반드시 'true' 또는 'false'여야 한다.
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH="\\\"\\\""


# JVM을 시작할 Java 명령을 결정한다.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # AIX의 IBM JDK는 실행 파일 위치가 일반적이지 않다.
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# 가능하면 최대 파일 디스크립터 수를 늘린다.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # POSIX sh는 ulimit -H를 정의하지 않으므로 실행 결과를 확인한다.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # POSIX sh는 ulimit -n을 정의하지 않으므로 실행 결과를 확인한다.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# java 명령의 모든 인자를 역순으로 쌓아 모은다.
#   * 명령줄 인자
#   * 메인 클래스 이름
#   * 클래스 경로 옵션 -classpath
#   * 애플리케이션 이름 설정 -D...appname
#   * 모듈 경로 옵션 --module-path (필요한 경우만)
#   * 환경변수 DEFAULT_JVM_OPTS, JAVA_OPTS, GRADLE_OPTS.

# Cygwin 또는 MSYS에서는 java 실행 전에 경로를 Windows 형식으로 바꾼다.
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # 인자를 변환한다. /bin/sh만 사용하기 위한 우회 처리다.
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # 옵션은 변경하지 않는다. #(
              /?*)  t=${arg#/} t=/${t%%/*}              # POSIX 파일 경로로 보이는 값
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # 인자 수만큼 인자 목록을 순환한다.
        # 각 인자는 원래 위치로 돌아오며,
        # 필요하면 값이 바뀐다.
        #
        # 주의: `for` 반복문은 시작 전에 반복할 목록을 저장한다.
        # 따라서 여기서 위치 인자를 바꿔도 반복 횟수와
        # `arg`에 전달되는 값은 변하지 않는다.
        shift                   # 기존 인자 제거
        set -- "$@" "$arg"      # 대체 인자 추가
    done
fi


# 기본 JVM 옵션을 여기에 추가한다. JAVA_OPTS와 GRADLE_OPTS로도 JVM 옵션을 전달할 수 있다.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# java 명령의 모든 인자를 모은다.
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, optsEnvironmentVar에는 셸 코드를 넣을 수 없다.
#     포함된 셸 구문은 이스케이프 처리한다.
#   * 예를 들어 환경변수 ${Hostname}은 확장되지 않으며,
#     명령줄에서 '${Hostname}' 문자열 자체로 처리된다.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "$@"

# "xargs"를 사용할 수 없으면 중단한다.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# "xargs"로 따옴표가 있는 인자를 파싱한다.
#
# -n1은 따옴표와 역슬래시를 제거하고 한 줄에 인자 하나를 출력한다.
#
# Bash에서는 다음과 같이 간단히 처리할 수 있다.
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# POSIX 셸에는 배열과 해당 명령 치환 구문이 없으므로,
# 각 인자를 sed 입력의 한 줄로 후처리한다.
# 셸 메타문자가 될 수 있는 문자는 역슬래시로 이스케이프하고,
# eval로 역변환하되 인자 사이의 구분은 유지한다.
# 전체 처리는 하나의 "set" 문으로 묶는다.
#
# 이 변수에 줄바꿈 또는 짝이 맞지 않는 따옴표가 있으면
# 정상적으로 처리할 수 없다.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
