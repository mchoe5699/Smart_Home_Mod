package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.block.ControlBlock;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 세대 방범: 외출모드 / 방범모드와 동체감지기.
 * - 설정은 비밀번호 없이, 90초 유예(집을 나갈 시간) 뒤 동체 감지 시작
 * - 해제는 세대 비밀번호 필요
 * - 감지기 앞에서 벽에 가리지 않은 사람·동물이 보이면 경비실에 통보
 * - 외출모드: 실내·외부 감지기 모두 / 방범모드(재택): 외부 감지기만 (집 안 사람은 감지하지 않음)
 */
public final class SecurityLogic {
    public static final int EXIT_DELAY = 20 * 90;
    private static final int ALERT_COOLDOWN = 20 * 20;

    private SecurityLogic() {}

    public static String modeName(int mode) {
        return mode == 1 ? "외출모드" : mode == 2 ? "방범모드" : "해제";
    }

    // ------------------------------------------------------------------ 설정 / 해제

    public static String arm(ServerPlayer player, ServerLevel level, NetworkData data, int cid, int dong, int ho, int mode) {
        if (mode != 1 && mode != 2) return null;
        NetworkData.Security s = data.securityOrCreate(cid, dong, ho);
        if (s.mode != 0) return "이미 " + modeName(s.mode) + "입니다. 해제하려면 세대 비밀번호를 누르세요";
        s.mode = mode;
        s.armAt = level.getGameTime() + EXIT_DELAY;
        s.alarm = false;
        int sensors = 0;
        for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
            if (c.kind.isMotion()) sensors++;
            // 외출모드: 조명 소등 · 가스 잠금 · 환기 정지
            if (mode == 1 && (c.kind == ControlKind.LIGHT || c.kind == ControlKind.GAS_VALVE || c.kind == ControlKind.VENTILATION) && c.on) {
                c.on = false;
                if (c.kind == ControlKind.VENTILATION) c.level = 0;
                HomeLogic.apply(level, data, c);
            }
        }
        data.setDirty();
        String msg = modeName(mode) + " 설정 — 90초 뒤 동체 감지를 시작합니다"
                + (sensors == 0 ? " (연동된 동체감지기 없음)" : "");
        DeviceLogic.msg(player, msg, sensors == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
        return null;
    }

    public static String disarm(ServerPlayer player, ServerLevel level, BlockPos at, NetworkData data, int cid, int dong, int ho, String pw) {
        NetworkData.Security s = data.security(cid, dong, ho);
        if (s == null || s.mode == 0) return "설정된 외출/방범모드가 없습니다";
        String hpw = data.householdPassword(cid, dong, ho);
        if (!hpw.isEmpty() && !hpw.equals(pw == null ? "" : pw.trim())) {
            level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return "세대 비밀번호가 틀립니다";
        }
        String was = modeName(s.mode);
        s.mode = 0;
        s.alarm = false;
        data.setDirty();
        for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
            if (c.kind.isMotion() && c.on) {
                c.on = false;
                HomeLogic.apply(level, data, c);
            }
        }
        level.playSound(null, at, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        DeviceLogic.msg(player, was + " 해제" + (hpw.isEmpty() ? " (세대 비밀번호가 없어 바로 해제됨 — 설정을 권장)" : ""), ChatFormatting.GREEN);
        return null;
    }

