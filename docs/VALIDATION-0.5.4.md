# Validation — 0.5.4

Completed **2026-09-08**, Windows 11, Java 21.0.12, Minecraft 1.21.1, NeoForge 21.1.244. Runtime checks used the disposable development worlds.

| Gate | Result |
| --- | --- |
| Build and JUnit | **46 tests passed**, zero failures/errors/skips |
| GameTests | **109 required tests passed** |
| Single particle paths | Repeated server emissions share one curve and entrance per hop; cubic paths are finite, curved, reversible, and tangent to all nine orbital planes |
| Ring timing and spacing | Sequential channels wait for the ring to fill; fractional particle spacing remains on one curve with synchronized finishing pulse |
| Power priority | Higher unsplit power wins when sharing cannot upgrade both; modifier power, equal-power tie-breaking, subtraction magnitude, and successful sharing checked |
| Hidden pages | Component-bearing pages resolve the common hidden-item tag in the actual server registry; books remain visible |
| Recipe-viewer integration | Installed JEI 19.51.0.418 and EMI 1.1.24 implementations both filter the common hidden-item tag; no viewer dependency added |
| Actual client | Curved flights, persistent layers, sequential arrivals, stable item float, pulse, fading burst, correct XP debit and final enchantments passed |
| Actual disenchantment | Sharpness V became III; the released item had normal gravity while reverse particles continued; no completion burst was observed |
| Mixed completion | GameTest confirms a ritual that removes one enchantment and adds another still emits all 96 burst particles |
| Server lifecycle | Dedicated server started, served the client, saved, and stopped normally |
| Resources | Requested beacon sound exists in the local registry; generated sound, tier translations, and hidden-page tag match the packaged resources |

The beacon completion event keeps the previous half-volume multiplier. Five presentation tiers are Weakest, Weak, Average, Strong, and Strongest; existing percentage thresholds and translation keys are retained. Default enchantment definitions and textures are byte-identical to 1.5.3.

Reverse effects drain downstream after successful completion without holding the target or reserving the table. Each last particle keeps its normal travel time. The trailing state contains only visual routes and timing; no player/item references, resource spending, or network rescans. Failed rituals do not schedule a tail.

The live client captures below were visually inspected. JEI/EMI integration was checked through the loaded common item tag and the installed viewer implementations; their actual search windows were not exercised. The sound resource was verified without an audio audition. The client and server were stopped cleanly after validation.

- [Unit results](validation/refinements-1.5.4-tests.json)
- [GameTests](validation/refinements-1.5.4-gametests.log)
- [Actual client checks](validation/refinements-1.5.4-client.log)
- [Layered single-path flows](validation/refinements-1.5.4-automatic-ritual-layered.png)
- [Disenchantment after item release](validation/refinements-1.5.4-disenchantment-draining.png)

Previous validation is retained in [0.5.3 validation](VALIDATION-0.5.3.md).

```powershell
.\gradlew.bat build runGameTestServer --console=plain
.\gradlew.bat runSmokeServer --console=plain
# In another terminal after the local server is ready:
.\gradlew.bat runClientSmoke --console=plain
python tools/rcon.py stop
python tools/package_release.py
```
