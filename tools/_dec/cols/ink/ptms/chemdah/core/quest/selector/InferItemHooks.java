/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import com.ssomar.executableitems.api.ExecutableItemsAPI;
import com.ssomar.executableitems.items.Item;
import github.july_summer.julyitems.api.JItemAPI;
import github.july_summer.julyitems.item.JItem;
import ink.ptms.chemdah.api.event.InferItemHookEvent;
import ink.ptms.chemdah.core.quest.selector.DataMatch;
import ink.ptms.chemdah.core.quest.selector.Flags;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.module.nms.ItemTagData;
import ink.ptms.chemdah.taboolib.module.nms.NMSItemTagKt;
import ink.ptms.zaphkiel.api.ItemStream;
import ink.ptms.zaphkiel.impl.item.ExtensionsKt;
import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import net.mmogroup.mmolib.MMOLib;
import net.mmogroup.mmolib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.quantumrpg.stats.items.ItemStats;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.RPGItem;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0007\u0007\b\t\n\u000b\f\rB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0003\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks;", "", "()V", "onItemHook", "", "e", "Link/ptms/chemdah/api/event/InferItemHookEvent;", "ItemExecutableItems", "ItemJulyItems", "ItemMMOItem", "ItemPxRPG", "ItemQuantumRPG", "ItemRPGItem", "ItemZaphkiel", "Chemdah"})
public final class InferItemHooks {
    @NotNull
    public static final InferItemHooks INSTANCE = new InferItemHooks();

    private InferItemHooks() {
    }

