"""GUI helper assets (cooking progress bar / heat indicator / slot filler).

Models + textures are vendored from the REF CE pack (tools/ce_gen/assets_gui);
they are the item-sprite rework of the mod's cooking pot GUI texture.
The plugin paints progress on plain PAPER via custom model data 325001..325022
and the heat indicator on plain CAMPFIRE via 114001, so the CE items here exist
mainly to register those material+CMD overrides in the generated resource pack.
"""
import shutil
from pathlib import Path

from ce_gen.common import CE_RP, NS, write_config

SRC = Path(__file__).resolve().parents[0] / "assets_gui"


def copy_gui_assets() -> tuple[int, int]:
    models = 0
    textures = 0
    dst_models = CE_RP / "models" / "gui" / "icons"
    dst_textures = CE_RP / "textures" / "gui" / "sprites" / "icons"
    for f in sorted((SRC / "models" / "gui" / "icons").glob("*.json")):
        dst_models.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dst_models / f.name)
        models += 1
    for f in sorted((SRC / "textures" / "icons").glob("*.png")):
        dst_textures.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dst_textures / f.name)
        textures += 1
    return models, textures


def generate_gui_items() -> int:
    blocks = []

    # filler pane rendering the mod's empty-slot texture (black pane + CMD 114001)
    blocks.append(
        f'  "{NS}:gui_space":\n'
        f'    material: minecraft:black_stained_glass_pane\n'
        f'    texture: {NS}:gui/sprites/icons/empty\n'
        f'    custom_model_data: 114001\n'
        f'    data:\n'
        f'      item_name: ""'
    )

    # heat indicator (campfire + CMD 114001)
    blocks.append(
        f'  "{NS}:heat_indicator":\n'
        f'    material: minecraft:campfire\n'
        f'    model:\n'
        f'      type: minecraft:model\n'
        f'      path: {NS}:gui/icons/heat_indicator\n'
        f'    custom_model_data: 114001\n'
        f'    data:\n'
        f'      item_name: ""'
    )

    # 22 cooking progress stages (paper + CMD 325001..325022)
    for i in range(1, 23):
        blocks.append(
            f'  "{NS}:cooking_progress_{i}":\n'
            f'    material: minecraft:paper\n'
            f'    model:\n'
            f'      type: minecraft:model\n'
            f'      path: {NS}:gui/icons/cooking_progress_{i}\n'
            f'    custom_model_data: {325000 + i}\n'
            f'    data:\n'
            f'      item_name: ""'
        )

    write_config("gui.yml", [("items", "\n\n".join(blocks))])
    return len(blocks)
