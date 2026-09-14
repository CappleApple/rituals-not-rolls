"""Reproducible authored pixel assets and default data. Python standard library only.
Run from the repository root; Minecraft's local development JAR supplies the vanilla enchantment list.
No vanilla artwork is copied or overwritten.
"""
from pathlib import Path
import json, struct, zlib, random, zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def data(path,value):
    path=RES/path; path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

class Pixels:
    def __init__(self,w,h,color=(0,0,0,0)):
        self.w,self.h=w,h; self.p=[list(color) for _ in range(w*h)]
    def rect(self,x,y,w,h,c):
        c=(*c,255) if len(c)==3 else c
        for yy in range(max(0,y),min(self.h,y+h)):
            for xx in range(max(0,x),min(self.w,x+w)): self.p[yy*self.w+xx]=list(c)
    def bevel(self,x,y,w,h,fill=(198,198,198),light=(255,255,255),dark=(85,85,85),border=(30,25,23)):
        self.rect(x,y,w,h,border); self.rect(x+1,y+1,w-2,h-2,fill)
        self.rect(x+1,y+1,w-3,1,light);self.rect(x+1,y+1,1,h-3,light)
        self.rect(x+2,y+h-2,w-3,1,dark);self.rect(x+w-2,y+2,1,h-3,dark)
    def png(self,path):
        path=RES/path;path.parent.mkdir(parents=True,exist_ok=True)
        raw=b''.join(b'\0'+bytes(sum(self.p[y*self.w:(y+1)*self.w],[])) for y in range(self.h))
        def chunk(tag,b):return struct.pack('>I',len(b))+tag+b+struct.pack('>I',zlib.crc32(tag+b)&0xffffffff)
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def sprite(name,kind='stone'):
    p=Pixels(24,24)
    if kind=='parchment':
        p.bevel(0,0,24,24,(213,196,153),(242,229,194),(147,120,76),(82,61,39))
        for x,y in [(4,5),(18,8),(7,18),(20,19)]:p.rect(x,y,1,1,(198,178,136))
    elif kind=='selected':p.bevel(0,0,24,24,(213,205,173),(255,239,170),(139,121,74),(86,67,29))
    elif kind=='consumed':p.bevel(0,0,24,24,(211,185,162),(245,223,199),(146,101,73),(98,53,42))
    elif kind=='slot':p.bevel(0,0,24,24,(139,139,139),(55,55,55),(255,255,255),(198,198,198))
    else:p.bevel(0,0,24,24)
    p.png(Path('assets/ritualsnotrolls/textures/gui/sprites')/(name+'.png'))
    data(Path('assets/ritualsnotrolls/textures/gui/sprites')/(name+'.png.mcmeta'),{'gui':{'scaling':{'type':'nine_slice','width':24,'height':24,'border':3}}})

for name,kind in {
    'ritual/background':'stone','ritual/knowledge_browser':'parchment','ritual/enchantment_row':'parchment',
    'ritual/selected_enchantment':'selected','ritual/material_row':'parchment','ritual/consumed_material':'consumed',
    'common/button':'stone','common/button_highlighted':'selected','common/button_disabled':'slot','common/search':'slot','book/background':'parchment','book/page_row':'parchment','book/tear':'stone','common/slot':'slot',
}.items():sprite(name,kind)
for name,color in [('power_empty',(68,57,52)),('power_full',(124,153,97))]:
    p=Pixels(16,5);p.bevel(0,0,16,5,color);p.png(Path('assets/ritualsnotrolls/textures/gui/sprites/ritual')/(name+'.png'))

# Worn paper and leather book silhouettes, with an intentionally quiet center for the runtime book inset.
# page_base.png is a supplied source texture; preserve it during resource generation.
for name,colors in [('consumption_catalyst',[(70,35,39),(154,55,44),(239,118,62),(255,220,137)]),('experience_catalyst',[(28,57,44),(48,121,88),(128,195,97),(224,241,143)]),('subtraction_catalyst',[(41,28,66),(82,49,137),(162,121,224),(229,208,255)])]:
    p=Pixels(16,16)
    for y in range(2,13):
        width=min(y-1,13-y,4);p.rect(7-width,y,width*2+1,1,colors[0]);
        if width>0:p.rect(8-width,y,max(1,width*2-1),1,colors[1])
    p.rect(6,4,2,6,colors[2]);p.rect(7,3,1,4,colors[3]);p.rect(4,12,8,2,(105,74,39));p.rect(5,12,6,1,(222,179,78));p.png(Path('assets/ritualsnotrolls/textures/item')/(name+'.png'))

