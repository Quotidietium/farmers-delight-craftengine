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
    locales: dict[str, dict[str, str]] = {}
    for f in sorted(lang_dir.glob("*.json")):
        locale = f.stem
        data = load_json(f)
        filtered = {k: v for k, v in data.items()
                    if isinstance(v, str) and (k.startswith(f"item.{NS}.")
                                               or k.startswith(f"block.{NS}.")
                                               or k.startswith(f"container.{NS}.")
                                               or k.startswith(f"{NS}.")
                                               or "farmersdelight" in k)}
        if filtered:
            locales.setdefault(locale, {}).update(filtered)
    # extra keys needed by the plugin port (merged into their locale section)
    for locale, extra in {
        "en_us": {
            "farmersdelight.enchantment.backstabbing": "Backstabbing",
            "farmersdelight.sign.edit": "Enter sign text (or 'cancel'):",
        },
        "zh_cn": {
            "farmersdelight.enchantment.backstabbing": "背刺",
            "farmersdelight.sign.edit": "请输入告示牌文本（输入 cancel 取消）：",
        },
    }.items():
        for k, v in extra.items():
            locales.setdefault(locale, {}).setdefault(k, v)

    lines = []
    for locale in sorted(locales):
        lines.append(f"  {locale}:")
        for k in sorted(locales[locale]):
            lines.append(f"    {yaml_str(k)}: {yaml_str(locales[locale][k])}")
    return "\n".join(lines), len(locales)
