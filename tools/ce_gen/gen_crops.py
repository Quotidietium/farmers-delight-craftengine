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
    ("advanced_torchflower_crop", "torchflower_crop", "torchflower_seeds", 2, True),
    ("advanced_pitcher_crop", "pitcher_crop", "pitcher_pod", 4, False),
]
# torchflower & pitcher bone-meal with a fixed +1 (vanilla subclass overrides)
FIXED_MEAL = {"advanced_torchflower_crop", "advanced_pitcher_crop"}
# torchflower age=max displays the flower block itself (vanilla getStateForAge)
FLOWER_TOP = {"advanced_torchflower_crop": "minecraft:torchflower"}

# stems grow and place their fruit through CE's built-in stem_block; the fruit may
# land on rich soil (fruit_bottom_blocks), mirroring the reference pack's coverage
STEMS = [
    ("advanced_pumpkin_stem", "pumpkin_stem", "attached_advanced_pumpkin_stem",
     "attached_pumpkin_stem", "minecraft:pumpkin", "pumpkin_seeds"),
    ("advanced_melon_stem", "melon_stem", "attached_advanced_melon_stem",
     "attached_melon_stem", "minecraft:melon", "melon_seeds"),
]

FRUIT_BOTTOM_BLOCKS = [
    "minecraft:farmland", "minecraft:dirt", "minecraft:grass_block",
    "minecraft:coarse_dirt", "minecraft:mycelium", "minecraft:podzol",
    "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:pale_moss_block",
    "minecraft:mud", "minecraft:muddy_mangrove_roots",
    f"{NS}:rich_soil_farmland", f"{NS}:rich_soil",
]


def generate_stems() -> tuple[int, int, int]:
    """Returns (seed redirects, stem blocks, attached stem blocks)."""
    item_lines = []
    stem_lines = []
    attached_lines = 0

    def block_header(bid):
        return (
            f'  "{NS}:{bid}":\n'
            f'    settings:\n'
            f'      hardness: 0\n'
            f'      resistance: 0\n'
            f'      is_suffocating: false\n'
            f'      is_redstone_conductor: false\n'
            f'      sounds:\n'
            f'        break: minecraft:block.wood.break\n'
            f'        step: minecraft:block.hemp.step\n'
            f'        place: minecraft:item.crop.plant\n'
            f'        hit: minecraft:block.wood.hit\n'
            f'        fall: minecraft:block.wood.fall\n'
        )

    for bid, visual, attached, attached_visual, fruit, seed in STEMS:
        item_lines.append(
            f'  minecraft:{seed}:\n'
            f'    behavior:\n'
            f'      type: block_item\n'
            f'      block: {NS}:{bid}'
        )
        bottoms = "".join(f'\n          - {b}' for b in FRUIT_BOTTOM_BLOCKS)
        stem_lines.append(
            block_header(bid)
            + f'      item: minecraft:{seed}\n'
            f'    behavior:\n'
            f'      - type: stem_block\n'
            f'        fruit: {fruit}\n'
            f'        attached_stem: {NS}:{attached}\n'
            f'        fruit_bottom_blocks:{bottoms}\n'
            f'    states:\n'
            f'      properties:\n'
            f'        age:\n'
            f'          type: int\n'
            f'          default: 0\n'
            f'          range: 0~7\n'
            + "      appearances:\n"
            + "\n".join(f'        s{i}: {{ state: "minecraft:{visual}[age={i}]" }}' for i in range(8))
            + "\n      variants:\n"
            + "\n".join(f'        "age={i}": {{ appearance: s{i} }}' for i in range(8))
        )
        faces = ["north", "south", "east", "west"]
        attached_lines += 1
        stem_lines.append(
            block_header(attached)
            + f'      item: minecraft:{seed}\n'
            f'    states:\n'
            f'      properties:\n'
            f'        facing:\n'
            f'          type: horizontal_direction\n'
            f'          default: north\n'
            f'      appearances:\n'
            + "\n".join(
                f'        f{i}: {{ state: "minecraft:{attached_visual}[facing={f}]" }}'
                for i, f in enumerate(faces))
            + "\n      variants:\n"
            + "\n".join(
                f'        "facing={f}": {{ appearance: f{i} }}'
                for i, f in enumerate(faces))
        )

    write_config("crops_stems.yml", [
        ("items", "\n\n".join(item_lines)),
        ("blocks", "\n\n".join(stem_lines)),
    ])
    return len(item_lines), len(STEMS), len(STEMS)


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
        if bid in FIXED_MEAL:
            behavior += '\n        bone_meal_bonus: "1-1"'
        if bid in FLOWER_TOP:
            # torchflower's final age displays the flower block (vanilla getStateForAge)
            appearances = "\n".join(
                [f'        s{i}: {{ state: "minecraft:{visual}[age={i}]" }}' for i in range(max_age)]
                + [f'        s{max_age}: {{ state: "{FLOWER_TOP[bid]}" }}']
            )
        else:
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
