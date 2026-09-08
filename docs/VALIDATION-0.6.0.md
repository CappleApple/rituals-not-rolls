# Validation — 0.6.0

Completed **2026-09-08**, Windows, Java 21, Minecraft 1.21.1. Build and GameTests use NeoForge **21.1.244**; the real optional-mod server/client gates use **21.1.248**, as required by the installed Supplementaries version. All worlds are disposable development fixtures.

| Gate | Result |
| --- | --- |
| Build and JUnit | **51 tests passed**, zero failures/errors/skips |
| GameTests | **116 required tests passed** |
| Continuous routes | All physical waypoints traversed; shared velocity through knots, vertical routes, near-zero segments, return visits, distant world coordinates, and orbital entrance verified |
| Failure physics | Calm start, increasing shake, longer near misses, continuous momentum at collapse, gravity, and synchronized failure clocks verified |
| Wire format | Full spline and instability clock round-trip through codec and registry-aware particle packet |
| Resource lifecycle | Arrival-ordered early consumption, no double extraction, interruption refund, orphaned-item recovery once, and native inventory rollback verified |
| Per-chain allocation | Mixed success/failure preserves successful power and keeps failed-chain materials; failed-only attempts capture/release unchanged without XP debit |
| Native-level stability | Registry maximum is used even when the ritual datapack defines higher levels |
| Native pedestals | Supplementaries and Iron’s Spellbooks: held items, both modifiers, serialization, native extraction/refund, subtraction commit, modifier removal/native-interaction fallback, and exactly-once break drops passed |
| Real client | Native modifier sync/rendering, full-route particles, ordered disappearing offerings, calm/shaky/falling states, fading vertex opacity, successful mixed completion, and failed-only unchanged release passed |
| Disenchant client | Pure disenchantment releases the tool while existing reverse particles continue, with no burst |
| Sound client | All eight events resolve; consumption uses configured 0.5 event pitch, disenchant 0.6; all five failure choices 0.2–0.6 resolve including below vanilla’s clamp |
| Resources | Generator language and sound definitions match packaged resources; default enchantment definitions and textures are unchanged from 1.5.4 |

The native tests used Supplementaries **1.21.1-3.9.7**, Moonlight **1.21.1-3.6.1**, Iron’s Spells ’n Spellbooks **1.21.1-3.16.3**, Iron’s Lib **1.21.1-2.1.0**, Curios **9.5.1+1.21.1**, GeckoLib **4.9.2**, and Player Animator **2.0.4+1.21.1**. The isolated server reached startup, completed both pedestal gates, saved, and stopped; the client and its server also stopped after validation. Optional-mod logs include nonfatal upstream warnings/errors, so these results do not assert warning-free third-party logs.

Sound verification inspected real resolved client sounds and the mixer pitch path; it was not a subjective audio audition. Vanilla Allay assets multiply pitch by 1.25, so the configured event factor 0.5 resolves to 0.625, matching normal vanilla event playback. Failure choices are five equally weighted entries, editable in sounds.json.

Screenshots below were visually inspected. The complete spline curves through the native pedestals; both attached icons appear on their rim faces; the failed chain breaks into displaced particles. Temporal checks use actual client particles and rendered vertex opacity as well as server state.

- [Unit results](validation/refinements-1.6.0-tests.json)
- [GameTests](validation/refinements-1.6.0-gametests.log)
- [Native pedestal gate](validation/refinements-1.6.0-pedestal-compat.log)
- [Actual client checks](validation/refinements-1.6.0-client.log)
- [Continuous spline and orbit](validation/refinements-1.6.0-continuous-spline-orbit.png)
- [Native pedestal modifier icons](validation/refinements-1.6.0-native-pedestal-modifiers.png)
- [Failed chain collapse](validation/refinements-1.6.0-failure-collapse-4.png)

Earlier validation is retained in [0.5.4 validation](VALIDATION-0.5.4.md).

```powershell
.\gradlew.bat build runGameTestServer --console=plain
# Optional native gate: copy the listed local mod JARs to run-pedestal-compat/mods first.
.\gradlew.bat '-Pneo_version=21.1.248' runPedestalCompatServer --console=plain
# Real client fixture uses separate run-refinement-server/client directories and the same mods.
.\gradlew.bat '-Pneo_version=21.1.248' runRefinementServer --console=plain
.\gradlew.bat '-Pneo_version=21.1.248' runRefinementClient --console=plain
python tools/package_release.py
```

The opt-in fixtures replace blocks and inventory contents only in their development worlds. Fixture and GameTest classes are excluded from the production JAR.
