"""Convert mod recipes into CE recipes + plugin-side configs (cooking/cutting/effects)."""
import json
from pathlib import Path

from .common import NS, MOD_DATA, PLUGIN_RECIPES, load_json, yaml_str

RECIPE_DIR = MOD_DATA / NS / "recipes"

SMELT_TYPES = {"smelting": "smelting", "blasting": "blasting", "smoking": "smoking",
               "campfire_cooking": "campfire_cooking"}

# ------------------------------------------------------------------ tag loading

def load_item_tags() -> dict[str, list[str]]:
    """Return {tag_id(without #): [member ids...]} for farmersdelight:/c: item tags."""
    tags = {}
    for ns_dir, ns in (("farmersdelight", NS), ("c", "c")):
        base = MOD_DATA / ns_dir / "tags" / "items"
        if not base.exists():
            continue
        for f in base.rglob("*.json"):
            tag_id = f"{ns}:{f.relative_to(base).with_suffix('').as_posix()}"
            data = load_json(f)
            members = []
            for v in data.get("values", []):
                if isinstance(v, str):
                    members.append(v)
                elif isinstance(v, dict) and "id" in v:
                    members.append(v["id"])
                if isinstance(v, dict) and v.get("required", True) is False and "id" in v:
                    # optional entries are dropped from expansion (conservative)
                    pass
            tags[tag_id] = members
    return tags


TAGS = load_item_tags()


def tags_on_items() -> dict[str, list[str]]:
    """Map FD item id -> CE tag list to declare via settings.tags."""
    out: dict[str, list[str]] = {}
    for tag, members in TAGS.items():
        for m in members:
            if m.startswith(f"{NS}:"):
                out.setdefault(m[len(NS) + 1:], []).append(tag)
    return out


def expand_tag(tag_id: str, seen: frozenset = frozenset()) -> list[str]:
    if tag_id in seen or len(seen) > 8:
        return []
    if tag_id.startswith("minecraft:"):
        return [tag_id]
    members: list[str] = TAGS.get(tag_id)
    if members is None:
        # the mod relies on the external "c:" convention for dyes; resolve locally
        if tag_id.startswith("c:") and tag_id.endswith("_dyes"):
            color = tag_id[len("c:"):-len("_dyes")]
            return [f"minecraft:{color}_dye"]
        return []
    out: list[str] = []
    resolved: list[str] = []
    for v in members:
        if v.startswith("#"):
            resolved.extend(expand_tag(v[1:], seen | {tag_id}))
        else:
            resolved.append(v)
    for m in resolved:
        if m not in out:
            out.append(m)
    return out


# ------------------------------------------------------------------ ingredient mapping

def ce_ingredient(ing):
    """Ingredient JSON -> CE recipe ingredient string or list (c: tags get expanded)."""
    if "item" in ing:
        return ing["item"]
    if "tag" in ing:
        tag = ing["tag"]
        if tag.startswith("minecraft:"):
            return f"#{tag}"
        if tag.startswith(f"{NS}:"):
            return f"#{tag}"  # declared on items via settings.tags
        # c: and other tags -> expand to explicit member list
        members = expand_tag(tag)
        if members:
            return members
        return f"#{tag}"
    return "minecraft:air"


def plugin_ingredient_list(ing) -> list[str]:
    """Ingredient JSON -> concrete item id list for plugin matching."""
    if "item" in ing:
        return [ing["item"]]
    if "tag" in ing:
        return expand_tag(ing["tag"])
    return []


# ------------------------------------------------------------------ CE recipes

