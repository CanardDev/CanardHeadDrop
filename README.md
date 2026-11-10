# CanardHeadDrop

A lightweight Paper plugin that makes a player's head drop when they die.

You choose when it happens (PvP, PvE or both), in which worlds, how often, and how many heads a player can lose. Heads can't be renamed in an anvil, so nobody can fake one.

## Features

- Separate toggles for **PvP** deaths (killed by a player) and **PvE** deaths (mobs, fall, lava...)
- World whitelist (empty = every world)
- Drop chance in %
- Optional limit of heads per player, with a reset command
- Custom head name and lore, with `&` colors and `&#rrggbb` hex colors
- Anvil protection: dropped heads can't be renamed
- Optional lightning effect, chat broadcast and sound
- `/headdrop give` to give anyone's head (works for offline players without freezing the server)
- English and French messages, fully editable

## Commands

| Command | Description |
|---|---|
| `/headdrop give <owner> [player]` | Give the head of `<owner>` (to yourself or to `[player]`) |
| `/headdrop worlds` | List the server's worlds and where heads drop |
| `/headdrop reset <player>` | Reset a player's head limit |
| `/headdrop reload` | Reload the config and messages |

Alias: `/hdrop`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `headdrop.admin` | op | Use every `/headdrop` command |

## Configuration

```yaml
language: en            # en or fr

drop:
  pvp: true
  pve: false
  worlds: []            # empty = every world
  chance: 100.0
  limit:
    enabled: false
    max: 5

head:
  name: "&e%player% &7- &fHead"
  lore:
    - "&8☠ Player head"
  prevent-rename: true

effects:
  lightning: false
  broadcast: false
  sound:
    enabled: false
    name: entity.player.levelup
```

Messages are in `messages_en.yml` and `messages_fr.yml`.

## Requirements

- Paper 1.21 or newer (or a Paper fork like Purpur)
- Java 21

## Installation

1. Download the `.jar` from the [Releases](../../releases) page
2. Put it in your `plugins/` folder
3. Restart the server and edit `plugins/CanardHeadDrop/config.yml`

## Building

```
mvn package
```

The jar is created in `target/`.

---

Made by **CanardDev**. Questions or custom plugins: [Discord](https://discord.com/users/982732557365964881)
