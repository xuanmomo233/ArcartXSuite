package xuanmo.arcartxsuite.vanilla.recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.potion.PotionBrewer;
import org.bukkit.potion.Potion;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station;
import xuanmo.arcartxsuite.api.inventory.ItemSnapshots;

final class NativeRecipeFactory {
    private NativeRecipeFactory() { }

    record Native(NamespacedKey key, Recipe recipe, Object potionMix, Method addMix, Method removeMix) {
        void add() {
            if (recipe != null) {
                if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("原版拒绝注册配方 " + key);
            } else invoke(addMix, potionMix);
        }

        void remove() {
            if (recipe != null) Bukkit.removeRecipe(key);
            else invoke(removeMix, key);
        }

        private void invoke(Method method, Object value) {
            try {
                method.invoke(Potion.getBrewer(), value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("无法更新酿造配方 " + key, exception);
            }
        }
    }

    static Native create(NamespacedKey key, Definition definition) {
        validate(definition);
        List<RecipeChoice> choices = definition.ingredients().stream().map(NativeRecipeFactory::choice).toList();
        ItemStack output = definition.result();
        if (definition.station() == Station.BREWING) {
            try {
                Class<?> mixClass = Class.forName("io.papermc.paper.potion.PotionMix");
                var ctor = mixClass.getConstructor(NamespacedKey.class, ItemStack.class, RecipeChoice.class, RecipeChoice.class);
                var mix = ctor.newInstance(key, output, choices.get(0), choices.get(1));
                return new Native(key, null, mix, PotionBrewer.class.getMethod("addPotionMix", mixClass),
                    PotionBrewer.class.getMethod("removePotionMix", NamespacedKey.class));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("当前平台缺少 Paper PotionMix 能力", exception);
            }
        }
        Recipe recipe = switch (definition.station()) {
            case SHAPED -> shaped(key, output, choices);
            case SHAPELESS -> {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, output);
                choices.forEach(shapeless::addIngredient);
                yield shapeless;
            }
            case FURNACE -> new FurnaceRecipe(key, output, choices.get(0), definition.experience(), definition.cookingTicks());
            case BLAST_FURNACE -> new BlastingRecipe(key, output, choices.get(0), definition.experience(), definition.cookingTicks());
            case SMOKER -> new SmokingRecipe(key, output, choices.get(0), definition.experience(), definition.cookingTicks());
            case STONECUTTER -> new StonecuttingRecipe(key, output, choices.get(0));
            case SMITHING -> smithing(key, definition, choices);
            case BREWING -> throw new AssertionError("brewing handled above");
        };
        return new Native(key, recipe, null, null, null);
    }

    private static RecipeChoice choice(Ingredient ingredient) {
        if (ingredient.empty()) return null;
        if (!ingredient.materials().isEmpty()) return new RecipeChoice.MaterialChoice(ingredient.materials());
        return new RecipeChoice.ExactChoice(ingredient.exact());
    }

    private static ShapedRecipe shaped(NamespacedKey key, ItemStack output, List<RecipeChoice> choices) {
        int left = 2, right = 0, top = 2, bottom = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i) == null) continue;
            left = Math.min(left, i % 3);
            right = Math.max(right, i % 3);
            top = Math.min(top, i / 3);
            bottom = Math.max(bottom, i / 3);
        }
        List<String> rows = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = left; x <= right; x++) {
                int slot = y * 3 + x;
                row.append(choices.get(slot) == null ? ' ' : (char) ('A' + slot));
            }
            rows.add(row.toString());
        }
        ShapedRecipe recipe = new ShapedRecipe(key, output).shape(rows.toArray(String[]::new));
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i) != null) recipe.setIngredient((char) ('A' + i), choices.get(i));
        }
        return recipe;
    }

    private static SmithingTransformRecipe smithing(NamespacedKey key, Definition definition, List<RecipeChoice> choices) {
        if (definition.smithingData() == SmithingData.INHERIT_BASE) {
            return new SmithingTransformRecipe(key, definition.result(), choices.get(0), choices.get(1), choices.get(2));
        }
        try {
            return SmithingTransformRecipe.class.getConstructor(NamespacedKey.class, ItemStack.class,
                RecipeChoice.class, RecipeChoice.class, RecipeChoice.class, boolean.class)
                .newInstance(key, definition.result(), choices.get(0), choices.get(1), choices.get(2), false);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("当前平台不支持固定锻造产物数据", exception);
        }
    }

    private static void validate(Definition definition) {
        if (!definition.id().matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("配方 ID 格式无效");
        int total = definition.ingredients().size();
        long used = definition.ingredients().stream().filter(ingredient -> !ingredient.empty()).count();
        boolean valid = switch (definition.station()) {
            case SHAPED -> total == 9 && used > 0;
            case SHAPELESS -> total >= 1 && total <= 9 && used == total;
            case SMITHING -> total == 3 && used == total;
            case BREWING -> total == 2 && used == total;
            default -> total == 1 && used == 1;
        };
        if (!valid) throw new IllegalArgumentException("材料格数量与工位不匹配");
        if (!crafting(definition.station()) && !definition.permission().isEmpty()) {
            throw new IllegalArgumentException("自动工位配方不支持玩家权限限制");
        }
        if (!Float.isFinite(definition.experience()) || definition.experience() < 0
            || definition.cookingTicks() < 1) throw new IllegalArgumentException("耗时或经验无效");
        ItemStack result = definition.result();
        if (result.getAmount() > result.getMaxStackSize()) throw new IllegalArgumentException("产物超过原版堆叠上限");
        if (definition.station() == Station.BREWING && result.getAmount() != 1) {
            throw new IllegalArgumentException("酿造产物每瓶只能生成一个物品");
        }
        validateSnapshot(result);
        for (Ingredient ingredient : definition.ingredients()) {
            if (ingredient.materials().size() + ingredient.exact().size() > 64) {
                throw new IllegalArgumentException("每格最多 64 个材料备选");
            }
            for (Material material : ingredient.materials()) {
                if (!material.isItem() || material.isAir()) throw new IllegalArgumentException("材料必须是物品");
            }
            ingredient.exact().forEach(NativeRecipeFactory::validateSnapshot);
        }
    }

    static void validateSnapshot(ItemStack item) {
        if (!item.equals(ItemSnapshots.decode(ItemSnapshots.encode(item)))) {
            throw new IllegalArgumentException("物品数据无法无损还原");
        }
    }

    static boolean crafting(Station station) { return station == Station.SHAPED || station == Station.SHAPELESS; }
}
