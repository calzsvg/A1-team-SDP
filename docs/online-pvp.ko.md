# 온라인 PvP 실행 안내

## 구현된 동작

- 각 PC에서 게임의 PvP 메뉴를 선택하면 지정한 TCP 서버에 접속합니다.
- 서버가 접속 순서대로 두 사람을 매칭합니다. 최대 16개 방(32명)입니다.
- 두 사람이 SPACE로 준비하면 3초 카운트다운 후 대전합니다.
- A/D 또는 좌우 화살표로 이동하고 SPACE를 누르고 있으면 연사합니다.
- 체력은 5이며 먼저 상대를 격추하면 승리합니다. 동시 격추는 무승부입니다.
- 각자 자신은 초록색 아래 기체, 상대는 빨간색 위 기체로 보입니다.
- 서버 공통 좌표에서 플레이어 2의 화면만 180도 회전합니다. 기체, 총알 위치와
  이동 입력을 함께 변환하며 글자와 체력 UI는 회전하지 않습니다.
- ESC는 메뉴로 돌아갑니다. 종료 또는 연결 실패 후 R로 새 매칭에 참가합니다.
  R은 기존 상대와의 재대전을 보장하지 않습니다. 둘 다 다시 참가해야 합니다.
- 한 사람이 나가면 방을 닫고 상대에게 연결 종료를 표시합니다.
- 대기/준비 제한은 2분, 세션 제한은 접속 후 10분입니다.

## 1. Ubuntu 미니 PC에서 서버 실행

Java 17 이상 JDK가 필요합니다. 다음은 Ubuntu에서 저장소를 새로 받는 예입니다.

```bash
sudo apt update
sudo apt install openjdk-17-jdk-headless git
git clone -b a1/online-pvp https://github.com/calzsvg/A1-team-SDP.git
cd A1-team-SDP
bash scripts/build-pvp.sh
bash scripts/run-pvp-server.sh
```

브랜치가 원격에 없다면 제공된 소스 ZIP을 풀고 해당 폴더에서 빌드합니다.
기본 서버 주소는 `0.0.0.0:7777`입니다. 서버에는 GUI나 그래픽 리소스가 필요 없습니다.
다른 포트/인터페이스는 `bash scripts/run-pvp-server.sh 8888 0.0.0.0`처럼 지정합니다.

```bash
ss -ltn 'sport = :7777'
```

## 2. 각자의 PC에서 게임 실행

서버와 동일한 브랜치의 소스를 받습니다. 클라이언트는 데스크톱 환경과 JDK 17 이상이 필요합니다.

Windows PowerShell (저장소 폴더에서):

```powershell
.\scripts\run-pvp-client.ps1 -ServerAddress '서버주소' -Port 7777
```

스크립트 실행 정책으로 차단된다면 정책을 변경하지 않고 직접 실행할 수 있습니다.

```powershell
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
New-Item -ItemType Directory -Force bin | Out-Null
javac --release 17 -encoding UTF-8 -d bin @sources
java '-Dpvp.host=서버주소' '-Dpvp.port=7777' -cp 'bin;res' engine.Core
```

Linux/macOS:

```bash
bash scripts/build-pvp.sh
bash scripts/run-pvp-client.sh 서버주소 7777
```

IDE 실행 시 VM options에 `-Dpvp.host=서버주소 -Dpvp.port=7777`을 넣습니다.
프로그램 인수가 아니라 JVM 옵션입니다. Working directory는 저장소 최상위입니다.
주소를 생략하면 `127.0.0.1`로 접속하므로 다른 PC에서는 반드시 서버 주소를 지정하세요.
게임을 실행한 다음 PvP 메뉴로 들어갑니다.

먼저 같은 네트워크에서는 미니 PC의 내부 IP로 확인합니다. 같은 PC에서 게임을 두 개
실행하는 테스트는 `127.0.0.1`로 할 수 있습니다. 서로 다른 집에서는 아래 외부 접속 설정 후
공인 IP 또는 도메인을 사용합니다. `0.0.0.0`은 서버가 대기하는 주소이며 접속 주소가 아닙니다.

## 3. 서로 다른 집에서 접속

1. 공유기에서 미니 PC의 내부 IPv4 주소에 DHCP 예약을 설정합니다.
2. 공유기 포트포워딩을 **외부 TCP 7777 → 미니 PC 내부 IP의 TCP 7777**로 설정합니다.
3. Ubuntu 방화벽 상태를 확인하고 필요한 포트를 허용합니다.

