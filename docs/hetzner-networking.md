---
name: reference-hetzner-networking
description: "Hetzner server networking concept: public/internal port separation via Hetzner Firewall + WireGuard VPN, including multi-server topologies"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 37fff7be-c135-4c4c-a9fd-4f48d162f6d5
---

# Hetzner Networking: Public/Internal Port Separation

## Konzept

Nur öffentliche Ports nach außen freigeben; interne Ports nur via VPN erreichbar.

```
Internet
  │
  ├── :80/:443  → Nginx (öffentlich)
  ├── :51820    → WireGuard (VPN-Einstieg)
  └── alles andere → BLOCKIERT
           │
           └── VPN-Tunnel (10.10.0.0/24)
                  ├── :5432  Postgres
                  ├── :8080  App intern
                  └── :9090  Monitoring
```

## Setup-Schritte

### 1. Hetzner Cloud Firewall (Netzwerkrand)

| Protokoll | Port | Quelle | Zweck |
|-----------|------|--------|-------|
| TCP | 80 | 0.0.0.0/0 | HTTP |
| TCP | 443 | 0.0.0.0/0 | HTTPS |
| UDP | 51820 | 0.0.0.0/0 | WireGuard |
| TCP | 22 | eigene-IP | SSH |

### 2. WireGuard VPN

Server `/etc/wireguard/wg0.conf`:
```ini
[Interface]
Address = 10.10.0.1/24
ListenPort = 51820
PrivateKey = <server.key>

[Peer]
# Laptop
PublicKey = <client.pub>
AllowedIPs = 10.10.0.2/32

[Peer]
# GitHub Actions CI
PublicKey = <ci.pub>
AllowedIPs = 10.10.0.5/32
```

Client (Laptop):
```ini
[Interface]
Address = 10.10.0.2/24
PrivateKey = <client.key>

[Peer]
PublicKey = <server.pub>
Endpoint = <hetzner-ip>:51820
AllowedIPs = 10.10.0.0/24
PersistentKeepalive = 25
```

Client (GitHub Actions CI, road-warrior peer wie der Laptop, wird per Workflow-Step aufgebaut und wieder abgebaut, Config als Secret `WG_CONFIG` hinterlegt):
```ini
[Interface]
Address = 10.10.0.5/32
PrivateKey = <ci.key>

[Peer]
PublicKey = <server.pub>
Endpoint = <hetzner-ip>:51820
AllowedIPs = 10.10.0.0/24
PersistentKeepalive = 25
```

### CI-Peer einrichten (einmalig, für GitHub Actions)

Auf dem Hetzner-Server per SSH:

1. Neuen `[Peer]`-Block in `/etc/wireguard/wg0.conf` ergänzen (Public Key des CI-Keypairs aus Schritt 1 einsetzen):
   ```ini
   [Peer]
   # GitHub Actions CI
   PublicKey = <Inhalt von ci.pub>
   AllowedIPs = 10.10.0.5/32
   ```
2. Ohne Verbindungsabbruch für bestehende Peers neu laden:
   ```bash
   sudo wg syncconf wg0 <(wg-quick strip wg0)
   ```
3. Prüfen, dass der Peer registriert ist:
   ```bash
   sudo wg show wg0
   ```

Danach im GitHub-Repo unter **Settings → Secrets and variables → Actions → New repository secret** ein Secret namens `WG_CONFIG` anlegen. Der Wert wird **roh** eingefügt (kein Base64), da der Workflow ihn 1:1 als `/etc/wireguard/wg0.conf` schreibt — exakte Vorlage:

```ini
[Interface]
Address = 10.10.0.5/32
PrivateKey = <Inhalt von ci.key>

[Peer]
PublicKey = <Server-Public-Key>
Endpoint = <hetzner-ip>:51820
AllowedIPs = 10.10.0.0/24
PersistentKeepalive = 25
```

Woher die Platzhalter kommen:

