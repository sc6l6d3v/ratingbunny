# Redis 5.0.3 → 8.8.0 Upgrade Notes

**Date:** June 2026
**Hosts:** davos (dev), saillon (prod)
**Platform:** AlmaLinux 8.x
**Package source:** Remi repository (`redis:remi-8.8` module stream)

## Motivation

Lettuce 6.4+ (pulled in by redis4cats 2.0.1) sends `CLIENT SETINFO` during its connection handshake to tag connections with library metadata. Redis versions prior to 7.2 do not support this subcommand, resulting in `RedisCommandExecutionException` errors logged at DEBUG level on every connection:

```
ERR Unknown subcommand or wrong number of arguments for 'SETINFO'. Try CLIENT HELP
```

While harmless (connections still succeed), upgrading Redis to 7.2+ resolves the error. Redis 8.8.0 was selected as the target version.

## Pre-upgrade checklist

### 1. Install Remi repository (if not present)

```bash
dnf install epel-release
dnf install https://rpms.remirepo.net/enterprise/remi-release-8.rpm
```

### 2. Prepare config with ziplist → listpack renames

Redis 7.0 renamed internal encoding from "ziplist" to "listpack". The following config directives must be updated:

| Old (5.0) | New (8.x) |
|---|---|
| `hash-max-ziplist-entries` | `hash-max-listpack-entries` |
| `hash-max-ziplist-value` | `hash-max-listpack-value` |
| `zset-max-ziplist-entries` | `zset-max-listpack-entries` |
| `zset-max-ziplist-value` | `zset-max-listpack-value` |
| `list-max-ziplist-size` | `list-max-listpack-size` |

Redis 8.x silently accepts the old names, but the config should use the new names going forward.

```bash
cp /etc/redis.conf /etc/redis.conf.5.0.bak
cp /etc/redis.conf /etc/redis.conf.8.8
sed -i 's/hash-max-ziplist-entries/hash-max-listpack-entries/' /etc/redis.conf.8.8
sed -i 's/hash-max-ziplist-value/hash-max-listpack-value/' /etc/redis.conf.8.8
sed -i 's/zset-max-ziplist-entries/zset-max-listpack-entries/' /etc/redis.conf.8.8
sed -i 's/zset-max-ziplist-value/zset-max-listpack-value/' /etc/redis.conf.8.8
sed -i 's/list-max-ziplist-size/list-max-listpack-size/' /etc/redis.conf.8.8
```

### 3. Kernel tuning

```bash
cat > /etc/sysctl.d/99-redis.conf << 'EOF'
vm.overcommit_memory = 1
net.core.somaxconn = 65536
EOF
sysctl --system
```

- `vm.overcommit_memory = 1` — required for `BGSAVE` fork on large datasets; without it, background saves can fail
- `net.core.somaxconn = 65536` — allows Redis to use its configured TCP backlog

### 4. Extend systemd startup timeout

With ~35GB RDB dumps (~59GB in memory), the dataset load takes 3-5 minutes, exceeding the default 90-second systemd timeout.

```bash
systemctl edit redis
```

Add:

```ini
[Service]
TimeoutStartSec=600
```

```bash
systemctl daemon-reload
```

### 5. Pre-download packages

```bash
dnf module reset redis -y
dnf module enable redis:remi-8.8 -y
dnf install --downloadonly redis
```

## Cutover procedure

```bash
# Final save of running instance
redis-cli BGSAVE
# Wait for completion — poll LASTSAVE until timestamp updates

# Stop 5.0.3
systemctl stop redis

# Swap config
cp /etc/redis.conf.8.8 /etc/redis.conf
# If RPM places config at /etc/redis/redis.conf, copy there instead

# Install (packages already cached)
dnf install redis -y

# Start 8.8
systemctl start redis

# Monitor load
tail -f /var/log/redis/redis.log
```

## Post-upgrade verification

```bash
redis-cli INFO server | grep redis_version
redis-cli DBSIZE
redis-cli CLIENT SETINFO LIB-NAME test
journalctl -u redis --since "5 minutes ago"
```

## Known behaviors during upgrade

- **`LOADING` errors during RDB load:** Redis rejects all commands with `LOADING Redis is loading the dataset in memory` for 3-5 minutes while the dataset loads. Applications using redis4cats/Lettuce handle this gracefully — the image service (tmdbimgservice) returns fallback responses during this window.
- **Orphan temp RDB files:** Failed or interrupted `BGSAVE` operations leave `temp-*.rdb` files in `/var/lib/redis/`. These can be safely removed (except any actively being written).
- **Config path change:** Redis 8.x RPM may use `/etc/redis/redis.conf` instead of `/etc/redis.conf`.

## Rollback

```bash
systemctl stop redis
cp /etc/redis.conf.5.0.bak /etc/redis.conf
dnf module reset redis -y
dnf module enable redis:5.0 -y
dnf downgrade redis
systemctl start redis
```

## Other notes

- `rename-command` is deprecated in Redis 8.x; ACLs are the replacement. Still accepted for backward compatibility.
- RDB format is forward-compatible — Redis 8.x loads 5.0.3 dumps without issues.
- Do not keep backup copies of `dump.rdb` in the Redis data directory during startup — they waste disk and are unnecessary during the load.
