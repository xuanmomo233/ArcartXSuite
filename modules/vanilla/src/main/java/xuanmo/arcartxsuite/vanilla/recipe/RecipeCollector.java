package xuanmo.arcartxsuite.vanilla.recipe;

import xuanmo.arcartxsuite.api.inventory.MaterialStock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 原版配方收集器，遍历服务端已注册的合成/熔炼配方，
 * 转换为可发送给客户端 UI 的数据结构（含中文名解析）。
 */
public final class RecipeCollector {
    private final RecipeMaterialPlanner planner = new RecipeMaterialPlanner();
    public RecipeMaterialPlanner materialPlanner() { return planner; }

    private final VanillaItemNameBridge nameBridge;
    private final Map<Material, String> itemIcons = new EnumMap<>(Material.class);

    public RecipeCollector(VanillaItemNameBridge nameBridge) {
        this.nameBridge = nameBridge;
    }

    public Map<String, Object> collectRecipes(Player player, WorkshopType type, MaterialStock stock) {
        if (type == WorkshopType.CRAFTING) return collectCraftingRecipes(player, stock);
        var availability = planner.availability(player, type, stock);
        Map<String, Object> recipes = new LinkedHashMap<>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!type.visibleTo(player, recipe)) continue;
            Map<String, Object> entry;
            if (recipe instanceof org.bukkit.inventory.CookingRecipe<?> cooking) {
                entry = buildCookingEntry(cooking.getResult(), sample(cooking.getInputChoice()), type.key());
            } else if (recipe instanceof org.bukkit.inventory.StonecuttingRecipe stone) {
                entry = buildCookingEntry(stone.getResult(), sample(stone.getInputChoice()), type.key());
            } else if (recipe instanceof org.bukkit.inventory.SmithingRecipe smith) {
                org.bukkit.inventory.RecipeChoice template = recipe instanceof org.bukkit.inventory.SmithingTransformRecipe transform
                    ? transform.getTemplate() : ((org.bukkit.inventory.SmithingTrimRecipe) recipe).getTemplate();
                ItemStack equipment = availability.station()[1];
                ItemStack base = equipment != null && smith.getBase().test(equipment) ? equipment.clone() : sample(smith.getBase());
                base.setAmount(1);
                ItemStack result = recipe instanceof org.bukkit.inventory.SmithingTrimRecipe ? sample(template) : smith.getResult();
                entry = buildCookingEntry(result, sample(template), type.key());
                entry.put("previewGrid", List.of(serializeItemWithLore(sample(template)), serializeItemWithLore(base), serializeItemWithLore(sample(smith.getAddition()))));
                entry.put("previewAmounts", List.of(1, 1, 1));
                if (recipe instanceof org.bukkit.inventory.SmithingTrimRecipe) {
                    entry.put("resultZhName", "纹饰 · " + translate(result.getType()));
                    entry.put("resultName", "armor trim " + result.getType().name().toLowerCase());
                }
            } else continue;
            entry.put("canCraft", availability.canMake(recipe) ? 1 : 0);
            recipes.put(((org.bukkit.Keyed) recipe).getKey().toString(), entry);
        }
        return recipes;
    }

    private static ItemStack sample(org.bukkit.inventory.RecipeChoice choice) {
        ItemStack item = choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exact
            ? exact.getChoices().get(0).clone()
            : new ItemStack(((org.bukkit.inventory.RecipeChoice.MaterialChoice) choice).getChoices().get(0));
        item.setAmount(1);
        return item;
    }

    public Map<String, Object> collectCraftingRecipes(Player player) {
        return collectCraftingRecipes(player, planner.stock(player));
    }
    public Map<String, Object> collectCraftingRecipes(Player player, MaterialStock stock) {
        Map<String, Object> recipes = new LinkedHashMap<>();
        Set<NamespacedKey> discovered = player.getDiscoveredRecipes();
        if (discovered.isEmpty()) return recipes;
        var availability = planner.availability(player, WorkshopType.CRAFTING, stock);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof ShapedRecipe) {
                ShapedRecipe shaped = (ShapedRecipe) recipe;
                if (!discovered.contains(shaped.getKey())) continue;
                Map<String, Object> entry = buildShapedEntry(shaped);
                entry.put("canCraft", availability.canMake(shaped) ? 1 : 0);
                recipes.put(shaped.getKey().toString(), entry);
            } else if (recipe instanceof ShapelessRecipe) {
                ShapelessRecipe shapeless = (ShapelessRecipe) recipe;
                if (!discovered.contains(shapeless.getKey())) continue;
                Map<String, Object> entry = buildShapelessEntry(shapeless);
                entry.put("canCraft", availability.canMake(shapeless) ? 1 : 0);
                recipes.put(shapeless.getKey().toString(), entry);
            }
        }
        return recipes;
    }

    public Map<String, Object> collectFurnaceRecipes(Player player) {
        return collectFurnaceRecipes(player, planner.stock(player));
    }
    public Map<String, Object> collectFurnaceRecipes(Player player, MaterialStock stock) {
        // 只收集 FurnaceRecipe，还原原版行为：熔炉配方书只显示熔炉配方。
        // SmokingRecipe / BlastingRecipe 留给将来烟熏炉/高炉 UI 替换使用。
        Map<String, Object> recipes = new LinkedHashMap<>();
        Set<NamespacedKey> discovered = player.getDiscoveredRecipes();
        if (discovered.isEmpty()) return recipes;
        var availability = planner.availability(player, WorkshopType.FURNACE, stock);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof FurnaceRecipe) {
                FurnaceRecipe furnace = (FurnaceRecipe) recipe;
                if (!discovered.contains(furnace.getKey())) continue;
                Map<String, Object> entry = buildCookingEntry(furnace.getResult(), furnace.getInput(), "furnace");
                entry.put("canCraft", availability.canMake(furnace) ? 1 : 0);
                recipes.put(furnace.getKey().toString(), entry);
            }
        }
        return recipes;
    }

    private Map<String, Object> buildShapedEntry(ShapedRecipe recipe) {
        Map<String, Object> entry = new LinkedHashMap<>();
        addResult(entry, recipe.getResult());
        entry.put("shaped", true);
        String[] shape = recipe.getShape();
        entry.put("width", shape.length > 0 ? shape[0].length() : 0);
        entry.put("height", shape.length);
        Map<String, String> ingredientMap = new LinkedHashMap<>();
        Map<Character, ItemStack> rawIngredients = recipe.getIngredientMap();
        for (Map.Entry<Character, ItemStack> e : rawIngredients.entrySet()) {
            if (e.getKey() != null) {
                ingredientMap.put(e.getKey().toString(), serializeItem(e.getValue()));
            }
        }
        entry.put("ingredients", ingredientMap);
        List<String> shapeList = new ArrayList<>();
        for (String row : shape) shapeList.add(row);
        entry.put("shape", shapeList);
        entry.put("previewGrid", buildShapedPreviewGrid(shape, rawIngredients));
        List<Integer> amounts = new ArrayList<>(Collections.nCopies(9, 0));
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                ItemStack ingredient = rawIngredients.get(shape[row].charAt(col));
                if (ingredient != null) amounts.set(row * 3 + col, ingredient.getAmount());
            }
        }
        entry.put("previewAmounts", amounts);
        return entry;
    }

    private Map<String, Object> buildShapelessEntry(ShapelessRecipe recipe) {
        Map<String, Object> entry = new LinkedHashMap<>();
        addResult(entry, recipe.getResult());
        entry.put("shaped", false);
        entry.put("width", 3);
        entry.put("height", 3);
        List<String> ingredientList = new ArrayList<>();
        List<ItemStack> ingredients = recipe.getIngredientList();
        for (ItemStack item : ingredients) {
            ingredientList.add(serializeItem(item));
        }
        entry.put("ingredients", ingredientList);
        entry.put("previewGrid", buildShapelessPreviewGrid(ingredients));
        List<Integer> amounts = new ArrayList<>(Collections.nCopies(9, 0));
        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) amounts.set(i, ingredients.get(i).getAmount());
        entry.put("previewAmounts", amounts);
        return entry;
    }

    private Map<String, Object> buildCookingEntry(ItemStack result, ItemStack input, String type) {
        Map<String, Object> entry = new LinkedHashMap<>();
        addResult(entry, result);
        String inputIcon = serializeItem(input);
        entry.put("inputJson", inputIcon);
        entry.put("type", type);
        entry.put("previewGrid", List.of(inputIcon));
        entry.put("previewAmounts", List.of(input.getAmount()));
        return entry;
    }

    private void addResult(Map<String, Object> entry, ItemStack result) {
        entry.put("resultJson", serializeItemWithLore(result));
        entry.put("resultAmount", result.getAmount());
        entry.put("resultItemId", result.getType().getKey().toString());
        entry.put("resultName", result.getType().name().toLowerCase().replace('_', ' '));
        entry.put("resultZhName", translate(result.getType()));
        entry.put("category", categorizeItem(result));
    }

    private List<String> buildShapedPreviewGrid(String[] shape, Map<Character, ItemStack> ingredientMap) {
        String air = serializeItem(null);
        List<String> grid = new ArrayList<>(Collections.nCopies(9, air));
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                char c = shape[row].charAt(col);
                if (c == ' ') continue;
                ItemStack item = ingredientMap.get(c);
                grid.set(row * 3 + col, serializeItemWithLore(item));
            }
        }
        return grid;
    }

    private List<String> buildShapelessPreviewGrid(List<ItemStack> ingredients) {
        String air = serializeItem(null);
        List<String> grid = new ArrayList<>(Collections.nCopies(9, air));
        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
            grid.set(i, serializeItemWithLore(ingredients.get(i)));
        }
        return grid;
    }

    public String serializeItem(ItemStack item) {
        Material material = item == null ? Material.AIR : item.getType();
        return itemIcons.computeIfAbsent(material, type -> "{\"id\":\"" + type.getKey() + "\",\"Count\":1b}");
    }

    public String serializeIconWithLore(Material material, String displayName, String lore) {
        String id = material.getKey().toString();
        String nameComponent = "{\"text\":\"" + displayName + "\"}";
        String loreComponent = "{\"text\":\"" + lore + "\"}";
        String escapedName = nameComponent.replace("\\", "\\\\").replace("\"", "\\\"");
        String escapedLore = loreComponent.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"" + id + "\",\"Count\":1b,\"tag\":{\"HideFlags\":63,\"display\":{\"Name\":\"" + escapedName + "\",\"Lore\":[\"" + escapedLore + "\"]}}}";
    }

    public Map<String, String> craftingCategoryIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("book", serializeIconWithLore(Material.BOOK, "§f配方书", "§7点击打开/关闭配方书"));
        icons.put("toggle", serializeIconWithLore(Material.CRAFTING_TABLE, "§f显示切换", "§7点击切换显示模式"));
        icons.put("all", serializeIconWithLore(Material.COMPASS, "§f全部", "§7点击查看所有配方"));
        icons.put("tools", serializeIconWithLore(Material.IRON_AXE, "§f工具", "§7点击查看工具配方"));
        icons.put("building", serializeIconWithLore(Material.BRICKS, "§f建筑", "§7点击查看建筑配方"));
        icons.put("food", serializeIconWithLore(Material.LAVA_BUCKET, "§f食品", "§7点击查看食品配方"));
        icons.put("redstone", serializeIconWithLore(Material.REDSTONE, "§f红石", "§7点击查看红石配方"));
        return icons;
    }

    public Map<String, String> furnaceCategoryIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("book", serializeIconWithLore(Material.BOOK, "§f配方书", "§7点击打开/关闭配方书"));
        icons.put("toggle", serializeIconWithLore(Material.FURNACE, "§f显示切换", "§7点击切换显示模式"));
        icons.put("all", serializeIconWithLore(Material.COMPASS, "§f全部", "§7点击查看所有配方"));
        icons.put("food", serializeIconWithLore(Material.PORKCHOP, "§f食品", "§7点击查看食品配方"));
        icons.put("building", serializeIconWithLore(Material.STONE, "§f建筑", "§7点击查看建筑配方"));
        icons.put("misc", serializeIconWithLore(Material.LAVA_BUCKET, "§f杂项", "§7点击查看杂项配方"));
        return icons;
    }

    private boolean canCraftShaped(Player player, ShapedRecipe recipe) {
        Map<Material, Integer> needed = new HashMap<>();
        Map<Character, ItemStack> ingredients = recipe.getIngredientMap();
        for (String row : recipe.getShape()) {
            for (char symbol : row.toCharArray()) {
                ItemStack item = ingredients.get(symbol);
                if (item == null || item.getType().isAir()) continue;
                needed.merge(item.getType(), 1, Integer::sum);
            }
        }
        return hasMaterials(player, needed);
    }

    private boolean canCraftShapeless(Player player, ShapelessRecipe recipe) {
        Map<Material, Integer> needed = new HashMap<>();
        for (ItemStack item : recipe.getIngredientList()) {
            if (item == null || item.getType() == Material.AIR) continue;
            Material mat = item.getType();
            needed.merge(mat, 1, Integer::sum);
        }
        return hasMaterials(player, needed);
    }

    private boolean hasMaterials(Player player, Map<Material, Integer> needed) {
        Map<Material, Integer> have = new HashMap<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            have.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        for (Map.Entry<Material, Integer> e : needed.entrySet()) {
            int got = have.getOrDefault(e.getKey(), 0);
            if (got < e.getValue()) return false;
        }
        return true;
    }

    public String serializeItemWithLore(ItemStack item) {
        return serializeItem(item);
    }

    private String categorizeItem(ItemStack result) {
        if (result == null) return "misc";
        Material type = result.getType();
        String name = type.name();

        if (type.isEdible()) return "food";

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.endsWith("_ARROW") || name.equals("TRIDENT") || name.equals("TURTLE_HELMET")
                || name.equals("ELYTRA") || name.equals("SHIELD"))
            return "equipment";

        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.endsWith("_SWORD") || name.equals("SHEARS")
                || name.equals("FLINT_AND_STEEL") || name.equals("FISHING_ROD")
                || name.equals("COMPASS") || name.equals("CLOCK") || name.equals("MAP")
                || name.equals("SPYGLASS") || name.equals("BRUSH") || name.equals("CARTOGRAPHY_TABLE")
                || name.equals("SMITHING_TABLE") || name.equals("GRINDSTONE") || name.equals("STONECUTTER")
                || name.equals("LOOM") || name.equals("ANVIL") || name.endsWith("_ANVIL"))
            return "tools";

        if (name.contains("REDSTONE") || name.endsWith("_REPEATER") || name.endsWith("_COMPARATOR")
                || name.endsWith("_PISTON") || name.equals("OBSERVER") || name.equals("HOPPER")
                || name.equals("DROPPER") || name.equals("DISPENSER") || name.equals("LEVER")
                || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_RAIL")
                || name.equals("TRIPWIRE_HOOK") || name.equals("DAYLIGHT_DETECTOR")
                || name.equals("NOTE_BLOCK") || name.equals("JUKEBOX") || name.equals("TARGET")
                || name.equals("SLIME_BLOCK") || name.equals("HONEY_BLOCK") || name.equals("TNT"))
            return "redstone";

        if (type.isBlock()) return "building";

        return "misc";
    }

    private String translate(Material material) {
        if (nameBridge != null && nameBridge.isAvailable()) {
            return nameBridge.translate(material);
        }
        return material.name().toLowerCase().replace('_', ' ');
    }
}