    /** 세대 내부기기 화면용 방범 상태 */
    public static void write(ServerLevel level, NetworkData data, int cid, int dong, int ho, CompoundTag tag) {
        NetworkData.Security s = data.security(cid, dong, ho);
        int mode = s == null ? 0 : s.mode;
        tag.putInt("secMode", mode);
        tag.putInt("secRemain", mode == 0 ? 0 : (int) Math.max(0, (s.armAt - level.getGameTime() + 19) / 20));
        tag.putBoolean("secAlarm", s != null && s.alarm);
        int sensors = 0;
        for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) if (c.kind.isMotion()) sensors++;
        tag.putInt("motionCount", sensors);
        tag.putBoolean("hasHouseholdPw", !data.householdPassword(cid, dong, ho).isEmpty());
    }

    /** 경비실기 화면용 경보 목록 */
    public static void writeAlerts(NetworkData data, int cid, CompoundTag tag) {
        ListTag l = new ListTag();
        for (NetworkData.Alert a : data.alertsOf(cid)) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", a.id);
            t.putString("text", a.text);
            t.putString("time", a.time);
            l.add(t);
        }
        tag.put("alerts", l);
    }

    // ------------------------------------------------------------------ 감지

    /** 동체감지기 블록 엔티티에서 10틱마다 호출 */
    public static void sensorTick(ServerLevel level, BlockPos pos, BlockState state, NetworkData.Control c) {
        NetworkData data = NetworkData.get(level);
        boolean active = false;
        int cid = -1;
        NetworkData.Security s = null;
        if (c.linked) {
            cid = data.complexAt(pos);
            s = cid < 0 ? null : data.security(cid, c.dong, c.ho);
            active = s != null && s.mode > 0 && level.getGameTime() >= s.armAt
                    && !(s.mode == 2 && c.kind == ControlKind.MOTION_INDOOR);
        }
        if (c.on != active) {
            c.on = active;
            HomeLogic.apply(level, data, c);
        }
        if (!active) return;
        LivingEntity seen = detect(level, pos, state, c.kind == ControlKind.MOTION_OUTDOOR ? 10 : 7);
        if (seen != null) trigger(level, data, cid, c, s, seen);
    }

    /** 감지기 앞쪽에서, 벽에 가리지 않고 보이는 생물 */
    private static LivingEntity detect(ServerLevel level, BlockPos pos, BlockState state, double range) {
        Direction facing = state.hasProperty(ControlBlock.FACING) ? state.getValue(ControlBlock.FACING) : Direction.NORTH;
        Vec3 normal = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        Vec3 origin = Vec3.atCenterOf(pos).add(normal.scale(0.3)).add(0, 0.15, 0);
        AABB box = new AABB(pos).inflate(range);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en.isAlive() && !en.isSpectator() && !(en instanceof ArmorStand))) {
            Vec3 target = e.getBoundingBox().getCenter();
            Vec3 d = target.subtract(origin);
            double dist = d.length();
            if (dist > range || dist < 0.01) continue;
            if (d.normalize().dot(normal) < 0.1) continue; // 감지기 뒤쪽(벽 안쪽)은 보지 못함
            BlockHitResult hr = level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            if (hr.getType() == HitResult.Type.MISS || hr.getLocation().distanceTo(origin) >= dist - 0.3) return e;
        }
        return null;
    }

    private static void trigger(ServerLevel level, NetworkData data, int cid, NetworkData.Control c, NetworkData.Security s, LivingEntity who) {
        long now = level.getGameTime();
        if (now - s.lastAlert < ALERT_COOLDOWN) return;
        s.lastAlert = now;
        s.alarm = true;
        String name = who instanceof Player p ? p.getGameProfile().getName() : who.getName().getString();
        String where = c.room.isEmpty() ? c.kind.korean : c.room + "(" + c.kind.korean + ")";
        String text = c.dong + "동 " + c.ho + "호 " + where + " 감지 · " + name + " [" + modeName(s.mode) + "]";
        String time = DeviceLogic.timeString(level);
        data.addAlert(cid, text, time);

        level.playSound(null, c.pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.5f, 0.5f);
        Component msg = Component.literal("[방범 경보] " + text).withStyle(ChatFormatting.RED);
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (d.type == DeviceType.GUARD_STATION && level.isLoaded(d.pos)) {
                level.playSound(null, d.pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, 2.0f);
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(Vec3.atCenterOf(d.pos)) < 16 * 16) p.displayClientMessage(msg, true);
                }
            }
            if (d.type.internal && d.dong == c.dong && d.ho == c.ho) CallManager.log(level, d.pos, "방범 감지 - " + where + " · " + name);
        }
    }

    /** 세대 내부기기 [화재] 버튼: 경비실에 화재 비상 통보 */
    public static void reportFire(ServerLevel level, NetworkData data, int cid, int dong, int ho, String from) {
        String text = dong + "동 " + ho + "호 화재 비상 신고 (" + from + ")";
        data.addAlert(cid, text, DeviceLogic.timeString(level));
        Component msg = Component.literal("[화재 경보] " + text).withStyle(ChatFormatting.RED);
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (d.type == DeviceType.GUARD_STATION && level.isLoaded(d.pos)) {
                level.playSound(null, d.pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0f, 0.6f);
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(Vec3.atCenterOf(d.pos)) < 16 * 16) p.displayClientMessage(msg, true);
                }
            }
            if (d.type.internal && d.dong == dong && d.ho == ho) CallManager.log(level, d.pos, "화재 비상 신고");
        }
    }

    /** 경비실기: 확인하지 않은 경보가 있으면 경보음 */
    public static void guardTick(ServerLevel level, DeviceBlockEntity guard) {
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(guard.getBlockPos());
        if (cid >= 0 && !data.alertsOf(cid).isEmpty()) {
            level.playSound(null, guard.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0f, 2.0f);
        }
    }
}