def generate_ce_recipes() -> tuple[list[str], list[str]]:
    sections = []
    warnings = []
    bodies = []
    for f in sorted(RECIPE_DIR.glob("*.json")):
        rid = f.stem
        data = load_json(f)
        rtype = data.get("type", "")
        lines = [f"  {yaml_str(f'{NS}:{rid}')}:"]

        if rtype == "minecraft:crafting_shaped":
            lines.append("    type: shaped")
            if data.get("group"):
                lines.append(f"    group: {data['group']}")
            lines.append("    pattern:")
            for row in data["pattern"]:
                lines.append(f"      - {yaml_str(row)}")
            lines.append("    ingredients:")
            for key, ing in data["key"].items():
                lines.append(f"      {yaml_str(key)}: {ing_str(ce_ingredient(ing))}")
            lines.append("    result:")
            res = data["result"]
            lines.append(f"      id: {res['item']}")
            if res.get("count", 1) != 1:
                lines.append(f"      count: {res['count']}")
        elif rtype == "minecraft:crafting_shapeless":
            lines.append("    type: shapeless")
            if data.get("group"):
                lines.append(f"    group: {data['group']}")
            lines.append("    ingredients:")
            for ing in data["ingredients"]:
                if isinstance(ing, list):  # nested OR group
                    group = []
                    for sub in ing:
                        v = ce_ingredient(sub)
                        group.extend(v if isinstance(v, list) else [v])
                    lines.append(f"      - {ing_str(group)}")
                else:
                    lines.append(f"      - {ing_str(ce_ingredient(ing))}")
            lines.append("    result:")
            res = data["result"]
            lines.append(f"      id: {res['item']}")
            if res.get("count", 1) != 1:
                lines.append(f"      count: {res['count']}")
        elif rtype in ("minecraft:" + t for t in SMELT_TYPES):
            ce_type = SMELT_TYPES[rtype.split(":")[1]]
            lines.append(f"    type: {ce_type}")
            ing = data["ingredient"]
            if isinstance(ing, list):
                ing = ing[0]
            lines.append(f"    ingredient: {ing_str(ce_ingredient(ing))}")
            lines.append(f"    experience: {data.get('experience', 0.0)}")
            t = data.get("cookingtime")
            if t and int(t) != (100 if ce_type == 'smelting' else 200 if ce_type != 'campfire_cooking' else 600):
                lines.append(f"    time: {int(t)}")
            elif t and ce_type == "smelting" and int(t) != 100:
                lines.append(f"    time: {int(t)}")
            res = data["result"]
            res_id = res if isinstance(res, str) else res.get("item")
            lines.append("    result:")
            lines.append(f"      id: {res_id}")
            if not isinstance(res, str) and res.get("count", 1) != 1:
                lines.append(f"      count: {res['count']}")
        elif rtype == "minecraft:smithing_transform":
            lines.append("    type: smithing_transform")
            lines.append(f"    template_type: {data['template']['item']}")
            lines.append(f"    base: {data['base']['item']}")
            lines.append(f"    addition: {data['addition']['item']}")
            lines.append("    result:")
            lines.append(f"      id: {data['result']['item']}")
        else:
            warnings.append(f"skip {rid}: unsupported CE recipe type {rtype}")
            continue
        bodies.append("\n".join(lines))

    # split into chunks of 60 recipes per file
    chunks = []
    for i in range(0, len(bodies), 60):
        chunks.append("\n\n".join(bodies[i:i + 60]))
    return chunks, warnings


def ing_str(v) -> str:
    if isinstance(v, list):
        return "[" + ", ".join(yaml_str(x) for x in v) + "]"
    return yaml_str(v)


# ------------------------------------------------------------------ plugin recipes

def generate_plugin_cooking() -> str:
    entries = []
    for f in sorted((RECIPE_DIR / "cooking").glob("*.json")):
        data = load_json(f)
        ings = [i for i in (plugin_ingredient_list(x) for x in data.get("ingredients", [])) if i]
        res = data["result"]
        lines = [f"  {yaml_str(f.stem)}:"]
        lines.append(f"    ingredients:")
        for g in ings:
            lines.append(f"      - [{', '.join(yaml_str(x) for x in g)}]")
        lines.append(f"    result: {yaml_str(res['item'])}")
        if res.get("count", 1) != 1:
            lines.append(f"    result_count: {res['count']}")
        container = res.get("container") or data.get("container")
        if container and "item" in container:
            lines.append(f"    container: {yaml_str(container['item'])}")
        lines.append(f"    experience: {data.get('experience', 0)}")
        lines.append(f"    cook_time: {data.get('cookingtime', 200)}")
        if data.get("recipe_book_tab"):
            lines.append(f"    tab: {data['recipe_book_tab']}")
        entries.append("\n".join(lines))
    return "recipes:\n" + "\n\n".join(entries)


