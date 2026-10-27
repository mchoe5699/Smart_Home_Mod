package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 서버 전체의 호출/통화 상태 관리 */
public final class CallManager {
    public static final int RING_TIMEOUT = 20 * 30;
    public static final int TALK_TIMEOUT = 20 * 180;
    public static final double VOICE_RANGE = 6.0;

    private static final Map<Integer, Call> CALLS = new LinkedHashMap<>();
    private static int nextId = 1;
    private static long now = 0;

    private CallManager() {}

    public static Collection<Call> all() {
        return CALLS.values();
    }

    public static void clear() {
        CALLS.clear();
    }

    public static Call get(int id) {
        return CALLS.get(id);
    }

    /** 이 기기가 걸었거나 받은(통화 중) 호출을 우선, 없으면 울리고 있는 호출 */
    public static Call find(ResourceKey<Level> dim, BlockPos pos) {
        Call ringing = null;
        for (Call c : CALLS.values()) {
            if (!c.dim.equals(dim)) continue;
            if (c.caller.equals(pos) || pos.equals(c.answered)) return c;
            if (ringing == null && c.answered == null && c.targets.contains(pos)) ringing = c;
        }
        return ringing;
    }

    /** 이 기기에서 울리고 있는 모든 호출 (경비실기는 여러 건을 동시에 받을 수 있음) */
    public static List<Call> ringingAt(ResourceKey<Level> dim, BlockPos pos) {
        List<Call> out = new ArrayList<>();
        for (Call c : CALLS.values()) {
            if (c.dim.equals(dim) && c.answered == null && c.targets.contains(pos)) out.add(c);
        }
        return out;
    }

    /** 걸고 있거나 통화 중인 기기 */
    public static boolean isBusy(ResourceKey<Level> dim, BlockPos pos) {
        for (Call c : CALLS.values()) {
            if (c.dim.equals(dim) && (c.caller.equals(pos) || pos.equals(c.answered))) return true;
        }
        return false;
    }

    /**
     * 호출 시작.
     * @return 실패 사유, 성공이면 null
     */
    public static String start(ServerLevel level, BlockPos caller, NetworkData.Device callerDev,
                               List<NetworkData.Device> targets, ServerPlayer player) {
        ResourceKey<Level> dim = level.dimension();
        Call old = find(dim, caller);
        if (old != null) {
            if (old.caller.equals(caller) || caller.equals(old.answered)) end(level, old, "이전 통화를 종료했습니다");
            else decline(level, old, caller);
        }
        List<NetworkData.Device> free = new ArrayList<>();
        for (NetworkData.Device d : targets) {
            if (d.pos.equals(caller)) continue;
            if (!isBusy(dim, d.pos)) free.add(d);
        }
        if (free.isEmpty()) return "상대 기기가 모두 통화 중입니다";

        Call c = new Call(nextId++, dim, caller, callerDev.type, DeviceLogic.label(callerDev));
        c.createdTick = now;
        for (NetworkData.Device d : free) {
            c.targets.add(d.pos);
            c.targetLabels.add(DeviceLogic.label(d));
        }
        if (c.hasVideo() && player != null) {
            c.visitorId = player.getUUID();
            c.visitorName = player.getGameProfile().getName();
        }
        CALLS.put(c.id, c);
        level.playSound(null, caller, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        return null;
    }

    public static String answer(ServerLevel level, BlockPos pos, int callId) {
        Call c = callId > 0 ? CALLS.get(callId) : null;
        if (c == null) {
            List<Call> r = ringingAt(level.dimension(), pos);
            if (!r.isEmpty()) c = r.get(0);
        }
        if (c == null || c.answered != null || !c.targets.contains(pos)) return "받을 호출이 없습니다";
        // 이 기기가 다른 통화 중이면 먼저 끊는다
        for (Call other : new ArrayList<>(CALLS.values())) {
            if (other != c && other.dim.equals(c.dim) && (other.caller.equals(pos) || pos.equals(other.answered))) {
                end(level, other, "다른 호출을 받아 통화를 종료했습니다");
            }
        }
        NetworkData.Device dev = NetworkData.get(level).getDevice(pos);
        c.answered = pos.immutable();
        c.answeredLabel = dev != null ? DeviceLogic.label(dev) : "";
        c.answeredTick = now;
        // 같은 세대의 다른 기기는 울림을 멈추고 기록을 남긴다
        for (BlockPos t : c.targets) {
            if (!t.equals(pos)) log(level, t, "응답됨(다른 기기) - " + c.callerLabel);
        }
        log(level, pos, "통화 - " + c.callerLabel);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.8f);
        notifyNear(level, c.caller, Component.literal("[" + c.answeredLabel + "] 연결되었습니다. 근처에서 채팅하면 음성으로 전달됩니다.")
                .withStyle(ChatFormatting.GREEN));
        return null;
    }

    /** 통화 종료 또는 호출 거절 */
    public static void hangup(ServerLevel level, BlockPos pos) {
        Call c = find(level.dimension(), pos);
        if (c == null) return;
        if (c.caller.equals(pos) || pos.equals(c.answered)) {
            end(level, c, "통화가 종료되었습니다");
        } else {
            decline(level, c, pos);
        }
    }

