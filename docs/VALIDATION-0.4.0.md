# Validation — 0.4.0

Completed **2026-09-07** on Windows 11, Java **21.0.12**, Minecraft **1.21.1**, and NeoForge **21.1.244**. Client captures are 1280×720 from an NVIDIA RTX 5070 Ti. All work used this repository's disposable development worlds; no modpack installation was changed.

| Gate | Result |
| --- | --- |
| Compilation and release build | Passed |
| JUnit | **37 passed**, zero failures/errors/skips |
| Minecraft GameTests | **88 required tests passed** |
| Dedicated server | Saved-world startup and clean shutdown completed |
| Definitions | **42 loaded**, `/ritual validate` reported zero warnings |
| Missing-definition command | Actual registry query reported the retained legacy Arcane Assembly entry |
| Real client/server sequence | Completed automatic multi-enchantment ritual, exact XP debit, and subtraction ritual |
| Resource reload and page geometry | Written-book reload, centered spin, and rendered 3D edges passed |
| Artifact inspection | JSON, mixins, metadata, new catalyst assets, supplied page texture, and production-only classes checked |

```powershell
.\gradlew.bat test build runGameTestServer
python tools/package_release.py
```

## Automated coverage

The unit suite covers definition/knowledge codecs, thresholds, additive catalyst bonuses, exact combined XP costs, particles and geometry, scheduling and density, and conditional signed power allocation. New allocation cases include 20/10 splitting into 10/5, refusal of an ineffective share, five simultaneous relative shares, release of redundant material allocations, consumed duplicates considered together, partial subtraction thresholds, weak subtraction with no users, and missing-level interpolation.

The GameTests exercise the actual server registries and world objects. New coverage includes ordered outward and return routes; the same physical pedestal at exactly 150%, both consumed and unconsumed; reusable return visits while separate duplicates remain excluded; mixed reusable/consumed copies with extraction only from the marked copy; unconsumed duplicate exclusion; every consumed duplicate contributing and being extracted once; isolated bookshelf routes and separate starter-material branches from one bookshelf; concurrent-route configuration; combined modifiers; curse removal; partial surviving rings, including mixed incoming and removed power; weak/absent subtraction with no session, spending, or active routes; exact material identity and modifier revalidation; three-slot persistence and old-inventory migration; one-material limits; configured/unconfigured/mixed chest books; the actual chest-context boundary; the live missing-definition command; configurable gentle enchantability; and the full default material-set balance for every vanilla enchantment.

Existing regression coverage remains for knowledge acquisition, book interaction, adapters, rollback, multiplayer capture, XP changes, table/target interruption, orphaned item gravity, six-way pedestal geometry, all 42 unique resolved effects, bounded bursts, and sequential ring starts.

Machine-readable evidence: [unit tests](validation/unit-tests.json), [GameTests](validation/gametest-results.log), [client checks](validation/client-checks.log), [server startup](validation/server-start.log), and [server observations](validation/server-observations.txt).

## Actual client evidence

The opt-in driver used real Minecraft screens and ordinary menu/drop packets against a separate dedicated server. It confirmed:

1. Book Auto-Add toggles, tear/gather actions, and resource reload while open.
2. Read-only table knowledge, navigation controls, and two XP catalysts displaying **550 XP / ×1.2**.
3. All six pedestal orientations synchronized, with centered page geometry and real edges.
4. The dropped target held a stable height over 50 measured ticks, with **0.0 blocks of Y variation**.
5. Curved particle flights included player-to-catalyst routes to both XP pedestals.
6. Four enchantment effects formed rings in sequence, with differing synchronized radii; prior rings persisted.
7. The inward pulse was followed by particles moving into all eight octants, vertex opacity fading **221 → 44**, then expiration.
8. Automatic enchanting committed and released the result, with exactly **1395 → 845 XP**.
9. Both pedestal modifiers synchronized and appeared side by side on the rim.
10. The subtraction reference showed **−145** potential power: **−90** from the consumed, revisited diamond and **−55** from scrap.
11. Actual reverse flights originated at the enchanted target and traveled to a subtraction pedestal.
12. Subtraction changed **Sharpness V → III**, consumed the marked diamond once, and retained both modifiers.

A result can be picked up normally after release. The client fixture accepts a valid result either in the world or in the player's inventory. Early fixture assertions that required it to remain on the table were corrected after the saved player inventory confirmed a successfully picked-up result.

The final mixed incoming/subtraction ring-fraction edge case has automated server coverage; it was not separately measured in the graphical client. Third-party modpacks, resource packs, and external pedestal adapters beyond the included test adapters still need their own runtime checks.

## Screenshots

- [Automatic layered ritual](screenshots/automatic-ritual-layered.png)
- [Pulse](screenshots/ritual-pulse.png), [burst](screenshots/ritual-burst.png), and [fade](screenshots/ritual-burst-fading.png)
- [Subtraction reference](screenshots/subtraction-reference.png) and [reverse flow](screenshots/subtraction-inverse-flow.png)
- [Multiple pedestal modifiers](screenshots/multiple-pedestal-modifiers.png)
- [Written-book interface](screenshots/knowledge-book.png), [centered page](screenshots/knowledge-page-centered.png), and [rotated page](screenshots/knowledge-page-rotated.png)

## Development fixture

`runSmokeServer` enables `/ritualtest setup`, `book`, `ritual`, `pages`, and `subtract`; `runClientSmoke` drives the client sequence on loopback port 25585. The fixture changes the disposable world near **0,64,0**, resets nearby displays, and replaces the test player's inventory/XP. Normal runs do not enable it. A `run-client/stop-smoke` marker stops the smoke client. Development classes and test structures are excluded from the shipped JAR.

The clarified unconsumed-return rule was checked with two additional server GameTests. A 20-power unconsumed diamond revisited on the return leg supplies 30 power, while a second unconsumed diamond pedestal stays outside the route. A separate consumption-marked copy can join and is extracted once. The production JAR is unchanged because this behavior was already implemented in 0.4.0.
