"""Parse ItemsRegistry.java / Foods.java / BlocksRegistry.java into structured data."""
import re
from pathlib import Path

from .common import MOD_JAVA, load_json, MOD_ASSETS

# ---------------------------------------------------------------- Foods.java

def parse_foods() -> dict:
    """Return {FOOD_NAME: {hunger, saturation, effect, chance, meat, fast, always}}"""
    text = (MOD_JAVA / "item" / "enumeration" / "Foods.java").read_text(encoding="utf-8")
    # strip comments
    text = re.sub(r"//[^\n]*", "", text)
    foods = {}
    # match entries until the constructor section starts
    body = text.split("private final")[0]
    entry_re = re.compile(
        r"^\s*([A-Z0-9_]+)\(([^;]*?)\),?$", re.M)
    for m in entry_re.finditer(body):
        name, args_raw = m.group(1), m.group(2).strip()
        if name == "Foods" or args_raw.startswith("int "):
            continue
        if not args_raw or args_raw == "":
            continue
        args = split_args(args_raw)
        # constructors: (hunger, saturation)
        #   (hunger, saturation, isMeat)
        #   (hunger, saturation, effect, chance)
        #   (hunger, saturation, effect, chance, isMeat)
        #   (hunger, saturation, effect, chance, isMeat, fast, always)
        try:
            hunger = int(args[0])
            sat = float(args[1].rstrip("fF"))
        except (ValueError, IndexError):
            continue
        effect = None
        chance = 0.0
        meat = fast = always = False
        if len(args) == 3:
            meat = args[2] == "true"
        elif len(args) >= 5:
            effect = parse_effect(args[2]) if args[2] != "null" else None
            chance = float(args[3].rstrip("fF"))
            meat = args[4] == "true"
            if len(args) >= 7:
                fast = args[5] == "true"
                always = args[6] == "true"
        foods[name] = dict(hunger=hunger, saturation=sat, effect=effect,
                           chance=chance, meat=meat, fast=fast, always=always)
    return foods


def parse_effect(expr: str):
    """new StatusEffectInstance(StatusEffects.X, DURATION, amp) -> (key, ticks, amp)"""
    m = re.search(r"StatusEffects\.([A-Z_]+)\s*,\s*([A-Za-z0-9_.]+)\s*,\s*(\d+)", expr)
    if m:
        effect_name, dur, amp = m.group(1), m.group(2), int(m.group(3))
        ticks = duration_ticks(dur)
        return dict(key="minecraft:" + vanilla_effect_name(effect_name), duration=ticks, amplifier=amp)
    m = re.search(r"EffectsRegistry\.([A-Z_]+)\.get\(\)\s*,\s*([A-Za-z0-9_.]+)\s*,\s*(\d+)", expr)
    if m:
        effect_name, dur, amp = m.group(1), m.group(2), int(m.group(3))
        ticks = duration_ticks(dur)
        return dict(key="farmersdelight:" + effect_name.lower(), duration=ticks, amplifier=amp)
    return None


DURATIONS = {"ConsumableItem.BRIEF_DURATION": 600, "ConsumableItem.SHORT_DURATION": 1200,
             "ConsumableItem.MEDIUM_DURATION": 3600, "ConsumableItem.LONG_DURATION": 6000}


def duration_ticks(expr: str) -> int:
    expr = expr.strip()
    if expr in DURATIONS:
        return DURATIONS[expr]
    try:
        return int(expr)
    except ValueError:
        return 600


def vanilla_effect_name(name: str) -> str:
    mapping = {
        "ABSORPTION": "absorption", "HUNGER": "hunger", "SPEED": "speed",
        "REGENERATION": "regeneration", "NAUSEA": "nausea", "GLOWING": "glowing",
    }
    return mapping.get(name, name.lower())


def split_args(raw: str):
    """Split top-level comma-separated args (paren/bracket depth aware)."""
    args, depth, cur = [], 0, ""
    for ch in raw:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur.strip())
    return args


# ---------------------------------------------------------------- ItemsRegistry.java

