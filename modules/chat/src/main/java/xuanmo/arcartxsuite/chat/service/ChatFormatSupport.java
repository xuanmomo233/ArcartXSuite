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

public final class ChatFormatSupport {

    public static final String ITEM_MARKER = "__AXS_CHAT_ITEM__";

    private ChatFormatSupport() {
    }

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

    public static String normalizeForDuplicateCheck(String message) {
        return ChatColor.stripColor(translateColors(message == null ? "" : message))
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

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

    public static String plainText(String renderedText, ChatItemPreview itemPreview) {
        if (renderedText == null) {
            return "";
        }
        String resolved = itemPreview == null ? renderedText : renderedText.replace(ITEM_MARKER, itemPreview.displayText());
        return ChatColor.stripColor(resolved);
    }

    public static String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', nullToEmpty(text));
    }

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
