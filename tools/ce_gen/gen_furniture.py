"""Generate CE furniture configs (custom-hitbox blocks) + internal display items."""
import json

from .common import NS, CE_RP, yaml_str, load_json, MOD_ASSETS

# fid -> list of (variant_name, block_model_basename, hitbox spec)
# hitbox: (pos, width, height, blocks_building)
SIMPLE_FURNITURE = {
    "cutting_board": [("ground", "cutting_board", ("0.0625,0,0.0625", 0.875, 0.0625, False))],
    "rope": [("ground", "rope_post", ("0.375,0,0.375", 0.25, 1.0, False))],
    "safety_net": [("ground", "safety_net", ("0,0.4375,0", 1.0, 0.125, False))],
    "canvas_rug": [("ground", "canvas_rug", ("0.03125,0,0.03125", 0.9375, 0.0625, False))],
    "half_tatami_mat": [("ground", "tatami_mat_half", ("0,0,0", 1.0, 0.0625, True))],
}

FEAST_BLOCKS = ["roast_chicken_block", "stuffed_pumpkin_block", "honey_glazed_ham_block",
                "shepherds_pie_block", "rice_roll_medley_block"]

FEAST_HITBOX = {
    "roast_chicken_block": ("0.125,0,0.125", 0.75, 0.75, True),
    "stuffed_pumpkin_block": ("0,0,0", 1.0, 1.0, True),
    "honey_glazed_ham_block": ("0.125,0,0.125", 0.75, 0.5, True),
    "shepherds_pie_block": ("0.125,0,0.125", 0.75, 0.5, True),
    "rice_roll_medley_block": ("0.0625,0,0.0625", 0.875, 0.4, True),
}

FURNITURE_SOUNDS = {
    "default": "minecraft:block.wood",
}

SIGN_COLORS = ["", "white", "orange", "magenta", "light_blue", "yellow", "lime",
               "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
               "green", "red", "black"]

# internal display items emitted: model basename -> item id
_internal_items: dict[str, str] = {}


def internal_item_for(model_basename: str) -> str:
    if model_basename not in _internal_items:
        _internal_items[model_basename] = f"{NS}:internal/{model_basename}"
    return _internal_items[model_basename]


def emit_internal_items() -> str:
    lines = []
    for base in sorted(_internal_items):
        lines.append(f"  {yaml_str(f'{NS}:internal/{base}')}:")
        lines.append(f"    material: minecraft:paper")
        lines.append(f"    model:")
        lines.append(f"      type: minecraft:model")
        lines.append(f"      path: {yaml_str(f'{NS}:item/internal/{base}')}")
        write_selfcontained_model(base)
    return "\n".join(lines)


def write_selfcontained_model(base: str):
    """Copy the mod block model into a parent-free item model (no parent chain)."""
    import json as _json
    src = MOD_ASSETS / "models" / "block" / f"{base}.json"
    if not src.exists():
        # generated models (canvas signs) live in the pack's own block folder
        src = CE_RP / "models" / "block" / f"{base}.json"
    if not src.exists():
        return
    data = _json.loads(src.read_text(encoding="utf-8"))
    data.pop("parent", None)
    data.pop("credit", None)
    data.pop("__comment", None)
    out = CE_RP / "models" / "item" / "internal" / f"{base}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(_json.dumps(data, indent=1), encoding="utf-8")



# variants rendered via the furniture's own item (proven CE bench pattern)
OWN_ITEM_DEFAULT_VARIANTS = {
    "cutting_board": "ground", "rope": "ground", "safety_net": "ground",
    "canvas_rug": "ground", "half_tatami_mat": "ground",
    "tatami": "ground", "full_tatami_mat": "foot",
    "skillet": "ground", "cooking_pot": "ground",
    "roast_chicken_block": "s4", "stuffed_pumpkin_block": "s4",
    "honey_glazed_ham_block": "s4", "shepherds_pie_block": "s4",
    "rice_roll_medley_block": "s8",
}


def _furniture_head(fid: str, hit_times: int = 3) -> list[str]:
    snd = FURNITURE_SOUNDS["default"]
    lines = [f"  {yaml_str(f'{NS}:{fid}')}:",
             "    settings:",
             f"      item: {yaml_str(f'{NS}:{fid}')}",
             f"      hit_times: {hit_times}",
             "      sounds:",
             f"        break: {snd}.break",
             f"        place: {snd}.place",
             f"        hit: {snd}.hit",
             "    loot:",
             "      pools:",
             "        - rolls: 1",
             "          entries:",
             "            - type: item",
             f"              item: {yaml_str(f'{NS}:{fid}')}",
             "    variants:"]
    return lines