def parse_items() -> list[dict]:
    text = (MOD_JAVA / "registry" / "ItemsRegistry.java").read_text(encoding="utf-8")
    text = re.sub(r"//[^\n]*", "", text)
    body = text[text.index("public enum ItemsRegistry"):]
    body = body.split("private final")[0]
    items = []
    entry_re = re.compile(r'^\s*([A-Z0-9_]+)\("([^"]+)",\s*(.+?),?(?:,\s*(\d+))?\),?;?$', re.M)
    for m in entry_re.finditer(body):
        name, path, supplier, burn = m.group(1), m.group(2), m.group(3).strip(), m.group(4)
        items.append(dict(
            enum=name, id=path, supplier=supplier,
            burn_time=int(burn) if burn else None,
            kind=classify_item(name, path, supplier),
        ))
    return items


def classify_item(name: str, path: str, supplier: str) -> str:
    if "KnifeItem" in supplier:
        return "knife"
    if "SkilletItem" in supplier:
        return "skillet"
    if "RottenTomatoItem" in supplier:
        return "rotten_tomato"
    if "SignItem" in supplier:
        return "canvas_sign"
    if "RopeItem" in supplier:
        return "rope"
    if "MushroomColonyBlockItem" in supplier:
        return "block_item"
    if "MilkBottleItem" in supplier or "HotCocoaItem" in supplier:
        return "milk_bottle"
    if "MelonJuiceItem" in supplier:
        return "melon_juice"
    if "PopsicleItem" in supplier:
        return "popsicle"
    if "KelpRollItem" in supplier:
        return "food"
    if "DogFoodItem" in supplier:
        return "dog_food"
    if "HorseFeedItem" in supplier:
        return "horse_feed"
    if "ModBlockItem" in supplier or "AliasedBlockItem" in supplier:
        return "block_item"
    if "DrinkableItem" in supplier:
        return "drink"
    if "ConsumableItem" in supplier:
        return "meal"
    if re.search(r"new Item\(food\(Foods\.", supplier):
        return "food"
    if "new Item(" in supplier:
        return "simple"
    return "simple"


def item_foods_ref(supplier: str):
    m = re.search(r"Foods\.([A-Z0-9_]+)", supplier)
    return m.group(1) if m else None


def item_container_ref(supplier: str):
    """food(Foods.X, Items.BOWL, 16) -> 'minecraft:bowl'"""
    m = re.search(r"Items\.([A-Z_]+)", supplier)
    if m:
        return "minecraft:" + m.group(1).lower()
    return None


def item_stack_size(supplier: str):
    m = re.search(r",\s*(\d+)\)\)?$", supplier.strip())
    if m:
        return int(m.group(1))
    return None


# ---------------------------------------------------------------- BlocksRegistry.java

def parse_blocks() -> list[dict]:
    text = (MOD_JAVA / "registry" / "BlocksRegistry.java").read_text(encoding="utf-8")
    text = re.sub(r"//[^\n]*", "", text)
    body = text[text.index("public enum BlocksRegistry"):]
    body = body.split("private static FlammableBlockRegistry.Entry flammable")[0]
    blocks = []
    entry_re = re.compile(r'^\s*([A-Z0-9_]+)\("([^"]+)",\s*(.+?)\),?$', re.M)
    for m in entry_re.finditer(body):
        name, path, args_raw = m.group(1), m.group(2), m.group(3)
        if name == "BlocksRegistry" or path.endswith(".java"):
            continue
        args = split_args(args_raw)
        flammable = "flammable(" in args_raw
        blocks.append(dict(enum=name, id=path, args=args_raw, flammable=flammable))
    return blocks


# ---------------------------------------------------------------- blockstates

def parse_blockstate(block_id: str):
    """Return (props, appearances) where props maps name->list of values in order,
    appearances maps (model, x, y) combo index -> model ref."""
    path = MOD_ASSETS / "blockstates" / f"{block_id}.json"
    if not path.exists():
        return None
    data = load_json(path)
    variants = data.get("variants", {})
    prop_values = {}
    combos = []
    for key, val in variants.items():
        states = {}
        if key != "":
            for part in key.split(","):
                k, v = part.split("=")
                states[k] = v
                prop_values.setdefault(k, [])
                if v not in prop_values[k]:
                    prop_values[k].append(v)
        entries = val if isinstance(val, list) else [val]
        for e in entries:
            combos.append(dict(states=states, model=e.get("model"),
                               x=e.get("x", 0), y=e.get("y", 0)))
    return prop_values, combos
