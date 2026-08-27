"""Advanced vanilla crops: wheat / carrot / potato / beetroot on rich soil farmland.

In the mod, mixins make any vanilla crop plantable on rich soil farmland (which is a
real FarmlandBlock). On CraftEngine the equivalent trick - taken from the reference
pack - is redirecting the vanilla seed items to these blocks: placement on rich soil
farmland succeeds through the farmersdelight:crop behavior's canSurvive, while any
other ground fails the CE block_item and falls back to vanilla planting.
Appearances reuse the vanilla crop block states directly, so no custom block state
budget is consumed. Growth math runs in the plugin's FDCropBlockBehavior
(vanilla CropBlock formula); harvest drops + knife straw are handled by the plugin.
"""

from .common import NS, write_config

# (block id, visual vanilla block, redirect item, max age, beetroot-like slow ticks)
CROPS = [
    ("advanced_wheat", "wheat", "wheat_seeds", 7, False),
    ("advanced_carrots", "carrots", "carrot", 7, False),
    ("advanced_potatoes", "potatoes", "potato", 7, False),
    ("advanced_beetroots", "beetroots", "beetroot_seeds", 3, True),
]


def generate_advanced_crops() -> tuple[int, int]:
    item_lines = []
    block_lines = []
    for bid, visual, seed, max_age, slow in CROPS:
        item_lines.append(
            f'  minecraft:{seed}:\n'
            f'    behavior:\n'
            f'      type: block_item\n'
            f'      block: {NS}:{bid}'
        )
        behavior = (
            f'    behavior:\n'
            f'      - type: {NS}:crop\n'
            f'        age: age\n'
            f'        soils:\n'
            f'          - block: minecraft:farmland\n'
            f'          - block: {NS}:rich_soil_farmland'
        )
        if slow:
            behavior += '\n        slow_random_ticks: true'
        appearances = "\n".join(
            f'        s{i}: {{ state: "minecraft:{visual}[age={i}]" }}' for i in range(max_age + 1)
        )
        variants = "\n".join(
            f'        "age={i}": {{ appearance: s{i} }}' for i in range(max_age + 1)
        )
        block_lines.append(
            f'  "{NS}:{bid}":\n'
            f'    settings:\n'
            f'      hardness: 0\n'
            f'      resistance: 0\n'
            f'      is_suffocating: false\n'
            f'      is_redstone_conductor: false\n'
            f'      sounds:\n'
            f'        break: minecraft:block.grass.break\n'
            f'        step: minecraft:block.grass.step\n'
            f'        place: minecraft:item.crop.plant\n'
            f'        hit: minecraft:block.grass.hit\n'
            f'        fall: minecraft:block.grass.fall\n'
            f'      item: minecraft:{seed}\n'
            f'{behavior}\n'
            f'    states:\n'
            f'      properties:\n'
            f'        age:\n'
            f'          type: int\n'
            f'          default: 0\n'
            f'          range: 0~{max_age}\n'
            f'      appearances:\n'
            f'{appearances}\n'
            f'      variants:\n'
            f'{variants}'
        )
    write_config("crops_vanilla.yml", [
        ("items", "\n\n".join(item_lines)),
        ("blocks", "\n\n".join(block_lines)),
    ])
    return len(item_lines), len(block_lines)
