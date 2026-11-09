package xuanmo.arcartxsuite.api.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;

/** Native recipes managed by Vanilla. Call on the Bukkit primary thread. */
@PublicCapability
public interface VanillaRecipeAccess {
    enum Station { SHAPED, SHAPELESS, FURNACE, BLAST_FURNACE, SMOKER, STONECUTTER, SMITHING, BREWING }
    enum SmithingData { INHERIT_BASE, FIXED_RESULT }

    /** Empty, material alternatives, or exact stack alternatives. Ingredient amounts are always one. */
    record Ingredient(List<Material> materials, List<ItemStack> exact) {
        public Ingredient {
            materials = List.copyOf(materials);
            if (!materials.isEmpty() && !exact.isEmpty()) {
                throw new IllegalArgumentException("Material and exact choices are separate match modes");
            }
            List<ItemStack> samples = new ArrayList<>(exact.size());
            for (ItemStack item : exact) {
                if (InventoryItems.empty(item)) throw new IllegalArgumentException("Empty exact item");
                ItemStack sample = item.clone();
                sample.setAmount(1);
                samples.add(sample);
            }
            exact = List.copyOf(samples);
        }

        @Override public List<ItemStack> exact() { return exact.stream().map(ItemStack::clone).toList(); }
        public boolean empty() { return materials.isEmpty() && exact.isEmpty(); }
        public static Ingredient blank() { return new Ingredient(List.of(), List.of()); }
    }

    /**
     * Shaped definitions have exactly nine row-major cells. Shapeless definitions have 1–9 ingredients.
     * Smithing uses template, base, addition. Brewing uses bottle input, ingredient.
     */
    record Definition(String id, Station station, List<Ingredient> ingredients, ItemStack result,
                      String permission, int cookingTicks, float experience, SmithingData smithingData) {
        public Definition {
            Objects.requireNonNull(id);
            Objects.requireNonNull(station);
            ingredients = List.copyOf(ingredients);
            if (InventoryItems.empty(result)) throw new IllegalArgumentException("Empty recipe result");
            result = result.clone();
            permission = permission == null ? "" : permission.trim();
            smithingData = smithingData == null ? SmithingData.INHERIT_BASE : smithingData;
        }

        @Override public ItemStack result() { return result.clone(); }
    }

    record Entry(NamespacedKey key, String owner, long revision, Definition definition) { }
    record Preview(Entry entry, boolean canFill) { }
    record FillReceipt(NamespacedKey key, long revision, boolean placed, String message) { }

    interface Registration extends AutoCloseable {
        NamespacedKey key();
        @Override void close();
    }

    Registration register(Plugin owner, Definition definition);
    Registration registerModule(String ownerModuleId, Definition definition);
    List<Entry> recipes();
    Optional<Entry> recipe(NamespacedKey key);
    CompletableFuture<Preview> preview(Player player, NamespacedKey key);
    CompletableFuture<FillReceipt> fill(Player player, NamespacedKey key, long expectedRevision);
}
