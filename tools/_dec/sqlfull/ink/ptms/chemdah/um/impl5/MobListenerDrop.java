/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.event.MobDropLoadEvent;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import ink.ptms.chemdah.um.item.DropMeta;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IDrop;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import io.lumine.mythic.core.drops.DropMetadataImpl;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerDrop;", "", "()V", "onDropLoadEvent", "", "event", "Lio/lumine/mythic/bukkit/events/MythicDropLoadEvent;", "implementation-v5"})
@SourceDebugExtension(value={"SMAP\nMobListenerDrop.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MobListenerDrop.kt\nink/ptms/um/impl5/MobListenerDrop\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,58:1\n1855#2,2:59\n*S KotlinDebug\n*F\n+ 1 MobListenerDrop.kt\nink/ptms/um/impl5/MobListenerDrop\n*L\n23#1:59,2\n*E\n"})
public final class MobListenerDrop {
    @NotNull
    public static final MobListenerDrop INSTANCE = new MobListenerDrop();

    private MobListenerDrop() {
    }

    @Ghost
    @SubscribeEvent
    public final void onDropLoadEvent(@NotNull MythicDropLoadEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        String string = event.getDropName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getDropName(...)");
        MobDropLoadEvent e = new MobDropLoadEvent(string).fire();
        Iterable $this$forEach$iv = e.getItemDrops();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Function dropFunc = (Function)element$iv;
            boolean bl = false;
            String string2 = event.getDropName();
            MythicLineConfig mythicLineConfig = event.getConfig();
            event.register((IDrop)new IItemDrop(dropFunc, string2, mythicLineConfig){
                final /* synthetic */ Function<DropMeta, ItemStack> $dropFunc;
                {
                    this.$dropFunc = $dropFunc;
                    super($super_call_param$1, $super_call_param$2);
                }

                @NotNull
                public AbstractItemStack getDrop(@NotNull DropMetadata dropMeta, double amount) {
                    Intrinsics.checkNotNullParameter((Object)dropMeta, (String)"dropMeta");
                    DropMetadataImpl cfr_ignored_0 = (DropMetadataImpl)dropMeta;
                    ItemStack itemStack = this.$dropFunc.apply(new DropMeta(dropMeta){
                        final /* synthetic */ DropMetadata $dropMeta;
                        {
                            this.$dropMeta = $dropMeta;
                        }

                        @Nullable
                        public ink.ptms.chemdah.um.skill.SkillCaster getDropper() {
                            SkillCaster skillCaster = ((DropMetadataImpl)this.$dropMeta).getCaster();
                            return skillCaster != null ? UtilsKt.toUniversal(skillCaster) : null;
                        }

                        @Nullable
                        public Entity getCause() {
                            AbstractEntity abstractEntity = ((DropMetadataImpl)this.$dropMeta).getTrigger();
                            return abstractEntity != null ? abstractEntity.getBukkitEntity() : null;
                        }

                        public float getAmount() {
                            return ((DropMetadataImpl)this.$dropMeta).getAmount();
                        }

                        public void setAmount(float value2) {
                            ((DropMetadataImpl)this.$dropMeta).setAmount(value2);
                        }

                        public int getGenerations() {
                            return ((DropMetadataImpl)this.$dropMeta).getGenerations();
                        }

                        public void setGenerations(int value2) {
                            ((DropMetadataImpl)this.$dropMeta).setGenerations(value2);
                        }

                        public void tick() {
                            this.$dropMeta.tick();
                        }
                    });
                    Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"apply(...)");
                    ItemStack item2 = itemStack;
                    item2.setAmount((int)((double)item2.getAmount() * amount));
                    AbstractItemStack abstractItemStack = BukkitAdapter.adapt((ItemStack)item2);
                    Intrinsics.checkNotNullExpressionValue((Object)abstractItemStack, (String)"adapt(...)");
                    return abstractItemStack;
                }
            });
        }
    }
}

