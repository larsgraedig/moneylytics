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
           └── VPN-Tunnel (10.0.0.0/24)
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
Address = 10.0.0.1/24
ListenPort = 51820
PrivateKey = <server.key>

[Peer]
PublicKey = <client.pub>
AllowedIPs = 10.0.0.2/32
```

Client (Laptop):
```ini
[Interface]
Address = 10.0.0.2/24
PrivateKey = <client.key>

[Peer]
PublicKey = <server.pub>
Endpoint = <hetzner-ip>:51820
AllowedIPs = 10.0.0.0/24
PersistentKeepalive = 25
```

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
Laptop (10.0.0.2)
      │
Server-1/Hub (10.0.0.1)
      ├── Server-2 (10.0.0.3)
      └── Server-3 (10.0.0.4)
```

Hub-Config hat alle anderen als `[Peer]` mit je einer `/32`-AllowedIPs.
Clients haben nur den Hub als `[Peer]` mit `AllowedIPs = 10.0.0.0/24`.

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

**Hetzner Private Network** für Server-zu-Server-Kommunikation + **WireGuard** nur für externen Laptop-Zugang. Minimaler Konfigurationsaufwand, kein VPN-Overhead zwischen den Servern.
