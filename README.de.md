# Perfect Enchanting Table (Perfekter Zaubertisch)

[🇬🇧 English version](README.md)

Fabric-Mod für **Minecraft 1.21.11** (Fabric Loader 0.20.1+, Fabric API). Fügt den **Perfekten Zaubertisch** hinzu:
Du wählst selbst, welche Verzauberung auf welcher Stufe auf den Gegenstand kommt – eine nach der anderen, ohne Zufall,
ohne Erfahrungslevel, ohne Bücherregale. Bezahlt wird **nur mit Lapislazuli**.

## Installation

`perfectenchanting-1.0.0.jar` zusammen mit der [Fabric API](https://modrinth.com/mod/fabric-api) in den
`mods`-Ordner legen – auf dem Server **und** bei jedem Client. [Mod Menu](https://modrinth.com/mod/modmenu) ist
optional und bringt den Einstellungsbildschirm im Spiel.

## Spielen

1. Tisch craften:

   ```
   Lapisblock   Buch          Lapisblock
   Diamant      Zaubertisch   Diamant
   Lapisblock   Lapisblock    Lapisblock
   ```

   (Kreativ: Reiter „Funktionsblöcke“, direkt hinter dem Zaubertisch.)
2. Tisch mit Rechtsklick öffnen, Gegenstand oben links einlegen, Lapislazuli darunter.

### Die Oberfläche

- **Liste**: zeigt standardmäßig *alle* Verzauberungen, die der Gegenstand tragen kann (auch Schätze wie
  Reparatur oder Seelenläufer), alphabetisch, Flüche am Ende. Pro Zeile: Name, aktuelle Stufe, Stufenwahl, Kosten.
  - **Zeile anklicken** → Verzauberung wird angewendet.
  - **◀ ▶ oder Mausrad** über der Stufe → Stufe wählen (startet immer bei der Höchststufe).
  - Vorhandene Verzauberungen lassen sich **aufwerten** (z. B. Haltbarkeit II → III); bezahlt werden nur die
    hinzukommenden Stufen.
  - Grün = schon auf Höchststufe, Rot/Grau = unverträglich mit einer vorhandenen Verzauberung
    (z. B. Bann bei Schärfe). Der Tooltip zeigt Details.
- **Suchfeld**: filtert nach dem übersetzten Namen *oder* der ID (`sharp` findet „Schärfe“ in jeder Sprache).
- **Namensfeld + Feder-Knopf** (oder Enter): benennt den Gegenstand um, standardmäßig kostenlos.
  Ein leerer Name stellt den Originalnamen wieder her. Während man tippt, schließt **E** das Fenster nicht.
- **Bücher**: Ein normales Buch wird zum verzauberten Buch, auf ein verzaubertes Buch kann jede Verzauberung.
- Im **Kreativmodus** ist alles kostenlos (abschaltbar).

### Kosten

Standard: **1 Lapis pro Stufe × Seltenheit** der Verzauberung (ihre Vanilla-Ambosskosten: 1 / 2 / 4 / 8).

| Beispiel | Kosten |
| --- | --- |
| Schärfe V, Effizienz V, Schutz IV | 5 / 5 / 4 |
| Haltbarkeit III | 6 |
| Reparatur | 4 |
| Glück III, Plünderung III | 12 |
| Behutsamkeit, Unendlichkeit | 8 |
| Dornen III, Huschen III | 24 |

Ein komplett verzaubertes Schwert (Schärfe V, Plünderung III, Verbrennung II, Schwungkraft III, Rückstoß II,
Haltbarkeit III, Reparatur) kostet 51 Lapis. Bezahlt wird aus dem Lapis-Slot (max. 64 pro Vorgang).

## Einstellungen

Mit Mod Menu: **Mods → Perfekter Zaubertisch → Einstellungs-Button**. Jede Option hat einen Tooltip und einen
„Standard“-Button, Eingaben werden vor dem Speichern geprüft. Ohne Mod Menu `config/perfectenchanting.json`
bearbeiten (wird beim ersten Start angelegt) und `/perfectenchanting reload` ausführen (OP-Level 2).

Es gelten die Einstellungen dessen, der die Welt betreibt: in Einzelspieler- und LAN-Welten deine eigenen, auf einem
Server die `config/perfectenchanting.json` des Servers. Der Client bekommt die Werte beim Öffnen des Tisches
mitgeschickt, die Liste zeigt also immer genau das, was der Server erlaubt; eine Änderung gilt **ab dem nächsten
Öffnen eines Tisches**. Jede Anfrage wird auf dem Server erneut geprüft.

| Option | Standard | Bedeutung |
| --- | --- | --- |
| `lapisPerLevel` | `1` | Lapis pro Stufe (0–64); `0` = alles gratis |
| `scaleCostByRarity` | `true` | Kosten × Seltenheit (1/2/4/8); `false` = jede Stufe kostet genau `lapisPerLevel` |
| `renameCost` | `0` | Lapis fürs Umbenennen (0–64) |
| `allowIncompatibleEnchantments` | `false` | erlaubt Vanilla-verbotene Kombinationen (Schärfe + Bann, Reparatur + Unendlichkeit, …) |
| `allowCurses` | `true` | Flüche in der Liste anbieten |
| `creativeIgnoresCosts` | `true` | Kreativmodus zahlt nichts |
| `disabledEnchantments` | `[]` | IDs, die nie angeboten werden, z. B. `["minecraft:mending"]` |

## Sprachen

Englisch und Deutsch. Es gilt die Spielsprache; auch der Einstellungsbildschirm ist übersetzt.

## Entwickeln

```
gradlew build              # baut build/libs/perfectenchanting-1.0.0.jar, inkl. Übersetzungsprüfung
gradlew runClient          # Testclient (mit Mod Menu)
gradlew runClientGameTest  # End-to-End-Test im echten Client, Screenshots in build/run/clientGameTest/screenshots
python tools/gen_textures.py .    # erzeugt Block-, GUI-Textur und Icon neu
```

`checkTranslations` (Teil von `gradlew build`) bricht ab, wenn `en_us.json` und `de_de.json` nicht dieselben
Schlüssel haben. Der Client-Gametest läuft auf Englisch in 1920×1080: Er sucht, wählt Stufen, verzaubert per
Mausklick, benennt per Tastatur um, verzaubert ein
Buch und prüft alles inkl. Lapis-Verbrauch auf dem Server. Danach ändert er Einstellungen im Einstellungsbildschirm
(Listen-Editor, ungültige Eingaben, „Änderungen verwerfen?“) und prüft, dass ein neu geöffneter Tisch die neuen Werte
bekommt.

## Lizenz

[MIT](LICENSE)
