"""Block classification tables shared between generators."""
from .parse_registry import parse_blocks

# blocks realized as CraftEngine furniture (custom hitboxes / thin plates)
FURNITURE_BLOCK_IDS = {
    "cooking_pot", "cutting_board", "skillet", "rope", "safety_net", "canvas_rug",
    "tatami", "full_tatami_mat", "half_tatami_mat",
    "roast_chicken_block", "stuffed_pumpkin_block", "honey_glazed_ham_block",
    "shepherds_pie_block", "rice_roll_medley_block",
}

def _sign_block_ids():
    colors = ["", "white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_",
              "pink_", "gray_", "light_gray_", "cyan_", "purple_", "blue_", "brown_",
              "green_", "red_", "black_"]
    ids = set()
    for c in colors:
        ids.add(c + "canvas_sign")
        ids.add(c + "canvas_wall_sign")
    return ids

SIGN_BLOCK_IDS = _sign_block_ids()

# plant-like CE blocks (no collision auto_state)
PLANT_BLOCKS = {
    "cabbages", "onions", "budding_tomatoes", "tomatoes", "rice", "rice_panicle",
    "sandy_shrub", "wild_cabbages", "wild_onions", "wild_tomatoes", "wild_carrots",
    "wild_potatoes", "wild_beetroots", "wild_rice",
    "brown_mushroom_colony", "red_mushroom_colony",
}

# distribute plant appearances across several no-collision auto_state groups
# (capacities: kelp 26, twisting_vines 26, weeping_vines 26, cave_vines 52)
PLANT_GROUP = {
    "rice": "kelp", "rice_panicle": "kelp", "cabbages": "kelp", "onions": "kelp",
    "tomatoes": "twisting_vines", "budding_tomatoes": "twisting_vines",
    "wild_rice": "cave_vines",
    "wild_cabbages": "weeping_vines", "wild_onions": "weeping_vines",
    "wild_tomatoes": "weeping_vines", "wild_carrots": "weeping_vines",
    "wild_potatoes": "weeping_vines", "wild_beetroots": "weeping_vines",
    "sandy_shrub": "weeping_vines",
    "brown_mushroom_colony": "weeping_vines", "red_mushroom_colony": "weeping_vines",
}

# blocks whose drops are fully plugin-controlled (no CE loot)
PLUGIN_LOOT_BLOCKS = PLANT_BLOCKS | {
    "apple_pie", "sweet_berry_cheesecake", "chocolate_pie",
    "organic_compost",
}

# enum name -> block id mapping from BlocksRegistry
BLOCK_ID_BY_ENUM: dict[str, str] = {}
BLOCK_ENUM_BY_ID: dict[str, str] = {}
for _b in parse_blocks():
    BLOCK_ID_BY_ENUM[_b["enum"]] = _b["id"]
    BLOCK_ENUM_BY_ID[_b["id"]] = _b["enum"]

CE_BLOCK_IDS = [bid for bid in BLOCK_ID_BY_ENUM.values()
                if bid not in FURNITURE_BLOCK_IDS and bid not in SIGN_BLOCK_IDS]

# per-family settings: hardness, sounds, tool
FAMILY_SETTINGS = {
    "wood": dict(hardness=2.0, sounds="minecraft:block.wood"),
    "dirt": dict(hardness=0.6, sounds="minecraft:block.gravel"),
    "stone": dict(hardness=2.0, sounds="minecraft:block.stone"),
    "plant": dict(hardness=0.0, sounds="minecraft:block.grass"),
    "wool": dict(hardness=0.8, sounds="minecraft:block.wool"),
}

BLOCK_FAMILY = {
    "stove": "stone", "basket": "wood",
    "carrot_crate": "wood", "potato_crate": "wood", "beetroot_crate": "wood",
    "cabbage_crate": "wood", "tomato_crate": "wood", "onion_crate": "wood",
    "rice_bale": "wood", "rice_bag": "wool", "straw_bale": "wood",
    "rich_soil": "dirt", "rich_soil_farmland": "dirt", "organic_compost": "dirt",
    "apple_pie": "plant", "sweet_berry_cheesecake": "plant", "chocolate_pie": "plant",
}
for _pid in PLANT_BLOCKS:
    BLOCK_FAMILY[_pid] = "plant"
for _wood in ("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove",
              "crimson", "warped"):
    BLOCK_FAMILY[f"{_wood}_cabinet"] = "wood"