def generate_plugin_cutting() -> str:
    entries = []
    for f in sorted((RECIPE_DIR / "cutting").glob("*.json")):
        data = load_json(f)
        lines = [f"  {yaml_str(f.stem)}:"]
        inputs = data.get("ingredients", [])
        if inputs:
            lines.append(f"    input: [{', '.join(yaml_str(x) for x in plugin_ingredient_list(inputs[0]))}]")
        tool = data.get("tool", {})
        tools = plugin_ingredient_list(tool) if tool else []
        if tools:
            lines.append(f"    tool: [{', '.join(yaml_str(x) for x in tools)}]")
        lines.append("    result:")
        for r in data.get("result", []):
            cnt = r.get("count", 1)
            chance = 1.0
            # FD cutting results may carry chance via "chance" field
            if "chance" in r:
                chance = r["chance"]
            lines.append(f"      - item: {yaml_str(r['item'])}")
            if cnt != 1:
                lines.append(f"        count: {cnt}")
            if chance != 1.0:
                lines.append(f"        chance: {chance}")
        if data.get("sound"):
            lines.append(f"    sound: {yaml_str(data['sound'])}")
        entries.append("\n".join(lines))
    return "recipes:\n" + "\n\n".join(entries)


# ------------------------------------------------------------------ plugin food effects

def generate_food_effects(foods: dict, items: list[dict]) -> str:
    from .parse_registry import item_foods_ref
    lines = ["# food item -> applied effects on consume (plugin side)"]
    for item in items:
        ref = item_foods_ref(item["supplier"])
        if not ref:
            continue
        food = foods.get(ref)
        if not food or not food.get("effect"):
            continue
        eff = food["effect"]
        lines.append(f"{yaml_str(item['id'])}:")
        lines.append(f"  - effect: {yaml_str(eff['key'])}")
        lines.append(f"    duration: {eff['duration']}")
        lines.append(f"    amplifier: {eff['amplifier']}")
        lines.append(f"    chance: {food.get('chance', 0)}")
    # special plugin-handled items
    lines.append(f"{yaml_str('milk_bottle')}:")
    lines.append(f"  - effect: {yaml_str('farmersdelight:remove_random_effect')}")
    lines.append(f"{yaml_str('hot_cocoa')}:")
    lines.append(f"  - effect: {yaml_str('farmersdelight:remove_random_bad_effect')}")
    lines.append(f"{yaml_str('melon_juice')}:")
    lines.append(f"  - effect: {yaml_str('farmersdelight:heal_2')}")
    # vanilla soup comfort & rabbit stew jump (mod mixin behavior)
    for vid in ("minecraft:mushroom_stew", "minecraft:beetroot_soup"):
        lines.append(f"{yaml_str(vid)}:")
        lines.append(f"  - effect: {yaml_str('farmersdelight:comfort')}")
        lines.append(f"    duration: 6000")
        lines.append(f"    amplifier: 0")
        lines.append(f"    chance: 1.0")
    lines.append(f"{yaml_str('minecraft:rabbit_stew')}:")
    lines.append(f"  - effect: {yaml_str('farmersdelight:comfort')}")
    lines.append(f"    duration: 6000")
    lines.append(f"    amplifier: 0")
    lines.append(f"    chance: 1.0")
    lines.append(f"  - effect: {yaml_str('minecraft:jump_boost')}")
    lines.append(f"    duration: 200")
    lines.append(f"    amplifier: 1")
    lines.append(f"    chance: 1.0")
    return "\n".join(lines)


# ------------------------------------------------------------------ plugin misc content

