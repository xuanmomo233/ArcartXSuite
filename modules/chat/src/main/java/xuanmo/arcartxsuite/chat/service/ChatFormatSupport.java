package xuanmo.arcartxsuite.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.chat.model.ChatItemPreview;

/**
 * 聊天格式化工具类，提供模板渲染、颜色转义、物品组件构建等静态方法。
 * <p>
 * 被 {@link ChatService} 用于将频道格式模板渲染为最终消息文本，
 * 并将包含物品标记（{@link #ITEM_MARKER}）的文本拆分为带悬浮提示的 BungeeCord 聊天组件。
 */
public final class ChatFormatSupport {

    public static final String ITEM_MARKER = "__AXS_CHAT_ITEM__";

    private ChatFormatSupport() {
    }

    /**
     * 渲染格式模板：替换变量占位符、解析 PAPI 占位符、转义颜色代码。
     *
     * @param player            玩家上下文，用于 PAPI 解析（可为 {@code null}）
     * @param template          格式模板字符串
     * @param variables         变量映射（如 {player_name} → 实际值）
     * @param placeholderResolver PAPI 解析器（可为 {@code null}）
     * @return 渲染后的字符串
     */
    public static String renderTemplate(
        Player player,
        String template,
        Map<String, String> variables,
        PlaceholderResolverAPI placeholderResolver
    ) {
        String rendered = template == null ? "" : template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                rendered = rendered.replace("{" + entry.getKey() + "}", nullToEmpty(entry.getValue()));
            }
        }
        if (player != null && placeholderResolver != null) {
            rendered = placeholderResolver.applyPlaceholders(player, rendered);
        }
        return translateColors(rendered);
    }

    /**
     * 将消息规范化用于重复检测：去除颜色代码、压缩空白、转小写。
     *
     * @param message 原始消息
     * @return 规范化后的字符串
     */
    public static String normalizeForDuplicateCheck(String message) {
        return ChatColor.stripColor(translateColors(message == null ? "" : message))
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 将渲染后文本构建为 BungeeCord 聊天组件数组，处理物品标记的悬浮提示。
     *
     * @param renderedText 渲染后文本（可能包含 {@link #ITEM_MARKER}）
     * @param itemPreview  物品预览信息，为 {@code null} 时直接转为纯文本
     * @param hoverItem    是否为物品组件添加悬浮提示事件
     * @return 聊天组件数组
     */
    public static BaseComponent[] buildComponents(String renderedText, ChatItemPreview itemPreview, boolean hoverItem) {
        if (renderedText == null || renderedText.isBlank()) {
            return new ComponentBuilder("").create();
        }
        if (itemPreview == null || !renderedText.contains(ITEM_MARKER)) {
            return TextComponent.fromLegacyText(renderedText.replace(ITEM_MARKER, ""));
        }

        List<BaseComponent> result = new ArrayList<>();
        String[] parts = renderedText.split(ITEM_MARKER, -1);
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].isEmpty()) {
                BaseComponent[] components = TextComponent.fromLegacyText(parts[index]);
                for (BaseComponent component : components) {
                    result.add(component);
                }
            }
            if (index + 1 < parts.length) {
                BaseComponent[] itemComponents = toItemComponents(itemPreview, hoverItem);
                for (BaseComponent itemComponent : itemComponents) {
                    result.add(itemComponent);
                }
            }
        }
        return result.toArray(new BaseComponent[0]);
    }

    /**
     * 将渲染后文本转为纯文本（去除颜色代码，物品标记替换为展示文本）。
     *
     * @param renderedText 渲染后文本
     * @param itemPreview  物品预览信息（可为 {@code null}）
     * @return 纯文本字符串
     */
    public static String plainText(String renderedText, ChatItemPreview itemPreview) {
        if (renderedText == null) {
            return "";
        }
        String resolved = itemPreview == null ? renderedText : renderedText.replace(ITEM_MARKER, itemPreview.displayText());
        return ChatColor.stripColor(resolved);
    }

    /**
     * 将 {@code &} 颜色代码转义为 Minecraft 颜色代码。
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    public static String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', nullToEmpty(text));
    }

    /**
     * 构建基础变量映射，包含频道名、发送者名称、目标名称与消息内容。
     *
     * @param channelName 频道显示名称
     * @param sender      发送者玩家
     * @param targetName  目标名称（私聊场景）
     * @param message     消息内容
     * @return 变量映射
     */
    public static Map<String, String> baseVariables(String channelName, Player sender, String targetName, String message) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("channel", nullToEmpty(channelName));
        values.put("player_name", sender == null ? "" : sender.getName());
        values.put("player_display_name", sender == null ? "" : nullToEmpty(sender.getDisplayName()));
        values.put("target_name", nullToEmpty(targetName));
        values.put("message", nullToEmpty(message));
        return values;
    }

    private static BaseComponent[] toItemComponents(ChatItemPreview itemPreview, boolean hoverItem) {
        BaseComponent[] components = TextComponent.fromLegacyText(itemPreview.displayText());
        if (!hoverItem) {
            return components;
        }
        String nbtTag = extractNbtTag(itemPreview.itemJson());
        ItemTag tag = (nbtTag != null) ? ItemTag.ofNbt(nbtTag) : null;
        HoverEvent hoverEvent = new HoverEvent(
            HoverEvent.Action.SHOW_ITEM,
            new Item(itemPreview.materialKey(), itemPreview.amount(), tag)
        );
        for (BaseComponent component : components) {
            component.setHoverEvent(hoverEvent);
        }
        return components;
    }

    static String extractNbtTag(String fullItemData) {
        if (fullItemData == null || fullItemData.isBlank()) {
            return null;
        }
        int searchPos = 0;
        int braceStart = -1;
        while (searchPos < fullItemData.length()) {
            int keyPos = fullItemData.indexOf("tag:", searchPos);
            if (keyPos < 0) {
                break;
            }
            int afterColon = keyPos + 4;
            while (afterColon < fullItemData.length() && Character.isWhitespace(fullItemData.charAt(afterColon))) {
                afterColon++;
            }
            if (afterColon < fullItemData.length() && fullItemData.charAt(afterColon) == '{') {
                braceStart = afterColon;
                break;
            }
            searchPos = afterColon;
        }
        if (braceStart < 0) {
            return null;
        }
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = braceStart; i < fullItemData.length(); i++) {
            char c = fullItemData.charAt(i);
            if (inQuote) {
                if (c == '\\') { i++; continue; }
                if (c == quoteChar) { inQuote = false; }
                continue;
            }
            switch (c) {
                case '"', '\'' -> { inQuote = true; quoteChar = c; }
                case '{' -> depth++;
                case '}' -> { if (--depth == 0) return fullItemData.substring(braceStart, i + 1); }
                default -> {}
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