def _variant(lines: list[str], name: str, model_basename: str, hitbox, extra_height: str = "0",
             fid: str = ""):
    pos, width, height, blocks = hitbox
    own = OWN_ITEM_DEFAULT_VARIANTS.get(fid) == name and fid
    display_item = f"{NS}:{fid}" if own else internal_item_for(model_basename)
    lines.append(f"      {name}:")
    lines.append("        elements:")
    lines.append(f"          - item: {yaml_str(display_item)}")
    lines.append("            display_transform: none")
    lines.append("            billboard: fixed")
    lines.append("            position: 0.5,0,0.5")
    lines.append("            translation: 0,0.5,0")
    lines.append("            scale: 1")
    lines.append("        hitboxes:")
    lines.append("          - type: interaction")
    lines.append("            can_use_item_on: true")
    lines.append("            can_be_hit_by_projectile: true")
    lines.append(f"            blocks_building: {'true' if blocks else 'false'}")
    lines.append(f"            position: {pos}")
    lines.append(f"            width: {width}")
    lines.append(f"            height: {height}")
    lines.append("            interactive: true")


def north_models(block_id: str) -> dict[str, str]:
    data = load_json(MOD_ASSETS / "blockstates" / f"{block_id}.json")
    out = {}
    for key, val in data.get("variants", {}).items():
        e = val if isinstance(val, list) else val
        e = e[0] if isinstance(e, list) else e
        if "north" in key or key in ("", "support=false"):
            model = e["model"]
            if key.startswith("facing=north,"):
                out[key.split(",")[-1]] = model
            elif key == "":
                out[""] = model
    return out


def generate_furniture() -> str:
    chunks = []

    # ---------------- cooking pot: variants none/tray/handle
    lines = _furniture_head("cooking_pot")
    _variant(lines, "ground", "cooking_pot", ("0.125,0,0.125", 0.75, 0.625, True), fid="cooking_pot")
    _variant(lines, "tray", "cooking_pot_tray", ("0.0625,0,0.0625", 0.875, 0.625, True), fid="cooking_pot")
    _variant(lines, "handle", "cooking_pot_handle", ("0.25,0,0.25", 0.5, 0.625, True), fid="cooking_pot")
    lines.append("    behavior:")
    lines.append("      type: simple_storage_furniture")
    lines.append("      data_key: farmersdelight:pot_contents")
    lines.append('      title: "<!i><lang:farmersdelight.container.cooking_pot>"')
    lines.append("      rows: 1")
    chunks.append("\n".join(lines))

    # ---------------- skillet: ground + tray
    lines = _furniture_head("skillet")
    _variant(lines, "ground", "skillet", ("0.125,0,0.125", 0.75, 0.25, True), fid="skillet")
    _variant(lines, "tray", "skillet_tray", ("0.0625,0,0.0625", 0.875, 0.25, True), fid="skillet")
    chunks.append("\n".join(lines))

    # ---------------- simple furniture
    for fid, variants in SIMPLE_FURNITURE.items():
        lines = _furniture_head(fid)
        for vname, model, hitbox in variants:
            _variant(lines, vname, model, hitbox, fid=fid)
        chunks.append("\n".join(lines))

    # ---------------- tatami (unpaired half / paired full)
    lines = _furniture_head("tatami")
    _variant(lines, "ground", "tatami_half", ("0,0,0", 1.0, 0.5, True), fid="tatami")
    _variant(lines, "paired", "tatami_even", ("0,0,0", 1.0, 0.5, True), fid="tatami")
    chunks.append("\n".join(lines))

    # ---------------- full tatami mat: foot + head parts (plugin pairs them)
    lines = _furniture_head("full_tatami_mat")
    _variant(lines, "foot", "tatami_mat_foot", ("0,0,0", 1.0, 0.0625, True), fid="full_tatami_mat")
    _variant(lines, "head", "tatami_mat_head", ("0,0,0", 1.0, 0.0625, True), fid="full_tatami_mat")
    chunks.append("\n".join(lines))

    # ---------------- feasts: one variant per servings value
    for fid in FEAST_BLOCKS:
        models = north_models(fid)
        lines = _furniture_head(fid)
        hitbox = FEAST_HITBOX[fid]
        for servings in sorted(models, key=lambda s: int(s.split("=")[-1]) if "=" in s else 0):
            model = models[servings]
            base = model.split("/")[-1]
            n = int(servings.split("=")[-1]) if "=" in servings else 0
            _variant(lines, f"s{n}", base, hitbox, fid=fid)
        chunks.append("\n".join(lines))

    # ---------------- canvas signs
    for color in SIGN_COLORS:
        suffix = f"{color}_canvas_sign" if color else "canvas_sign"
        texture = f"farmersdelight:block/sign/canvas_{color}" if color else "farmersdelight:block/sign/canvas"
        write_sign_model(suffix, texture)
        # standing
        lines = _furniture_head(suffix, hit_times=2)
        lines.append("      ground:")
        lines.append("        elements:")
        lines.append(f"          - item: {yaml_str(internal_item_for('sign_' + suffix))}")
        lines.append("            display_transform: none")
        lines.append("            billboard: fixed")
        lines.append("            position: 0.5,0,0.5")
        lines.append("            translation: 0,0.5,0")
        lines.append("        hitboxes:")
        lines.append("          - type: interaction")
        lines.append("            blocks_building: false")
        lines.append("            position: 0.15,0,0.15")
        lines.append("            width: 0.7")
        lines.append("            height: 1.0")
        lines.append("            interactive: true")
        chunks.append("\n".join(lines))
        # wall
        wall_fid = f"{color}_canvas_wall_sign" if color else "canvas_wall_sign"
        write_sign_model(wall_fid, texture)
        lines = _furniture_head(wall_fid, hit_times=2)
        lines.append("      wall:")
        lines.append("        elements:")
        lines.append(f"          - item: {yaml_str(internal_item_for('sign_' + wall_fid))}")
        lines.append("            display_transform: none")
        lines.append("            billboard: fixed")
        lines.append("            position: 0.5,0.5,0.94")
        lines.append("        hitboxes:")
        lines.append("          - type: interaction")
        lines.append("            blocks_building: false")
        lines.append("            position: 0.15,0.2,0.9")
        lines.append("            width: 0.7")
        lines.append("            height: 0.6")
        lines.append("            interactive: true")
        chunks.append("\n".join(lines))

    return "\n\n".join(chunks)


