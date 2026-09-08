"""Package the built mod, complete source, example packs, and SHA-256 checksums."""

from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
version = re.search(r"(?m)^mod_version=(.+)$", (ROOT / "gradle.properties").read_text()).group(1).strip()
name = f"ritualsnotrolls-{version}"
jar = ROOT / "build/libs" / f"{name}.jar"
if not jar.is_file():
    raise SystemExit("Build the mod first: gradlew.bat test build")

# Fail packaging if development fixtures or malformed resources leaked into the JAR.
with zipfile.ZipFile(jar) as archive:
    members = archive.namelist()
    assert not any("/gametest/" in member for member in members), "Development classes in artifact"
    assert not any(member.endswith("/structure/network.nbt") or member.endswith("/structure/empty.nbt") for member in members)
    definitions = [member for member in members if "/ritual_enchanting/enchantments/" in member and member.endswith(".json")]
    assert len(definitions) == 70, f"Expected 70 bundled definition/exclusion files, got {len(definitions)}"
    defaults = [json.loads(archive.read(member)) for member in definitions]
    active = [d for d in defaults if d.get('enabled', True)]
    disabled = {d['enchantment'] for d in defaults if not d.get('enabled', True)}
    assert len(active) == 68
    assert disabled == {'ritualsnotrolls:arcane_assembly', 'notenoughtrials:storm_front_marker'}
    assert len({(d['particle'], d['particle_color']) for d in active}) == 68
    optional = [d for d in active if d.get('optional', False)]
    manifest = json.loads((ROOT / 'tools/compat-defaults-manifest.json').read_text())
    assert {d['enchantment'] for d in optional} == {d['enchantment'] for d in manifest}
    for meta in manifest:
        d = next(d for d in optional if d['enchantment'] == meta['enchantment'])
        assert len({a['item'] for a in d['materials']}) == len(d['materials'])
        total = sum(a['power'] for a in d['materials'])
        assert total == meta['total_power'] == d['levels'][str(meta['native_maximum'])]
        assert all(total - a['power'] < total for a in d['materials'])
    by_id = {d['enchantment']: d for d in defaults}
    assert by_id['minecraft:mending']['levels']['1'] == 256
    iron = lambda name: next(a['power'] for a in by_id['minecraft:'+name]['materials'] if a['id'] == 'iron_ingot')
    assert iron('knockback') > iron('sharpness')
    assert 'assets/ritualsnotrolls/textures/item/subtraction_catalyst.png' in members
    assert 'data/ritualsnotrolls/recipe/subtraction_catalyst.json' in members
    assert archive.read('assets/ritualsnotrolls/textures/item/page_base.png') == (ROOT / 'src/main/resources/assets/ritualsnotrolls/textures/item/page_base.png').read_bytes()
    shelf_books = json.loads(archive.read('data/minecraft/tags/item/bookshelf_books.json'))['values']
    assert 'ritualsnotrolls:knowledge_page' in shelf_books and 'ritualsnotrolls:knowledge_book' in shelf_books
    states = json.loads(archive.read('assets/ritualsnotrolls/blockstates/pedestal.json'))['variants']
    assert set(states) == {'facing='+direction for direction in ['up','down','north','south','east','west']}
    for member in members:
        if member.endswith((".json", ".mcmeta")):
            json.loads(archive.read(member))
    sounds = json.loads(archive.read('assets/ritualsnotrolls/sounds.json'))
    assert set(sounds) == {'capture','knowledge','material','consumption','experience','complete','disenchant','failure'}
    assert all('volume' in event and 'pitch' in event for sound in sounds.values() for event in sound['sounds'])
    assert sounds['consumption']['sounds'][0]['name'] == 'minecraft:entity.allay.item_taken'
    assert sounds['consumption']['sounds'][0]['pitch'] == .5
    assert sounds['disenchant']['sounds'][0]['pitch'] == .6
    assert {s['pitch'] for s in sounds['failure']['sounds']} == {.2,.3,.4,.5,.6}
    pedestal_ids = json.loads(archive.read('data/ritualsnotrolls/tags/block/enchanting_pedestals.json'))['values']
    assert all({'id': id, 'required': False} in pedestal_ids for id in ['supplementaries:pedestal','irons_spellbooks:pedestal'])
    metadata = archive.read("META-INF/neoforge.mods.toml").decode()
    assert f'version="{version}"' in metadata and "${version}" not in metadata
    assert 'logoFile="logo.png"' in metadata and 'logoBlur=false' in metadata
    assert archive.read('logo.png') == (ROOT / 'src/main/resources/logo.png').read_bytes()
    mixins = json.loads(archive.read("ritualsnotrolls.mixins.json"))
    for mixin in mixins.get("mixins", []) + mixins.get("client", []):
        path = (mixins["package"] + "." + mixin).replace(".", "/") + ".class"
        assert path in members, f"Missing mixin class: {path}"

DIST.mkdir(exist_ok=True)
subprocess.run([sys.executable, str(ROOT / "tools/package_examples.py")], check=True)
shutil.copy2(jar, DIST / jar.name)

root_files = [
    ".gitignore", ".gitattributes", "build.gradle", "gradle.properties", "settings.gradle",
    "gradlew", "gradlew.bat", "LICENSE", "README.md", "SPECIFICATION.md", "CHANGELOG.md",
]
source_files = [ROOT / path for path in root_files if (ROOT / path).is_file()]
for directory in ["src", "gradle", "tools", "docs", "examples"]:
    source_files.extend(path for path in (ROOT / directory).rglob("*") if path.is_file() and "__pycache__" not in path.parts)
source_zip = DIST / f"{name}-source.zip"
with zipfile.ZipFile(source_zip, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source_files):
        archive.write(path, f"{name}/" + path.relative_to(ROOT).as_posix())

outputs = [DIST / jar.name, source_zip, DIST / "ritualsnotrolls-example-datapack.zip", DIST / "ritualsnotrolls-example-resourcepack.zip"]
for pack in outputs[2:]:
    with zipfile.ZipFile(pack) as archive:
        assert "pack.mcmeta" in archive.namelist(), "Pack metadata must be at ZIP root"
        assert archive.testzip() is None
hashes = "".join(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name + "\n" for path in outputs)
(DIST / "SHA256SUMS.txt").write_text(hashes, encoding="utf-8")
print(f"Verified 68 definitions, 2 exclusions, and production-only classes in {jar.name}.")
for path in outputs:
    print(f"{path.name}: {path.stat().st_size:,} bytes")
print(hashes)
