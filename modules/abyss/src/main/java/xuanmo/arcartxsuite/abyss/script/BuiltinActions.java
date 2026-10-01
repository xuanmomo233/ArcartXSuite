package xuanmo.arcartxsuite.abyss.script;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.potion.PotionEffectTypeResolver;

/**
 * 内置动作集（走宿主基础设施）。
 *
 * 选择器：@all @trigger @random @dead 或玩家名。
 * 所有 {player}/{var:x} 等占位由 context.replace 处理。
 */
final class BuiltinActions {

    private BuiltinActions() { }

    static void registerAll(ActionRegistry reg, ScriptRunner runner,
                            Supplier<AbyssService> service) {

        // ═══ 消息/展示 ═══
        reg.register("message", (ctx, args, s) ->
            forEach(ctx, first(args), p -> p.sendMessage(
                color(ctx.replace(rest(args))))));
        reg.register("broadcast", (ctx, args, s) -> {
            String msg = color(ctx.replace(args));
            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        });
        reg.register("title", (ctx, args, s) -> {
            // title <sel> <fadeIn> <stay> <fadeOut> <主标题>|<副标题>
            String[] parts = splitFirst(args, 4);
            if (parts.length < 4) return;
            int[] times = parseInts(parts[1], parts[2], parts[3]);
            String[] tt = rest(parts, 4).split("\\|", 2);
            forEach(ctx, parts[0], p -> p.sendTitle(
                color(ctx.replace(tt[0])),
                tt.length > 1 ? color(ctx.replace(tt[1])) : "",
                times[0], times[1], times[2]));
        });
        reg.register("actionbar", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            forEach(ctx, parts[0], p -> actionBar(p, color(ctx.replace(rest(parts, 1)))));
        });
        reg.register("sound", (ctx, args, s) -> {
            // sound <sel> <音效> [音量] [音调]
            String[] parts = splitFirst(args, 3);
            if (parts.length < 2) return;
            try {
                Sound sound = Sound.valueOf(ctx.replace(parts[1]).toUpperCase());
                float vol = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
                float pitch = parts.length > 3 ? Float.parseFloat(rest(parts, 3)) : 1f;
                forEach(ctx, parts[0], p -> p.playSound(p.getLocation(), sound, vol, pitch));
            } catch (Exception ignored) { }
        });
        reg.register("effect", (ctx, args, s) -> {
            // effect <sel> <粒子> [数量] [偏移]
            String[] parts = splitFirst(args, 3);
            if (parts.length < 2) return;
            try {
                Particle particle = Particle.valueOf(ctx.replace(parts[1]).toUpperCase());
                int count = parts.length > 2 ? Integer.parseInt(parts[2]) : 20;
                double off = parts.length > 3 ? Double.parseDouble(rest(parts, 3)) : 0.5;
                forEach(ctx, parts[0], p ->
                    p.spawnParticle(particle, p.getLocation().add(0, 1, 0),
                        count, off, off, off, 0.05));
            } catch (Exception ignored) { }
        });
        reg.register("firework", (ctx, args, s) -> {
            // firework <sel> [类型] [颜色]
            String[] parts = splitFirst(args, 2);
            forEach(ctx, parts[0], p -> {
                Firework fw = (Firework) p.getWorld().spawnEntity(
                    p.getLocation(), EntityType.FIREWORK);
                FireworkMeta meta = fw.getFireworkMeta();
                FireworkEffect.Type type = parts.length > 1
                    ? parseEnum(FireworkEffect.Type.class, ctx.replace(parts[1]), FireworkEffect.Type.BURST)
                    : FireworkEffect.Type.BURST;
                Color c = parts.length > 2
                    ? parseColor(rest(parts, 2)) : Color.fromRGB(0xFFD700);
                meta.addEffect(FireworkEffect.builder().with(type).withColor(c).withTrail().build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            });
        });
        reg.register("bossbar", (ctx, args, s) -> {
            // bossbar <sel> <秒数或clear> <颜色> <样式> <标题...>
            String[] parts = splitFirst(args, 4);
            if (parts.length < 3) return;
            String colorName = parts.length > 2 ? parts[2] : "WHITE";
            String style = parts.length > 3 ? first(rest(parts, 3)) : "SOLID";
            String title = parts.length > 4 ? rest(parts, 4) : rest(parts, 3);
            forEach(ctx, parts[0], p -> service.get().bossBar(
                p, ctx.replace(parts[1]), color(ctx.replace(title)),
                ctx.replace(colorName) + " " + ctx.replace(style)));
        });

        // ═══ 玩家操作 ═══
        reg.register("teleport", (ctx, args, s) -> {
            // teleport <sel> <x,y,z[,yaw,pitch]>
            String[] parts = splitFirst(args, 1);
            Location loc = parseLoc(ctx, rest(parts, 1));
            if (loc == null) return;
            forEach(ctx, parts[0], p -> p.teleport(loc));
        });
        reg.register("teleport_dungeon", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            Location loc = service.get().spawnLocation(ctx.getInstance());
            if (loc != null) forEach(ctx, parts[0], p -> p.teleport(loc));
        });
        reg.register("heal", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            double amount = parts.length > 1 ? parseDouble(rest(parts, 1), -1) : -1;
            forEach(ctx, parts[0], p -> {
                double max = p.getMaxHealth();
                p.setHealth(amount < 0 ? max : Math.min(max, p.getHealth() + amount));
            });
        });
        reg.register("feed", (ctx, args, s) ->
            forEach(ctx, args, p -> {
                p.setFoodLevel(20);
                p.setSaturation(10f);
            }));
        reg.register("gamemode", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            GameMode gm = parseEnum(GameMode.class, ctx.replace(rest(parts, 1)), null);
            if (gm != null) forEach(ctx, parts[0], p -> p.setGameMode(gm));
        });
        reg.register("fly", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            boolean on = !"off".equalsIgnoreCase(rest(parts, 1));
            forEach(ctx, parts[0], p -> { p.setAllowFlight(on); p.setFlying(on); });
        });
        reg.register("apply_potion", (ctx, args, s) -> {
            // apply_potion <sel> <类型> <秒> [等级（1 起，与 prop/title 一致）]
            String[] parts = splitFirst(args, 3);
            if (parts.length < 3) return;
            PotionEffectType type = PotionEffectTypeResolver.resolve(ctx.replace(parts[1]));
            if (type == null) return;
            int ticks = (int) (parseDouble(parts[2], 5) * 20);
            int level = parts.length > 3 ? (int) parseDouble(rest(parts, 3), 1) : 1;
            int amp = Math.max(0, level - 1);
            forEach(ctx, parts[0], p ->
                p.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true)));
        });
        reg.register("clear_potion", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String t = rest(parts, 1);
            forEach(ctx, parts[0], p -> {
                if (t.isBlank()) {
                    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                } else {
                    PotionEffectType type = PotionEffectTypeResolver.resolve(t);
                    if (type != null) p.removePotionEffect(type);
                }
            });
        });
        reg.register("give_exp", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            int amount = (int) parseDouble(rest(parts, 1), 0);
            forEach(ctx, parts[0], p -> p.giveExp(amount));
        });
        reg.register("setlevel", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            int level = (int) parseDouble(rest(parts, 1), 0);
            forEach(ctx, parts[0], p -> p.setLevel(level));
        });
        reg.register("velocity", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String[] v = rest(parts, 1).split(",");
            if (v.length < 3) return;
            forEach(ctx, parts[0], p -> p.setVelocity(new org.bukkit.util.Vector(
                parseDouble(v[0], 0), parseDouble(v[1], 0), parseDouble(v[2], 0))));
        });
        reg.register("revive_player", (ctx, args, s) ->
            forEach(ctx, args, p -> service.get().revivePlayer(ctx.getInstance(), p)));

        // ═══ 物品 ═══
        reg.register("give_item", (ctx, args, s) -> {
            // give_item <sel> <物品定义: material:X:n | mythic:id:n | ...>
            String[] parts = splitFirst(args, 1);
            String def = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> service.get().giveDefinedItem(p, def, ctx.getInstance()));
        });
        reg.register("take_item", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String def = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> service.get().takeDefinedItem(p, def));
        });
        reg.register("clear_inventory", (ctx, args, s) ->
            forEach(ctx, args, p -> p.getInventory().clear()));

        // ═══ 命令 ═══
        reg.register("command", (ctx, args, s) ->
            runSync(runner, () -> Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(), ctx.replace(args))));
        reg.register("console", (ctx, args, s) ->
            runSync(runner, () -> Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(), ctx.replace(args))));
        reg.register("player_command", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String cmd = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> runSync(runner, () -> p.performCommand(cmd)));
        });
        reg.register("op_command", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String cmd = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> {
                boolean wasOp = p.isOp();
                try {
                    if (!wasOp) p.setOp(true);
                    p.performCommand(cmd);
                } finally {
                    if (!wasOp) p.setOp(false);
                }
            });
        });

        // ═══ 副本流程 ═══
        reg.register("complete_dungeon", (ctx, args, s) ->
            service.get().completeDungeon(ctx.getInstance()));
        reg.register("fail_dungeon", (ctx, args, s) ->
            service.get().failDungeon(ctx.getInstance(), FailReason.SCRIPTED));
        reg.register("cancel_dungeon", (ctx, args, s) ->
            service.get().cancelDungeon(ctx.getInstance()));
        reg.register("exit_dungeon", (ctx, args, s) ->
            forEach(ctx, args, p -> service.get().playerExit(ctx.getInstance(), p, LeaveReason.QUIT)));
        reg.register("kick_all_players", (ctx, args, s) -> {
            for (Player p : ctx.getInstance().getPlayers()) {
                service.get().playerExit(ctx.getInstance(), p, LeaveReason.ADMIN);
            }
        });
        reg.register("run_script", (ctx, args, s) ->
            service.get().runCustomScript(ctx.getInstance(), ctx.replace(args), ctx.getTrigger()));
        reg.register("delay", (ctx, args, s) -> {
            // delay <秒> <动作行...>：延迟执行一段动作（不阻塞当前脚本）
            String[] parts = splitFirst(args, 1);
            double sec = parseDouble(parts[0], 0);
            String line = rest(parts, 1);
            xuanmo.arcartxsuite.api.scheduler.ScheduledTask[] holder =
                new xuanmo.arcartxsuite.api.scheduler.ScheduledTask[1];
            holder[0] = runner.scheduler().scheduleAtFixedRate(runner.plugin(), () -> {
                if (holder[0] != null) {
                    try { holder[0].cancel(); } catch (Throwable ignored) { }
                }
                runner.executeLine(line, ctx, s);
            }, (long) (sec * 20), Long.MAX_VALUE);
        });

        // ═══ 变量 ═══
        reg.register("setvar", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            ctx.setVariable(parts[0], ctx.replace(rest(parts, 1)));
        });
        reg.register("delvar", (ctx, args, s) ->
            ctx.getInstance().getVariables().remove(args));
        reg.register("addvar", (ctx, args, s) -> {
            // addvar <key> <数字>：数值累加
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            Object cur = ctx.getVariable(parts[0]);
            double base = cur instanceof Number n ? n.doubleValue() : parseDouble(String.valueOf(cur), 0);
            double nv = base + parseDouble(rest(parts, 1), 0);
            ctx.setVariable(parts[0], nv == Math.floor(nv) ? (long) nv : nv);
        });

        // ═══ 条件守卫 ═══
        reg.register("check", (ctx, args, s) -> {
            // check <sel> <条件行...>：目标不满足条件则终止本脚本
            String[] parts = splitFirst(args, 1);
            String expr = rest(parts, 1);
            for (Player p : ctx.resolvePlayers(parts[0])) {
                var cond = xuanmo.arcartxsuite.api.condition.ScriptCondition.parseInline(
                    ctx.replace(expr));
                if (cond != null) {
                    var failed = ScriptConditionServices.evaluator()
                        .firstFailed(p, java.util.List.of(cond));
                    if (failed != null) {
                        s.cancel();
                        return;
                    }
                }
            }
        });

        // ═══ 怪物组 ═══
        reg.register("spawn_group", (ctx, args, s) ->
            service.get().mobGroups().spawnGroup(ctx.getInstance(), ctx.replace(args), ctx.getTrigger()));
        reg.register("kill_group", (ctx, args, s) ->
            service.get().mobGroups().killGroup(ctx.getInstance(), ctx.replace(args)));
        reg.register("remove_group", (ctx, args, s) ->
            service.get().mobGroups().removeGroup(ctx.getInstance(), ctx.replace(args)));
        reg.register("group_alive_count", (ctx, args, s) -> {
            // group_alive_count <组> <变量名>：把存活数写入变量
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            int n = service.get().mobGroups().aliveCount(ctx.getInstance(), ctx.replace(parts[0]));
            ctx.setVariable(rest(parts, 1), n);
        });

        // ═══ 楼层/房间（floors 模式）═══
        reg.register("floor_next", (ctx, args, s) -> {
            var rm = service.get().roomManager();
            if (rm != null) rm.advanceFloor(ctx.getInstance());
        });
        reg.register("room_lock", (ctx, args, s) -> {
            var rm = service.get().roomManager();
            if (rm != null) rm.lockRoom(ctx.getInstance(), ctx.replace(args));
        });
        reg.register("room_unlock", (ctx, args, s) -> {
            var rm = service.get().roomManager();
            if (rm != null) rm.unlockRoom(ctx.getInstance(), ctx.replace(args));
        });
        reg.register("room_status", (ctx, args, s) -> {
            // room_status <房id> <变量名>：把房间状态写入变量（SEALED/FIGHTING/CLEARED/UNKNOWN）
            var rm = service.get().roomManager();
            String[] parts = splitFirst(args, 1);
            if (rm == null || parts.length < 2) return;
            var st = rm.stateOf(ctx.getInstance());
            String val = "UNKNOWN";
            if (st != null) {
                String target = ctx.replace(parts[0]);
                outer:
                for (var f : st.floors) {
                    for (var r : f.rooms) {
                        if (r.id.equals(target) || r.tpl.id().equals(target)) {
                            val = r.state.name();
                            break outer;
                        }
                    }
                }
            }
            ctx.setVariable(rest(parts, 1), val);
        });

        // ═══ 障碍物/交互点 ═══
        reg.register("create_obstacle", (ctx, args, s) ->
            service.get().obstacles().create(ctx.getInstance(), ctx.replace(args)));
        reg.register("delete_obstacle", (ctx, args, s) ->
            service.get().obstacles().delete(ctx.getInstance(), ctx.replace(args)));
        reg.register("create_interact", (ctx, args, s) ->
            service.get().interacts().create(ctx.getInstance(), ctx.replace(args)));
        reg.register("delete_interact", (ctx, args, s) ->
            service.get().interacts().delete(ctx.getInstance(), ctx.replace(args)));

        // ═══ 方块 ═══
        reg.register("setblock", (ctx, args, s) -> {
            // setblock <x,y,z> <material>
            String[] parts = splitFirst(args, 1);
            Location loc = parseLoc(ctx, parts[0]);
            Material mat = Material.matchMaterial(ctx.replace(rest(parts, 1)).toUpperCase());
            if (loc != null && mat != null) loc.getBlock().setType(mat);
        });
        reg.register("fillblocks", (ctx, args, s) -> {
            // fillblocks <x1,y1,z1> <x2,y2,z2> <material>
            String[] parts = splitFirst(args, 2);
            if (parts.length < 3) return;
            Location a = parseLoc(ctx, parts[0]);
            Location b = parseLoc(ctx, parts[1]);
            Material mat = Material.matchMaterial(ctx.replace(rest(parts, 2)).toUpperCase());
            if (a == null || b == null || mat == null || a.getWorld() == null) return;
            int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
            int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
            int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++)
                        a.getWorld().getBlockAt(x, y, z).setType(mat);
        });

        // ═══ NPC ═══
        reg.register("spawn_npc", (ctx, args, s) ->
            service.get().npcs().spawn(ctx.getInstance(), ctx.replace(args)));
        reg.register("remove_npc", (ctx, args, s) ->
            service.get().npcs().remove(ctx.getInstance(), ctx.replace(args)));
        reg.register("npc_dialog", (ctx, args, s) -> {
            // npc_dialog <npc名> [sel]：对玩家播放该 NPC 的对话
            String[] parts = splitFirst(args, 1);
            String sel = parts.length > 1 ? rest(parts, 1) : "@all";
            forEach(ctx, sel, p -> service.get().npcs().playDialog(
                ctx.getInstance(), ctx.replace(parts[0]), p));
        });

        // ═══ 奖励/宝箱 ═══
        reg.register("grant_reward", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String rewardName = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p ->
                service.get().rewards().grant(p, rewardName, ctx.getInstance()));
        });
        reg.register("open_chest_ui", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            String chest = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> service.get().openChest(p, chest, ctx.getInstance()));
        });
        reg.register("give_money", (ctx, args, s) -> {
            String[] parts = splitFirst(args, 1);
            double amount = parseDouble(rest(parts, 1), 0);
            if (amount != 0) forEach(ctx, parts[0], p ->
                service.get().giveMoney(p, amount));
        });

        // ═══ 世界 ═══
        reg.register("time", (ctx, args, s) -> {
            if (ctx.getInstance().getWorld() != null) {
                ctx.getInstance().getWorld().setTime((long) parseDouble(ctx.replace(args), 6000));
            }
        });
        reg.register("weather", (ctx, args, s) -> {
            if (ctx.getInstance().getWorld() != null) {
                String w = ctx.replace(args).toLowerCase();
                boolean storm = w.equals("rain") || w.equals("storm") || w.equals("thunder");
                ctx.getInstance().getWorld().setStorm(storm);
                ctx.getInstance().getWorld().setThundering(w.equals("thunder"));
            }
        });
        reg.register("kill_all_mobs", (ctx, args, s) ->
            service.get().mobGroups().killAll(ctx.getInstance()));
        reg.register("cast_skill", (ctx, args, s) -> {
            // cast_skill <sel> <MM技能名> [威力]
            String[] parts = splitFirst(args, 2);
            if (parts.length < 2) return;
            String skill = ctx.replace(parts[1]);
            double power = parts.length > 2 ? parseDouble(rest(parts, 2), 1.0) : 1.0;
            forEach(ctx, parts[0], p -> service.get().castMythicSkill(p, skill, power));
        });
        reg.register("play_model_animation", (ctx, args, s) -> {
            // play_model_animation <npc名> <动画名> [速度] [过渡tick] [保持tick]
            String[] parts = splitFirst(args, 4);
            if (parts.length < 2) return;
            service.get().npcs().playAnimation(ctx.getInstance(), ctx.replace(parts[0]),
                ctx.replace(parts[1]),
                parts.length > 2 ? parseDouble(parts[2], 1.0) : 1.0,
                parts.length > 3 ? Integer.parseInt(parts[3]) : 0,
                parts.length > 4 ? (long) parseDouble(rest(parts, 4), 0) : 0);
        });
        reg.register("announce", (ctx, args, s) -> {
            // announce <sel> <big|small> <内容>：字幕/广播能力（走宿主 AnnouncerBroadcastable 若可用）
            String[] parts = splitFirst(args, 2);
            if (parts.length < 3) return;
            forEach(ctx, parts[0], p -> {
                if ("big".equalsIgnoreCase(parts[1])) {
                    p.sendTitle(color(ctx.replace(parts[2])), "", 10, 60, 10);
                } else {
                    actionBar(p, color(ctx.replace(rest(parts, 2))));
                }
            });
        });

        // ═══ 任务 ═══
        reg.register("accept_quest", (ctx, args, s) -> {
            // accept_quest <任务id>
            if (args == null || args.isBlank()) return;
            Player t = ctx.getTrigger();
            if (t == null) return;
            boolean ok = service.get().quests().accept(
                ctx.getInstance(), t, runner, ctx.replace(args.trim()));
            if (!ok) {
                t.sendMessage(color("&c任务无法接取（不存在或已处理）"));
            }
        });
        reg.register("submit_quest", (ctx, args, s) -> {
            // submit_quest <任务id>
            if (args == null || args.isBlank()) return;
            Player t = ctx.getTrigger();
            if (t == null) return;
            var r = service.get().quests().submit(
                ctx.getInstance(), t, runner, ctx.replace(args.trim()));
            switch (r) {
                case NOT_FOUND -> t.sendMessage(color("&c任务不存在"));
                case NOT_ACCEPTED -> t.sendMessage(color("&c尚未接取该任务"));
                case CONDITION_FAILED -> t.sendMessage(color("&c任务条件未达成"));
                case ALREADY_DONE -> t.sendMessage(color("&c任务已完成"));
                default -> { }
            }
        });
        reg.register("quest_step", (ctx, args, s) -> {
            // quest_step <任务id>：智能推进——未接取→接取；已接取→尝试提交（条件不过仅提示）
            if (args == null || args.isBlank()) return;
            Player t = ctx.getTrigger();
            if (t == null) return;
            var qm = service.get().quests();
            String qid = ctx.replace(args.trim());
            switch (qm.status(ctx.getInstance(), qid)) {
                case NONE -> qm.accept(ctx.getInstance(), t, runner, qid);
                case ACCEPTED -> {
                    var r = qm.submit(ctx.getInstance(), t, runner, qid);
                    if (r == xuanmo.arcartxsuite.abyss.quest.QuestManager.SubmitResult.CONDITION_FAILED) {
                        t.sendMessage(color("&e任务条件还未达成"));
                    }
                }
                default -> t.sendMessage(color("&7该任务已完成"));
            }
        });
        reg.register("quest_status", (ctx, args, s) -> {
            // quest_status <任务id> <变量名>：把任务状态写入实例变量(NONE/ACCEPTED/COMPLETED)
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            var st = service.get().quests().status(
                ctx.getInstance(), ctx.replace(parts[0]));
            ctx.setVariable(ctx.replace(rest(parts, 1)), st.name());
        });

        // ═══ 跨模块联动（capability 缺席时静默跳过）═══
        reg.register("signal", (ctx, args, s) -> {
            // signal <sel> <信号名>：触发 EventPacket 规则引擎信号
            String[] parts = splitFirst(args, 1);
            var links = service.get().links();
            if (links == null || parts.length < 2) return;
            String sig = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> links.signal(p, sig, null));
        });
        reg.register("announce_global", (ctx, args, s) -> {
            // announce_global <文本> [forward]：Announcer 全服公告（forward=true 跨服）
            var links = service.get().links();
            if (links == null || args == null || args.isBlank()) return;
            boolean forward = args.trim().endsWith(" forward")
                || args.trim().endsWith(" true");
            String text = forward
                ? args.trim().substring(0, args.trim().lastIndexOf(' ')) : args;
            links.announce(color(ctx.replace(text)), forward);
        });
        reg.register("qq_group", (ctx, args, s) -> {
            // qq_group [群号] <消息>：QQ 群推送（群号省略/0=全部已配置群）
            var links = service.get().links();
            if (links == null || args == null || args.isBlank()) return;
            String[] parts = splitFirst(args, 1);
            long group = 0;
            String msg = args;
            if (parts.length > 1 && parts[0].matches("-?\\d+")) {
                group = Long.parseLong(parts[0]);
                msg = rest(parts, 1);
            }
            links.qqNotify(group, ctx.replace(msg));
        });
        reg.register("subtitle", (ctx, args, s) -> {
            // subtitle <sel> <字幕组id>：播放 Announcer/Subtitle 字幕组
            String[] parts = splitFirst(args, 1);
            var links = service.get().links();
            if (links == null || parts.length < 2) return;
            String group = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> links.subtitle(p, group));
        });
        reg.register("chat_card", (ctx, args, s) -> {
            // chat_card <sel> <卡片id> [k=v,k=v]：发送聊天卡片
            String[] parts = splitFirst(args, 2);
            var links = service.get().links();
            if (links == null || parts.length < 2) return;
            String card = ctx.replace(parts[1]);
            Map<String, String> data = new java.util.LinkedHashMap<>();
            if (parts.length > 2) {
                for (String kv : ctx.replace(rest(parts, 2)).split(",")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) data.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
                }
            }
            forEach(ctx, parts[0], p -> links.chatCard(p, card, data));
        });
        reg.register("combat_effect", (ctx, args, s) -> {
            // combat_effect <sel> <特效包id>：触发 CombatEffect 特效包
            String[] parts = splitFirst(args, 1);
            var links = service.get().links();
            if (links == null || parts.length < 2) return;
            String packet = ctx.replace(rest(parts, 1));
            forEach(ctx, parts[0], p -> links.combatEffect(p, packet, null));
        });
        reg.register("bp_task", (ctx, args, s) -> {
            // bp_task <sel> <任务id> [增量]：推进 BattlePass 任务进度
            String[] parts = splitFirst(args, 2);
            var links = service.get().links();
            if (links == null || parts.length < 2) return;
            String task = ctx.replace(parts[1]);
            int inc = parts.length > 2 ? (int) parseDouble(rest(parts, 2), 1) : 1;
            forEach(ctx, parts[0], p -> links.battlePassTask(p, task, inc));
        });
        reg.register("apply_attribute", (ctx, args, s) -> {
            // apply_attribute <sel> <属性id> <值> [percent|flat]：
            // 副本内属性修饰（Symphony 桥），出本/结束时统一 remove 清理
            String[] parts = splitFirst(args, 3);
            var links = service.get().links();
            if (links == null || parts.length < 3) return;
            String attr = ctx.replace(parts[1]);
            double val = parseDouble(ctx.replace(parts[2]), 0);
            boolean percent = parts.length > 3
                && "percent".equalsIgnoreCase(ctx.replace(rest(parts, 3)).trim());
            forEach(ctx, parts[0], p -> links.applyAttribute(
                p, attr, percent, val, "abyss"));
        });
        reg.register("remove_attribute", (ctx, args, s) -> {
            // remove_attribute <sel>：移除本副本施加的全部属性修饰
            var links = service.get().links();
            if (links == null) return;
            forEach(ctx, first(args), p -> links.removeAttribute(p, "abyss"));
        });

        // ═══ 全服共享计数（跨副本/跨实例持久化；阈值→axs.abyss.global_threshold）═══
        reg.register("global_add", (ctx, args, s) -> {
            // global_add <key> [n]：累加全服计数（默认 +1），命中配置阈值时发阈值事件
            String[] parts = splitFirst(args, 1);
            if (parts.length < 1 || parts[0].isBlank()) return;
            long delta = parts.length > 1 ? (long) parseDouble(rest(parts, 1), 1) : 1;
            var gc = service.get().globalCounters();
            if (gc != null) gc.add(ctx.replace(parts[0]), delta, globalCtx(ctx));
        });
        reg.register("global_set", (ctx, args, s) -> {
            // global_set <key> <n>
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            var gc = service.get().globalCounters();
            if (gc != null) gc.set(ctx.replace(parts[0]),
                (long) parseDouble(rest(parts, 1), 0), globalCtx(ctx));
        });
        reg.register("global_get", (ctx, args, s) -> {
            // global_get <key> <变量名>：把全服计数读进实例变量
            String[] parts = splitFirst(args, 1);
            if (parts.length < 2) return;
            var gc = service.get().globalCounters();
            ctx.setVariable(rest(parts, 1),
                gc != null ? gc.get(ctx.replace(parts[0])) : 0L);
        });
        reg.register("global_reset", (ctx, args, s) -> {
            // global_reset <key>：归零并清阈值触发记录（一轮全服事件结束用）
            if (args == null || args.isBlank()) return;
            var gc = service.get().globalCounters();
            if (gc != null) gc.reset(ctx.replace(args.trim()), globalCtx(ctx));
        });
    }

    /** 全局计数事件的附带上下文（dungeon/instance/player）。 */
    private static Map<String, Object> globalCtx(ScriptContext ctx) {
        Map<String, Object> m = new java.util.HashMap<>();
        var inst = ctx.getInstance();
        if (inst != null) {
            m.put("dungeon", inst.getTemplate().id());
            m.put("instance", inst.getInstanceId().toString());
        }
        if (ctx.getTrigger() != null) {
            m.put("player", ctx.getTrigger().getName());
        }
        return m;
    }

    // ═══ 工具 ═══

    private static void forEach(ScriptContext ctx, String selector,
                                java.util.function.Consumer<Player> fn) {
        for (Player p : ctx.resolvePlayers(selector)) {
            fn.accept(p);
        }
    }

    /** ActionBar：Spigot 兼容路径（Paper 的 Player#sendActionBar 不在 Spigot API 面）。 */
    private static void actionBar(Player p, String msg) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
    }

    private static void runSync(ScriptRunner runner, Runnable r) {
        runner.runOnMain(r);
    }

    /** 取第一个空白分隔 token。 */
    private static String first(String s) {
        if (s == null) return "";
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }

    /** 取原始串中第一个 token 之后的剩余部分。 */
    private static String rest(String s) {
        if (s == null) return "";
        int i = s.indexOf(' ');
        return i < 0 ? "" : s.substring(i + 1).trim();
    }

    /**
     * 取 {@link #splitFirst} 结果中"跳过 n 个 token 后的剩余段"。
     * <p>
     * 约定：{@code splitFirst(s, n)} 的第 n 个元素（若存在）即"第 n 个 token 之后的剩余串"，
     * 所以这里直接取 parts[n]；不足时返回空串。
     */
    private static String rest(String[] parts, int n) {
        return parts != null && parts.length > n ? parts[n] : "";
    }

    /**
     * 把串按空白切成至多 {@code n+1} 段：前 n 个 token 各自一段，
     * 第 n+1 个元素（下标 n）为剩余全部内容。
     */
    private static String[] splitFirst(String s, int n) {
        if (s == null || s.isBlank()) return new String[0];
        return s.trim().split("\\s+", Math.max(1, n + 1));
    }

    private static int[] parseInts(String... ss) {
        int[] r = new int[ss.length];
        for (int i = 0; i < ss.length; i++) r[i] = (int) parseDouble(ss[i], 10);
        return r;
    }

    static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private static Location parseLoc(ScriptContext ctx, String s) {
        String[] v = ctx.replace(s).split(",");
        if (v.length < 3 || ctx.getInstance().getWorld() == null) return null;
        double x = parseDouble(v[0], 0), y = parseDouble(v[1], 64), z = parseDouble(v[2], 0);
        float yaw = v.length > 3 ? (float) parseDouble(v[3], 0) : 0;
        float pitch = v.length > 4 ? (float) parseDouble(v[4], 0) : 0;
        return new Location(ctx.getInstance().getWorld(), x, y, z, yaw, pitch);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E def) {
        try { return Enum.valueOf(type, name.toUpperCase()); } catch (Exception e) { return def; }
    }

    private static Color parseColor(String s) {
        try { return Color.fromRGB(Integer.parseInt(s.replace("#", ""), 16)); }
        catch (Exception e) { return Color.WHITE; }
    }

    static String color(String s) {
        return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
