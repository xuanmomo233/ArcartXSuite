package xuanmo.arcartxsuite.pickup.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class PickupHudTemplateWriter {

    private PickupHudTemplateWriter() {
    }

    public static void write(File target, int maxVisible, long entryTtlMs) throws IOException {
        Files.writeString(target.toPath(), build(maxVisible, entryTtlMs), StandardCharsets.UTF_8);
    }

    public static String signature(int maxVisible, long entryTtlMs) {
        return "# AXS pickup_hud; version=6; maxVisible=" + maxVisible + "; ttlMs=" + entryTtlMs;
    }

    private static String build(int maxVisible, long entryTtlMs) {
        StringBuilder builder = new StringBuilder();
        line(builder, 0, signature(maxVisible, entryTtlMs));
        line(builder, 0, "ui:");
        line(builder, 1, "isHud: true");
        line(builder, 1, "defaultOpen: false");
        line(builder, 1, "through: false");
        line(builder, 1, "background: false");
        line(builder, 1, "closeDied: false");
        line(builder, 1, "show: true");
        line(builder, 1, "level: 10");
        line(builder, 1, "packetHandler:");
        line(builder, 2, "pick: |-");
        line(builder, 3, "var.now = Time.currentTime()");
        for (int index = maxVisible; index >= 2; index--) {
            int previous = index - 1;
            line(builder, 3, "var.entry" + index + "Visible = var.entry" + previous + "Visible");
            line(builder, 3, "var.entry" + index + "CreatedAt = var.entry" + previous + "CreatedAt");
            line(builder, 3, "var.entry" + index + "Amount = var.entry" + previous + "Amount");
            line(builder, 3, "var.entry" + index + "ItemJson = var.entry" + previous + "ItemJson");
            line(builder, 3, "var.entry" + index + "IconDirty = true");
        }
        line(builder, 3, "var.entry1Visible = true");
        line(builder, 3, "var.entry1CreatedAt = var.now");
        line(builder, 3, "var.entry1Amount = packet['amount']");
        line(builder, 3, "var.entry1ItemJson = packet['itemJson']");
        line(builder, 3, "var.entry1IconDirty = true");
        line(builder, 1, "action:");
        line(builder, 2, "load: |-");
        line(builder, 3, "var.entryTtlMs = " + entryTtlMs);
        line(builder, 3, "var.now = 0");
        for (int index = 1; index <= maxVisible; index++) {
            line(builder, 3, "var.entry" + index + "Visible = false");
            line(builder, 3, "var.entry" + index + "CreatedAt = 0");
            line(builder, 3, "var.entry" + index + "Amount = 0");
            line(builder, 3, "var.entry" + index + "ItemJson = ''");
            line(builder, 3, "var.entry" + index + "IconDirty = false");
        }
        line(builder, 2, "tick: |-");
        line(builder, 3, "var.now = Time.currentTime()");
        for (int index = 1; index <= maxVisible; index++) {
            line(builder, 3, "if(var.entry" + index + "Visible && (var.now - var.entry" + index + "CreatedAt) >= var.entryTtlMs){");
            line(builder, 4, "var.entry" + index + "Visible = false");
            line(builder, 3, "}");
        }
        for (int index = 1; index <= maxVisible; index++) {
            line(builder, 3, "if(var.entry" + index + "IconDirty){");
            line(builder, 4, "if(var.entry" + index + "ItemJson != ''){");
            line(builder, 5, "val.pickup_slot_" + index + ".setItemIcon(var.entry" + index + "ItemJson)");
            line(builder, 4, "}");
            line(builder, 4, "var.entry" + index + "IconDirty = false");
            line(builder, 3, "}");
        }
        line(builder, 0, "");
        line(builder, 0, "controls:");
        line(builder, 1, "adaptive:");
        line(builder, 2, "type: Adaptive");
        line(builder, 2, "attribute:");
        line(builder, 3, "point: ~stretch_all");
        line(builder, 3, "width: 1920");
        line(builder, 3, "height: 1080");
        line(builder, 2, "children:");
        line(builder, 3, "pickup_stack:");
        line(builder, 4, "type: VStack");
        line(builder, 4, "attribute:");
        line(builder, 5, "point: ~bottom_right");
        line(builder, 5, "x: -210");
        line(builder, 5, "y: -120");
        line(builder, 5, "spaceBetween: 8");
        line(builder, 4, "children:");

        for (int index = 1; index <= maxVisible; index++) {
            line(builder, 5, "pickup_entry_" + index + ":");
            line(builder, 6, "type: Canvas");
            line(builder, 6, "attribute:");
            line(builder, 7, "width: 240");
            line(builder, 7, "height: 60");
            line(builder, 7, "visible: var.entry" + index + "Visible");
            line(builder, 6, "children:");

            line(builder, 7, "background" + index + ":");
            line(builder, 8, "type: Texture");
            line(builder, 8, "attribute:");
            line(builder, 9, "point: ~stretch_all");
            line(builder, 9, "normal: ~255,255,255,120");
            line(builder, 9, "shape: ~round_rect");
            line(builder, 9, "radius: 16");
            line(builder, 8, "effect:");
            line(builder, 9, "stroke:");
            line(builder, 10, "width: 2");
            line(builder, 10, "color: ~0,0,0,120");

            line(builder, 7, "slot" + index + ":");
            line(builder, 8, "val: pickup_slot_" + index);
            line(builder, 8, "type: Slot");
            line(builder, 8, "attribute:");
            line(builder, 9, "point: ~middle_left");
            line(builder, 9, "x: 10");
            line(builder, 9, "y: 0");
            line(builder, 9, "width: 40");
            line(builder, 9, "height: 40");
            line(builder, 9, "slotType: ~Icon");
            line(builder, 9, "normal: ~0,0,0,0");
            line(builder, 9, "hover: ~0,0,0,0");
            line(builder, 9, "itemScale: 0.85");
            line(builder, 9, "visible: var.entry" + index + "ItemJson != ''");

            line(builder, 7, "text" + index + ":");
            line(builder, 8, "type: Text");
            line(builder, 8, "attribute:");
            line(builder, 9, "point: ~middle_left");
            line(builder, 9, "x: 60");
            line(builder, 9, "y: 0");
            line(builder, 9, "texts: \"val.pickup_slot_" + index + ".getSlotItemStack().getName() + ' &fx' + var.entry" + index + "Amount.round()\"");
            line(builder, 9, "fontSize: 36");
        }

        return builder.toString();
    }

    private static void line(StringBuilder builder, int indent, String text) {
        for (int index = 0; index < indent; index++) {
            builder.append("  ");
        }
        builder.append(text).append('\n');
    }
}