p=Pixels(16,16);rng=random.Random(744)
for y in range(16):
    for x in range(16):
        gray=rng.choice([93,97,103,109,113]);p.rect(x,y,1,1,(gray,gray+2,gray+6))
p.rect(0,0,16,1,(137,139,143));p.rect(0,15,16,1,(54,56,60));p.rect(0,0,1,16,(131,133,137));p.rect(15,1,1,15,(58,60,65));p.png(Path('assets/ritualsnotrolls/textures/block/pedestal.png'))
p=Pixels(16,16,(66,56,72,255));p.bevel(0,0,16,16,(93,78,94),(155,135,128),(45,38,53),(56,46,58));
for x,y,w,h in [(4,4,8,1),(4,11,8,1),(4,4,1,8),(11,4,1,8),(7,6,2,4),(6,7,4,2)]:p.rect(x,y,w,h,(200,176,117))
p.png(Path('assets/ritualsnotrolls/textures/block/pedestal_top.png'))

display={'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,3,1],'scale':[.55,.55,.55]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[.68,.68,.68]}}
for name in ['knowledge_page','knowledge_book']:data(Path('assets/ritualsnotrolls/models/item')/(name+'.json'),{'parent':'builtin/entity','gui_light':'front','textures':{'particle':('ritualsnotrolls:item/page_base' if name=='knowledge_page' else 'minecraft:item/enchanted_book')},'display':display if name=='knowledge_page' else {}})
for name in ['consumption_catalyst','experience_catalyst','subtraction_catalyst']:data(Path('assets/ritualsnotrolls/models/item')/(name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'ritualsnotrolls:item/'+name}})
elements=[]
for lo,hi in [([2,0,2],[14,3,14]),([5,3,5],[11,12,11]),([1,12,1],[15,15,15])]:
    faces={face:{'texture':'#top' if face=='up' else '#side','uv':[0,0,16,16]} for face in ['up','down','north','south','east','west']}
    elements.append({'from':lo,'to':hi,'faces':faces})
data(Path('assets/ritualsnotrolls/models/block/pedestal.json'),{'parent':'minecraft:block/block','textures':{'side':'ritualsnotrolls:block/pedestal','top':'ritualsnotrolls:block/pedestal_top','particle':'ritualsnotrolls:block/pedestal'},'elements':elements})
data(Path('assets/ritualsnotrolls/models/item/pedestal.json'),{'parent':'ritualsnotrolls:block/pedestal'})
rotations={'up':(0,0),'down':(180,0),'north':(90,0),'south':(270,0),'east':(90,90),'west':(90,270)}
data(Path('assets/ritualsnotrolls/blockstates/pedestal.json'),{'variants':{'facing='+f:{'model':'ritualsnotrolls:block/pedestal','x':x,'y':y} for f,(x,y) in rotations.items()}})

