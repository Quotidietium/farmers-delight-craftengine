"""Copy mod textures, models and sounds into the CraftEngine pack resource folder."""
import shutil
from pathlib import Path

from .common import MOD_ASSETS, CE_RP


def copy_assets():
    stats = {}
    # textures: block/ and item/ (skip entity/gui textures used only by custom renderers,
    # but copy entity signs for canvas sign usage later if needed)
    for sub in ("block", "item"):
        src = MOD_ASSETS / "textures" / sub
        dst = CE_RP / "textures" / sub
        if src.exists():
            if dst.exists():
                shutil.rmtree(dst)
            shutil.copytree(src, dst)
            stats[f"textures/{sub}"] = sum(1 for _ in src.rglob("*.png"))
    # models: block/ and item/ JSONs are copied verbatim; CE references them by path
    for sub in ("block", "item"):
        src = MOD_ASSETS / "models" / sub
        dst = CE_RP / "models" / sub
        if src.exists():
            if dst.exists():
                shutil.rmtree(dst)
            shutil.copytree(src, dst)
            stats[f"models/{sub}"] = sum(1 for _ in src.rglob("*.json"))
    # sounds
    src = MOD_ASSETS / "sounds"
    dst = CE_RP / "sounds"
    if src.exists():
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        stats["sounds"] = sum(1 for _ in src.rglob("*.ogg"))
    # entity sign textures for canvas signs
    src = MOD_ASSETS / "textures" / "entity"
    if src.exists():
        dst = CE_RP / "textures" / "entity"
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        stats["textures/entity"] = sum(1 for _ in src.rglob("*.png"))
    return stats
