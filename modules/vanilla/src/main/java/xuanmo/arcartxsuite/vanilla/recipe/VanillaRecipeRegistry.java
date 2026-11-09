package xuanmo.arcartxsuite.vanilla.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess;
import xuanmo.arcartxsuite.api.event.ModuleLifecycleEvent;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

public final class VanillaRecipeRegistry implements VanillaRecipeAccess, Listener, AutoCloseable {
    private static final String CONFIG = "axs_recipe_config";
    private record Owner(String namespace, Plugin plugin, String module) { }
    private record Installed(Owner owner, Entry entry, NativeRecipeFactory.Native recipe) { }
    private final RecipeFile file;
    private final RecipeMaterialPlanner planner;
    private final BrewingRecipes brewing = new BrewingRecipes();
    private final Map<NamespacedKey, Installed> entries = new LinkedHashMap<>();
    private long nextRevision = System.currentTimeMillis();
    private long editRevision;
    private String diskDigest;
    private boolean closed;

    public VanillaRecipeRegistry(File folder, ItemSourceRegistry items, RecipeMaterialPlanner planner) {
        this.file = new RecipeFile(folder.toPath().resolve("recipes.yml"), items);
        this.planner = planner;
    }

    public void reload() throws Exception {
        requireMain();
        RecipeFile.Document document = file.read();
        replace(configured(document.definitions()));
        diskDigest = document.digest();
        editRevision = Math.max(editRevision + 1, document.revision());
    }

    public long editRevision() { return editRevision; }
    public boolean configured(Entry entry) { return entry.key().getNamespace().equals(CONFIG); }

    public void save(String id, Definition definition, long expected) throws Exception {
        requireMain();
        if (expected != editRevision) throw new IllegalStateException("配方已被修改，请刷新后重新编辑");
        if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("配方 ID 格式无效");
        RecipeFile.Document current = file.read();
        if (!current.digest().equals(diskDigest)) throw new IllegalStateException("recipes.yml 已修改，请先重载");
        current.yaml().set("recipes." + id, null);
        if (definition != null) current.yaml().createSection("recipes." + id, RecipeFile.serialize(definition));
        current.yaml().set("config-version", 1);
        current.yaml().set("revision", editRevision + 1);
        var previous = new LinkedHashMap<>(entries);
        var candidate = file.parse(current.yaml(), "").definitions();
        replace(configured(candidate));
        try {
            diskDigest = file.write(current.yaml());
            editRevision++;
        } catch (Exception exception) {
            try { replace(previous); }
            catch (RuntimeException rollback) { exception.addSuppressed(rollback); }
            throw exception;
        }
    }

    private Map<NamespacedKey, Installed> configured(Map<String, Definition> definitions) {
        var candidate = new LinkedHashMap<>(entries);
        candidate.entrySet().removeIf(entry -> configured(entry.getValue().entry()));
        Owner owner = new Owner(CONFIG, null, null);
        definitions.values().forEach(definition -> {
            var installed = prepare(owner, definition);
            candidate.put(installed.entry().key(), installed);
        });
        return candidate;
    }

    @Override
    public Registration register(Plugin plugin, Definition definition) {
        requireMain();
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin owner must be enabled");
        return register(new Owner("axs_recipe_plugin_" + normalize(plugin.getName()), plugin, null), definition);
    }

    @Override
    public Registration registerModule(String owner, Definition definition) {
        requireMain();
        return register(new Owner("axs_recipe_module_" + normalize(owner), null, owner), definition);
    }

    private static String normalize(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid recipe owner");
        return normalized;
    }

    private Registration register(Owner owner, Definition definition) {
        Installed installed = prepare(owner, definition);
        NamespacedKey key = installed.entry().key();
        if (entries.containsKey(key)) throw new IllegalArgumentException("配方已注册 " + key);
        var candidate = new LinkedHashMap<>(entries);
        candidate.put(key, installed);
        replace(candidate);
        return new Registration() {
            @Override public NamespacedKey key() { return key; }
            @Override public void close() {
                requireMain();
                if (entries.get(key) == installed) removeOwnerEntries(value -> value == installed);
            }
        };
    }

    private Installed prepare(Owner owner, Definition definition) {
        NamespacedKey key = new NamespacedKey(owner.namespace(), definition.id());
        Installed current = entries.get(key);
        if (current != null && current.owner().equals(owner) && current.entry().definition().equals(definition)) return current;
        return new Installed(owner, new Entry(key, owner.namespace(), ++nextRevision, definition),
            NativeRecipeFactory.create(key, definition));
    }

