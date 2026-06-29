/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.papi.impl;

import ink.ptms.adyeshach.module.editor.EditUtilsKt;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.PlaceholderHookEvent;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.addon.AddonStats;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0003\u00a8\u0006\u0004"}, d2={"onPlaceholderData", "", "e", "Link/ptms/chemdah/api/event/PlaceholderHookEvent;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPlayerQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerQuest.kt\nink/ptms/chemdah/module/papi/impl/PlayerQuestKt\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,86:1\n1#2:87\n*E\n"})
public final class PlayerQuestKt {
    /*
     * Unable to fully structure code
     */
    @SubscribeEvent
    private static final void onPlaceholderData(PlaceholderHookEvent e) {
        var1_1 = e.getIdentifier();
        tmp = -1;
        switch (var1_1.hashCode()) {
            case -309519186: {
                if (var1_1.equals("proceed")) {
                    tmp = 1;
                }
                break;
            }
            case -2146525273: {
                if (var1_1.equals("accepted")) {
                    tmp = 2;
                }
                break;
            }
            case -1402931637: {
                if (var1_1.equals("completed")) {
                    tmp = 3;
                }
                break;
            }
            case 632034560: {
                if (var1_1.equals("acceptcheck")) {
                    tmp = 4;
                }
                break;
            }
            case -969794050: {
                if (var1_1.equals("progresstarget")) {
                    tmp = 5;
                }
                break;
            }
            case 1270488759: {
                if (var1_1.equals("tracking")) {
                    tmp = 6;
                }
                break;
            }
            case 1153283088: {
                if (var1_1.equals("checkaccept")) {
                    tmp = 4;
                }
                break;
            }
            case -1782397044: {
                if (var1_1.equals("questdata")) {
                    tmp = 7;
                }
                break;
            }
            case -1423461112: {
                if (var1_1.equals("accept")) {
                    tmp = 2;
                }
                break;
            }
            case 801842020: {
                if (var1_1.equals("progressvalue")) {
                    tmp = 5;
                }
                break;
            }
            case 860505464: {
                if (var1_1.equals("progresspercent")) {
                    tmp = 5;
                }
                break;
            }
            case 110621003: {
                if (var1_1.equals("track")) {
                    tmp = 6;
                }
                break;
            }
            case -599445191: {
                if (var1_1.equals("complete")) {
                    tmp = 3;
                }
                break;
            }
        }
        switch (tmp) {
            case 6: {
                v0 = AddonTrack.Companion.getTrackQuest(e.getProfile());
                if (v0 == null || (v0 = v0.getId()) == null) {
                    v0 = "null";
                }
                e.setResult(v0);
                break;
            }
            case 2: {
                e.setResult(e.getProfile().getQuestById(e.getParameter(), false) != null);
                break;
            }
            case 1: {
                e.setResult(e.getProfile().getQuestById(e.getParameter(), true) != null);
                break;
            }
            case 3: {
                e.setResult(e.getProfile().isQuestCompleted(e.getParameter()));
                break;
            }
            case 4: {
                template = ChemdahAPI.INSTANCE.getQuestTemplate(e.getParameter());
                if (template != null) {
                    e.setResult((Object)template.checkAccept(e.getProfile()).getNow(new AcceptResult(AcceptResult.Type.FAILED)).getType());
                    break;
                }
                e.setResult("QUEST_NOT_FOUND");
                break;
            }
            case 7: {
                name = StringsKt.substringBefore$default((String)e.getParameter(), (char)':', null, (int)2, null);
                quest = e.getProfile().getQuestById(name, true);
                if (quest != null) {
                    node = StringsKt.substringBefore$default((String)StringsKt.substringAfter$default((String)e.getParameter(), (char)':', null, (int)2, null), (String)"?:", null, (int)2, null);
                    var6_9 = StringsKt.substringAfter$default((String)e.getParameter(), (String)"?:", null, (int)2, null);
                    if (var6_9.length() == 0) {
                        $i$a$-ifEmpty-PlayerQuestKt$onPlaceholderData$def$1 = false;
                        v1 = "null";
                    } else {
                        v1 = var6_9;
                    }
                    def = (String)v1;
                    v2 = quest.getPersistentDataContainer().get(node);
                    if (v2 == null || (v2 = v2.toString()) == null) {
                        v2 = def;
                    }
                    e.setResult(v2);
                    break;
                }
                e.setResult("QUEST_NOT_FOUND");
                break;
            }
            case 5: {
                name = StringsKt.substringBefore$default((String)e.getParameter(), (char)':', null, (int)2, null);
                quest = e.getProfile().getQuestById(name, true);
                if (quest == null) ** GOTO lbl148
                taskId = StringsKt.substringAfter$default((String)e.getParameter(), (char)':', null, (int)2, null);
                if (((CharSequence)taskId).length() == 0) {
                    v3 = AddonStats.Companion.getProgress(quest.getTemplate(), e.getProfile()).getNow(Progress.Companion.getZERO());
                } else {
                    task = quest.getTask(taskId);
                    if (task != null) {
                        v3 = AddonStats.Companion.getProgress(task, e.getProfile()).getNow(Progress.Companion.getZERO());
                    } else {
                        e.setResult("TASK_NOT_FOUND");
                        v3 = progress = null;
                    }
                }
                if (progress == null) break;
                var6_10 = e.getIdentifier();
                switch (var6_10.hashCode()) {
                    case 860505464: {
                        if (!var6_10.equals("progresspercent")) {
                            ** break;
                        }
                        ** GOTO lbl143
                    }
                    case -969794050: {
                        if (var6_10.equals("progresstarget")) break;
                        ** break;
                    }
                    case 801842020: {
                        if (!var6_10.equals("progressvalue")) ** break;
                        v4 = progress.getValue();
                        ** GOTO lbl146
                    }
                }
                v4 = progress.getTarget();
                ** GOTO lbl146
lbl143:
                // 1 sources

                v4 = EditUtilsKt.format((double)(progress.getPercent() * (double)100));
                ** GOTO lbl146
lbl145:
                // 4 sources

                throw new IllegalStateException("out of case".toString());
lbl146:
                // 3 sources

                e.setResult(v4);
                break;
lbl148:
                // 1 sources

                e.setResult("QUEST_NOT_FOUND");
            }
        }
    }
}

