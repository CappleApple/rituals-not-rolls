"""Built-in optional affinities, verified against the user's installed NeoForge 1.21.1 mods.

Each complete distinct material set reaches the owning mod's native maximum at rating 10.
This generator authors ritual data only; it never registers or replaces another mod's enchantments.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
# enchantment, native max, particle, color, conflict groups, (item, power) pairs
DEFAULTS = [
    ('aggrofix:aggrobaiting', 5, 'trial_spawner_detection', 'E9AC64', [], [('carrot', 30), ('rotten_flesh', 15), ('salmon', 25), ('spider_eye', 35), ('honey_bottle', 45), ('golden_apple', 70)]),
    ('aggrofix:aggronizing', 5, 'angry_villager', 'DB4C3F', [], [('bell', 52), ('redstone', 18), ('magma_cream', 34), ('target', 42), ('goat_horn', 74)]),
    ('botanypots:idle_hands', 1, 'witch', '89BE62', [], [('botanypots:terracotta_botany_pot', 32), ('botanypots:terracotta_hopper_botany_pot', 80), ('composter', 24), ('bone_meal', 16), ('moss_block', 32)]),
    ('combat_roll:acrobat', 5, 'cloud', 'F2D5A2', [], [('string', 10), ('rabbit_hide', 22), ('leather', 26), ('scaffolding', 32), ('slime_ball', 50)]),
    ('combat_roll:longfooted', 4, 'small_gust', 'B9E996', [], [('rabbit_foot', 24), ('bamboo', 12), ('snow_block', 20), ('honeycomb', 32), ('phantom_membrane', 72)]),
    ('combat_roll:multi_roll', 3, 'gust', 'BFA5F6', [], [('wind_charge', 32), ('chorus_fruit', 36), ('popped_chorus_fruit', 32), ('ender_pearl', 52), ('echo_shard', 88)]),
    ('create:capacity', 3, 'campfire_cosy_smoke', 'CDA477', [], [('create:copper_sheet', 24), ('create:fluid_tank', 54), ('create:propeller', 32), ('create:brass_ingot', 28), ('create:precision_mechanism', 86)]),
    ('create:potato_recovery', 3, 'composter', 'D7CB79', [], [('potato', 8), ('create:brass_hand', 46), ('create:andesite_alloy', 26), ('wheat_seeds', 16), ('dried_kelp', 14), ('composter', 30)]),
    ('critical_strike:chance', 5, 'enchanted_hit', 'FFCE67', ['critical_strike:critical'], [('amethyst_shard', 16), ('rabbit_foot', 28), ('golden_carrot', 40), ('quartz', 24), ('emerald', 72)]),
    ('critical_strike:damage', 5, 'crit', 'FF8996', ['critical_strike:critical'], [('flint', 12), ('iron_block', 44), ('fire_charge', 26), ('blaze_rod', 52), ('netherite_scrap', 106)]),
    ('dungeons_arise:discharge', 3, 'electric_spark', '7AD8F1', [], [('copper_ingot', 18), ('lightning_rod', 36), ('redstone_block', 48), ('glowstone_dust', 28), ('trident', 110)]),
    ('dungeons_arise:ensnaring', 4, 'item_cobweb', '96B877', [], [('cobweb', 32), ('vine', 12), ('lead', 24), ('sweet_berries', 18), ('string', 8), ('spider_eye', 46)]),
    ('dungeons_arise:lolths_curse', 1, 'infested', 'E9B9FF', [], [('spider_eye', 40), ('fermented_spider_eye', 56), ('cobweb', 32), ('poisonous_potato', 24), ('wither_rose', 88)]),
    ('dungeons_arise:purification', 3, 'instant_effect', 'F6F0C7', [], [('milk_bucket', 28), ('honey_bottle', 24), ('lily_of_the_valley', 32), ('ghast_tear', 68), ('golden_apple', 88)]),
    ('dungeons_arise:voltaic_shot', 1, 'firework', 'A4AEFF', [], [('lightning_rod', 36), ('copper_block', 48), ('glowstone', 36), ('firework_star', 40), ('end_rod', 80)]),
    ('farmersdelight:backstabbing', 3, 'sweep_attack', 'AD7958', [], [('farmersdelight:flint_knife', 20), ('farmersdelight:straw', 12), ('farmersdelight:canvas', 32), ('farmersdelight:rope', 24), ('fermented_spider_eye', 72)]),
    ('gouge:grip', 3, 'scrape', 'C99356', [], [('lead', 24), ('leather', 16), ('honeycomb', 32), ('slime_block', 72), ('tripwire_hook', 16)]),
    ('gouge:momentum', 3, 'poof', 'E6AA87', [], [('piston', 32), ('iron_ingot', 12), ('activator_rail', 28), ('powered_rail', 48), ('minecart', 60)]),
    ('minecraft:soul_fire_aspect', 2, 'soul_fire_flame', '44D2D9', [], [('soul_soil', 24), ('soul_campfire', 36), ('soul_lantern', 44), ('sculk', 64), ('echo_shard', 88)]),
    ('minecraft:soul_flame', 1, 'soul', 'D3FCFF', [], [('soul_sand', 28), ('soul_torch', 16), ('fire_charge', 36), ('crying_obsidian', 68), ('ghast_tear', 76)]),
    ('notenoughtrials:daredevil', 1, 'explosion', 'ED9866', [], [('heavy_core', 160), ('breeze_rod', 48), ('ominous_trial_key', 80), ('wind_charge', 32), ('phantom_membrane', 64)]),
    ('notenoughtrials:equity', 1, 'small_flame', 'E4B987', [], [('iron_axe', 24), ('amethyst_shard', 20), ('gold_ingot', 28), ('diamond', 64), ('trial_key', 56)]),
    ('notenoughtrials:storm_front', 1, 'gust', '87BECF', ['trident'], [('breeze_rod', 48), ('nautilus_shell', 36), ('wind_charge', 24), ('lightning_rod', 28), ('heart_of_the_sea', 120)]),
    ('passablefoliage:leaf_walker', 1, 'cherry_leaves', 'FFFFFF', [], [('azalea_leaves', 24), ('moss_block', 28), ('big_dripleaf', 36), ('flowering_azalea', 32), ('spore_blossom', 72)]),
    ('supplementaries:stasis', 1, 'bubble_pop', 'ABE2ED', ['crossbow'], [('supplementaries:bubble_blower', 56), ('supplementaries:soap', 24), ('supplementaries:hourglass', 64), ('ender_pearl', 48), ('blue_ice', 32)]),
    ('veinmining:vein_mining', 1, 'dust_plume', '86B4AA', [], [('raw_iron_block', 36), ('raw_gold_block', 44), ('raw_copper_block', 28), ('diamond_pickaxe', 96), ('sculk_sensor', 52)]),
]
DISABLED = {
    'notenoughtrials:storm_front_marker': 'Internal function marker used by Storm Front; not a player enchantment.',
    'ritualsnotrolls:arcane_assembly': 'Legacy table-upgrade storage retained for old saves; current rituals have no upgrade requirement or Assembly gameplay benefit.',
}
CURVE = [15, 35, 75, 130, 210, 320, 480]

def output(identifier, value):
    namespace, name = identifier.split(':')
    path = ROOT / f'src/main/resources/data/{namespace}/ritual_enchanting/enchantments/{name}.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def generate():
    catalog = ['# Optional mod defaults', '',
        'These definitions activate only when their exact enchantment ID exists in the loaded registry. They do not add dependencies or register enchantments. Soul Fire\'d uses the `minecraft` namespace; its optional enchantment pack must be enabled.', '',
        'At enchantability 10, with no modifiers, sharing, or return bonus, the complete distinct set reaches the owning mod\'s native maximum. Omitting any material falls short. Multi-level enchantments also include one above-native threshold, reachable through bonuses. This balance applies only to these defaults.', '',
        '| Enchantment | Native maximum | Power at maximum | Materials (power) | Effect / color |',
        '| --- | ---: | ---: | --- | --- |']
    manifest = []
    for identifier, maximum, particle, color, groups, materials in DEFAULTS:
        materials = [(item if ':' in item else 'minecraft:' + item, power) for item, power in materials]
        assert len({item for item, _ in materials}) == len(materials)
        total = sum(power for _, power in materials)
        count = maximum + 1 if maximum > 1 else 1
        levels = {str(i + 1): round(total * CURVE[i] / CURVE[maximum - 1], 4) for i in range(count)}
        levels[str(maximum)] = total
        definition = {'enchantment': identifier, 'optional': True,
            'materials': [{'id': item.replace(':', '/'), 'item': item, 'power': power, 'resource_value': max(1, round(power / 8, 2))} for item, power in materials],
            'levels': levels, 'conflict_groups': groups,
            'particle': 'minecraft:' + particle, 'particle_color': '#' + color}
        output(identifier, definition)
        manifest.append({'enchantment': identifier, 'native_maximum': maximum, 'total_power': total})
        palette = ', '.join(f'`{item}` ({power})' for item, power in materials)
        catalog.append(f'| `{identifier}` | {maximum} | {total} | {palette} | `{particle}` / `#{color}` |')
    catalog += ['', '## Explicit exclusions', '',
        'These IDs have `enabled: false` files, so they do not generate pages, migrate ancient books, or participate in rituals. They are considered explicitly configured by `/ritual missing`. A custom datapack can replace the exclusion with a complete enabled ritual definition.', '']
    for identifier, reason in DISABLED.items():
        output(identifier, {'enchantment': identifier, 'enabled': False, 'disabled_reason': reason})
        catalog.append(f'- `{identifier}`: {reason}')
    (ROOT / 'docs/MOD_DEFAULTS.md').write_text('\n'.join(catalog) + '\n', encoding='utf-8')
    (ROOT / 'tools/compat-defaults-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'Generated {len(DEFAULTS)} optional definitions and {len(DISABLED)} explicit exclusions.')

if __name__ == '__main__':
    generate()
