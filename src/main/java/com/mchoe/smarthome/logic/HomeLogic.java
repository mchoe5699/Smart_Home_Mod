package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.blockentity.ControlBlockEntity;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** 월패드 홈네트워크: 조명·난방·가스·환기·엘리베이터·외출모드·세대 비밀번호 */
public final class HomeLogic {
    private HomeLogic() {}

    public static void writeHousehold(ServerLevel level, NetworkData data, int cid, int dong, int ho, CompoundTag tag) {
        ListTag list = new ListTag();
        for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) list.add(controlTag(c));
        tag.put("controls", list);
        tag.putBoolean("away", data.isAway(cid, dong, ho));
    }

    private static CompoundTag controlTag(NetworkData.Control c) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", c.pos.asLong());
        t.putString("kind", c.kind.id);
        t.putString("room", c.room.isEmpty() ? c.kind.korean : c.room);
        t.putBoolean("on", c.on);
        t.putInt("level", c.level);
        t.putInt("temp", c.temp);
        return t;
    }

    private static void apply(ServerLevel level, NetworkData data, NetworkData.Control c) {
        data.setDirty();
        if (level.isLoaded(c.pos) && level.getBlockEntity(c.pos) instanceof ControlBlockEntity be) be.applyFromRegistry();
    }

    private static void change(ServerLevel level, NetworkData data, NetworkData.Control c, String action, CompoundTag args) {
        switch (action) {
            case "ctl_toggle" -> {
                if (c.kind == ControlKind.ELEVATOR_LINK) c.pulse = 20;
                else c.on = !c.on;
            }
            case "ctl_level" -> {
                c.level = Math.max(0, Math.min(3, args.getInt("level")));
                c.on = c.level > 0;
            }
            case "ctl_temp" -> c.temp = Math.max(10, Math.min(35, c.temp + args.getInt("delta")));
            default -> { }
        }
        apply(level, data, c);
    }

    /** 월패드 화면의 홈네트워크 버튼 */
    public static String handleWallpad(ServerPlayer player, ServerLevel level, DeviceBlockEntity dev, String action, CompoundTag args) {
        if (!dev.getDeviceType().internal) return null;
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        int dong = dev.getDong(), ho = dev.getHo();
        if (cid < 0) return "단지 구획 밖입니다";
        if (dong < 0 || ho < 0) return "먼저 세대번호를 설정하세요";

        switch (action) {
            case "ctl_toggle", "ctl_level", "ctl_temp" -> {
                NetworkData.Control c = data.getControl(BlockPos.of(args.getLong("pos")));
                if (c == null || c.dong != dong || c.ho != ho || data.complexAt(c.pos) != cid) return "이 세대의 설비가 아닙니다";
                change(level, data, c, action, args);
                if (c.kind == ControlKind.ELEVATOR_LINK) DeviceLogic.msg(player, "엘리베이터를 호출했습니다", ChatFormatting.AQUA);
            }
            case "lights_off" -> {
                for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
                    if (c.kind == ControlKind.LIGHT && c.on) {
                        c.on = false;
                        apply(level, data, c);
                    }
                }
                DeviceLogic.msg(player, "일괄 소등했습니다", ChatFormatting.AQUA);
            }
            case "gas_close" -> {
                for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
                    if (c.kind == ControlKind.GAS_VALVE && c.on) {
                        c.on = false;
                        apply(level, data, c);
                    }
                }
                DeviceLogic.msg(player, "가스밸브를 모두 잠갔습니다", ChatFormatting.AQUA);
            }
            case "elevator" -> {
                int n = 0;
                for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
                    if (c.kind == ControlKind.ELEVATOR_LINK) {
                        c.pulse = 20;
                        apply(level, data, c);
                        n++;
                    }
                }
                if (n == 0) return "세대에 연결된 엘리베이터 호출 연동기가 없습니다";
                level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5f, 1.5f);
                DeviceLogic.msg(player, "엘리베이터를 호출했습니다", ChatFormatting.AQUA);
            }
            case "away" -> {
                boolean away = args.getBoolean("on");
                data.setAway(cid, dong, ho, away);
                if (away) {
                    for (NetworkData.Control c : data.controlsOf(cid, dong, ho)) {
                        if ((c.kind == ControlKind.LIGHT || c.kind == ControlKind.GAS_VALVE || c.kind == ControlKind.VENTILATION) && c.on) {
                            c.on = false;
                            if (c.kind == ControlKind.VENTILATION) c.level = 0;
                            apply(level, data, c);
                        }
                    }
                }
                DeviceLogic.msg(player, away ? "외출모드 설정: 조명 소등, 가스 잠금, 환기 정지" : "외출모드 해제", ChatFormatting.AQUA);
            }
            case "set_pw" -> {
                String pw = args.getString("pw").trim();
                if (!pw.isEmpty() && !pw.matches("\\d{4,8}")) return "세대 비밀번호는 숫자 4~8자리입니다";
                data.setHouseholdPassword(cid, dong, ho, pw);
                DeviceLogic.msg(player, pw.isEmpty() ? "세대 비밀번호를 삭제했습니다" : "세대 비밀번호를 저장했습니다", ChatFormatting.GREEN);
            }
            default -> { }
        }
        return null;
    }

    // ------------------------------------------------------------------ 설비 블록 자체 화면

    public static CompoundTag controlScreenData(ServerLevel level, ControlBlockEntity be) {
        NetworkData data = NetworkData.get(level);
        NetworkData.Control c = be.control();
        CompoundTag tag = controlTag(c);
        tag.putString("kind", "control");
        tag.putString("ckind", c.kind.id);
        tag.putString("room", c.room);
        tag.putInt("dong", c.dong);
        tag.putInt("ho", c.ho);
        int cid = data.complexAt(be.getBlockPos());
        NetworkData.Complex cx = data.getComplex(cid);
        tag.putString("complex", cx == null ? "" : cx.name);
        return tag;
    }

    public static void handleControlBlock(ServerPlayer player, ServerLevel level, ControlBlockEntity be, String action, CompoundTag args) {
        NetworkData data = NetworkData.get(level);
        NetworkData.Control c = be.control();
        if (action.equals("ctl_settings")) {
            c.dong = DeviceLogic.clamp(args.getInt("dong"));
            c.ho = DeviceLogic.clamp(args.getInt("ho"));
            String room = args.getString("room").trim();
            c.room = room.length() > 12 ? room.substring(0, 12) : room;
            apply(level, data, c);
            DeviceLogic.msg(player, "설비 설정을 저장했습니다", ChatFormatting.GREEN);
        } else {
            change(level, data, c, action, args);
        }
    }
}