| Platzhalter | Herkunft |
|---|---|
| `<Inhalt von ci.key>` | Inhalt der Datei `ci.key` aus Schritt 1 (`wg genkey \| tee ci.key \| wg pubkey > ci.pub`) — der **private** Key des CI-Peers, bleibt nur in diesem Secret, nirgendwo sonst speichern |
| `<Server-Public-Key>` | Public Key des Servers, auf dem Hetzner-Server ermitteln mit `sudo cat /etc/wireguard/publickey` (falls vorhanden) oder `wg show wg0 public-key`, bzw. steht bereits als `PrivateKey`-Gegenstück in der Server-`wg0.conf` — daraus mit `wg pubkey` ableiten |
| `<hetzner-ip>` | Öffentliche IPv4-Adresse des Hetzner-Servers (dieselbe, über die auch der Laptop-Client sich verbindet) |

Alle anderen Zeilen (`Address`, `AllowedIPs`, `PersistentKeepalive`) 1:1 wie oben übernehmen — nicht verändern, das sind feste Werte für diesen CI-Peer.

Sicherheitshinweis: `ci.key` (privater Key) nach dem Einfügen in das GitHub-Secret **lokal löschen** — er wird nur einmalig gebraucht und sollte nicht dauerhaft auf der Festplatte liegen.

### 3. Dienste nur lokal binden

```yaml
# docker-compose.yml
ports:
  - "127.0.0.1:5432:5432"  # nicht 0.0.0.0
```

### 4. UFW als zweite Schicht (optional)

```bash
ufw default deny incoming
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 51820/udp
ufw allow in on wg0
ufw enable
```

---

## Multi-Server-Topologien

### Hub-and-Spoke (einfach)

Ein Hub-Server, alle anderen verbinden sich nur mit ihm. Traffic läuft immer über Hub.

```
Laptop (10.10.0.2)
      │
Server-1/Hub (10.10.0.1)
      ├── Server-2 (10.10.0.3)
      └── Server-3 (10.10.0.4)
```

Hub-Config hat alle anderen als `[Peer]` mit je einer `/32`-AllowedIPs.
Clients haben nur den Hub als `[Peer]` mit `AllowedIPs = 10.10.0.0/24`.

### Mesh (direkt, für kleine Setups)

Jeder Server kennt jeden direkt. Keine Bottlenecks, aber n²-Konfigurationsaufwand.

Jeder Server hat alle anderen als `[Peer]` mit deren öffentlicher IP als `Endpoint`.

### Hetzner Private Network (empfohlen für Hetzner-intern)

Wenn alle Server im gleichen Hetzner-Projekt: kostenloses privates VLAN im Panel erstellen.
- Server bekommen automatisch private IPs (z.B. `192.168.0.x`)
- Kommunikation über Hetzners internes Netz, kein Internet-Overhead
- Kein WireGuard zwischen Servern nötig

---

## Empfohlenes Setup für dieses Projekt

**Hetzner Private Network** für Server-zu-Server-Kommunikation + **WireGuard** für externen Zugang. Minimaler Konfigurationsaufwand, kein VPN-Overhead zwischen den Servern.

Externer WireGuard-Zugang hat zwei Road-Warrior-Peers:
- **Laptop** (`10.10.0.2`) — manueller Admin-Zugriff (`kubectl`, `helm`, DB-Zugriff).
- **GitHub Actions CI** (`10.10.0.5`) — die Deploy-Workflows (`deploy-production.yml`, `deploy-stage.yml`, `deploy-monitoring.yml`) bauen den Tunnel per `wg-quick up` zu Beginn des Jobs auf und wieder ab, bevor sie `kubectl`/`helm` gegen die (nur intern erreichbare) Kubernetes-API aufrufen. Die Client-Config liegt als GitHub-Secret `WG_CONFIG`, gemeinsam genutzt von allen drei Workflows.
