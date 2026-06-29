/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  io.lumine.xikage.mythicmobs.adapters.AbstractEntity
 *  io.lumine.xikage.mythicmobs.adapters.AbstractItemStack
 *  io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicDropLoadEvent
 *  io.lumine.xikage.mythicmobs.drops.Drop
 *  io.lumine.xikage.mythicmobs.drops.DropMetadata
 *  io.lumine.xikage.mythicmobs.drops.IItemDrop
 *  io.lumine.xikage.mythicmobs.io.MythicLineConfig
 *  io.lumine.xikage.mythicmobs.skills.SkillCaster
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Entity
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.event.MobDropLoadEvent;
import ink.ptms.chemdah.um.impl4.UtilsKt;
import ink.ptms.chemdah.um.item.DropMeta;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractItemStack;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicDropLoadEvent;
import io.lumine.xikage.mythicmobs.drops.Drop;
import io.lumine.xikage.mythicmobs.drops.DropMetadata;
import io.lumine.xikage.mythicmobs.drops.IItemDrop;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.skills.SkillCaster;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerDrop;", "", "()V", "onDropLoadEvent", "", "event", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicDropLoadEvent;", "implementation-v4"})
@SourceDebugExtension(value={"SMAP\nMobListenerDrop.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MobListenerDrop.kt\nink/ptms/um/impl4/MobListenerDrop\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,54:1\n1855#2,2:55\n*S KotlinDebug\n*F\n+ 1 MobListenerDrop.kt\nink/ptms/um/impl4/MobListenerDrop\n*L\n22#1:55,2\n*E\n"})
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
            event.register((Drop)new IItemDrop(dropFunc, string2, mythicLineConfig){
                final /* synthetic */ Function<DropMeta, ItemStack> $dropFunc;
                {
                    this.$dropFunc = $dropFunc;
                    super($super_call_param$1, $super_call_param$2);
                }

                @NotNull
                public AbstractItemStack getDrop(@NotNull DropMetadata dropMeta) {
                    Intrinsics.checkNotNullParameter((Object)dropMeta, (String)"dropMeta");
                    AbstractItemStack abstractItemStack = BukkitAdapter.adapt((ItemStack)this.$dropFunc.apply(new DropMeta(dropMeta){
                        final /* synthetic */ DropMetadata $dropMeta;
                        {
                            this.$dropMeta = $dropMeta;
                        }

                        @Nullable
                        public ink.ptms.chemdah.um.skill.SkillCaster getDropper() {
                            SkillCaster skillCaster = this.$dropMeta.getCaster();
                            return skillCaster != null ? UtilsKt.toUniversal(skillCaster) : null;
                        }

                        @Nullable
                        public Entity getCause() {
                            AbstractEntity abstractEntity = this.$dropMeta.getTrigger();
                            return abstractEntity != null ? abstractEntity.getBukkitEntity() : null;
                        }

                        public float getAmount() {
                            return this.$dropMeta.getAmount();
                        }

                        public void setAmount(float value2) {
                            this.$dropMeta.setAmount(value2);
                        }

                        public int getGenerations() {
                            return this.$dropMeta.getGenerations();
                        }

                        public void setGenerations(int value2) {
                            this.$dropMeta.setGenerations(value2);
                        }

                        public void tick() {
                            this.$dropMeta.tick();
                        }
                    }));
                    Intrinsics.checkNotNullExpressionValue((Object)abstractItemStack, (String)"adapt(...)");
                    return abstractItemStack;
                }
            });
        }
    }
}

