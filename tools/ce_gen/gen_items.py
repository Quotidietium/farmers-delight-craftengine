"""Generate CE item configurations from the parsed item registry."""
from .common import NS, yaml_str
from .parse_registry import item_foods_ref, item_container_ref

try:
    from .gen_recipes import tags_on_items
    _TAGS_ON_ITEMS = tags_on_items()
except Exception:
    _TAGS_ON_ITEMS = {}

# base materials for knives per tier: (material, durability, damage, speed_modifier, enchantability)
KNIFE_BASE = {
    "flint_knife": ("minecraft:flint", 131, 1.5, -1.8, 5),
    "iron_knife": ("minecraft:iron_nugget", 250, 2.5, -1.8, 14),
    "golden_knife": ("minecraft:gold_nugget", 32, 1.5, -1.8, 22),
    "diamond_knife": ("minecraft:diamond", 1561, 3.5, -1.8, 10),
    "netherite_knife": ("minecraft:netherite_scrap", 2031, 4.5, -1.8, 15),
}

# blocks that become CraftEngine furniture (custom hitbox); their items use furniture_item
FURNITURE_BLOCKS = {
    "cutting_board", "skillet", "rope", "safety_net", "canvas_rug",
    "tatami", "full_tatami_mat", "half_tatami_mat",
}

# ids of items that are block items of CE blocks; display uses block.<ns>.<id> lang key
_BLOCK_ITEM_LANG_KEYS: set[str] = set()


def register_block_item_lang(item_id: str):
    _BLOCK_ITEM_LANG_KEYS.add(item_id)


def is_sign_item(iid: str) -> bool:
    return iid.endswith("_canvas_sign") or iid.endswith("_canvas_wall_sign")


def _mod_lang_keys() -> set[str]:
    import json
    from .common import MOD_ASSETS
    data = json.load(open(MOD_ASSETS / "lang" / "en_us.json", encoding="utf-8"))
    return set(data.keys())


_MOD_LANG_KEYS = _mod_lang_keys()


def item_name_key(item_id: str, supplier: str = "") -> str:
    # resolve against the mod's own lang keys (ground truth)
    block_key = f"block.{NS}.{item_id}"
    item_key = f"item.{NS}.{item_id}"
    if block_key in _MOD_LANG_KEYS and item_key not in _MOD_LANG_KEYS:
        return block_key
    if item_key in _MOD_LANG_KEYS:
        return item_key
    # fallback: bound block items use the block key
    if item_id in FURNITURE_BLOCKS or is_sign_item(item_id) or "BlocksRegistry." in supplier:
        return block_key
    return item_key


def item_stack_size(item: dict, container: str | None) -> int:
    if "noStack()" in item["supplier"]:
        return 1
    if container:
        return 16
    if item["id"] == "rotten_tomato":
        return 16
    return 64


def block_id_for_item(item: dict) -> str | None:
    import re
    m = re.search(r"BlocksRegistry\.([A-Z0-9_]+)", item["supplier"])
    if not m:
        return None
    from .block_map import BLOCK_ID_BY_ENUM
    return BLOCK_ID_BY_ENUM.get(m.group(1))


