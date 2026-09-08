# Validation — 0.5.3

Completed **2026-09-08**, Minecraft 1.21.1 / NeoForge 21.1.244, Java 21.

- Gradle build passed; 41 JUnit tests passed with zero failures, errors, or skips.
- Verified `block.amethyst_block.chime` exists in the local Minecraft sound registry source.
- Completion references that vanilla event with a volume multiplier of 0.5; the other five phase sounds are unchanged.
- The sound generator produces the same sound definitions as the packaged resource.
- Compared with 0.5.2, the only changed JAR entries are `sounds.json` and version metadata. Gameplay classes, textures, and datapack definitions are identical.

No new in-game audio audition or GameTest run was performed for this resource-only change. Previous gameplay and client checks are retained in [0.5.2 validation](VALIDATION-0.5.2.md).

```powershell
.\gradlew.bat build --console=plain
python tools/package_release.py
```