    private static void decline(ServerLevel level, Call c, BlockPos pos) {
        int i = c.targets.indexOf(pos);
        if (i >= 0) {
            c.targets.remove(i);
            c.targetLabels.remove(i);
            log(level, pos, "거절 - " + c.callerLabel);
        }
        if (c.targets.isEmpty()) end(level, c, "상대가 호출을 거절했습니다");
    }

    public static void end(ServerLevel level, Call c, String reason) {
        CALLS.remove(c.id);
        if (c.answered == null) {
            for (BlockPos t : c.targets) log(level, t, "부재중 - " + c.callerLabel);
        }
        Component msg = Component.literal(reason).withStyle(ChatFormatting.YELLOW);
        notifyNear(level, c.caller, msg);
        if (c.answered != null) notifyNear(level, c.answered, msg);
    }

    public static void deviceRemoved(ServerLevel level, BlockPos pos) {
        for (Call c : new ArrayList<>(CALLS.values())) {
            if (!c.dim.equals(level.dimension())) continue;
            if (c.caller.equals(pos) || pos.equals(c.answered)) {
                end(level, c, "기기가 철거되어 통화가 종료되었습니다");
            } else if (c.answered == null && c.targets.contains(pos)) {
                int i = c.targets.indexOf(pos);
                c.targets.remove(i);
                c.targetLabels.remove(i);
                if (c.targets.isEmpty()) end(level, c, "호출 대상 기기가 없습니다");
            }
        }
    }

    public static void tick(MinecraftServer server) {
        now++;
        Iterator<Call> it = new ArrayList<>(CALLS.values()).iterator();
        while (it.hasNext()) {
            Call c = it.next();
            ServerLevel level = server.getLevel(c.dim);
            if (level == null) {
                CALLS.remove(c.id);
                continue;
            }
            if (NetworkData.get(level).getDevice(c.caller) == null) {
                end(level, c, "호출 기기가 없습니다");
                continue;
            }
            if (c.answered == null) {
                if (now - c.createdTick > RING_TIMEOUT) {
                    end(level, c, "응답이 없습니다 (부재중)");
                    continue;
                }
                long phase = (now - c.createdTick) % 40;
                for (BlockPos t : c.targets) {
                    if (!level.isLoaded(t)) continue;
                    if (phase == 0) level.playSound(null, t, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.26f);
                    if (phase == 7) level.playSound(null, t, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                if (phase == 0 && level.isLoaded(c.caller)) {
                    level.playSound(null, c.caller, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.3f, 0.8f);
                }
            } else if (now - c.answeredTick > TALK_TIMEOUT) {
                end(level, c, "통화 시간(3분)이 끝났습니다");
                continue;
            }
            // 카메라 영상: 카메라 앞에 있는 사람을 갱신
            if (c.hasVideo() && now % 20 == 0) {
                Vec3 cam = Vec3.atCenterOf(c.caller);
                ServerPlayer nearest = null;
                double best = 16.0;
                for (ServerPlayer p : level.players()) {
                    double d = p.distanceToSqr(cam);
                    if (d < best) {
                        best = d;
                        nearest = p;
                    }
                }
                if (nearest != null) {
                    c.visitorId = nearest.getUUID();
                    c.visitorName = nearest.getGameProfile().getName();
                } else {
                    c.visitorId = null;
                    c.visitorName = "";
                }
            }
        }
    }

    /** 통화 중 채팅을 상대편 기기 근처로 전달 (음성 통화 흉내) */
    public static void relayChat(ServerPlayer sender, String text) {
        if (!(sender.level() instanceof ServerLevel level)) return;
        for (Call c : CALLS.values()) {
            if (c.answered == null || !c.dim.equals(level.dimension())) continue;
            BlockPos from = null, to = null;
            String fromLabel = null;
            if (near(sender, c.caller)) {
                from = c.caller;
                to = c.answered;
                fromLabel = c.callerLabel;
            } else if (near(sender, c.answered)) {
                from = c.answered;
                to = c.caller;
                fromLabel = c.answeredLabel;
            }
            if (from == null) continue;
            Component msg = Component.literal("[통화 " + fromLabel + "] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(sender.getGameProfile().getName() + ": " + text).withStyle(ChatFormatting.WHITE));
            for (ServerPlayer p : level.players()) {
                if (near(p, to) && !near(p, from)) p.sendSystemMessage(msg);
            }
        }
    }

    private static boolean near(ServerPlayer p, BlockPos pos) {
        return p.distanceToSqr(Vec3.atCenterOf(pos)) <= VOICE_RANGE * VOICE_RANGE;
    }

    private static void notifyNear(ServerLevel level, BlockPos pos, Component msg) {
        for (ServerPlayer p : level.players()) {
            if (near(p, pos)) p.displayClientMessage(msg, true);
        }
    }

    private static void log(ServerLevel level, BlockPos pos, String text) {
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DeviceBlockEntity d) d.addLog(DeviceLogic.timeString(level) + " " + text);
    }
}