def emit_item(item: dict, foods: dict) -> str | None:
    iid = item["id"]
    kind = item["kind"]
    foods_ref = item_foods_ref(item["supplier"])
    container = item_container_ref(item["supplier"])
    food = foods.get(foods_ref) if foods_ref else None

    # ---------------- base material
    if iid in KNIFE_BASE:
        material, durability, dmg, speed, ench = KNIFE_BASE[iid]
    elif kind == "skillet":
        # inert base (iron_hoe would till dirt on right-click - vanilla behavior leak)
        material, durability, dmg, speed, ench = "minecraft:iron_ingot", 250, 6.0, -3.1, 14
    elif kind == "rotten_tomato":
        material, durability = "minecraft:snowball", None
    elif food is not None and container == "minecraft:bowl":
        material, durability = "minecraft:mushroom_stew", None
    elif food is not None and container == "minecraft:glass_bottle":
        material, durability = "minecraft:honey_bottle", None
    elif food is not None and food.get("fast"):
        material, durability = "minecraft:cookie", None
    elif food is not None:
        material, durability = "minecraft:bread", None
    else:
        material, durability = "minecraft:paper", None

    key = f"{NS}:{iid}"
    lines = [f"  {yaml_str(key)}:", f"    material: {material}"]
    model_path = f"{NS}:item/canvas_sign" if is_sign_item(iid) else f"{NS}:item/{iid}"
    lines += ["    model:", "      type: minecraft:model", f"      path: {yaml_str(model_path)}"]

    # ---------------- data section
    data: list[str] = []
    data.append(f"item_name: {yaml_str(f'<!i><lang:{item_name_key(iid, item["supplier"])}>')}")

    if food is not None:
        data.append("minecraft:food:")
        data.append(f"  nutrition: {food['hunger']}")
        data.append(f"  saturation: {food['saturation']}")
        if food.get("always"):
            data.append("  can_always_eat: true")
        drink = kind in ("drink", "milk_bottle", "melon_juice") or iid == "bone_broth"
        anim = "drink" if drink else "eat"
        sound = "minecraft:entity.generic.drink" if drink else "minecraft:entity.generic.eat"
        seconds = 0.8 if food.get("fast") else (3.2 if iid == "kelp_roll" else 1.6)
        data.append("minecraft:consumable:")
        data.append(f"  consume_seconds: {seconds}")
        data.append(f"  animation: {anim}")
        data.append(f"  sound: {sound}")
        data.append("  has_consume_particles: true")
        eff = food.get("effect")
        if eff and eff.get("key"):
            # mod ConsumableItem food-effect tooltip: effect name + duration (+ chance)
            ns, name = eff["key"].split(":", 1)
            lang_key = f"effect.{ns}.{name}"
            mins, secs = divmod(int(eff.get("duration", 0)) // 20, 60)
            dur_txt = f"{mins}:{secs:02d}"
            chance_pct = int(round(float(food.get("chance", 1.0)) * 100))
            prob = f" ({chance_pct}%)" if chance_pct < 100 else ""
            amp = int(eff.get("amplifier") or 0)
            amp_txt = f" {amp + 1}" if amp else ""
            data.append("minecraft:lore:")
            data.append("  - content:")
            data.append(f"    - '<!i><gray><lang:{lang_key}>{amp_txt} {dur_txt}{prob}</gray>'")
        if container:
            data.append(f"minecraft:use_remainder: {container}")
        data.append(f"minecraft:max_stack_size: {item_stack_size(item, container)}")

    if durability:
        data.append(f"minecraft:max_damage: {durability}")

    if iid in KNIFE_BASE or kind == "skillet":
        data.append("minecraft:enchantable:")
        data.append(f"  value: {ench}")
        if iid == "netherite_knife":
            data.append("minecraft:fire_resistant: {}")

    # weapon attributes
    if iid in KNIFE_BASE or kind == "skillet":
        data.append("attribute_modifiers:")
        data.append(f"  - {{type: attack_damage, amount: {dmg}, operation: add_value, slot: mainhand}}")
        data.append(f"  - {{type: attack_speed, amount: {speed}, operation: add_value, slot: mainhand}}")

    if data:
        lines.append("    data:")
        lines.extend(f"      {d}" for d in data)

    # ---------------- settings section
    settings = []
    if item.get("burn_time"):
        settings.append(f"fuel_time: {item['burn_time']}")
    tags = sorted(_TAGS_ON_ITEMS.get(iid, []))
    if tags:
        settings.append("tags: [" + ", ".join(yaml_str(t) for t in tags) + "]")
    if settings:
        lines += ["    settings:"]
        for s in settings:
            lines.append(f"      {s}")

    # ---------------- behavior section
    if iid in FURNITURE_BLOCKS or is_sign_item(iid):
        lines += ["    behavior:", "      type: furniture_item",
                  f"      furniture: {yaml_str(f'{NS}:{iid}')}"]
    elif kind == "block_item":
        block_id = block_id_for_item(item)
        if block_id:
            register_block_item_lang(iid)
            lines += ["    behavior:", "      type: block_item",
                      f"      block: {yaml_str(f'{NS}:{block_id}')}"]

    return "\n".join(lines)
