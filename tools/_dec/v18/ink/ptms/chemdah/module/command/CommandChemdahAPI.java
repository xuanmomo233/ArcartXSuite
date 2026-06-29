/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.command.CommandSender
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.core.bukkit.NMS;
import ink.ptms.chemdah.module.command.CommandChemdahAPI;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="ChemdahAPI", aliases={"chapi", "cha"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020 J\u0016\u0010!\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\"\u001a\u00020#R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0006R\u0016\u0010\u000b\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0006R\u0016\u0010\r\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0016\u0010\u000f\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0006R\u0016\u0010\u0011\u001a\u00020\u00128\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0016\u0010\u0015\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0006R\u0016\u0010\u0017\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0006R\u0016\u0010\u0019\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0006\u00a8\u0006$"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahAPI;", "", "()V", "blockinfo", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getBlockinfo", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "cancelscenes", "getCancelscenes", "check", "getCheck", "conversation", "getConversation", "createscenes", "getCreatescenes", "generate", "getGenerate", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "objective", "getObjective", "position", "getPosition", "wizard", "getWizard", "sendBlockInfo", "", "sender", "Lorg/bukkit/command/CommandSender;", "block", "Lorg/bukkit/block/Block;", "sendPositionInfo", "loc", "Lorg/bukkit/Location;", "Chemdah"})
public final class CommandChemdahAPI {
    @NotNull
    public static final CommandChemdahAPI INSTANCE = new CommandChemdahAPI();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody createscenes = SimpleCommandKt.subCommand((Function1)createscenes.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody cancelscenes = SimpleCommandKt.subCommand((Function1)cancelscenes.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody conversation = SimpleCommandKt.subCommand((Function1)conversation.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody generate = SimpleCommandKt.subCommand((Function1)generate.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody blockinfo = SimpleCommandKt.subCommand((Function1)blockinfo.1.INSTANCE);
    @CommandBody(aliases={"pos"})
    @NotNull
    private static final SimpleCommandBody position = SimpleCommandKt.subCommand((Function1)position.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody objective = SimpleCommandKt.subCommand((Function1)objective.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody wizard = SimpleCommandKt.subCommand((Function1)wizard.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody check = SimpleCommandKt.subCommand((Function1)check.1.INSTANCE);

    private CommandChemdahAPI() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getCreatescenes() {
        return createscenes;
    }

    @NotNull
    public final SimpleCommandBody getCancelscenes() {
        return cancelscenes;
    }

    @NotNull
    public final SimpleCommandBody getConversation() {
        return conversation;
    }

    @NotNull
    public final SimpleCommandBody getGenerate() {
        return generate;
    }

    @NotNull
    public final SimpleCommandBody getBlockinfo() {
        return blockinfo;
    }

    @NotNull
    public final SimpleCommandBody getPosition() {
        return position;
    }

    @NotNull
    public final SimpleCommandBody getObjective() {
        return objective;
    }

    @NotNull
    public final SimpleCommandBody getWizard() {
        return wizard;
    }

    @NotNull
    public final SimpleCommandBody getCheck() {
        return check;
    }

    public final void sendBlockInfo(@NotNull CommandSender sender, @NotNull Block block) {
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)block, (String)"block");
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-block-info-header", (Object[])new Object[0]);
        new RawMessage(null, 1, null).sendTo(AdapterKt.adaptCommandSender((Object)sender), (Function1)new Function1<RawMessage, Unit>(block){
            final /* synthetic */ Block $block;
            {
                this.$block = $block;
                super(1);
            }

            public final void invoke(@NotNull RawMessage $this$sendTo) {
                RawMessage rawMessage;
                Intrinsics.checkNotNullParameter((Object)$this$sendTo, (String)"$this$sendTo");
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                if (MinecraftVersion.INSTANCE.isLowerOrEqual(4)) {
                    StringBuilder stringBuilder = new StringBuilder().append("&8- &f");
                    String string = this.$block.getType().name().toLowerCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                    rawMessage = $this$sendTo.append(UtilKt.colored((String)stringBuilder.append(string).append(':').append(this.$block.getData()).toString()));
                } else {
                    StringBuilder stringBuilder = new StringBuilder().append("&8- &f");
                    String string = this.$block.getType().name().toLowerCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                    rawMessage = $this$sendTo.append(UtilKt.colored((String)stringBuilder.append(string).toString()));
                }
                RawMessage rawMessage2 = rawMessage.hoverText(UtilKt.colored((String)"&7Click to copy"));
                String string = this.$block.getType().name().toLowerCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                rawMessage2.suggestCommand(string).newLine();
                Map<String, Object> blocKData = NMS.Companion.getINSTANCE().getBlocKData(this.$block);
                if (!blocKData.isEmpty()) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String string2 = this.$block.getType().name().toLowerCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                    String info2 = stringBuilder.append(string2).append('[').append(CollectionsKt.joinToString$default((Iterable)blocKData.entrySet(), (CharSequence)",", null, null, (int)0, null, (Function1)sendBlockInfo.info.1.INSTANCE, (int)30, null)).append(']').toString();
                    $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                    $this$sendTo.append(UtilKt.colored((String)("&8- &f" + info2))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand(info2);
                }
            }
        });
    }

    public final void sendPositionInfo(@NotNull CommandSender sender, @NotNull Location loc) {
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)loc, (String)"loc");
        double x = Coerce.format((double)loc.getX());
        double y = Coerce.format((double)loc.getY());
        double z = Coerce.format((double)loc.getZ());
        double yaw = Coerce.format((double)loc.getYaw());
        double pitch = Coerce.format((double)loc.getPitch());
        BukkitLangKt.sendLang((CommandSender)sender, (String)"command-position-header", (Object[])new Object[0]);
        new RawMessage(null, 1, null).sendTo(AdapterKt.adaptCommandSender((Object)sender), (Function1)new Function1<RawMessage, Unit>(x, y, z, yaw, pitch){
            final /* synthetic */ double $x;
            final /* synthetic */ double $y;
            final /* synthetic */ double $z;
            final /* synthetic */ double $yaw;
            final /* synthetic */ double $pitch;
            {
                this.$x = $x;
                this.$y = $y;
                this.$z = $z;
                this.$yaw = $yaw;
                this.$pitch = $pitch;
                super(1);
            }

            public final void invoke(@NotNull RawMessage $this$sendTo) {
                Intrinsics.checkNotNullParameter((Object)$this$sendTo, (String)"$this$sendTo");
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                $this$sendTo.append(UtilKt.colored((String)("&8- &f" + this.$x + ',' + this.$y + ',' + this.$z))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + this.$x + ',' + this.$y + ',' + this.$z).append(" ");
                $this$sendTo.append(UtilKt.colored((String)("&7(" + (int)this.$x + ',' + (int)this.$y + ',' + (int)this.$z + ')'))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + (int)this.$x + ',' + (int)this.$y + ',' + (int)this.$z).newLine();
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                $this$sendTo.append(UtilKt.colored((String)("&8- &fx=" + this.$x + ",y=" + this.$y + ",z=" + this.$z))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("x=" + this.$x + ",y=" + this.$y + ",z=" + this.$z).append(" ");
                $this$sendTo.append(UtilKt.colored((String)("&7(x=" + (int)this.$x + ",y=" + (int)this.$y + ",z=" + (int)this.$z + ')'))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("x=" + (int)this.$x + ",y=" + (int)this.$y + ",z=" + (int)this.$z).newLine();
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                $this$sendTo.append(UtilKt.colored((String)("&8- &f" + this.$x + ' ' + this.$y + ' ' + this.$z))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + this.$x + ' ' + this.$y + ' ' + this.$z).append(" ");
                $this$sendTo.append(UtilKt.colored((String)("&7(" + (int)this.$x + ' ' + (int)this.$y + ' ' + (int)this.$z + ')'))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + (int)this.$x + ' ' + (int)this.$y + ' ' + (int)this.$z).newLine();
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                $this$sendTo.append(UtilKt.colored((String)("&8- &f" + this.$x + ' ' + this.$y + ' ' + this.$z + ' ' + this.$yaw + ' ' + this.$pitch))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + this.$x + ' ' + this.$y + ' ' + this.$z + ' ' + this.$yaw + ' ' + this.$pitch).append(" ");
                $this$sendTo.append(UtilKt.colored((String)("&7(" + (int)this.$x + ' ' + (int)this.$y + ' ' + (int)this.$z + ' ' + (int)this.$yaw + ' ' + (int)this.$pitch + ')'))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("" + (int)this.$x + ' ' + (int)this.$y + ' ' + (int)this.$z + ' ' + (int)this.$yaw + ' ' + (int)this.$pitch).newLine();
                $this$sendTo.append(UtilKt.colored((String)"&c[Chemdah] "));
                $this$sendTo.append(UtilKt.colored((String)("&8- &fx to " + this.$x + " y to " + this.$y + " z to " + this.$z))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("x to " + this.$x + " y to " + this.$y + " z to " + this.$z).append(" ");
                $this$sendTo.append(UtilKt.colored((String)("&7(x to " + (int)this.$x + " y to " + (int)this.$y + " z to " + (int)this.$z + ')'))).hoverText(UtilKt.colored((String)"&7Click to copy")).suggestCommand("x to " + (int)this.$x + " y to " + (int)this.$y + " z to " + (int)this.$z).newLine();
            }
        });
    }
}

