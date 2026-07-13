package xuanmo.arcartxsuite.api.item;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;

public final class ItemMatcherSupport implements ItemMatcherAPI {

    private final Function<ItemStack, String> mythicItemIdResolver;
    private final Function<ItemStack, String> neigeItemIdResolver;
    private final Function<ItemStack, String> overtureItemIdResolver;

    public ItemMatcherSupport(Function<ItemStack, String> mythicItemIdResolver, Function<ItemStack, String> neigeItemIdResolver) {
        this(mythicItemIdResolver, neigeItemIdResolver, null);
    }

    public ItemMatcherSupport(Function<ItemStack, String> mythicItemIdResolver, Function<ItemStack, String> neigeItemIdResolver, Function<ItemStack, String> overtureItemIdResolver) {
        this.mythicItemIdResolver = mythicItemIdResolver == null ? item -> "" : mythicItemIdResolver;
        this.neigeItemIdResolver = neigeItemIdResolver == null ? item -> "" : neigeItemIdResolver;
        this.overtureItemIdResolver = overtureItemIdResolver == null ? item -> "" : overtureItemIdResolver;
    }

    @Override
    public boolean matches(ItemMatcher matcher, ItemStack itemStack) {
        if (matcher == null || matcher.emptyMatcher() || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        String materialId = ItemMatcherLoader.normalizeId(itemStack.getType().name());
        String mythicItemId = ItemMatcherLoader.normalizeId(mythicItemIdResolver.apply(itemStack));
        String neigeItemId = ItemMatcherLoader.normalizeId(neigeItemIdResolver.apply(itemStack));
        String overtureItemId = ItemMatcherLoader.normalizeId(overtureItemIdResolver.apply(itemStack));
        String displayName = displayName(itemStack);
        String normalizedName = normalizeToken(displayName);
        List<String> loreLines = normalizedLore(itemStack);
        Set<String> kinds = detectKinds(itemStack, mythicItemId, neigeItemId, overtureItemId);

        if (!matcher.materialIds().isEmpty() && !matcher.materialIds().contains(materialId)) {
            return false;
        }
        if (!matcher.mythicItemIds().isEmpty() && !matcher.mythicItemIds().contains(mythicItemId)) {
            return false;
        }
        if (!matcher.neigeItemIds().isEmpty() && !matcher.neigeItemIds().contains(neigeItemId)) {
            return false;
        }
        if (!matcher.overtureItemIds().isEmpty() && !matcher.overtureItemIds().contains(overtureItemId)) {
            return false;
        }
        if (!matcher.kinds().isEmpty() && matcher.kinds().stream().noneMatch(kinds::contains)) {
            return false;
        }
        if (!matcher.nameContains().isEmpty() && matcher.nameContains().stream().noneMatch(normalizedName::contains)) {
            return false;
        }
        if (!matcher.loreContains().isEmpty() && matcher.loreContains().stream().noneMatch(token -> loreLines.stream().anyMatch(line -> line.contains(token)))) {
            return false;
        }
        if (!matcher.nbtKeys().isEmpty() && matcher.nbtKeys().stream().noneMatch(key -> hasNbtKey(itemStack, key))) {
            return false;
        }
        if (!matcher.namePatterns().isEmpty() && matcher.namePatterns().stream().noneMatch(pattern -> pattern.matcher(ChatColor.stripColor(displayName)).find())) {
            return false;
        }
        if (!matcher.lorePatterns().isEmpty() && matcher.lorePatterns().stream().noneMatch(pattern -> loreLines.stream().anyMatch(line -> pattern.matcher(ChatColor.stripColor(line)).find()))) {
            return false;
        }
        return true;
    }

    private static boolean hasNbtKey(ItemStack itemStack, String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return false;
        }
        String expected = ItemMatcherLoader.normalizeId(configuredKey);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                if (matchesNbtKey(expected, key.toString())
                    || matchesNbtKey(expected, key.getKey())) {
                    return true;
                }
            }
        }
        return RawNbtAccess.contains(itemStack, expected);
    }

    private static boolean matchesNbtKey(String expected, String actual) {
        String normalizedActual = ItemMatcherLoader.normalizeId(actual);
        if (expected.equals(normalizedActual)) {
            return true;
        }
        int expectedSeparator = expected.lastIndexOf(':');
        int actualSeparator = normalizedActual.lastIndexOf(':');
        String expectedKey = expectedSeparator >= 0
            ? expected.substring(expectedSeparator + 1) : expected;
        String actualKey = actualSeparator >= 0
            ? normalizedActual.substring(actualSeparator + 1) : normalizedActual;
        return expectedKey.equals(actualKey);
    }

    private static final class RawNbtAccess {
        private static volatile RawNbtAccess instance;

        private final Method asNmsCopy;
        private final Constructor<?> compoundConstructor;
        private final Method getTag;
        private final Method save;
        private final Class<?> compoundClass;
        private final Class<?> listClass;
        private final Method getAllKeys;
        private final Method keySet;
        private final Method get;
        private final Method listSize;
        private final Method listGet;

        private RawNbtAccess(
            Method asNmsCopy,
            Constructor<?> compoundConstructor,
            Method getTag,
            Method save,
            Class<?> compoundClass,
            Class<?> listClass,
            Method getAllKeys,
            Method keySet,
            Method get,
            Method listSize,
            Method listGet
        ) {
            this.asNmsCopy = asNmsCopy;
            this.compoundConstructor = compoundConstructor;
            this.getTag = getTag;
            this.save = save;
            this.compoundClass = compoundClass;
            this.listClass = listClass;
            this.getAllKeys = getAllKeys;
            this.keySet = keySet;
            this.get = get;
            this.listSize = listSize;
            this.listGet = listGet;
        }

        private static boolean contains(ItemStack itemStack, String expected) {
            try {
                RawNbtAccess access = resolve();
                if (access == null) {
                    return false;
                }
                Object nmsItem = access.asNmsCopy.invoke(null, itemStack);
                Object root = access.readTag(nmsItem);
                if (root == null) {
                    return false;
                }
                return access.contains(root, expected,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static RawNbtAccess resolve() {
            RawNbtAccess cached = instance;
            if (cached != null) {
                return cached;
            }
            synchronized (RawNbtAccess.class) {
                cached = instance;
                if (cached != null) {
                    return cached;
                }
                try {
                    if (Bukkit.getServer() == null) {
                        return null;
                    }
                    String craftPackage = Bukkit.getServer().getClass().getPackageName();
                    Class<?> craftItemStack = Class.forName(
                        craftPackage + ".inventory.CraftItemStack"
                    );
                    Method asNmsCopy = craftItemStack.getMethod(
                        "asNMSCopy", ItemStack.class
                    );
                    String craftVersion = craftPackage.substring(
                        craftPackage.lastIndexOf('.') + 1
                    );
                    Class<?> compoundClass = findClass(
                        "net.minecraft.nbt.CompoundTag",
                        "net.minecraft.server." + craftVersion
                            + ".NBTTagCompound"
                    );
                    Constructor<?> compoundConstructor =
                        compoundClass.getDeclaredConstructor();
                    Method getTag = findNoArgMethod(
                        "getTag", "getTagCompound"
                    );
                    Method save = findSaveMethod(compoundClass);
                    if (getTag == null && save == null) {
                        return null;
                    }
                    Class<?> listClass = findClass(
                        "net.minecraft.nbt.ListTag",
                        "net.minecraft.server." + craftVersion
                            + ".NBTTagList"
                    );
                    Method getAllKeys = findMethod(compoundClass, "getAllKeys");
                    Method keySet = findMethod(compoundClass, "keySet");
                    Method get = findMethod(compoundClass, "get", String.class);
                    Method listSize = findMethod(listClass, "size");
                    Method listGet = findMethod(listClass, "get", int.class);
                    if (get == null || (getAllKeys == null && keySet == null)) {
                        return null;
                    }
                    cached = new RawNbtAccess(
                        asNmsCopy,
                        compoundConstructor,
                        getTag,
                        save,
                        compoundClass,
                        listClass,
                        getAllKeys,
                        keySet,
                        get,
                        listSize,
                        listGet
                    );
                    instance = cached;
                    return cached;
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }

        private Object readTag(Object nmsItem) throws ReflectiveOperationException {
            if (getTag != null) {
                Object tag = getTag.invoke(nmsItem);
                if (tag != null) {
                    return tag;
                }
            }
            if (save == null) {
                return null;
            }
            Object result = save.invoke(nmsItem, compoundConstructor.newInstance());
            if (compoundClass.isInstance(result)) {
                return result;
            }
            if (result != null) {
                try {
                    Method resultMethod = result.getClass().getMethod("result");
                    Object optional = resultMethod.invoke(result);
                    if (optional instanceof java.util.Optional<?> value) {
                        return value.orElse(null);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Some versions return the compound directly.
                }
            }
            return null;
        }

        private boolean contains(
            Object node,
            String expected,
            Set<Object> visited
        ) throws ReflectiveOperationException {
            if (node == null || !visited.add(node)) {
                return false;
            }
            if (compoundClass.isInstance(node)) {
                Set<?> keys = keys(node);
                if (keys == null) {
                    return false;
                }
                for (Object rawKey : keys) {
                    String key = String.valueOf(rawKey);
                    if (matchesNbtKey(expected, key)) {
                        return true;
                    }
                    Object child = get.invoke(node, key);
                    if (contains(child, expected, visited)) {
                        return true;
                    }
                }
                return false;
            }
            if (listClass.isInstance(node) && listSize != null && listGet != null) {
                int size = ((Number) listSize.invoke(node)).intValue();
                for (int index = 0; index < size; index++) {
                    if (contains(listGet.invoke(node, index), expected, visited)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Set<?> keys(Object compound) throws ReflectiveOperationException {
            Object result = getAllKeys != null
                ? getAllKeys.invoke(compound) : keySet.invoke(compound);
            return result instanceof Set<?> set ? set : null;
        }

        private static Class<?> findClass(String... names)
            throws ClassNotFoundException {
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                try {
                    return Class.forName(name);
                } catch (ClassNotFoundException ignored) {
                    // Try the next version-specific class name.
                }
            }
            throw new ClassNotFoundException(names[0]);
        }

        private static Method findNoArgMethod(String... names) {
            for (String name : names) {
                try {
                    for (Method method : findNmsItemStackClass().getMethods()) {
                        if (method.getName().equals(name)
                            && method.getParameterCount() == 0) {
                            return method;
                        }
                    }
                } catch (Throwable ignored) {
                    return null;
                }
            }
            return null;
        }

        private static Method findSaveMethod(Class<?> compoundClass) {
            try {
                for (Method method : findNmsItemStackClass().getMethods()) {
                    if ((method.getName().equals("save")
                            || method.getName().equals("saveOptional")
                            || method.getName().equals("saveWithoutMetadata"))
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0]
                            .isAssignableFrom(compoundClass)) {
                        return method;
                    }
                }
            } catch (Throwable ignored) {
                // Raw NBT support is optional.
            }
            return null;
        }

        private static Class<?> findNmsItemStackClass() {
            try {
                String craftPackage = Bukkit.getServer().getClass().getPackageName();
                Class<?> craftItemStack = Class.forName(
                    craftPackage + ".inventory.CraftItemStack"
                );
                Method asNmsCopy = craftItemStack.getMethod(
                    "asNMSCopy", ItemStack.class
                );
                return asNmsCopy.getReturnType();
            } catch (Throwable exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            if (type == null) {
                return null;
            }
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private static String displayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return itemStack.getType().name();
        }
        return meta.getDisplayName();
    }

    private static Set<String> detectKinds(ItemStack itemStack, String mythicItemId, String neigeItemId, String overtureItemId) {
        LinkedHashSet<String> kinds = new LinkedHashSet<>();
        Material material = itemStack.getType();
        String name = material.name();
        if (material.isBlock()) {
            kinds.add("block");
        }
        if (material.isEdible()) {
            kinds.add("food");
            kinds.add("consumable");
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            kinds.add("weapon");
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            kinds.add("tool");
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("SHIELD")) {
            kinds.add("armor");
        }
        if (name.endsWith("_BOW") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")) {
            kinds.add("ranged");
        }
        if (name.contains("POTION")) {
            kinds.add("potion");
            kinds.add("consumable");
        }
        if (!mythicItemId.isBlank()) {
            kinds.add("mythic");
        }
        if (!neigeItemId.isBlank()) {
            kinds.add("neige");
        }
        if (!overtureItemId.isBlank()) {
            kinds.add("overture");
        }
        if (name.contains("INGOT") || name.contains("GEM") || name.contains("SHARD") || name.contains("ORE")) {
            kinds.add("material");
        }
        return kinds;
    }

    private static List<String> normalizedLore(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String line : meta.getLore()) {
            result.add(normalizeToken(line));
        }
        return List.copyOf(result);
    }

    private static String normalizeToken(String value) {
        return ChatColor.stripColor(value == null ? "" : value).trim().toLowerCase();
    }
}
