package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.blockentity.DashboardBlockEntity;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import com.mchoe.smarthome.network.ModNetwork;
import com.mchoe.smarthome.network.ScreenDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** 기기 조작(버튼) 처리와 연동 규칙 */
public final class DeviceLogic {
    public static final int OUTER_LOBBY_DONG = 9999;
    public static final int MAX_NUMBER = 9999;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_RINGING = 1;
    public static final int STATUS_CALLING = 2;
    public static final int STATUS_CONNECTED = 3;

    private DeviceLogic() {}

    // ------------------------------------------------------------------ 표기

    public static String num(int n) {
        return n < 0 ? "미설정" : String.valueOf(n);
    }

    public static String label(NetworkData.Device d) {
        return label(d.type, d.guard, d.dong, d.ho);
    }

    public static String label(DeviceType type, int guard, int dong, int ho) {
        return switch (type) {
            case GUARD_STATION -> "경비실 " + num(guard);
            case LOBBY_PHONE -> dong == OUTER_LOBBY_DONG ? "단지 외곽 로비폰 " + num(ho) : num(dong) + "동 로비폰 " + num(ho);
            default -> type.korean + " " + num(dong) + "동 " + num(ho) + "호";
        };
    }

    public static String timeString(ServerLevel level) {
        long t = level.getDayTime();
        long day = t / 24000L + 1;
        int h = (int) ((t / 1000L + 6) % 24);
        int m = (int) ((t % 1000L) * 60 / 1000L);
        return String.format("%d일 %02d:%02d", day, h, m);
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(MAX_NUMBER, n));
    }

    // ------------------------------------------------------------------ 상태 계산

    /** 기기 화면에 표시할 통화 상태를 tag 에 기록 */
    public static void writeStatus(ServerLevel level, BlockPos pos, CompoundTag tag) {
        Call c = CallManager.find(level.dimension(), pos);
        int status = STATUS_IDLE;
        String peer = "";
        boolean video = false;
        String visitor = "";
        java.util.UUID vid = null;
        int callId = 0;
        if (c != null) {
            callId = c.id;
            if (c.caller.equals(pos)) {
                status = c.answered != null ? STATUS_CONNECTED : STATUS_CALLING;
                peer = c.answered != null ? c.answeredLabel
                        : (c.targetLabels.isEmpty() ? "" : c.targetLabels.get(0) + (c.targetLabels.size() > 1 ? " 외 " + (c.targetLabels.size() - 1) : ""));
            } else {
                status = c.answered != null ? STATUS_CONNECTED : STATUS_RINGING;
                peer = c.callerLabel;
                video = c.hasVideo();
                visitor = c.visitorName;
                vid = c.visitorId;
            }
        }
        tag.putInt("status", status);
        tag.putString("peer", peer);
        tag.putBoolean("video", video);
        tag.putString("visitor", visitor);
        if (vid != null) tag.putUUID("visitorId", vid);
        tag.putInt("callId", callId);
        tag.putBoolean("entrance", c != null && !c.caller.equals(pos) && c.callerType.isEntrance());
    }

    // ------------------------------------------------------------------ 화면 데이터

    public static void openScreen(ServerPlayer player, BlockPos pos, boolean refreshOnly) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag;
        if (be instanceof DeviceBlockEntity d) {
            tag = deviceScreenData(level, d);
        } else if (be instanceof DashboardBlockEntity dash) {
            tag = dashboardScreenData(level, dash);
        } else {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScreenDataPacket(pos, refreshOnly, tag));
    }

    private static CompoundTag deviceScreenData(ServerLevel level, DeviceBlockEntity d) {
        BlockPos pos = d.getBlockPos();
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "device");
        tag.putString("type", d.getDeviceType().id);
        tag.putInt("guard", d.getGuard());
        tag.putInt("dong", d.getDong());
        tag.putInt("ho", d.getHo());
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.complexAt(pos);
        tag.putString("complex", cx == null ? "" : cx.name);
        writeStatus(level, pos, tag);
        ListTag log = new ListTag();
        for (String s : d.getLog()) log.add(StringTag.valueOf(s));
        tag.put("log", log);

        // 경비실기: 들어온 호출 목록
        ListTag incoming = new ListTag();
        for (Call c : CallManager.ringingAt(level.dimension(), pos)) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putString("label", c.callerLabel);
            t.putBoolean("video", c.hasVideo());
            incoming.add(t);
        }
        tag.put("incoming", incoming);

        // 같은 관할의 세대 수 / 단지 경비실 정보
        if (cx != null) {
            int guards = 0, linked = 0;
            boolean myGuardExists = false;
            for (NetworkData.Device o : data.devicesIn(cx)) {
                if (o.type == DeviceType.GUARD_STATION) {
                    guards++;
                    if (o.guard == d.getGuard()) myGuardExists = true;
                } else if (o.guard == d.getGuard() && !o.pos.equals(pos) && d.getGuard() >= 0) {
                    linked++;
                }
            }
            tag.putInt("guards", guards);
            tag.putInt("linked", linked);
            tag.putBoolean("myGuardExists", myGuardExists);
        }
        return tag;
    }

    private static CompoundTag dashboardScreenData(ServerLevel level, DashboardBlockEntity dash) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "dashboard");
        tag.putString("name", dash.getComplexName());
        tag.putInt("x1", dash.getX1());
        tag.putInt("z1", dash.getZ1());
        tag.putInt("x2", dash.getX2());
        tag.putInt("z2", dash.getZ2());
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.getComplex(dash.getBlockPos());
        ListTag list = new ListTag();
        if (cx != null) {
            List<NetworkData.Device> ds = new ArrayList<>(data.devicesIn(cx));
            ds.sort((a, b) -> {
                int c = Integer.compare(a.type.ordinal(), b.type.ordinal());
                if (c != 0) return c;
                c = Integer.compare(a.dong, b.dong);
                return c != 0 ? c : Integer.compare(a.ho, b.ho);
            });
            for (NetworkData.Device d : ds) {
                String guardPart = d.type == DeviceType.GUARD_STATION || d.type.settings == DeviceType.SettingsKind.DOOR
                        ? "" : " | 관할 " + num(d.guard);
                list.add(StringTag.valueOf(label(d) + guardPart + " | " + d.pos.getX() + ", " + d.pos.getY() + ", " + d.pos.getZ()));
            }
        }
        tag.put("devices", list);
        return tag;
    }

    // ------------------------------------------------------------------ 버튼 처리

    public static void handleAction(ServerPlayer player, BlockPos pos, String action, CompoundTag args) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) return;
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof DashboardBlockEntity dash) {
            if (action.equals("dash_save")) {
                String name = args.getString("name").trim();
                if (name.isEmpty()) name = "단지";
                if (name.length() > 24) name = name.substring(0, 24);
                dash.setArea(name, args.getInt("x1"), args.getInt("z1"), args.getInt("x2"), args.getInt("z2"));
                msg(player, "단지 구획을 저장했습니다: " + name, ChatFormatting.GREEN);
            }
            openScreen(player, pos, true);
            return;
        }
        if (!(be instanceof DeviceBlockEntity dev)) return;

        String error = switch (action) {
            case "refresh" -> null;
            case "settings" -> saveSettings(dev, args);
            case "call_guard" -> callGuard(level, player, dev);
            case "call_unit" -> callUnit(level, player, dev, args.getInt("dong"), args.getInt("ho"));
            case "call_lobby" -> callLobby(level, player, dev, args.getInt("dong"), args.getInt("ho"));
            case "ring" -> ringHousehold(level, player, dev);
            case "answer" -> CallManager.answer(level, pos, args.getInt("callId"));
            case "hangup" -> {
                CallManager.hangup(level, pos);
                yield null;
            }
            case "open_door" -> openDoor(level, player, pos);
            default -> null;
        };
        if (error != null) msg(player, error, ChatFormatting.RED);
        openScreen(player, pos, true);
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting color) {
        p.displayClientMessage(Component.literal(s).withStyle(color), true);
    }

    private static String saveSettings(DeviceBlockEntity dev, CompoundTag args) {
        DeviceType.SettingsKind kind = dev.getDeviceType().settings;
        int guard = dev.getGuard(), dong = dev.getDong(), ho = dev.getHo();
        switch (kind) {
            case UNIT, LOBBY -> {
                guard = clamp(args.getInt("guard"));
                dong = clamp(args.getInt("dong"));
                ho = clamp(args.getInt("ho"));
            }
            case GUARD -> guard = clamp(args.getInt("guard"));
            case DOOR -> {
                dong = clamp(args.getInt("dong"));
                ho = clamp(args.getInt("ho"));
            }
        }
        dev.setNumbers(guard, dong, ho);
        return null;
    }

    private static NetworkData.Complex complexOrNull(ServerLevel level, BlockPos pos) {
        return NetworkData.get(level).complexAt(pos);
    }

    private static final String OUTSIDE = "단지 구획 밖입니다. 단지 대시보드에서 설정한 구획 안에 설치하세요";

    /** 경비실 호출: 관할 경비실 우선, 없거나 모두 통화 중이면 단지 내 다른 경비실 */
    private static String callGuard(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev) {
        DeviceType type = dev.getDeviceType();
        if (type == DeviceType.GUARD_STATION || type.settings == DeviceType.SettingsKind.DOOR) return "이 기기는 경비실을 호출할 수 없습니다";
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.complexAt(dev.getBlockPos());
        if (cx == null) return OUTSIDE;
        NetworkData.Device me = myDevice(data, dev);
        List<NetworkData.Device> mine = new ArrayList<>();
        List<NetworkData.Device> others = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cx)) {
            if (d.type != DeviceType.GUARD_STATION) continue;
            if (dev.getGuard() >= 0 && d.guard == dev.getGuard()) mine.add(d);
            else others.add(d);
        }
        if (mine.isEmpty() && others.isEmpty()) return "단지에 경비실기가 없습니다";
        if (!mine.isEmpty()) {
            String err = CallManager.start(level, dev.getBlockPos(), me, mine, player);
            if (err == null) {
                msg(player, "관할 경비실 " + dev.getGuard() + " 호출 중...", ChatFormatting.AQUA);
                return null;
            }
            if (others.isEmpty()) return "관할 경비실이 통화 중입니다";
        }
        String err = CallManager.start(level, dev.getBlockPos(), me, others, player);
        if (err == null) {
            msg(player, mine.isEmpty() ? "관할 경비실이 없어 단지 내 경비실을 호출합니다" : "관할 경비실이 통화 중이라 다른 경비실을 호출합니다",
                    ChatFormatting.AQUA);
        }
        return err;
    }

    /** 세대 호출 */
    private static String callUnit(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int ho) {
        DeviceType type = dev.getDeviceType();
        if (dong < 0 || dong > MAX_NUMBER || ho < 0 || ho > MAX_NUMBER) return "동·호 번호는 0~9999 사이로 입력하세요";
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.complexAt(dev.getBlockPos());
        if (cx == null) return OUTSIDE;

        boolean requireSameGuard;
        switch (type) {
            case WALLPAD, VIDEOPHONE, INTERPHONE -> {
                if (dev.getGuard() < 0) return "먼저 관할경비실·세대번호를 설정하세요";
                requireSameGuard = true;
            }
            case LOBBY_PHONE -> {
                if (dev.getGuard() < 0 || dev.getDong() < 0) return "먼저 관할경비실·동/로비번호를 설정하세요";
                if (dev.getDong() == OUTER_LOBBY_DONG) {
                    requireSameGuard = false; // 단지 외곽 로비폰: 단지 안 어느 동·호든 호출
                } else {
                    if (dong != dev.getDong()) return "이 로비폰은 " + dev.getDong() + "동 세대만 호출할 수 있습니다";
                    requireSameGuard = true;
                }
            }
            case GUARD_STATION -> requireSameGuard = false;
            default -> {
                return "이 기기는 세대를 호출할 수 없습니다";
            }
        }

        List<NetworkData.Device> targets = new ArrayList<>();
        boolean otherGuard = false;
        for (NetworkData.Device d : data.devicesIn(cx)) {
            if (!d.type.internal || d.dong != dong || d.ho != ho || d.pos.equals(dev.getBlockPos())) continue;
            if (requireSameGuard && d.guard != dev.getGuard()) {
                otherGuard = true;
                continue;
            }
            targets.add(d);
        }
        if (targets.isEmpty()) {
            return otherGuard ? dong + "동 " + ho + "호는 관할 경비실 번호가 달라 연동되지 않습니다"
                    : "단지 안에 " + dong + "동 " + ho + "호 세대기가 없습니다";
        }
        String err = CallManager.start(level, dev.getBlockPos(), myDevice(data, dev), targets, player);
        if (err == null) msg(player, dong + "동 " + ho + "호 호출 중...", ChatFormatting.AQUA);
        return err;
    }

    /** 경비실기 → 로비폰 호출 */
    private static String callLobby(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int lobby) {
        if (dev.getDeviceType() != DeviceType.GUARD_STATION) return "경비실기에서만 로비폰을 호출할 수 있습니다";
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.complexAt(dev.getBlockPos());
        if (cx == null) return OUTSIDE;
        List<NetworkData.Device> targets = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cx)) {
            if (d.type == DeviceType.LOBBY_PHONE && d.dong == dong && d.ho == lobby) targets.add(d);
        }
        if (targets.isEmpty()) return "단지 안에 해당 로비폰이 없습니다";
        String err = CallManager.start(level, dev.getBlockPos(), myDevice(data, dev), targets, player);
        if (err == null) msg(player, label(DeviceType.LOBBY_PHONE, 0, dong, lobby) + " 호출 중...", ChatFormatting.AQUA);
        return err;
    }

    /** 도어카메라/도어폰: 설정된 세대 호출 */
    private static String ringHousehold(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev) {
        if (dev.getDeviceType().settings != DeviceType.SettingsKind.DOOR) return null;
        if (dev.getDong() < 0 || dev.getHo() < 0) return "먼저 세대번호(동·호)를 설정하세요";
        NetworkData data = NetworkData.get(level);
        NetworkData.Complex cx = data.complexAt(dev.getBlockPos());
        if (cx == null) return OUTSIDE;
        List<NetworkData.Device> targets = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cx)) {
            if (d.type.internal && d.dong == dev.getDong() && d.ho == dev.getHo()) targets.add(d);
        }
        if (targets.isEmpty()) return dev.getDong() + "동 " + dev.getHo() + "호 세대 내부기기가 없습니다";
        String err = CallManager.start(level, dev.getBlockPos(), myDevice(data, dev), targets, player);
        if (err == null) msg(player, "딩동~ 세대를 호출했습니다", ChatFormatting.AQUA);
        return err;
    }

    private static String openDoor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Call c = CallManager.find(level.dimension(), pos);
        if (c == null || c.caller.equals(pos) || !c.callerType.isEntrance()) return "문을 열 수 있는 출입 호출이 없습니다";
        if (level.isLoaded(c.caller) && level.getBlockEntity(c.caller) instanceof DeviceBlockEntity door) {
            door.openDoor(100);
        }
        msg(player, c.callerLabel + " 문을 열었습니다", ChatFormatting.GREEN);
        return null;
    }

    private static NetworkData.Device myDevice(NetworkData data, DeviceBlockEntity dev) {
        dev.syncRegistry();
        return data.getDevice(dev.getBlockPos());
    }
}