    @SubscribeEvent
    private final void onItemHook(InferItemHookEvent e) {
        String string = e.getId().toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        switch (string) {
            case "zaphkiel": {
                e.setItemClass(ItemZaphkiel.class);
                break;
            }
            case "rpgitems": 
            case "rpgitem": {
                e.setItemClass(ItemRPGItem.class);
                break;
            }
            case "mmoitem": 
            case "mmoitems": {
                e.setItemClass(ItemMMOItem.class);
                break;
            }
            case "quantumrpg": 
            case "qrpg": {
                e.setItemClass(ItemQuantumRPG.class);
                break;
            }
            case "pxrpg": {
                e.setItemClass(ItemPxRPG.class);
                break;
            }
            case "julyitem": 
            case "julyitems": {
                e.setItemClass(ItemJulyItems.class);
                break;
            }
            case "executableitems": 
            case "eitems": {
                e.setItemClass(ItemExecutableItems.class);
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\n\u0010\u000e\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemExecutableItems;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "executableId", "Chemdah"})
    public static final class ItemExecutableItems
    extends InferItem.Item {
        public ItemExecutableItems(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String executableId(@NotNull ItemStack $this$executableId) {
            Intrinsics.checkNotNullParameter((Object)$this$executableId, (String)"<this>");
            Item item2 = ExecutableItemsAPI.getExecutableItemConfig((ItemStack)$this$executableId);
            String string = item2 != null ? item2.getIdentification() : null;
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.executableId(item2)) && this.matchMetaData(item2);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\n\u0010\u000e\u001a\u00020\u0003*\u00020\rJ\n\u0010\u000f\u001a\u00020\u0010*\u00020\r\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemJulyItems;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "julyName", "quantumLevel", "", "Chemdah"})
    public static final class ItemJulyItems
    extends InferItem.Item {
        public ItemJulyItems(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String julyName(@NotNull ItemStack $this$julyName) {
            Intrinsics.checkNotNullParameter((Object)$this$julyName, (String)"<this>");
            JItem jItem = JItemAPI.getInstance().toJItem($this$julyName);
            String string = jItem != null ? jItem.itemId : null;
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        public final int quantumLevel(@NotNull ItemStack $this$quantumLevel) {
            Intrinsics.checkNotNullParameter((Object)$this$quantumLevel, (String)"<this>");
            return ItemStats.getLevel((ItemStack)$this$quantumLevel);
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.julyName(item2)) && this.matchMetaData(item2);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\"\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\bH\u0016J\n\u0010\u0012\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0013\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0014\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemMMOItem;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "mmoId", "mmoSet", "mmoType", "Chemdah"})
    public static final class ItemMMOItem
    extends InferItem.Item {
        public ItemMMOItem(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String mmoId(@NotNull ItemStack $this$mmoId) {
            Intrinsics.checkNotNullParameter((Object)$this$mmoId, (String)"<this>");
            NBTItem item2 = MMOLib.plugin.getVersion().getWrapper().getNBTItem($this$mmoId);
            String string = item2.getString("MMOITEMS_ITEM_ID");
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        @NotNull
        public final String mmoSet(@NotNull ItemStack $this$mmoSet) {
            Intrinsics.checkNotNullParameter((Object)$this$mmoSet, (String)"<this>");
            NBTItem item2 = MMOLib.plugin.getVersion().getWrapper().getNBTItem($this$mmoSet);
            String string = item2.getString("MMOITEMS_ITEM_SET");
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        @NotNull
        public final String mmoType(@NotNull ItemStack $this$mmoType) {
            Intrinsics.checkNotNullParameter((Object)$this$mmoType, (String)"<this>");
            NBTItem item2 = MMOLib.plugin.getVersion().getWrapper().getNBTItem($this$mmoType);
            String string = item2.getString("MMOITEMS_ITEM_TYPE");
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.mmoId(item2)) && this.matchMetaData(item2);
        }

        @Override
        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            String string = dataMatch.getKey();
            return Intrinsics.areEqual((Object)string, (Object)"set") ? DataMatch.check$default(dataMatch, this.mmoSet(item2), null, 2, null) : (Intrinsics.areEqual((Object)string, (Object)"type") ? DataMatch.check$default(dataMatch, this.mmoType(item2), null, 2, null) : super.matchMetaData(item2, itemMeta, dataMatch));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0005\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\"\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\bH\u0016J\n\u0010\u0012\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0013\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0014\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0015\u001a\u00020\u0016*\u00020\rJ\n\u0010\u0017\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0018\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0019\u001a\u00020\u0003*\u00020\rJ\n\u0010\u001a\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemPxRPG;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "pxAuthor", "pxBind", "pxId", "pxLevel", "", "pxName", "pxQuality", "pxTemplate", "pxType", "Chemdah"})
    public static final class ItemPxRPG
    extends InferItem.Item {
        public ItemPxRPG(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String pxId(@NotNull ItemStack $this$pxId) {
            Intrinsics.checkNotNullParameter((Object)$this$pxId, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxId, (boolean)false, (int)1, null).getDeep("pxrpg.id");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @NotNull
        public final String pxName(@NotNull ItemStack $this$pxName) {
            Intrinsics.checkNotNullParameter((Object)$this$pxName, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxName, (boolean)false, (int)1, null).getDeep("pxrpg.name");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @NotNull
        public final String pxAuthor(@NotNull ItemStack $this$pxAuthor) {
            Intrinsics.checkNotNullParameter((Object)$this$pxAuthor, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxAuthor, (boolean)false, (int)1, null).getDeep("pxrpg.authorName");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @NotNull
        public final String pxQuality(@NotNull ItemStack $this$pxQuality) {
            Intrinsics.checkNotNullParameter((Object)$this$pxQuality, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxQuality, (boolean)false, (int)1, null).getDeep("pxrpg.itemQuality");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @NotNull
        public final String pxType(@NotNull ItemStack $this$pxType) {
            Intrinsics.checkNotNullParameter((Object)$this$pxType, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxType, (boolean)false, (int)1, null).getDeep("pxrpg.itemType");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @NotNull
        public final String pxTemplate(@NotNull ItemStack $this$pxTemplate) {
            Intrinsics.checkNotNullParameter((Object)$this$pxTemplate, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxTemplate, (boolean)false, (int)1, null).getDeep("pxrpg.template");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        public final int pxLevel(@NotNull ItemStack $this$pxLevel) {
            Intrinsics.checkNotNullParameter((Object)$this$pxLevel, (String)"<this>");
            ItemTagData itemTagData = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxLevel, (boolean)false, (int)1, null).getDeep("pxrpg.level");
            return itemTagData != null ? itemTagData.asInt() : -1;
        }

        @NotNull
        public final String pxBind(@NotNull ItemStack $this$pxBind) {
            Intrinsics.checkNotNullParameter((Object)$this$pxBind, (String)"<this>");
            Object object = NMSItemTagKt.getItemTag$default((ItemStack)$this$pxBind, (boolean)false, (int)1, null).getDeep("pxrpg.bind");
            if (object == null || (object = object.asString()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.pxId(item2)) && this.matchMetaData(item2);
        }

        @Override
        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            boolean bl;
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            switch (dataMatch.getKey()) {
                case "name": {
                    bl = DataMatch.check$default(dataMatch, this.pxName(item2), null, 2, null);
                    break;
                }
                case "author": {
                    bl = DataMatch.check$default(dataMatch, this.pxAuthor(item2), null, 2, null);
                    break;
                }
                case "quality": {
                    bl = DataMatch.check$default(dataMatch, this.pxQuality(item2), null, 2, null);
                    break;
                }
                case "type": {
                    bl = DataMatch.check$default(dataMatch, this.pxType(item2), null, 2, null);
                    break;
                }
                case "template": {
                    bl = DataMatch.check$default(dataMatch, this.pxTemplate(item2), null, 2, null);
                    break;
                }
                case "level": {
                    bl = DataMatch.check$default(dataMatch, this.pxLevel(item2), null, 2, null);
                    break;
                }
                case "bind": {
                    bl = DataMatch.check$default(dataMatch, this.pxBind(item2), null, 2, null);
                    break;
                }
                default: {
                    bl = super.matchMetaData(item2, itemMeta, dataMatch);
                }
            }
            return bl;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\"\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\bH\u0016J\n\u0010\u0012\u001a\u00020\u0013*\u00020\rJ\n\u0010\u0014\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemQuantumRPG;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "quantumLevel", "", "quantumName", "Chemdah"})
    public static final class ItemQuantumRPG
    extends InferItem.Item {
        public ItemQuantumRPG(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String quantumName(@NotNull ItemStack $this$quantumName) {
            Intrinsics.checkNotNullParameter((Object)$this$quantumName, (String)"<this>");
            String string = ItemStats.getId((ItemStack)$this$quantumName);
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        public final int quantumLevel(@NotNull ItemStack $this$quantumLevel) {
            Intrinsics.checkNotNullParameter((Object)$this$quantumLevel, (String)"<this>");
            return ItemStats.getLevel((ItemStack)$this$quantumLevel);
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.quantumName(item2)) && this.matchMetaData(item2);
        }

        @Override
        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            return Intrinsics.areEqual((Object)dataMatch.getKey(), (Object)"level") ? DataMatch.check$default(dataMatch, this.quantumLevel(item2), null, 2, null) : super.matchMetaData(item2, itemMeta, dataMatch);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\"\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\bH\u0016J\n\u0010\u0012\u001a\u00020\u0003*\u00020\rJ\n\u0010\u0013\u001a\u00020\u0014*\u00020\r\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemRPGItem;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "rpgName", "rpgUid", "", "Chemdah"})
    public static final class ItemRPGItem
    extends InferItem.Item {
        public ItemRPGItem(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String rpgName(@NotNull ItemStack $this$rpgName) {
            Intrinsics.checkNotNullParameter((Object)$this$rpgName, (String)"<this>");
            RPGItem rPGItem = ItemManager.toRPGItem((ItemStack)$this$rpgName).orElse(null);
            String string = rPGItem != null ? rPGItem.getName() : null;
            if (string == null) {
                string = "@vanilla";
            }
            return string;
        }

        public final int rpgUid(@NotNull ItemStack $this$rpgUid) {
            Intrinsics.checkNotNullParameter((Object)$this$rpgUid, (String)"<this>");
            RPGItem rPGItem = ItemManager.toRPGItem((ItemStack)$this$rpgUid).orElse(null);
            return rPGItem != null ? rPGItem.getUid() : -1;
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.rpgName(item2)) && this.matchMetaData(item2);
        }

        @Override
        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            return Intrinsics.areEqual((Object)dataMatch.getKey(), (Object)"uid") ? DataMatch.check$default(dataMatch, this.rpgUid(item2), null, 2, null) : super.matchMetaData(item2, itemMeta, dataMatch);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\"\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\bH\u0016J\n\u0010\u0012\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemHooks$ItemZaphkiel;", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "zaphkielId", "Chemdah"})
    public static final class ItemZaphkiel
    extends InferItem.Item {
        public ItemZaphkiel(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String zaphkielId(@NotNull ItemStack $this$zaphkielId) {
            Intrinsics.checkNotNullParameter((Object)$this$zaphkielId, (String)"<this>");
            ItemStream itemStream = ExtensionsKt.toExtensionStreamOrNull((ItemStack)$this$zaphkielId);
            Object object = itemStream;
            if (object == null || (object = object.getZaphkielName()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @Override
        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            return this.matchType(this.zaphkielId(item2)) && this.matchMetaData(item2);
        }

        @Override
        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            if (StringsKt.startsWith$default((String)dataMatch.getKey(), (String)"config.", (boolean)false, (int)2, null)) {
                Object data2;
                ItemStream itemStream = ExtensionsKt.toExtensionStreamOrNull((ItemStack)item2);
                if (itemStream == null) {
                    return false;
                }
                ItemStream itemStream2 = itemStream;
                Object object = data2 = itemStream2.getZaphkielItem().getConfig().get(StringsKt.substringAfter$default((String)dataMatch.getKey(), (String)"data.", null, (int)2, null));
                return object != null ? DataMatch.check$default(dataMatch, object, null, 2, null) : false;
            }
            if (StringsKt.startsWith$default((String)dataMatch.getKey(), (String)"data.", (boolean)false, (int)2, null)) {
                Object data3;
                ItemStream itemStream = ExtensionsKt.toExtensionStreamOrNull((ItemStack)item2);
                if (itemStream == null) {
                    return false;
                }
                ItemStream itemStream3 = itemStream;
                ink.ptms.zaphkiel.taboolib.module.nms.ItemTagData itemTagData = itemStream3.getZaphkielData().getDeep(StringsKt.substringAfter$default((String)dataMatch.getKey(), (String)"data.", null, (int)2, null));
                Object object = data3 = itemTagData != null ? itemTagData.unsafeData() : null;
                return object != null ? DataMatch.check$default(dataMatch, object, null, 2, null) : false;
            }
            return super.matchMetaData(item2, itemMeta, dataMatch);
        }
    }
}