SIGN_MODEL_TEMPLATE = {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "#board"
    },
    "elements": [
        {
            "name": "board",
            "from": [2, 6, 7], "to": [14, 15, 9],
            "faces": {
                "north": {"uv": [2, 3, 14, 12], "texture": "#board"},
                "south": {"uv": [2, 3, 14, 12], "texture": "#board"},
                "up": {"uv": [2, 13, 14, 15], "texture": "#stick"},
                "down": {"uv": [2, 13, 14, 15], "texture": "#stick"},
                "west": {"uv": [7, 3, 9, 12], "texture": "#stick"},
                "east": {"uv": [7, 3, 9, 12], "texture": "#stick"}
            }
        },
        {
            "name": "stick",
            "from": [7.5, 0, 7.5], "to": [8.5, 6, 8.5],
            "faces": {
                "up": {"uv": [0, 0, 1, 1], "texture": "#stick"},
                "down": {"uv": [0, 0, 1, 1], "texture": "#stick"},
                "north": {"uv": [1, 0, 2, 6], "texture": "#stick"},
                "south": {"uv": [1, 0, 2, 6], "texture": "#stick"},
                "west": {"uv": [0, 0, 1, 6], "texture": "#stick"},
                "east": {"uv": [0, 0, 1, 6], "texture": "#stick"}
            }
        }
    ]
}


def write_sign_model(fid: str, board_texture: str):
    model = json.loads(json.dumps(SIGN_MODEL_TEMPLATE))
    model["textures"]["board"] = board_texture
    model["textures"]["stick"] = "minecraft:block/oak_planks"
    path = CE_RP / "models" / "block" / f"sign_{fid}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, indent=2), encoding="utf-8")


def copy_sign_textures():
    import shutil
    src = MOD_ASSETS / "textures" / "entity" / "signs"
    dst = CE_RP / "textures" / "block" / "sign"
    if not src.exists():
        return 0
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    return sum(1 for _ in dst.glob("*.png"))


def furniture_sign_item_ids() -> list[str]:
    ids = []
    for color in SIGN_COLORS:
        ids.append(f"{color}_canvas_sign" if color else "canvas_sign")
        ids.append(f"{color}_canvas_wall_sign" if color else "canvas_wall_sign")
    return ids
