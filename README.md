# Perfect Enchanting Table

[🇩🇪 Deutsche Version](README.de.md)

Fabric mod for **Minecraft 1.21.11** (Fabric Loader 0.20.1+, Fabric API). Adds the **Perfect Enchanting Table**: you
choose which enchantment goes on the item and at which level – one after another, no randomness, no experience
levels, no bookshelves. You pay with **lapis lazuli only**.

## Installation

Put `perfectenchanting-1.0.0.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) into the `mods` folder – on
the server **and** on every client. [Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds the in-game
settings screen.

## How to play

1. Craft the table:

   ```
   Lapis Block   Book              Lapis Block
   Diamond       Enchanting Table  Diamond
   Lapis Block   Lapis Block       Lapis Block
   ```

   (Creative: "Functional Blocks" tab, right after the enchanting table.)
2. Right-click the table, put the item into the top left slot and lapis lazuli below it.

### The interface

- **List**: shows *every* enchantment the item can carry by default (treasures like Mending or Soul Speed too),
  sorted alphabetically, curses last. Each row: name, current level, level picker, cost.
  - **Click a row** → the enchantment is applied.
  - **◀ ▶ or the mouse wheel** over the level → pick the level (always starts at the highest).
  - Existing enchantments can be **upgraded** (e.g. Unbreaking II → III); you only pay for the added levels.
  - Green = already at the highest level, red/grey = incompatible with an enchantment on the item
    (e.g. Smite next to Sharpness). The tooltip shows the details.
- **Search field**: filters by the translated name *or* the id (`sharp` finds Sharpness in every language).
- **Name field + quill button** (or Enter): renames the item, free by default. An empty name restores the original
  name. While you type, **E** does not close the window.
- **Books**: a plain book becomes an enchanted book, and any enchantment can go on an enchanted book.
- In **Creative mode** everything is free (can be turned off).

### Costs

Default: **1 lapis per level × the enchantment's rarity** (its vanilla anvil cost: 1 / 2 / 4 / 8).

| Example | Cost |
| --- | --- |
| Sharpness V, Efficiency V, Protection IV | 5 / 5 / 4 |
| Unbreaking III | 6 |
| Mending | 4 |
| Fortune III, Looting III | 12 |
| Silk Touch, Infinity | 8 |
| Thorns III, Swift Sneak III | 24 |

A fully enchanted sword (Sharpness V, Looting III, Fire Aspect II, Sweeping Edge III, Knockback II, Unbreaking III,
Mending) costs 51 lapis. The lapis comes from the lapis slot (up to 64 per action).

## Settings

With Mod Menu: **Mods → Perfect Enchanting Table → settings button**. Every option has a tooltip, a reset button and
is checked before saving. Without Mod Menu, edit `config/perfectenchanting.json` (created on first start) and run
`/perfectenchanting reload` (operator level 2).

The settings belong to whoever runs the world: in singleplayer and LAN worlds your own settings count, on a dedicated
server the server's `config/perfectenchanting.json`. Clients get the values when they open a table, so the list always
shows exactly what the server allows, and a change applies the **next time a table is opened**. Every request is
checked on the server again.

| Option | Default | Meaning |
| --- | --- | --- |
| `lapisPerLevel` | `1` | lapis per level (0–64); `0` = everything free |
| `scaleCostByRarity` | `true` | cost × rarity (1/2/4/8); `false` = every level costs exactly `lapisPerLevel` |
| `renameCost` | `0` | lapis for renaming (0–64) |
| `allowIncompatibleEnchantments` | `false` | allows combinations vanilla forbids (Sharpness + Smite, Mending + Infinity, …) |
| `allowCurses` | `true` | offer curses in the list |
| `creativeIgnoresCosts` | `true` | Creative mode pays nothing |
| `disabledEnchantments` | `[]` | ids that are never offered, e.g. `["minecraft:mending"]` |

## Languages

English and German (Deutsch). The game language decides; the config screen is translated too.

## Development

```
gradlew build              # builds build/libs/perfectenchanting-1.0.0.jar and runs the translation check
gradlew runClient          # test client (with Mod Menu)
gradlew runClientGameTest  # end-to-end test in a real client, screenshots in build/run/clientGameTest/screenshots
python tools/gen_textures.py .    # regenerates the block and GUI textures and the icon
```

The client game test (English, survival, 1920×1080) searches, picks levels with the arrows, enchants by clicking rows,
renames with the keyboard, enchants a book and checks everything on the server, including the lapis that was paid.
It then changes settings in the config screen behind Mod Menu's button – including the list editor, invalid input
and the "discard changes?" prompt – and checks that a table opened afterwards gets the new values.

How it works: the table extends the vanilla enchanting table block and reuses its block entity, so the floating book
comes for free. The server config reaches the client as the menu's opening data; the client only ever sends
enchantment ids, which the server checks against the item and the config.

## License

[MIT](LICENSE)