    private void replace(Map<NamespacedKey, Installed> candidate) {
        List<Installed> removed = entries.values().stream()
            .filter(entry -> candidate.get(entry.entry().key()) != entry).toList();
        List<Installed> added = candidate.values().stream()
            .filter(entry -> entries.get(entry.entry().key()) != entry).toList();
        for (Installed entry : added) {
            NamespacedKey key = entry.entry().key();
            if (!entries.containsKey(key) && Bukkit.getRecipe(key) != null) {
                throw new IllegalArgumentException("原版配方键已被占用 " + key);
            }
        }
        List<Installed> unregistered = new ArrayList<>();
        List<Installed> registered = new ArrayList<>();
        try {
            for (Installed entry : removed) {
                entry.recipe().remove();
                unregistered.add(entry);
            }
            for (Installed entry : added) {
                entry.recipe().add();
                registered.add(entry);
            }
            for (Installed entry : added) verifyExample(entry);
        } catch (RuntimeException exception) {
            Collections.reverse(registered);
            for (Installed entry : registered) {
                try { entry.recipe().remove(); }
                catch (RuntimeException rollback) { exception.addSuppressed(rollback); }
            }
            for (Installed entry : unregistered) {
                try { entry.recipe().add(); }
                catch (RuntimeException rollback) { exception.addSuppressed(rollback); }
            }
            Bukkit.getOnlinePlayers().forEach(this::discover);
            throw exception;
        }
        entries.clear();
        entries.putAll(candidate);
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Installed entry : removed) {
                if (discoverable(entry.entry())) player.undiscoverRecipe(entry.entry().key());
            }
            discover(player);
        }
    }

    private void verifyExample(Installed entry) {
        Definition definition = entry.entry().definition();
        if (!NativeRecipeFactory.crafting(definition.station()) || Bukkit.getWorlds().isEmpty()) return;
        ItemStack[] example = new ItemStack[9];
        for (int i = 0; i < definition.ingredients().size(); i++) {
            Ingredient choice = definition.ingredients().get(i);
            if (!choice.empty()) example[i] = example(choice);
        }
        if (!(Bukkit.getCraftingRecipe(example, Bukkit.getWorlds().get(0)) instanceof Keyed match)
            || !match.getKey().equals(entry.entry().key())) {
            throw new IllegalArgumentException("样品命中其他原版配方或平台未保留精确材料 " + entry.entry().key());
        }
    }

    @Override public List<Entry> recipes() { return entries.values().stream().map(Installed::entry).toList(); }
    @Override public Optional<Entry> recipe(NamespacedKey key) {
        return Optional.ofNullable(entries.get(key)).map(Installed::entry);
    }

    public boolean valid(Player player, NamespacedKey key, long revision) {
        Installed installed = entries.get(key);
        return !closed && installed != null && installed.entry().revision() == revision && allowed(player, installed.entry());
    }

    public boolean allowed(Player player, Entry entry) {
        return entry.definition().permission().isEmpty() || player.hasPermission(entry.definition().permission());
    }

    @Override
    public CompletableFuture<Preview> preview(Player player, NamespacedKey key) {
        requireMain();
        Entry entry = recipe(key).orElseThrow(() -> new IllegalArgumentException("配方不存在"));
        var future = new CompletableFuture<Preview>();
        planner.stockAsync(player, stock -> {
            try {
                Installed installed = entries.get(key);
                boolean possible = valid(player, key, entry.revision()) && (installed.recipe().recipe() != null
                    ? planner.canMake(player, installed.recipe().recipe(), stock)
                    : brewDemand(player, entry, stock) != null);
                future.complete(new Preview(entry, possible));
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<FillReceipt> fill(Player player, NamespacedKey key, long revision) {
        requireMain();
        if (!valid(player, key, revision)) {
            return CompletableFuture.completedFuture(new FillReceipt(key, revision, false, "配方已更新或没有使用权限"));
        }
        Entry entry = entries.get(key).entry();
        WorkshopType type = switch (entry.definition().station()) {
            case SHAPED, SHAPELESS -> WorkshopType.CRAFTING;
            case FURNACE -> WorkshopType.FURNACE;
            case BLAST_FURNACE -> WorkshopType.BLAST_FURNACE;
            case SMOKER -> WorkshopType.SMOKER;
            case STONECUTTER -> WorkshopType.STONECUTTER;
            case SMITHING -> WorkshopType.SMITHING;
            case BREWING -> WorkshopType.BREWING;
        };
        var future = new CompletableFuture<FillReceipt>();
        java.util.function.Consumer<String> done = message ->
            future.complete(new FillReceipt(key, revision, message.isEmpty(), message));
        if (type == WorkshopType.BREWING) {
            planner.fillCustom(player, type, stock -> brewDemand(player, entry, stock), done);
        } else {
            planner.fill(player, key.toString(), type, () -> valid(player, key, revision), done);
        }
        return future;
    }

    private RecipeMaterialPlanner.CustomDemand brewDemand(Player player, Entry entry, MaterialStock stock) {
        if (!valid(player, entry.key(), entry.revision()) || !WorkshopType.BREWING.matches(player.getOpenInventory())) return null;
        var top = player.getOpenInventory().getTopInventory();
        List<ItemStack> available = new ArrayList<>(Arrays.asList(top.getContents()));
        available.addAll(Arrays.asList(player.getInventory().getStorageContents()));
        stock.entries().forEach(row -> available.add(row.item()));
        var inputs = entry.definition().ingredients();
        List<ItemStack> bottles = available.stream().filter(item -> matches(inputs.get(0), item)).toList();
        List<ItemStack> ingredients = available.stream().filter(item -> matches(inputs.get(1), item)).toList();
        for (ItemStack bottle : bottles) for (ItemStack ingredient : ingredients) {
            var output = entry.definition().result();
            if (!output.equals(brewing.result(bottle, ingredient))) continue;
            ItemStack oneBottle = bottle.clone(), oneIngredient = ingredient.clone();
            oneBottle.setAmount(1);
            oneIngredient.setAmount(1);
            var wanted = Arrays.asList(oneBottle, null, null, oneIngredient);
            if (ExactMaterialPlan.build(player, WorkshopType.BREWING, wanted, stock) != null) {
                return new RecipeMaterialPlanner.CustomDemand(wanted,
                    () -> valid(player, entry.key(), entry.revision()) && output.equals(brewing.result(oneBottle, oneIngredient)),
                    entry.key() + "@" + entry.revision());
            }
        }
        return null;
    }

    private static ItemStack example(Ingredient choice) {
        return choice.materials().isEmpty() ? choice.exact().get(0) : new ItemStack(choice.materials().get(0));
    }

    private static boolean matches(Ingredient ingredient, ItemStack item) {
        return !InventoryItems.empty(item) && (ingredient.materials().contains(item.getType())
            || ingredient.exact().stream().anyMatch(item::isSimilar));
    }

    private static boolean discoverable(Entry entry) {
        return NativeRecipeFactory.crafting(entry.definition().station()) || switch (entry.definition().station()) {
            case FURNACE, BLAST_FURNACE, SMOKER -> true;
            default -> false;
        };
    }

    private void discover(Player player) {
        for (Installed installed : entries.values()) {
            Entry entry = installed.entry();
            if (discoverable(entry) && allowed(player, entry) && !player.hasDiscoveredRecipe(entry.key())) {
                player.discoverRecipe(entry.key());
            }
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) { discover(event.getPlayer()); }
    @EventHandler public void pluginDisable(PluginDisableEvent event) {
        removeOwnerEntries(entry -> entry.owner().plugin() == event.getPlugin());
    }
    @EventHandler public void moduleDisable(ModuleLifecycleEvent event) {
        if (event.phase() == ModuleLifecycleEvent.Phase.DISABLING) {
            removeOwnerEntries(entry -> event.moduleId().equals(entry.owner().module()));
        }
    }

    private void removeOwnerEntries(java.util.function.Predicate<Installed> predicate) {
        var candidate = new LinkedHashMap<>(entries);
        if (candidate.values().removeIf(predicate)) replace(candidate);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareCraft(PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player && !allowedCraft(player, event.getRecipe())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void claimCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getRecipe() instanceof Keyed key)
            || !managed(key.getKey())) return;
        Installed current = entries.get(key.getKey());
        var match = Bukkit.getCraftingRecipe(event.getInventory().getMatrix(), player.getWorld());
        if (current == null || !allowedCraft(player, event.getRecipe())
            || !(match instanceof Keyed actual) || !key.getKey().equals(actual.getKey())
            || !current.entry().definition().result().equals(event.getCurrentItem())) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
        }
    }

    private boolean allowedCraft(Player player, Recipe recipe) {
        if (!(recipe instanceof Keyed key) || !managed(key.getKey())) return true;
        Installed current = entries.get(key.getKey());
        return current != null && allowed(player, current.entry());
    }

    public boolean managed(NamespacedKey key) {
        return key.getNamespace().equals(CONFIG) || key.getNamespace().startsWith("axs_recipe_plugin_")
            || key.getNamespace().startsWith("axs_recipe_module_");
    }

    @Override
    public void close() {
        requireMain();
        closed = true;
        replace(Map.of());
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Recipe operation requires the primary thread");
    }
}
