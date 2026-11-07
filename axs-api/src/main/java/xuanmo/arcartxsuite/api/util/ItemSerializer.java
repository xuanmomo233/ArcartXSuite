package xuanmo.arcartxsuite.api.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * 物品序列化工具，基于 Bukkit 的对象流，并兼容 Mohist 等混合服务端的反序列化。
 * <p>
 * 反序列化失败时降级到兼容路径：解包 {@code Wrapper} 旧对象、递归清洗 ItemMeta 中
 * 复杂 NBT 值（转为 SNBT 字符串），以适配拒绝嵌套 NBT 的平台。
 * <p>
 * 注意：兼容路径是有损的——被转为 SNBT 字符串的 NBT 值反序列化后是 String 而非
 * 复合标签，依赖该 NBT 的 mod/插件可能无法识别。每次实际发生降级时都会输出
 * 警告日志（含物品数据哈希摘要，便于与数据库记录对照定位）。
 */
public final class ItemSerializer {

    private static final String WRAPPER_CLASS_NAME = "org.bukkit.util.io.Wrapper";
    private static final Logger LOGGER = Logger.getLogger("ArcartX-Suite");

    private ItemSerializer() {
    }

    /**
     * 将物品序列化为字节数组。
     *
     * @param itemStack 待序列化物品，null/空气返回空数组
     * @return 序列化字节
     * @throws IllegalStateException 序列化失败时抛出
     */
    public static byte[] serialize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new byte[0];
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(outputStream)) {
            dataStream.writeObject(itemStack);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize item stack.", exception);
        }
    }

    /**
     * 从字节数组反序列化物品。
 * <p>
 * 先尝试 Bukkit 标准路径，失败时降级到兼容路径（处理 Mohist 等平台）。
     *
     * @param bytes 序列化字节，null/空数组返回 null
     * @return 物品；无法反序列化时抛出 IllegalStateException
     * @throws IllegalStateException 反序列化失败时抛出
     */
    public static ItemStack deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataStream = new BukkitObjectInputStream(inputStream)) {
            Object object = dataStream.readObject();
            return object instanceof ItemStack itemStack ? itemStack : null;
        } catch (IOException | ClassNotFoundException | RuntimeException primaryException) {
            try {
                return deserializeCompat(bytes);
            } catch (IOException | ReflectiveOperationException compatException) {
                compatException.addSuppressed(primaryException);
                throw new IllegalStateException("Unable to deserialize item stack.", compatException);
            }
        }
    }

    /** Protection templates must not silently lose NBT through the compatibility fallback. */
    public static ItemStack deserializeStrict(byte[] bytes) {
        if(bytes==null || bytes.length==0)return null;
        try(ByteArrayInputStream input=new ByteArrayInputStream(bytes);BukkitObjectInputStream stream=new BukkitObjectInputStream(input)){
            Object value=stream.readObject();if(value instanceof ItemStack item)return item;
            throw new IllegalStateException("Item protection template is not an ItemStack.");
        }catch(IOException|ClassNotFoundException exception){throw new IllegalStateException("Unable to read item protection template without NBT loss.",exception);}
    }

    private static ItemStack deserializeCompat(byte[] bytes) throws IOException, ClassNotFoundException, ReflectiveOperationException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             ObjectInputStream dataStream = new ObjectInputStream(inputStream)) {
            Object object = unwrapLegacyObject(dataStream.readObject());
            if (object instanceof ItemStack itemStack) {
                return itemStack;
            }
            if (object instanceof Map<?, ?> map) {
                boolean[] degraded = new boolean[1];
                ItemStack itemStack = deserializeItemStackMap(asStringObjectMap(map), degraded);
                if (degraded[0]) {
                    // 走 SNBT 降级意味着部分 NBT 已变成字符串而非复合标签，取出后 mod/插件可能无法识别。
                    LOGGER.warning("[ItemSerializer] 物品反序列化发生有损降级：部分嵌套 NBT 已转为 SNBT 字符串，"
                        + "依赖原始 NBT 的 mod/插件数据可能无法读取。物品数据摘要 sha256=" + shortHash(bytes));
                }
                return itemStack;
            }
            return null;
        }
    }

    private static ItemStack deserializeItemStackMap(Map<String, Object> map, boolean[] degraded) {
        Map<String, Object> copy = new LinkedHashMap<>(map);
        Object rawMeta = copy.get("meta");
        if (rawMeta instanceof Map<?, ?> metaMap) {
            copy.put("meta", deserializeItemMetaMap(asStringObjectMap(metaMap), degraded));
        }
        return ItemStack.deserialize(copy);
    }

    private static ItemMeta deserializeItemMetaMap(Map<String, Object> map, boolean[] degraded) {
        try {
            Object direct = ConfigurationSerialization.deserializeObject(map);
            if (direct instanceof ItemMeta itemMeta) {
                return itemMeta;
            }
        } catch (RuntimeException ignored) {
            // Mohist may reject nested NBT-shaped values that Spigot accepts.
        }

        for (Map<String, Object> candidate : List.of(sanitizeMetaMap(map, false, degraded), sanitizeMetaMap(map, true, degraded))) {
            try {
                Object fallback = ConfigurationSerialization.deserializeObject(candidate);
                if (fallback instanceof ItemMeta itemMeta) {
                    return itemMeta;
                }
            } catch (RuntimeException ignored) {
                // Try the next, more aggressive candidate.
            }
        }
        throw new IllegalStateException("Unable to deserialize item meta.");
    }

    private static Map<String, Object> sanitizeMetaMap(Map<String, Object> map, boolean aggressive, boolean[] degraded) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeMetaValue(entry.getKey(), entry.getValue(), aggressive, degraded));
        }
        return sanitized;
    }

    private static Object sanitizeMetaValue(String key, Object value, boolean aggressive, boolean[] degraded) {
        if (shouldConvertToSnbt(key, value, aggressive)) {
            degraded[0] = true;
            return toSnbt(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                copy.put(nestedKey, sanitizeMetaValue(nestedKey, entry.getValue(), aggressive, degraded));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(sanitizeMetaValue(key, element, aggressive, degraded));
            }
            return copy;
        }
        return value;
    }

    private static boolean shouldConvertToSnbt(String key, Object value, boolean aggressive) {
        if (!isComplexNbtCandidate(value)) {
            return false;
        }
        if (aggressive) {
            return true;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase();
        return normalizedKey.contains("tag")
            || normalizedKey.contains("nbt")
            || normalizedKey.contains("internal")
            || normalizedKey.contains("unhandled")
            || normalizedKey.contains("component")
            || normalizedKey.contains("custom")
            || normalizedKey.contains("publicbukkitvalues")
            || normalizedKey.contains("blockentity")
            || normalizedKey.contains("entitytag")
            || normalizedKey.contains("bucketentity");
    }

    private static boolean isComplexNbtCandidate(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    return true;
                }
                Object nested = entry.getValue();
                if (nested instanceof Map<?, ?> || nested instanceof List<?>) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> || element instanceof List<?>) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object unwrapLegacyObject(Object value) throws ReflectiveOperationException {
        if (value == null) {
            return null;
        }
        if (WRAPPER_CLASS_NAME.equals(value.getClass().getName())) {
            Field mapField = value.getClass().getDeclaredField("map");
            mapField.setAccessible(true);
            return unwrapLegacyObject(mapField.get(value));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), unwrapLegacyObject(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(unwrapLegacyObject(element));
            }
            return copy;
        }
        return value;
    }

    private static Map<String, Object> asStringObjectMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static String toSnbt(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(escapeKey(String.valueOf(entry.getKey())))
                    .append(':')
                    .append(toSnbt(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(toSnbt(list.get(index)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof String string) {
            return '"' + escapeString(string) + '"';
        }
        if (value instanceof Byte byteValue) {
            return byteValue + "b";
        }
        if (value instanceof Short shortValue) {
            return shortValue + "s";
        }
        if (value instanceof Long longValue) {
            return longValue + "L";
        }
        if (value instanceof Float floatValue) {
            return stripTrailingZero(floatValue) + "f";
        }
        if (value instanceof Double doubleValue) {
            return stripTrailingZero(doubleValue) + "d";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "1b" : "0b";
        }
        if (value.getClass().isArray()) {
            return arrayToSnbt(value);
        }
        if (value instanceof Character character) {
            return '"' + escapeString(character.toString()) + '"';
        }
        return String.valueOf(value);
    }

    /**
     * 数组转 SNBT：byte[]/int[]/long[] 使用 SNBT 类型化数组语法（[B;…]/[I;…]/[L;…]），
     * 其他数组退化为普通列表逐元素转换。修复旧实现中数组走 {@code String.valueOf}
     * 输出 "[B@哈希码" 导致原始数据彻底丢失的问题。
     */
    private static String arrayToSnbt(Object array) {
        Class<?> componentType = array.getClass().getComponentType();
        String prefix = switch (componentType == null ? "" : componentType.getName()) {
            case "byte", "java.lang.Byte" -> "[B;";
            case "int", "java.lang.Integer" -> "[I;";
            case "long", "java.lang.Long" -> "[L;";
            default -> "[";
        };
        int length = Array.getLength(array);
        StringBuilder builder = new StringBuilder(prefix);
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(toSnbt(Array.get(array, index)));
        }
        return builder.append(']').toString();
    }

    private static String escapeKey(String value) {
        return value.matches("[A-Za-z0-9._+-]+") ? value : '"' + escapeString(value) + '"';
    }

    private static String escapeString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static String stripTrailingZero(Number value) {
        String text = String.valueOf(value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** 物品字节摘要（sha256 前 12 位十六进制），用于日志与库内 item_hash 对照。 */
    private static String shortHash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(12);
            for (int i = 0; i < 6 && i < digest.length; i++) {
                builder.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                builder.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            return "unavailable";
        }
    }
}
