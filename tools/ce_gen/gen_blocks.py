"""Generic blockstate -> CraftEngine blocks config converter."""
from .common import NS, yaml_str, iter_blockstates
from .block_map import CE_BLOCK_IDS, PLANT_BLOCKS, PLANT_GROUP, PLUGIN_LOOT_BLOCKS, BLOCK_FAMILY, FAMILY_SETTINGS


def prop_cep_type(name: str, values: list[str]):
    """Map a blockstate property to a CE property definition, or None to drop."""
    if name == "facing" and set(values) <= {"north", "south", "east", "west"}:
        return dict(type="horizontal_direction", default=values[0])
    if name == "facing":
        return dict(type="direction", default=values[0])
    if name == "axis":
        return dict(type="axis", default=values[0])
    if name in ("age", "composting", "bites", "moisture", "servings", "level"):
        nums = sorted(int(v) for v in values)
        return dict(type="int", default=nums[0], range=f"{nums[0]}~{nums[-1]}")
    if all(v in ("true", "false") for v in values):
        return dict(type="boolean", default=values[0])
    return dict(type="string", default=values[0], values=values)


def generate_blocks() -> tuple[str, list[str]]:
    """Returns (yaml body for blocks:, list of warnings)."""
    out = []
    warnings = []
    for bid in sorted(CE_BLOCK_IDS):
        parsed = None
        for name, data in iter_blockstates():
            if name == bid:
                parsed = (data,)
        if not parsed:
            warnings.append(f"no blockstate json for block {bid}")
            continue
        data = parsed[0]
        variants = data.get("variants")
        if variants is None:
            warnings.append(f"block {bid} uses multipart; skipped (should be furniture)")
            continue
        prop_values: dict[str, list[str]] = {}
        combos = []
        for key, val in variants.items():
            states = {}
            if key:
                for part in key.split(","):
                    k, v = part.split("=")
                    states[k] = v
                    prop_values.setdefault(k, [])
                    if v not in prop_values[k]:
                        prop_values[k].append(v)
            entries = val if isinstance(val, list) else [val]
            for e in entries[:1]:  # random rotations: take first
                combos.append(dict(states=states, model=e.get("model"),
                                   x=e.get("x", 0), y=e.get("y", 0)))
        # drop constant properties
        drop_props = {k for k, vals in prop_values.items() if len(vals) == 1}
        for c in combos:
            for k in drop_props:
                c["states"].pop(k, None)

        # appearances dedup
        appear_map: dict[tuple, str] = {}
        appears = []
        for c in combos:
            keyt = (c["model"], c["x"], c["y"])
            if keyt not in appear_map:
                appear_map[keyt] = f"app{len(appears)}"
                appears.append(keyt)

        lines = [f"  {yaml_str(f'{NS}:{bid}')}:"]

        # properties definition (single-state blocks use plain `state`)
        if not prop_values or all(k in drop_props for k in prop_values):
            m, x, y = appears[0]
            lines.append("    state:")
            lines.append(f"      auto_state: {PLANT_GROUP.get(bid, 'note_block') if bid in PLANT_BLOCKS else 'note_block'}")
            lines.append("      model:")
            lines.append(f"        path: {yaml_str(m)}")
            if x:
                lines.append(f"        x: {x}")
            if y:
                lines.append(f"        y: {y}")
        else:
            lines.append("    states:")
            lines.append("      properties:")
            for pname in sorted(prop_values):
                if pname in drop_props:
                    continue
                defn = prop_cep_type(pname, prop_values[pname])
                lines.append(f"        {pname}:")
                for dk, dv in defn.items():
                    if isinstance(dv, list):
                        lines.append(f"          {dk}: [{', '.join(dv)}]")
                    else:
                        lines.append(f"          {dk}: {dv}")
            # property names are preserved 1:1 from the blockstate keys
            lines.append("      appearances:")
            for i, (m, x, y) in enumerate(appears):
                lines.append(f"        app{i}:")
                lines.append(f"          auto_state: {PLANT_GROUP.get(bid, 'note_block') if bid in PLANT_BLOCKS else 'note_block'}")
                lines.append("          model:")
                lines.append(f"            path: {yaml_str(m)}")
                if x:
                    lines.append(f"            x: {x}")
                if y:
                    lines.append(f"            y: {y}")
            lines.append("      variants:")
            for c in combos:
                if not c["states"]:
                    continue
                conds = [f"{k}={c['states'][k]}" for k in sorted(c["states"])]
                app = appear_map[(c["model"], c["x"], c["y"])]
                lines.append(f"        {','.join(conds)}:")
                lines.append(f"          appearance: {app}")
            # variant-level settings overrides
            if bid == "stove":
                lines.append("        lit=true:")
                lines.append("          settings:")
                lines.append("            luminance: 13")

        # settings
        fam = BLOCK_FAMILY.get(bid, "wood")
        fs = FAMILY_SETTINGS[fam]
        lines.append("    settings:")
        lines.append(f"      hardness: {fs['hardness']}")
        lines.append(f"      sounds:")
        for act in ("break", "step", "place", "hit", "fall"):
            lines.append(f"        {act}: {fs['sounds']}.{act}")
        lines.append(f"      item: {yaml_str(f'{NS}:{bid}')}")
        if bid == "rice":
            lines.append("      fluid_state: water")

        # CE native storage GUI (cabinets + basket): right-click opens CE's own container
        if bid == "basket" or bid.endswith("_cabinet"):
            lines.append("    behavior:")
            lines.append("      type: simple_storage_block")
            lines.append(f"      title: {yaml_str('<!i><lang:block.' + NS + '.' + bid + '>')}")
            lines.append("      rows: 3")
            lines.append("      sounds:")
            lines.append("        open: minecraft:block.barrel.open")
            lines.append("        close: minecraft:block.barrel.close")

        # loot
        if bid not in PLUGIN_LOOT_BLOCKS:
            lines.append("    loot:")
            lines.append("      pools:")
            lines.append("        - rolls: 1")
            lines.append("          entries:")
            lines.append(f"            - type: item")
            lines.append(f"              item: {yaml_str(f'{NS}:{bid}')}")

        out.append("\n".join(lines))

    return "\n\n".join(out), warnings