sounds={'capture': {'subtitle': 'subtitles.ritualsnotrolls.capture', 'sounds': [{'name': 'minecraft:block.enchantment_table.use', 'type': 'event', 'volume': 0.8, 'pitch': 1.0}]}, 'knowledge': {'subtitle': 'subtitles.ritualsnotrolls.knowledge', 'sounds': [{'name': 'minecraft:item.book.page_turn', 'type': 'event', 'volume': 0.8, 'pitch': 1.0}]}, 'material': {'subtitle': 'subtitles.ritualsnotrolls.material', 'sounds': [{'name': 'minecraft:block.amethyst_block.resonate', 'type': 'event', 'volume': 0.8, 'pitch': 1.0}]}, 'consumption': {'subtitle': 'subtitles.ritualsnotrolls.consumption', 'sounds': [{'name': 'minecraft:entity.allay.item_taken', 'type': 'event', 'volume': 0.8, 'pitch': 0.5}]}, 'experience': {'subtitle': 'subtitles.ritualsnotrolls.experience', 'sounds': [{'name': 'minecraft:entity.experience_orb.pickup', 'type': 'event', 'volume': 0.8, 'pitch': 1.0}]}, 'complete': {'subtitle': 'subtitles.ritualsnotrolls.complete', 'sounds': [{'name': 'minecraft:block.beacon.power_select', 'type': 'event', 'volume': 0.4, 'pitch': 1.0}]}, 'disenchant': {'subtitle': 'subtitles.ritualsnotrolls.disenchant', 'sounds': [{'name': 'minecraft:block.beacon.deactivate', 'type': 'event', 'volume': 0.4, 'pitch': 0.6}]}, 'failure': {'subtitle': 'subtitles.ritualsnotrolls.failure', 'sounds': [{'name': 'minecraft:entity.firework_rocket.twinkle_far', 'type': 'event', 'volume': 0.6, 'pitch': 0.2}, {'name': 'minecraft:entity.firework_rocket.twinkle_far', 'type': 'event', 'volume': 0.6, 'pitch': 0.3}, {'name': 'minecraft:entity.firework_rocket.twinkle_far', 'type': 'event', 'volume': 0.6, 'pitch': 0.4}, {'name': 'minecraft:entity.firework_rocket.twinkle_far', 'type': 'event', 'volume': 0.6, 'pitch': 0.5}, {'name': 'minecraft:entity.firework_rocket.twinkle_far', 'type': 'event', 'volume': 0.6, 'pitch': 0.6}]}}
data(Path('assets/ritualsnotrolls/sounds.json'), sounds)
lang={'itemGroup.ritualsnotrolls':'Rituals Not Rolls','block.ritualsnotrolls.pedestal':'Ritual Pedestal','item.ritualsnotrolls.knowledge_page':'Knowledge Page','item.ritualsnotrolls.knowledge_book':'Book of Knowledge','item.ritualsnotrolls.knowledge_page.named':'Knowledge of %s','item.ritualsnotrolls.knowledge_book.named':'Book of Knowledge: %s','item.ritualsnotrolls.consumption_catalyst':'Consumption Catalyst','item.ritualsnotrolls.experience_catalyst':'Experience Catalyst','item.ritualsnotrolls.subtraction_catalyst':'Subtraction Catalyst','enchantment.ritualsnotrolls.arcane_assembly':'Arcane Assembly','container.ritualsnotrolls.ritual':'Ritual Enchanting','ritualsnotrolls.pages':'Pages: %s / %s','ritualsnotrolls.auto_add':'Auto-Add: %s','ritualsnotrolls.power':'%s Enchanting Power','ritualsnotrolls.unresolved':'Unresolved affinity (knowledge preserved)','ritualsnotrolls.tag_material':'%s (material group)','ritualsnotrolls.book_hint':'Use to read. Double-click in a container to gather pages.'}
lang.update({'subtitles.ritualsnotrolls.'+k:v for k,v in {'capture':'Ritual takes hold','knowledge':'Knowledge awakens','material':'Materials resonate','consumption':'Offering consumed','experience':'Experience channels','complete':'Ritual completes','disenchant':'Enchantment unravels','failure':'Ritual falters'}.items()})
lang.update({'ritualsnotrolls.migration.pending':'Ancient Book (awaiting ritual data)','ritualsnotrolls.migration.pending_hint':'Original data preserved. Converts when matching ritual data is available on load.'})
lang.update({'ritualsnotrolls.power': '%s Enchanting Power', 'ritualsnotrolls.potential_power': 'Potential: %s', 'ritualsnotrolls.power_tier.very_low': 'Weakest', 'ritualsnotrolls.power_tier.low': 'Weak', 'ritualsnotrolls.power_tier.medium': 'Average', 'ritualsnotrolls.power_tier.high': 'Strong', 'ritualsnotrolls.power_tier.very_high': 'Strongest'})
lang.update(json.loads((ROOT/'tools/guide_lang.json').read_text(encoding='utf-8')))
data(Path('assets/ritualsnotrolls/lang/en_us.json'),lang)

