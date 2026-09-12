"""Build small installable packs using the shipped schemas and preserve stable affinity IDs."""
from pathlib import Path
import json, shutil, zipfile
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
datapack=ROOT/'examples/datapack';resources=ROOT/'examples/resourcepack'
write(datapack/'pack.mcmeta',{'pack':{'pack_format':48,'description':'Rituals Not Rolls: example Sharpness threshold override'}})
definition=json.loads((RES/'data/ritualsnotrolls/ritual_enchanting/enchantments/sharpness.json').read_text())
original_six=definition['levels']['6']
definition['levels']['6']=300
write(datapack/'data/ritualsnotrolls/ritual_enchanting/enchantments/sharpness.json',definition)
write(resources/'pack.mcmeta',{'pack':{'pack_format':34,'description':'Rituals Not Rolls: example description, GUI and sound overrides'}})
write(resources/'assets/ritualsnotrolls/lang/en_us.json', {'enchantment.minecraft.sharpness.desc': 'Adds melee damage to each hit.'})
sounds=json.loads((RES/'assets/ritualsnotrolls/sounds.json').read_text(encoding='utf-8'))
for event in sounds.values():event['replace']=True
write(resources/'assets/ritualsnotrolls/sounds.json',sounds)
for name in ['ritual/selected_enchantment.png','ritual/selected_enchantment.png.mcmeta']:
    target=resources/'assets/ritualsnotrolls/textures/gui/sprites'/name;target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(RES/'assets/ritualsnotrolls/textures/gui/sprites'/name,target)
(datapack/'README.txt').write_text(f'Install this folder/ZIP in world/datapacks. Sharpness VI costs 300 power instead of {original_six}. Existing affinity IDs are preserved. Run /reload.\n')
(resources/'README.txt').write_text('Install this folder/ZIP in resourcepacks. Edit assets/ritualsnotrolls/lang/en_us.json for the standard enchantment.minecraft.sharpness.desc description, also read by Enchantment Descriptions. This includes editable ritual sound events (assets/ritualsnotrolls/sounds.json) with explicit volume/pitch multipliers and a selected-enchantment sprite. Edit the sound entries, or the PNG and matching nine-slice metadata, then use F3+T. Failure pitch choices are separate equally weighted entries. No vanilla texture is overwritten.\n')
dist=ROOT/'dist';dist.mkdir(exist_ok=True)
for name,folder in [('ritualsnotrolls-example-datapack.zip',datapack),('ritualsnotrolls-example-resourcepack.zip',resources)]:
    with zipfile.ZipFile(dist/name,'w',zipfile.ZIP_DEFLATED) as z:
        for path in sorted(folder.rglob('*')):
            if path.is_file():z.write(path,path.relative_to(folder).as_posix())
print('Packaged both installable example packs.')
