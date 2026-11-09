package xuanmo.arcartxsuite.vanilla.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.ItemSnapshots;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

final class RecipeFile {
    record Document(YamlConfiguration yaml, Map<String, Definition> definitions, String digest, long revision) { }
    private final Path path;
    private final ItemSourceRegistry items;

    RecipeFile(Path path, ItemSourceRegistry items) {
        this.path = path;
        this.items = items;
    }

    Document read() throws IOException, InvalidConfigurationException {
        String text = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return parse(yaml, ItemSnapshots.hash(text));
    }

    Document parse(YamlConfiguration yaml, String digest) {
        if (yaml.getInt("config-version", 1) != 1) throw new IllegalArgumentException("recipes.yml 版本不受支持");
        Map<String, Definition> definitions = new LinkedHashMap<>();
        var recipes = yaml.getConfigurationSection("recipes");
        if (recipes == null && yaml.contains("recipes")) throw new IllegalArgumentException("recipes 必须为键值表");
        if (recipes != null) for (String id : recipes.getKeys(false)) {
            try {
                var entry = recipes.getConfigurationSection(id);
                if (entry == null) throw new IllegalArgumentException("配方必须为键值表");
                definitions.put(id, parseDefinition(id, entry));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(id + " 配方无效，" + exception.getMessage(), exception);
            }
        }
        return new Document(yaml, definitions, digest, yaml.getLong("revision", 0));
    }

    private Definition parseDefinition(String id, ConfigurationSection section) {
        String station = section.getString("station", "shaped").replace('-', '_').toUpperCase(Locale.ROOT);
        var inputs = section.getList("ingredients");
        if (inputs == null) throw new IllegalArgumentException("缺少 ingredients 列表");
        List<Ingredient> ingredients = new ArrayList<>();
        for (Object input : inputs) {
            if (!(input instanceof Map<?, ?> data)) throw new IllegalArgumentException("材料格必须为键值表");
            List<Material> materials = new ArrayList<>();
            if (data.get("materials") instanceof List<?> values) for (Object value : values) {
                Material material = Material.matchMaterial(value.toString());
                if (material == null) throw new IllegalArgumentException("未知材质 " + value);
                materials.add(material);
            }
            List<ItemStack> exact = new ArrayList<>();
            if (data.get("exact") instanceof List<?> values) {
                for (Object value : values) exact.add(readItem(value));
            }
            ingredients.add(new Ingredient(materials, exact));
        }
        Object result = section.get("result");
        if (result instanceof ConfigurationSection mapping) result = mapping.getValues(false);
        return new Definition(id, Station.valueOf(station), ingredients, readItem(result),
            section.getString("permission", ""), section.getInt("cooking-ticks", 200),
            (float) section.getDouble("experience", 0),
            SmithingData.valueOf(section.getString("smithing-data", "INHERIT_BASE").replace('-', '_').toUpperCase(Locale.ROOT)));
    }

    private ItemStack readItem(Object value) {
        if (value instanceof String snapshot) return ItemSnapshots.decode(snapshot);
        if (value instanceof ItemStack item) return item.clone();
        if (value instanceof Map<?, ?> library && library.get("source") instanceof String source
            && library.get("id") instanceof String id) {
            int amount = library.get("amount") instanceof Number number ? number.intValue() : 1;
            if (amount < 1 || amount > 99) throw new IllegalArgumentException("物品库模板数量无效");
            ItemStack item = items.generateItem(source, id, amount, null, null);
            ItemStack repeat = items.generateItem(source, id, amount, null, null);
            if (InventoryItems.empty(item) || !item.equals(repeat) || item.getAmount() != amount) {
                throw new IllegalArgumentException("物品库必须能生成固定模板 " + source + "/" + id);
            }
            return item;
        }
        throw new IllegalArgumentException("缺少可读取的物品样品");
    }

    static Map<String, Object> serialize(Definition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("station", definition.station().name().toLowerCase(Locale.ROOT));
        result.put("ingredients", definition.ingredients().stream().map(ingredient -> {
            Map<String, Object> cell = new LinkedHashMap<>();
            if (!ingredient.materials().isEmpty()) cell.put("materials", ingredient.materials().stream().map(Material::name).toList());
            if (!ingredient.exact().isEmpty()) cell.put("exact", ingredient.exact().stream().map(ItemSnapshots::encode).toList());
            return cell;
        }).toList());
        result.put("result", ItemSnapshots.encode(definition.result()));
        result.put("permission", definition.permission());
        result.put("cooking-ticks", definition.cookingTicks());
        result.put("experience", definition.experience());
        result.put("smithing-data", definition.smithingData().name());
        return result;
    }

    String write(YamlConfiguration yaml) throws IOException {
        Files.createDirectories(path.getParent());
        String text = yaml.saveToString();
        Path temporary = Files.createTempFile(path.getParent(), "recipes-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return ItemSnapshots.hash(text);
    }
}