for domain,folder,name,values in [('c','item','hidden_from_recipe_viewers',['ritualsnotrolls:knowledge_page','ritualsnotrolls:knowledge_book']),('minecraft','item','bookshelf_books',['ritualsnotrolls:knowledge_book','ritualsnotrolls:knowledge_page']),('minecraft','block','mineable/pickaxe',['ritualsnotrolls:pedestal']),('ritualsnotrolls','block','enchanting_pedestals',['ritualsnotrolls:pedestal',{'id':'supplementaries:pedestal','required':False},{'id':'irons_spellbooks:pedestal','required':False}]),('ritualsnotrolls','block','knowledge_bookshelves',['minecraft:chiseled_bookshelf'])]:data(Path(f'data/{domain}/tags/{folder}/{name}.json'),{'replace':False,'values':values})
data(Path('data/ritualsnotrolls/loot_table/blocks/pedestal.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ritualsnotrolls:pedestal'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
data(Path('data/ritualsnotrolls/recipe/knowledge_book.json'),{'type':'ritualsnotrolls:knowledge_book','category':'misc'})
data(Path('data/ritualsnotrolls/recipe/enchanted_book_page.json'),{'type':'ritualsnotrolls:enchanted_book_page','category':'misc'})
recipes={
 'subtraction_catalyst':([' R ','GSG',' R '],{'R':'minecraft:redstone','G':'minecraft:gold_nugget','S':'minecraft:fermented_spider_eye'},'ritualsnotrolls:subtraction_catalyst',1),
 'pedestal':(['SSS',' C ','SSS'],{'S':'minecraft:stone_bricks','C':'minecraft:chiseled_stone_bricks'},'ritualsnotrolls:pedestal',2),
 'consumption_catalyst':([' R ','GAG',' R '],{'R':'minecraft:redstone','G':'minecraft:gold_nugget','A':'minecraft:amethyst_shard'},'ritualsnotrolls:consumption_catalyst',1),
 'experience_catalyst':([' L ','GEG',' L '],{'L':'minecraft:lapis_lazuli','G':'minecraft:gold_ingot','E':'minecraft:emerald'},'ritualsnotrolls:experience_catalyst',1)
}
for name,(pattern,key,result,count) in recipes.items():data(Path('data/ritualsnotrolls/recipe')/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'id':result,'count':count}})
data(Path('data/neoforge/loot_modifiers/global_loot_modifiers.json'),{'replace':False,'entries':['ritualsnotrolls:discovery']})
data(Path('data/ritualsnotrolls/loot_modifiers/discovery.json'),{'type':'ritualsnotrolls:discovery','conditions':[],'chance':0.0})
data(Path('data/ritualsnotrolls/enchantment/arcane_assembly.json'),{'description':{'translate':'enchantment.ritualsnotrolls.arcane_assembly'},'supported_items':['minecraft:enchanting_table'],'weight':1,'max_level':5,'min_cost':{'base':1,'per_level_above_first':10},'max_cost':{'base':30,'per_level_above_first':10},'anvil_cost':4,'slots':['any'],'effects':{}})

