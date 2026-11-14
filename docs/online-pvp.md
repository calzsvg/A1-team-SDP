# Online PvP

## Joining a match

1. Run the game and select **PvP**.
2. Enter the **Server address** supplied by the host, optionally followed by `:port`.
   A short DuckDNS name such as `my-lobby` expands to `my-lobby.duckdns.org`.
   Full domain names, IPv4 addresses, `localhost`, and bracketed IPv6 addresses are supported.
   The default port is `7777`; for example, `example.test:8888` uses port `8888`.
3. Enter a **Connection number** containing exactly three digits, including any leading zeroes.
4. Click **Connect**. Your friend must enter the same server and connection number.
5. Both players press **SPACE** to get ready. The match starts after three seconds.

The form starts empty. Opening the PvP menu does not connect to a bundled endpoint.
No personal server address, automatic LAN fallback, or router configuration is included.
The most recent input is retained only while that PvP screen remains open and is not saved to disk.
The form is drawn inside the game using the dark starfield and pixel font, without a popup.
Click a field or use **Tab** / **Shift+Tab** to move focus. **Backspace**, **Ctrl+A**, and
**Ctrl+V** support editing. Click **Connect** or press **Enter** to submit.
Press **ESC** to return to the menu.
After a connection failure or completed match, **R** reopens the form to edit the endpoint or number.

## Gameplay

- Use **A/D** or the left/right arrow keys to move. Hold **SPACE** to fire.
- Each player sees their own green ship at the bottom and their red opponent rotated 180 degrees at the top.
- Movement input and projectile positions use the same perspective transform. Text remains upright.
- Each ship has five health points. The server decides movement, shots, collisions, damage, and results.
- Simultaneous destruction is a draw. Press **ESC** to return to the menu.
- Disconnecting closes the room for both players. The number can then be reused.
- Different numbers create separate rooms. A third player cannot join a full room.
- The server supports up to 16 rooms. Waiting/ready rooms expire after two minutes;
  sessions expire after ten minutes from the first connection.

## Building and running

Use JDK 17 or later. Start commands from the repository root.

Windows PowerShell:

```powershell
.\scripts\run-pvp-client.ps1
```

If PowerShell blocks script execution, build and run directly without changing execution policy:

```powershell
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
New-Item -ItemType Directory -Force bin | Out-Null
javac --release 17 -encoding UTF-8 -d bin @sources
java -cp 'bin;res' engine.Core
```

Linux/macOS:

```bash
bash scripts/build-pvp.sh
bash scripts/run-pvp-client.sh
```

In an IDE, launch `engine.Core` with the working directory set to the repository root.
Enter the endpoint and connection number in the game. There are no required JVM options.

## Dedicated server

The server is headless and does not need a desktop, fonts, or graphics assets.
On Ubuntu, install a JDK and build the same protocol version as the clients:

```bash
sudo apt update
sudo apt install openjdk-17-jdk-headless git
git clone -b feat/online-pvp https://github.com/calzsvg/A1-team-SDP.git
cd A1-team-SDP
bash scripts/build-pvp.sh
bash scripts/run-pvp-server.sh
```

The server listens on `0.0.0.0:7777` by default. To change the port or bind address:

```bash
bash scripts/run-pvp-server.sh 8888 0.0.0.0
```

`0.0.0.0` is a listening address, not an address for players to enter.
For a local two-client test, use `127.0.0.1:7777` and the same three-digit number in both clients.
For LAN play, use the server's LAN address. Internet hosting requires an appropriate network
route and firewall configuration managed by the host. DNS provides an address; it does not
provide access control or hide the server IP. No router port-mapping automation is shipped.

An optional service template is available at `deploy/a1-pvp.service`.
It expects compiled classes in `/opt/a1-pvp/bin` and a dedicated `a1pvp` system user.
After preparing those paths and permissions:

```bash
sudo install -m 644 deploy/a1-pvp.service /etc/systemd/system/a1-pvp.service
sudo systemctl daemon-reload
sudo systemctl enable --now a1-pvp
systemctl status a1-pvp
journalctl -u a1-pvp -f
```

Stop the service before replacing its classes. Do not run a manual server and the service
on the same port. Service isolation reduces privileges; it is not a complete security boundary.

## Protocol and tests

Protocol version 2 adds a room number and explicit rejection messages. Version 1 clients
and servers are incompatible and must be updated together. The room number is sent as
an integer from 0 to 999; the entry form preserves the three-digit representation.

```bash
bash scripts/build-pvp.sh
javac --release 17 -encoding UTF-8 -cp bin -d bin tests/PvpTest.java
java -cp bin PvpTest
```

Tests cover endpoint validation, short names, leading zeroes, input directions, countdown,
boundaries, collisions, wins, draws, malformed states, and real TCP clients using isolated rooms.
They also cover full-room rejection, invalid room numbers, disconnection, and room-number reuse.

## Limitations

A three-digit number is a room identifier, not a secret or authentication mechanism.
Anyone who can reach the server can try numbers. Keeping an address out of source code does
not secure an Internet-accessible service. Use appropriate access controls for private play.
This protocol has no account authentication or TLS, so do not send passwords or personal data.

The initial implementation does not integrate shop weapons, skins, items, rankings, or reconnect
restoration. Simulation runs at approximately 60 ticks per second; clients receive snapshots
approximately 30 times per second without prediction or interpolation.
