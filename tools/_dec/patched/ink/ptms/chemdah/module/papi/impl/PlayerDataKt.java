/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 */
package ink.ptms.chemdah.module.papi.impl;

import ink.ptms.chemdah.api.event.PlaceholderHookEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelSystem;
import ink.ptms.chemdah.module.realms.Realms;
import ink.ptms.chemdah.module.realms.RealmsSystem;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0003\u00a8\u0006\u0004"}, d2={"onPlaceholderData", "", "e", "Link/ptms/chemdah/api/event/PlaceholderHookEvent;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPlayerData.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerData.kt\nink/ptms/chemdah/module/papi/impl/PlayerDataKt\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,51:1\n1#2:52\n*E\n"})
public final class PlayerDataKt {
    @SubscribeEvent
    private static final void onPlaceholderData(PlaceholderHookEvent e) {
        switch (e.getIdentifier()) {
            case "data": {
                CharSequence charSequence;
                String key = StringsKt.substringBefore$default((String)e.getParameter(), (String)"?:", null, (int)2, null);
                CharSequence charSequence2 = StringsKt.substringAfter$default((String)e.getParameter(), (String)"?:", null, (int)2, null);
                if (charSequence2.length() == 0) {
                    boolean bl = false;
                    charSequence = "null";
                } else {
                    charSequence = charSequence2;
                }
                String def = (String)charSequence;
                Object object = e.getProfile().getPersistentDataContainer().get(key);
                if (object == null || (object = ((Data)object).toString()) == null) {
                    object = def;
                }
                e.setResult(object);
                break;
            }
            case "level": {
                LevelOption option = LevelSystem.INSTANCE.getLevelOption(e.getParameter());
                if (option != null) {
                    e.setResult(LevelSystem.INSTANCE.getLevel(e.getProfile(), option).getLevel());
                    break;
                }
                e.setResult("LEVEL_OPTION_NOT_FOUND");
                break;
            }
            case "exp": {
                LevelOption option = LevelSystem.INSTANCE.getLevelOption(e.getParameter());
                if (option != null) {
                    e.setResult(LevelSystem.INSTANCE.getLevel(e.getProfile(), option).getExperience());
                    break;
                }
                e.setResult("LEVEL_OPTION_NOT_FOUND");
                break;
            }
            case "maxexp": {
                LevelOption option = LevelSystem.INSTANCE.getLevelOption(e.getParameter());
                if (option != null) {
                    e.setResult(option.getAlgorithm().getExp(LevelSystem.INSTANCE.getLevel(e.getProfile(), option).getLevel()).getNow(0));
                    break;
                }
                e.setResult("LEVEL_OPTION_NOT_FOUND");
                break;
            }
            case "realms": 
            case "realm": {
                Location location = e.getPlayer().getLocation();
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
                Object object = RealmsSystem.INSTANCE.getRealms(location);
                if (object == null || (object = ((Realms)object).getId()) == null) {
                    object = "null";
                }
                e.setResult(object);
            }
        }
    }
}