# Actual vanilla registry list; meaningful material palettes are specific to each enchantment.
palettes={'sharpness': 'flint iron_ingot diamond netherite_scrap quartz amethyst_shard', 'smite': 'bone rotten_flesh gold_ingot glowstone_dust ghast_tear', 'bane_of_arthropods': 'spider_eye fermented_spider_eye string honeycomb slime_ball', 'protection': 'iron_ingot copper_ingot diamond netherite_scrap turtle_scute', 'fire_protection': 'magma_cream blaze_powder polished_basalt nether_brick fire_charge', 'blast_protection': 'gunpowder tnt obsidian crying_obsidian end_crystal', 'projectile_protection': 'arrow leather chain armadillo_scute shield', 'feather_falling': 'feather phantom_membrane slime_ball rabbit_foot breeze_rod', 'respiration': 'kelp prismarine_shard nautilus_shell turtle_scute heart_of_the_sea', 'aqua_affinity': 'sponge wet_sponge sea_lantern conduit', 'thorns': 'cactus sweet_berries pointed_dripstone pufferfish wither_rose', 'depth_strider': 'prismarine_bricks water_bucket seagrass prismarine_shard turtle_egg', 'frost_walker': 'snowball ice packed_ice blue_ice diamond', 'binding_curse': 'string cobweb slime_ball chain crying_obsidian', 'soul_speed': 'soul_sand soul_soil ghast_tear echo_shard nether_star', 'swift_sneak': 'wool echo_shard sculk rabbit_foot phantom_membrane', 'knockback': 'piston slime_ball iron_ingot breeze_rod wind_charge', 'fire_aspect': 'blaze_powder blaze_rod magma_cream fire_charge netherite_scrap', 'looting': 'emerald gold_nugget rabbit_foot raw_gold_block nether_star glistering_melon_slice', 'sweeping_edge': 'bamboo shears iron_sword dried_kelp_block breeze_rod', 'efficiency': 'redstone copper_block diamond netherite_scrap repeater', 'silk_touch': 'string cobweb honeycomb slime_block amethyst_cluster', 'unbreaking': 'obsidian anvil diamond ancient_debris netherite_ingot', 'fortune': 'lapis_lazuli raw_gold raw_iron_block raw_copper_block emerald', 'power': 'flint arrow iron_ingot diamond blaze_rod', 'punch': 'piston slime_ball breeze_rod wind_charge iron_ingot', 'flame': 'blaze_powder fire_charge blaze_rod magma_cream', 'infinity': 'ender_pearl ender_eye chorus_fruit nether_star dragon_breath', 'luck_of_the_sea': 'prismarine_shard emerald nautilus_shell heart_of_the_sea rabbit_foot', 'lure': 'cod salmon tropical_fish pufferfish fishing_rod', 'loyalty': 'ender_pearl compass lead ender_eye echo_shard', 'impaling': 'pointed_dripstone prismarine_shard iron_nugget nautilus_shell', 'riptide': 'prismarine_crystals heart_of_the_sea breeze_rod wind_charge', 'channeling': 'copper_ingot amethyst_shard lightning_rod weathered_copper', 'multishot': 'arrow firework_star prismarine_crystals echo_shard', 'quick_charge': 'redstone tripwire_hook copper_ingot breeze_rod clock', 'piercing': 'flint iron_nugget pointed_dripstone netherite_scrap', 'mending': 'experience_bottle echo_shard glistering_melon_slice honey_bottle heart_of_the_sea', 'vanishing_curse': 'ender_pearl phantom_membrane echo_shard dragon_breath', 'density': 'iron_ingot gold_ingot obsidian heavy_core netherite_scrap', 'breach': 'flint iron_pickaxe diamond heavy_core netherite_scrap', 'wind_burst': 'feather breeze_rod wind_charge heavy_core nether_star', 'arcane_assembly': 'redstone amethyst_shard emerald ender_pearl nether_star diamond'}
groups={'protection':['protection','fire_protection','blast_protection','projectile_protection'],'damage':['sharpness','smite','bane_of_arthropods'],'boots':['depth_strider','frost_walker'],'bow':['infinity','mending'],'mining':['silk_touch','fortune'],'crossbow':['piercing','multishot'],'trident':['riptide','loyalty','channeling'],'mace':['density','breach']}
artifact=next((ROOT/'build/moddev/artifacts').glob('*-client-extra-aka-minecraft-resources.jar'))
with zipfile.ZipFile(artifact) as z:
    vanilla={Path(n).stem:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/minecraft/enchantment/') and n.endswith('.json')}
if not vanilla:raise SystemExit('Minecraft enchantment data missing from development JAR')
# Explicit affinity weights are enchantment-specific. Values follow each palette above.
weights={'sharpness': [7, 8, 30, 55, 44, 53], 'smite': [24, 12, 28, 42, 68], 'bane_of_arthropods': [26, 48, 12, 20, 34], 'protection': [22, 10, 40, 64, 36], 'fire_protection': [36, 18, 20, 12, 50], 'blast_protection': [22, 38, 48, 62, 90], 'projectile_protection': [14, 18, 28, 52, 46], 'feather_falling': [30, 62, 40, 54, 72], 'respiration': [12, 28, 48, 60, 90], 'aqua_affinity': [34, 42, 54, 82], 'thorns': [20, 10, 38, 52, 76], 'depth_strider': [24, 32, 12, 40, 72], 'frost_walker': [10, 24, 42, 72, 20], 'binding_curse': [18, 48, 30, 40, 64], 'soul_speed': [22, 34, 58, 78, 115], 'swift_sneak': [14, 85, 32, 42, 56], 'knockback': [42, 38, 24, 58, 76], 'fire_aspect': [22, 40, 28, 52, 46], 'looting': [40, 12, 56, 36, 108, 28], 'sweeping_edge': [10, 18, 34, 28, 66], 'efficiency': [24, 20, 44, 70, 32], 'silk_touch': [24, 46, 30, 38, 54], 'unbreaking': [24, 48, 45, 65, 100], 'fortune': [26, 38, 62, 36, 80], 'power': [12, 22, 16, 42, 54], 'punch': [46, 42, 64, 84, 20], 'flame': [28, 58, 46, 34], 'infinity': [36, 64, 22, 128, 92], 'luck_of_the_sea': [18, 34, 64, 110, 46], 'lure': [18, 26, 38, 56, 44], 'loyalty': [42, 28, 16, 72, 54], 'impaling': [40, 22, 12, 62], 'riptide': [32, 104, 48, 80], 'channeling': [26, 34, 68, 52], 'multishot': [20, 44, 28, 72], 'quick_charge': [38, 22, 18, 62, 40], 'piercing': [24, 12, 52, 82], 'mending': [96, 48, 24, 16, 72], 'vanishing_curse': [32, 46, 70, 96], 'density': [32, 50, 58, 110, 84], 'breach': [18, 36, 58, 96, 88], 'wind_burst': [22, 68, 92, 120, 138]}
# Every default has a distinct effect/color pair; colors are explicit and remain pack-editable.
effects={
 'sharpness':('end_rod','AFCFFF'),'smite':('end_rod','FFE89C'),'bane_of_arthropods':('spore_blossom_air','B5E86B'),
 'protection':('end_rod','94BCD6'),'fire_protection':('flame','FFC078'),'blast_protection':('poof','C4B4B0'),
 'projectile_protection':('crit','B8CCD1'),'feather_falling':('end_rod','FFF5DC'),'respiration':('end_rod','53C9E8'),
 'aqua_affinity':('end_rod','27C7C2'),'thorns':('crit','BD73C4'),'depth_strider':('end_rod','348DAA'),
 'frost_walker':('snowflake','C9F7FF'),'binding_curse':('end_rod','CE5E84'),'soul_speed':('soul_fire_flame','56F6E3'),
 'swift_sneak':('sculk_soul','247E91'),'knockback':('crit','EDBE6F'),'fire_aspect':('flame','FF7A22'),
 'looting':('end_rod','EDD65C'),'sweeping_edge':('end_rod','DAE5FF'),'efficiency':('electric_spark','E36A54'),
 'silk_touch':('end_rod','E2DAF2'),'unbreaking':('end_rod','8FE4FF'),'fortune':('end_rod','68D99E'),
 'power':('crit','EFA58D'),'punch':('poof','D6C0A0'),'flame':('flame','FFD340'),
 'infinity':('reverse_portal','C782FA'),'luck_of_the_sea':('end_rod','50DFB9'),'lure':('end_rod','75AFDC'),
 'loyalty':('end_rod','A592E8'),'impaling':('crit','67CED3'),'riptide':('cloud','A8E8EF'),
 'channeling':('electric_spark','F7F5A0'),'multishot':('crit','C898E7'),'quick_charge':('electric_spark','F2A358'),
 'piercing':('crit','BDCFEA'),'mending':('happy_villager','72F08C'),'vanishing_curse':('reverse_portal','925BBB'),
 'density':('poof','8C99B5'),'breach':('crit','F08E63'),'wind_burst':('cloud','D9FFE6')
}
assert set(weights)==set(effects)==set(vanilla)
assert len(set(effects.values()))==42
for name,meta in vanilla.items():
    items=palettes[name].replace(' scute',' turtle_scute').replace('wool','white_wool').split();materials=[]
    assert len(items)==len(weights[name]),name
    for item,power in zip(items,weights[name]):
        materials.append({'id':item,'item':'minecraft:'+item,'power':power,'resource_value':max(1,round(power/8,2))})
    if name=='sharpness':materials.append({'id':'amethyst_group','tag':'c:gems/amethyst','power':16,'resource_value':2})
    max_level=10 if meta['max_level']>1 else 1
    thresholds=[15,35,75,130,210,320,480,720,1080,1620]
    total=sum(weights[name])
    levels={str(i+1):round(total*thresholds[i]/thresholds[meta['max_level']-1],4) for i in range(max_level)}
    levels[str(meta['max_level'])]=total
    if name=='mending':levels={'1':256}
    effect,color=effects[name]
    data(Path('data/ritualsnotrolls/ritual_enchanting/enchantments')/(name+'.json'),{'enchantment':'minecraft:'+name,'materials':materials,'levels':levels,'conflict_groups':[group for group,members in groups.items() if name in members],'particle':'minecraft:'+effect,'particle_color':'#'+color})
print(f'Generated {len(vanilla)} enchantment definitions with individual powers/effects and authored pixel resources.')

# Keep the optional mod definitions in sync when regenerating all shipped resources.
from generate_compat_defaults import generate as generate_compat_defaults
generate_compat_defaults()