COMPOSTABLES = {
    "tree_bark": 0.3, "straw": 0.3, "cabbage_seeds": 0.3, "tomato_seeds": 0.3,
    "rice": 0.5, "rice_panicle": 0.5, "pumpkin_slice": 0.5, "cabbage_leaf": 0.5,
    "cabbage": 0.65, "onion": 0.65, "tomato": 0.65, "wild_cabbages": 0.65,
    "wild_onions": 0.65, "wild_tomatoes": 0.65, "wild_carrots": 0.65,
    "wild_potatoes": 0.65, "wild_beetroots": 0.65, "wild_rice": 0.65, "pie_crust": 0.65,
    "rice_bale": 0.85, "sweet_berry_cookie": 0.85, "honey_cookie": 0.85,
    "cake_slice": 0.85, "apple_pie_slice": 0.85, "sweet_berry_cheesecake_slice": 0.85,
    "chocolate_pie_slice": 0.85, "raw_pasta": 0.85, "rotten_tomato": 0.85,
    "apple_pie": 1.0, "sweet_berry_cheesecake": 1.0, "chocolate_pie": 1.0,
    "dumplings": 1.0, "stuffed_pumpkin": 1.0, "brown_mushroom_colony": 1.0,
    "red_mushroom_colony": 1.0,
}


def generate_misc_content() -> str:
    lines = ["# Plugin-side content tables (generated)"]
    lines.append("compostables:")
    for k, v in COMPOSTABLES.items():
        lines.append(f"  {yaml_str(k)}: {v}")
    # villager trades from FarmersDelightMod.registerVillagerTradeOffer
    lines.append("trades:")
    lines.append("  farmer:")
    lines.append("    - {input: 'farmersdelight:onion', count: 26, max_uses: 16, villager_xp: 2, price: 1}")
    lines.append("    - {input: 'farmersdelight:tomato', count: 26, max_uses: 16, villager_xp: 2, price: 1}")
    lines.append("    - {input: 'farmersdelight:cabbage', count: 16, max_uses: 16, villager_xp: 5, price: 1}")
    lines.append("    - {input: 'farmersdelight:rice', count: 20, max_uses: 16, villager_xp: 5, price: 1}")
    lines.append("  wandering_trader:")
    lines.append("    - {input: 'farmersdelight:cabbage_seeds', price: 1, max_uses: 12}")
    lines.append("    - {input: 'farmersdelight:tomato_seeds', price: 1, max_uses: 12}")
    lines.append("    - {input: 'farmersdelight:rice', price: 1, max_uses: 12}")
    lines.append("    - {input: 'farmersdelight:onion', price: 1, max_uses: 12}")
    # loot injections parsed from inject tables
    lines.append("loot_injects:")
    inject_root = MOD_DATA / NS / "loot_tables" / "inject"
    if inject_root.exists():
        for f in sorted(inject_root.rglob("*.json")):
            data = load_json(f)
            pools = data.get("pools", [])
            items_out = []
            for pool in pools:
                for entry in pool.get("entries", []):
                    if entry.get("type") == "minecraft:item":
                        cnt = 1
                        for fn in entry.get("functions", []):
                            if fn.get("function") == "minecraft:set_count":
                                c = fn.get("count", 1)
                                if isinstance(c, dict) and "min" in c:
                                    cnt = f"{c['min']}~{c.get('max', c['min'])}"
                                elif isinstance(c, (int, float)):
                                    cnt = c
                        items_out.append((entry.get("name", ""), cnt, pool.get("rolls", 1)))
            if not items_out:
                continue
            rel = f.relative_to(inject_root).with_suffix("")
            lines.append(f"  {yaml_str(rel.as_posix())}:")
            for name, cnt, rolls in items_out:
                if isinstance(cnt, str):
                    lines.append(f"    - {{item: {yaml_str(name)}, count: '{cnt}', rolls: {rolls}}}")
                else:
                    lines.append(f"    - {{item: {yaml_str(name)}, count: {cnt}, rolls: {rolls}}}")
    return "\n".join(lines)
