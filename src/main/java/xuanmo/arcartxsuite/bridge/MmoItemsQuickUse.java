package xuanmo.arcartxsuite.bridge;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.player.skill.PassiveSkill;
import io.lumine.mythic.lib.skill.trigger.TriggerMetadata;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.interaction.Consumable;
import net.Indyuce.mmoitems.api.interaction.UseItem;
import net.Indyuce.mmoitems.stat.data.AbilityListData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI.MmoClickResult;

import static xuanmo.arcartxsuite.api.bridge.PropBridgeAPI.MmoClickResult.*;

/** MMOItems 6.9.5 的额外槽位右键适配，使用物品自身的命令、消耗和技能入口。 */
final class MmoItemsQuickUse {
    private MmoItemsQuickUse() {}

    static boolean usesHandForRemainingDoses(ItemStack stack) {
        NBTItem tag = NBTItem.get(stack);
        return tag.hasType() && tag.getInteger(ItemStats.MAX_CONSUME.getNBTPath()) > 1;
    }

    static MmoClickResult use(Player player, ItemStack stack, BooleanSupplier takeOne, Runnable refundOne) {
        NBTItem tag = NBTItem.get(stack);
        if (!tag.hasType()) return UNSUPPORTED;
        if (tag.getBoolean("MMOITEMS_DISABLE_INTERACTION") || tag.hasTag("MMOITEMS_UNIDENTIFIED_ITEM")) {
            return REJECTED;
        }

        UseItem item = UseItem.getItem(player, tag, tag.getType());
        var definition = item.getMMOItem();
        Consumable consumable = item instanceof Consumable value ? value : null;
        if (consumable != null && (consumable.hasVanillaEating()
            || tag.getInteger(ItemStats.MAX_CONSUME.getNBTPath()) > 1)) {
            return UNSUPPORTED;
        }
        TriggerType trigger = player.isSneaking() ? TriggerType.SHIFT_RIGHT_CLICK : TriggerType.RIGHT_CLICK;
        // VolatileMMOItem loads stat data on hasData(), while getData() only reads the cache.
        List<PassiveSkill> skills = definition.hasData(ItemStats.ABILITIES)
            && definition.getData(ItemStats.ABILITIES) instanceof AbilityListData abilities
            ? abilities.getAbilities().stream().filter(skill -> trigger.equals(skill.getTrigger()))
                .map(skill -> new PassiveSkill("MMOItemsItem", skill, EquipmentSlot.MAIN_HAND,
                    definition.getType().getModifierSource()))
                .toList()
            : List.of();
        boolean hasItemAction = consumable != null || definition.hasData(ItemStats.COMMANDS);
        if (!hasItemAction && skills.isEmpty()) return UNSUPPORTED;
        if (!item.checkItemRequirements()) return REJECTED;

        var data = item.getPlayerData().getMMOPlayerData();
        var cooldowns = data.getCooldownMap();
        String reference = tag.getString("MMOITEMS_COOLDOWN_REFERENCE");
        if (reference == null || reference.isEmpty()) reference = definition.getCooldownPath();
        if (cooldowns.isOnCooldown(reference)) return REJECTED;

        if (consumable != null) {
            if (!takeOne.getAsBoolean()) return SLOT_UPDATE_FAILED;
            var result = consumable.useOnPlayer(org.bukkit.inventory.EquipmentSlot.HAND, false);
            if (result != Consumable.ConsumableConsumeResult.CONSUME) refundOne.run();
            if (result == Consumable.ConsumableConsumeResult.CANCEL) return REJECTED;
        }

        cooldowns.applyCooldown(reference, tag.getStat("ITEM_COOLDOWN"));
        if (hasItemAction) item.executeCommands();
        if (!skills.isEmpty()) {
            var metadata = new TriggerMetadata(data, trigger, EquipmentSlot.MAIN_HAND, null, null, null, null, null);
            data.triggerSkills(metadata, skills);
        }
        return USED;
    }
}
