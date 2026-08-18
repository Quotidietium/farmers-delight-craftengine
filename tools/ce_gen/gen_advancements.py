"""Generate the bundled advancements datapack + plugin trigger config from mod data.

Vanilla item predicates cannot reference CraftEngine items, so every advancement is
emitted with a single impossible criterion; the plugin awards them programmatically
(see AdvancementListener). Icons are remapped to the CE base material of FD items.
"""
import json
from pathlib import Path

from .common import MOD_DATA, PLUGIN, NS, load_json

ADV_DIR = MOD_DATA / NS / "advancements" / "main"
DATA_PACK = PLUGIN / "src" / "main" / "resources" / "datapack"

# FD item id -> vanilla icon fallback (base material used for the CE item)
ICON_FALLBACK = {
    "cooking_pot": "minecraft:mushroom_stew", "flint_knife": "minecraft:flint",
    "iron_knife": "minecraft:iron_nugget", "golden_knife": "minecraft:gold_nugget",
    "diamond_knife": "minecraft:diamond", "netherite_knife": "minecraft:netherite_scrap",
    "skillet": "minecraft:iron_hoe", "organic_compost": "minecraft:coarse_dirt",
    "rich_soil": "minecraft:rooted_dirt", "rice": "minecraft:wheat_seeds",
    "straw": "minecraft:wheat", "tomato": "minecraft:apple",
    "brown_mushroom_colony": "minecraft:brown_mushroom",
    "red_mushroom_colony": "minecraft:red_mushroom",
    "barbecue_stick": "minecraft:cooked_beef", "roast_chicken_block": "minecraft:cooked_chicken",
    "stuffed_pumpkin_block": "minecraft:pumpkin", "honey_glazed_ham_block": "minecraft:cooked_porkchop",
    "shepherds_pie_block": "minecraft:baked_potato", "rice_roll_medley_block": "minecraft:dried_kelp",
    "cabbage": "minecraft:green_dye", "roast_chicken": "minecraft:cooked_chicken",
    "stuffed_pumpkin": "minecraft:pumpkin", "honey_glazed_ham": "minecraft:cooked_porkchop",
    "shepherds_pie": "minecraft:baked_potato", "sweet_berry_cheesecake": "minecraft:sweet_berries",
    "rope": "minecraft:string", "canvas_sign": "minecraft:oak_sign",
    "canvas": "minecraft:paper", "cutting_board": "minecraft:oak_slab",
}

# plugin trigger spec per advancement id (generator also emits this for the plugin)
TRIGGERS = {
    "root": "any_fd_item",
    "craft_knife": "items:flint_knife,iron_knife,golden_knife,diamond_knife,netherite_knife",
    "use_cutting_board": "cut_board",
    "place_cooking_pot": "place:cooking_pot",
    "place_skillet": "place:skillet",
    "use_skillet": "skillet_cook",
    "place_organic_compost": "place:organic_compost",
    "get_rich_soil": "items:rich_soil",
    "plant_rice": "plant:rice",
    "harvest_straw": "harvest_straw",
    "harvest_ropelogged_tomato": "harvest_tomato",
    "get_mushroom_colony": "items:brown_mushroom_colony,red_mushroom_colony",
    "eat_comfort_food": "eat_effect:comfort",
    "eat_nourishing_food": "eat_effect:nourishment",
    "place_campfire": "place_vanilla:CAMPFIRE",
    "place_feast": "place:roast_chicken_block,stuffed_pumpkin_block,honey_glazed_ham_block,shepherds_pie_block,rice_roll_medley_block",
    "get_ham": "items:ham",
    "hit_raider_with_rotten_tomato": "tomato_raider",
    "obtain_netherite_knife": "items:netherite_knife",
    "get_fd_seed": "items:cabbage_seeds,tomato_seeds,rice",
    "plant_all_crops": "plant_all:cabbage_seeds,tomato_seeds,onion,rice",
    "master_chef": "master_chef",
}

# meals counted for master_chef (bowl foods with effects, from the mod's master_chef criterion)
MASTER_CHEF_MEALS = [
    "cooked_rice", "bone_broth", "beef_stew", "chicken_soup", "vegetable_soup", "fish_stew",
    "fried_rice", "pumpkin_soup", "baked_cod_stew", "noodle_soup", "bacon_and_eggs",
    "pasta_with_meatballs", "pasta_with_mutton_chop", "mushroom_rice", "roasted_mutton_chops",
    "vegetable_noodles", "steak_and_potatoes", "ratatouille", "squid_ink_pasta", "grilled_salmon",
]


def generate_advancements() -> int:
    if DATA_PACK.exists():
        import shutil
        shutil.rmtree(DATA_PACK)
    out_dir = DATA_PACK / "data" / NS / "advancement"
    out_dir.mkdir(parents=True)
    (DATA_PACK / "pack.mcmeta").write_text(json.dumps({
        "pack": {"pack_format": 88, "min_format": 88, "max_format": 999,
                 "description": "Farmer's Delight advancement triggers (awarded by plugin)"}
    }), encoding="utf-8")

    count = 0
    for f in sorted(ADV_DIR.glob("*.json")):
        adv_id = f.stem
        data = load_json(f)
        display = data.get("display")
        if display is None:
            continue
        icon = display.get("icon", {})
        icon_item = icon.get("item", icon.get("id", "minecraft:paper"))
        if icon_item.startswith(f"{NS}:"):
            fallback = ICON_FALLBACK.get(icon_item.split(":", 1)[1])
            icon_item = fallback or "minecraft:paper"
        new_icon = {"id": icon_item}
        display["icon"] = new_icon
        parent = data.get("parent")
        if parent and parent.startswith(f"{NS}:main/"):
            parent = f"{NS}:" + parent[len(f"{NS}:main/"):]
        out = {
            "parent": parent,
            "display": display,
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "requirements": [["done"]],
        }
        (out_dir / f"{adv_id}.json").write_text(
            json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")
        count += 1

    # plugin-side trigger config
    lines = ["# advancement id -> plugin trigger (awarded programmatically)"]
    for adv_id, spec in TRIGGERS.items():
        lines.append(f"{adv_id}: {spec}")
    lines.append("master_chef_meals: " + ",".join(MASTER_CHEF_MEALS))
    target = PLUGIN / "src" / "main" / "resources" / "recipes" / "advancement_triggers.yml"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return count
