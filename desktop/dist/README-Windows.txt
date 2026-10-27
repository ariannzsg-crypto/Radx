Radx for Windows
================

The same config pipeline as the Android app, behind a command line tool: you import a
subscription or a share link, and Radx writes the sing-box and Xray configs and runs both
cores for you. Nodes whose transport sing-box cannot run (XHTTP, KCP) automatically run on
the bundled Xray core and are bridged back through a local SOCKS port.

Requirements
------------
* Windows 10/11 (64-bit)
* Java 17 or newer. `radx.cmd` checks this and tells you what to install.
  Temurin builds: https://adoptium.net/

Layout
------
  radx.cmd      launcher (checks Java, then starts the tool)
  bin\radx.bat  plain Gradle start script, used when JAVA_HOME is already correct
  lib\*.jar     the tool and its dependencies
  cores\        sing-box.exe, xray.exe and wintun.dll that the tool runs
  README-Windows.txt

Quick start
-----------
  radx.cmd import https://example.com/sub --name my-sub
  radx.cmd nodes
  radx.cmd ping --select-fastest
  radx.cmd up --mode proxy

Proxy mode listens on 127.0.0.1:7890 as both a SOCKS5 and an HTTP proxy and needs no
special rights:

  curl --proxy http://127.0.0.1:7890 https://api.ipify.org

TUN mode routes the whole system through the tunnel and replaces the routing table, so it
has to run from an elevated shell:

  radx.cmd up --mode tun

Commands
--------
  import <url|file|->    add a subscription, a file with links, or stdin
  list                   saved configs
  nodes [--select N]     nodes of the active config (marks Xray-backed ones)
  ping [--select-fastest]  TCP latency of every node
  gen [--check]          write the configs; --check validates them with both cores
  up                     run the tunnel until Ctrl+C
  version                tool and core versions

Useful flags
------------
  --profile N   pick the Nth config from `radx list`
  --node N      pick the Nth node (use `radx nodes` to see the numbers)
  --mode proxy|tun, --port N, --lan, --no-xray, --quiet
  --cores DIR   where sing-box.exe and xray.exe live (or set RADX_CORES)

State is kept in %USERPROFILE%\.radx (override with RADX_HOME): config.json holds the
imported configs and run\ holds the generated configs and core logs, which is the first
place to look when a node does not carry traffic.

Notes
-----
* A few nodes of a subscription may be unusable: Xray 26.x refuses a VLESS/Trojan outbound
  without TLS unless the server sits on a private address. Radx detects those, leaves them
  out of both cores and says so, instead of letting one node stop the whole core.
* Traffic figures and the Android UI (glass surfaces, accent colour, settings) stay on the
  phone; this build is the command line counterpart.