```bash
sudo ufw status
sudo ufw allow 7777/tcp
```

UFW가 비활성 상태라면 위 명령만으로 활성화되지는 않습니다. 원격 SSH로 작업 중이면
SSH 포트 허용 여부를 먼저 확인한 뒤 방화벽 활성화 여부를 결정하세요.
방화벽 안내: [Ubuntu 공식 문서](https://ubuntu.com/server/docs/firewalls/).

4. 다른 집 또는 모바일 핫스팟에서 공인 IP/도메인으로 연결합니다.
   Windows에서는 `Test-NetConnection 서버주소 -Port 7777`로 TCP 접근을 먼저 확인합니다.
5. 공인 IP가 바뀌면 접속 주소를 갱신하거나 공유기의 DDNS를 사용합니다.

공유기 WAN 주소가 사설 IP이거나 통신사 CGNAT 환경이라면 포트포워딩만으로 연결되지 않을 수
있습니다. 이 경우 통신사에 공인 IPv4 제공 여부를 확인하거나, 양쪽 클라이언트와 서버를
같은 VPN에 연결하는 별도 구성이 필요합니다. 공유기가 두 대면 양쪽의 포워딩도 확인하세요.
같은 집에서 공인 주소로 접속하는 테스트는 공유기의 NAT loopback 지원 여부에 영향을 받습니다.

## 4. 부팅 시 자동 실행 (선택)

아래는 `/opt/a1-pvp`를 새 설치 폴더로 사용하는 예입니다. 기존 설치가 있다면 먼저 상태를
확인하고 경로를 맞추세요. 저장소 폴더에서 빌드를 마친 뒤 실행합니다.

```bash
sudo useradd --system --user-group --no-create-home --shell /usr/sbin/nologin a1pvp
sudo install -d -o root -g root -m 755 /opt/a1-pvp
sudo cp -r bin /opt/a1-pvp/
sudo chmod -R a+rX /opt/a1-pvp/bin
sudo install -m 644 deploy/a1-pvp.service /etc/systemd/system/a1-pvp.service
sudo systemctl daemon-reload
sudo systemctl enable --now a1-pvp
sudo systemctl status a1-pvp
journalctl -u a1-pvp -f
```

이미 `a1pvp` 사용자가 있으면 `useradd`는 생략합니다. 수동 서버가 같은 포트를 쓰고 있다면
먼저 종료해야 합니다. 서비스 포트를 바꾸면 방화벽, 공유기와 클라이언트 포트도 맞춥니다.
서버 코드를 갱신할 때는 서비스를 중지하고 다시 빌드한 `bin`을 설치한 뒤 재시작합니다.
실패 시 자동 재시작 설정은 [systemd 공식 문서](https://freedesktop.org/software/systemd/man/latest/systemd.service.html)를 따릅니다.

## 검증 및 범위

```bash
bash scripts/build-pvp.sh
javac --release 17 -encoding UTF-8 -cp bin -d bin tests/PvpTest.java
java -cp bin PvpTest
```

독립 실행 테스트는 대기/카운트다운, 양쪽 시점과 이동, 경계, 피격/승패/무승부,
패킷 검증, 실제 TCP 서버와 두 클라이언트의 연결/이동/접속 종료를 확인합니다.

이 버전은 기본 1:1 대전입니다. 기존 상점의 스킨/무기/아이템, 계정, 방 코드, 랭킹,
재접속 복원은 포함하지 않습니다. 지연 보간/예측 없이 서버 상태를 약 30회/초 표시합니다.
서버는 약 60회/초 시뮬레이션하며 클라이언트가 보낸 좌표/피해량을 신뢰하지 않습니다.
프로토콜 버전, 입력 비트, 수신 타임아웃과 클라이언트 수를 제한합니다.
로그인과 TLS가 없는 기본 TCP 프로토콜이므로 이 포트로 계정 정보나 비밀번호를 보내지 않습니다.
친구들과 시험하는 용도이며 불특정 다수 대상 운영 전에는 인증과 접속 제한 보강이 필요합니다.

미니 PC의 실제 설치, 공유기 설정 및 서로 다른 집 사이의 접속은 해당 장비에서 별도 확인해야 합니다.
