"""Copy mod textures, models and sounds into the CraftEngine pack resource folder."""
import shutil
from pathlib import Path

from .common import MOD_ASSETS, CE_RP

# REF pack model/texture rework (tools/ce_gen/assets_ref) overlaid on the raw mod
# assets after copying - these are the reference pack's deliberate visual fixes
# (display parents, feast leftovers artwork, tray/tatami textures, farmland template)
REF_OVERLAY = Path(__file__).resolve().parents[0] / "assets_ref"


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
    # REF overlay: reference-pack visual fixes on top of the raw mod assets
    if REF_OVERLAY.exists():
        overlay_files = 0
        for sub in ("block",):
            for f in (REF_OVERLAY / "models" / sub).glob("*.json"):
                shutil.copy(f, CE_RP / "models" / sub / f.name)
                overlay_files += 1
            for f in (REF_OVERLAY / "textures" / sub).glob("*.png"):
                shutil.copy(f, CE_RP / "textures" / sub / f.name)
                overlay_files += 1
        stats["ref_overlay"] = overlay_files
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
