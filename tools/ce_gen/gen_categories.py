"""Generate CE item-browser categories covering every FD item exactly once."""
from .common import NS, write_config, yaml_str
from .parse_registry import parse_items, item_container_ref

CATEGORIES = [
    # (id, icon, priority, matcher)
    ("cooking", "cooking_pot", 1, lambda iid: iid in {
        "stove", "cooking_pot", "cutting_board", "basket"}),
    ("farming", "cabbage", 2, lambda iid: (
            iid in {"cabbage_seeds", "tomato_seeds", "onion", "rice", "cabbage", "tomato",
                    "rice_panicle", "organic_compost", "rich_soil", "rich_soil_farmland",
                    "sandy_shrub", "rice_bale", "straw_bale"}
            or iid.startswith("wild_")
            or iid.endswith("_mushroom_colony"))),
    ("foods", "tomato", 3, lambda iid: True),  # fallback bucket (filled last)
    ("meals", "beef_stew", 4, lambda iid: iid in {
        "cooked_rice", "bone_broth", "beef_stew", "chicken_soup", "vegetable_soup", "fish_stew",
        "fried_rice", "pumpkin_soup", "baked_cod_stew", "noodle_soup", "bacon_and_eggs",
        "pasta_with_meatballs", "pasta_with_mutton_chop", "mushroom_rice", "roasted_mutton_chops",
        "vegetable_noodles", "steak_and_potatoes", "ratatouille", "squid_ink_pasta", "grilled_salmon",
        "roast_chicken", "stuffed_pumpkin", "honey_glazed_ham", "shepherds_pie",
        "milk_bottle", "hot_cocoa", "apple_cider", "melon_juice",
        "dog_food", "horse_feed"}),
    ("tools", "iron_knife", 5, lambda iid: iid.endswith("_knife") or iid == "skillet"),
    ("materials", "straw", 6, lambda iid: iid in {
        "straw", "canvas", "tree_bark", "rotten_tomato"}),
    ("furniture", "oak_cabinet", 7, lambda iid: (
            iid.endswith("_cabinet") or iid.endswith("_canvas_sign") or iid.endswith("_canvas_wall_sign")
            or iid in {"canvas_rug", "tatami", "full_tatami_mat", "half_tatami_mat", "rope",
                       "safety_net", "rice_bag", "apple_pie", "sweet_berry_cheesecake",
                       "chocolate_pie"}
            or iid.endswith("_block") or iid.endswith("_crate"))),
]


def generate_categories() -> int:
    items = parse_items()
    assigned: dict[str, str] = {}
    warnings = []
    for iid, item in [(i["id"], i) for i in items]:
        for cat, _, _, matcher in CATEGORIES:
            if cat == "foods":
                continue
            if matcher(iid):
                assigned[iid] = cat
                break
        else:
            assigned[iid] = "foods"

    lines = []
    total = 0
    for cat, icon, priority, _ in CATEGORIES:
        members = sorted(i for i, c in assigned.items() if c == cat)
        total += len(members)
        if not members:
            continue
        lines.append(f"  {yaml_str(f'{NS}:{cat}')}:")
        lines.append(f"    name: {yaml_str(f'<!i><green><lang:category.{NS}.{cat}></green>')}")
        lines.append("    lore: []")
        lines.append("    hidden: false")
        lines.append(f"    priority: {priority}")
        lines.append(f"    icon: {yaml_str(f'{NS}:{icon}')}")
        lines.append("    list:")
        for m in members:
            lines.append(f"      - {yaml_str(f'{NS}:{m}')}")
    write_config("categories.yml", [("categories", "\n".join(lines))])
    if total != len(items):
        warnings.append(f"category coverage {total}/{len(items)}")
    return total


def category_warnings() -> list[str]:
    return []
