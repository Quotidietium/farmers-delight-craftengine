"""Main entry: generate the full CraftEngine pack + plugin-side configs."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ce_gen.common import clean_output, CE_PACK, write_config, PLUGIN_RECIPES, NS
from ce_gen.parse_registry import parse_foods, parse_items
from ce_gen.gen_assets import copy_assets
from ce_gen import gen_items, gen_furniture, gen_recipes

def main():
    print("== Farmer's Delight -> CraftEngine pack generator ==")
    clean_output()
    stats = copy_assets()
    sign_tex = gen_furniture.copy_sign_textures()
    print("assets:", stats, "sign textures:", sign_tex)

    foods = parse_foods()
    items = parse_items()
    print(f"parsed {len(items)} items, {len(foods)} foods")

    # -------- CE items
    bodies = []
    skipped = []
    for item in items:
        body = gen_items.emit_item(item, foods)
        if body:
            bodies.append(body)
        else:
            skipped.append(item["id"])
    write_config("items.yml", [("items", "\n\n".join(bodies))])
    print(f"items: {len(bodies)} emitted, {len(skipped)} skipped")

    # -------- CE blocks
    from ce_gen.gen_blocks import generate_blocks
    blocks_body, block_warnings = generate_blocks()
    write_config("blocks.yml", [("blocks", blocks_body)])
    print(f"blocks: generated with {len(block_warnings)} warnings")
    for w in block_warnings[:20]:
        print("  [warn]", w)

    # -------- CE furniture (+ internal display items must come after)
    furn_body = gen_furniture.generate_furniture()
    internal = gen_furniture.emit_internal_items()
    # sign display items use generated sign_ models
    write_config("furniture.yml", [("items", internal), ("furniture", furn_body)])
    print(f"furniture: {furn_body.count(NS + ':')} refs; internal items: {len(gen_furniture._internal_items)}")

    # -------- CE recipes
    chunks, recipe_warnings = gen_recipes.generate_ce_recipes()
    for i, chunk in enumerate(chunks):
        write_config(f"recipes_{i}.yml", [("recipes", chunk)])
    print(f"ce recipes: {len(chunks)} files; warnings: {len(recipe_warnings)}")
    for w in recipe_warnings[:20]:
        print("  [warn]", w)

    # -------- CE sounds & lang
    from ce_gen.gen_sounds_lang import generate_sounds, generate_lang
    sounds_body = generate_sounds()
    lang_body, lang_count = generate_lang()
    write_config("sounds.yml", [("sounds", sounds_body)])
    write_config("lang.yml", [("lang", lang_body)])
    print(f"sounds + {lang_count} lang locales")

    # -------- GUI helper assets (progress bar / heat indicator / filler)
    from ce_gen.gen_gui import copy_gui_assets, generate_gui_items
    gui_models, gui_textures = copy_gui_assets()
    gui_items = generate_gui_items()
    print(f"gui assets: {gui_models} models, {gui_textures} textures; gui items: {gui_items}")

    # -------- advanced vanilla crops (wheat/carrot/potato/beetroot on rich soil farmland)
    from ce_gen.gen_crops import generate_advanced_crops, generate_stems
    crop_items, crop_blocks = generate_advanced_crops()
    stem_items, stem_blocks, attached = generate_stems()
    print(f"advanced vanilla crops: {crop_items} seed redirects, {crop_blocks} blocks")
    print(f"advanced stems: {stem_items} seed redirects, {stem_blocks} stems + {attached} attached")

    # -------- plugin-side configs
    (PLUGIN_RECIPES / "cooking_recipes.yml").write_text(
        gen_recipes.generate_plugin_cooking(), encoding="utf-8")
    (PLUGIN_RECIPES / "cutting_recipes.yml").write_text(
        gen_recipes.generate_plugin_cutting(), encoding="utf-8")
    (PLUGIN_RECIPES / "food_effects.yml").write_text(
        gen_recipes.generate_food_effects(foods, items), encoding="utf-8")
    (PLUGIN_RECIPES / "plugin_content.yml").write_text(
        gen_recipes.generate_misc_content(), encoding="utf-8")
    print("plugin configs written:", list(p.name for p in PLUGIN_RECIPES.glob("*.yml")))

    # -------- categories
    from ce_gen.gen_categories import generate_categories
    cat_total = generate_categories()
    print(f"categories: 7 groups covering {cat_total}/158 items")

    # -------- advancements datapack + trigger config
    from ce_gen.gen_advancements import generate_advancements
    adv_count = generate_advancements()
    print(f"advancements datapack: {adv_count} entries")

    # -------- pack.yml
    (CE_PACK / "pack.yml").write_text(
        f"author: Zifiv, vectorwing, port-by-Zurker\n"
        f"version: 1.0.0\n"
        f"description: Farmer's Delight assets for the Papo plugin port\n"
        f"namespace: {NS}\n", encoding="utf-8")
    print("pack.yml written")
    print("== done ==")


if __name__ == "__main__":
    main()
