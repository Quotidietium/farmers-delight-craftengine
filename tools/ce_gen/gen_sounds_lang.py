"""Generate CE sounds config and lang config from mod assets."""
from .common import MOD_ASSETS, NS, yaml_str, load_json

LANG_KEYS_PREFIX = (f"item.{NS}.", f"block.{NS}.", f"farmersdelight.subtitles.",
                    "farmersdelight.", f"container.{NS}.")


def generate_sounds() -> str:
    data = load_json(MOD_ASSETS / "sounds.json")
    lines = []
    for event_id, spec in data.items():
        full = f"{NS}:{event_id}"
        lines.append(f"  {yaml_str(full)}:")
        if spec.get("subtitle"):
            lines.append(f"    subtitle: {yaml_str(spec['subtitle'])}")
        snds = spec.get("sounds", [])
        if len(snds) == 1 and isinstance(snds[0], str):
            lines.append(f"    sounds: [{yaml_str(snds[0])}]")
        else:
            lines.append("    sounds:")
            for s in snds:
                if isinstance(s, str):
                    lines.append(f"      - {yaml_str(s)}")
                else:
                    lines.append(f"      - name: {yaml_str(s.get('name', ''))}")
                    if s.get("volume"):
                        lines.append(f"        volume: {s['volume']}")
                    if s.get("weight"):
                        lines.append(f"        weight: {s['weight']}")
    return "\n".join(lines)


def generate_lang() -> tuple[str, int]:
    lang_dir = MOD_ASSETS / "lang"
    lines = []
    count = 0
    for f in sorted(lang_dir.glob("*.json")):
        locale = f.stem
        data = load_json(f)
        filtered = {k: v for k, v in data.items()
                    if isinstance(v, str) and (k.startswith(f"item.{NS}.")
                                               or k.startswith(f"block.{NS}.")
                                               or k.startswith(f"container.{NS}.")
                                               or k.startswith(f"{NS}.")
                                               or "farmersdelight" in k)}
        if not filtered:
            continue
        count += 1
        lines.append(f"  {locale}:")
        for k in sorted(filtered):
            lines.append(f"    {yaml_str(k)}: {yaml_str(filtered[k])}")
    # extra keys needed by the plugin port
    lines.append(f"  en_us:")
    for k, v in {
        "farmersdelight.enchantment.backstabbing": "Backstabbing",
        "farmersdelight.container.cooking_pot": "Cooking Pot",
        "farmersdelight.sign.edit": "Enter sign text (or 'cancel'):",
    }.items():
        lines.append(f"    {yaml_str(k)}: {yaml_str(v)}")
    lines.append(f"  zh_cn:")
    for k, v in {
        "farmersdelight.enchantment.backstabbing": "背刺",
        "farmersdelight.container.cooking_pot": "烹饪锅",
        "farmersdelight.sign.edit": "请输入告示牌文本（输入 cancel 取消）：",
    }.items():
        lines.append(f"    {yaml_str(k)}: {yaml_str(v)}")
    return "\n".join(lines), count
