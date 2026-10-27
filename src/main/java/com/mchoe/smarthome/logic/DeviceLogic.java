package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.block.ComputerBlock;
import com.mchoe.smarthome.block.DashboardBlock;
import com.mchoe.smarthome.blockentity.ControlBlockEntity;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    private static final String OUTSIDE = "단지 구획 밖입니다. 단지 대시보드 지도에서 구획을 설정하세요";

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
            case LOBBY_PHONE -> dong == OUTER_LOBBY_DONG ? "단지 외곽 로비 " + num(ho) : num(dong) + "동 로비 " + num(ho);
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

    static int clamp(int n) {
        return Math.max(0, Math.min(MAX_NUMBER, n));
    }

    static void msg(ServerPlayer p, String s, ChatFormatting color) {
        p.displayClientMessage(Component.literal(s).withStyle(color), true);
    }

    // ------------------------------------------------------------------ 상태

    /** 기기 화면에 표시할 통화 상태 */
    public static void writeStatus(ServerLevel level, BlockPos pos, CompoundTag tag) {
        Call c = CallManager.find(level.dimension(), pos);
        int status = STATUS_IDLE;
        String peer = "";
        int callId = 0;
        BlockPos cam = null;
        if (c != null) {
            callId = c.id;
            if (c.caller.equals(pos)) {
                status = c.answered != null ? STATUS_CONNECTED : STATUS_CALLING;
                peer = c.answered != null ? c.answeredLabel
                        : (c.targetLabels.isEmpty() ? "" : c.targetLabels.get(0) + (c.targetLabels.size() > 1 ? " 외 " + (c.targetLabels.size() - 1) : ""));
                if (c.answered != null) {
                    NetworkData.Device other = NetworkData.get(level).getDevice(c.answered);
                    if (other != null && other.type.camera) cam = c.answered;
                }
            } else {
                status = c.answered != null ? STATUS_CONNECTED : STATUS_RINGING;
                peer = c.callerLabel;
                if (c.hasVideo()) cam = c.caller;
            }
        }
        tag.putInt("status", status);
        tag.putString("peer", peer);
        tag.putInt("callId", callId);
        tag.putBoolean("entrance", c != null && !c.caller.equals(pos) && c.callerType.isEntrance());
        if (cam != null) tag.putLong("camPos", cam.asLong());
    }

    // ------------------------------------------------------------------ 화면 열기

    /** 이번 응답 화면 데이터에 덧붙일 값 (관리자 인증 결과 등) */
    private static CompoundTag extra = new CompoundTag();

    static CompoundTag extra() {
        return extra;
    }

    public static void openScreen(ServerPlayer player, BlockPos pos, boolean refreshOnly) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag;
        if (be instanceof DeviceBlockEntity d) {
            tag = deviceScreenData(level, d);
        } else if (be instanceof ControlBlockEntity c) {
            tag = HomeLogic.controlScreenData(level, c);
        } else if (level.getBlockState(pos).getBlock() instanceof ComputerBlock) {
            tag = AdminLogic.computerScreenData(level, pos);
        } else if (level.getBlockState(pos).getBlock() instanceof DashboardBlock) {
            tag = new CompoundTag();
            tag.putString("kind", "dashboard");
        } else {
            return;
        }
        if (!extra.isEmpty()) {
            tag.merge(extra);
            extra = new CompoundTag();
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScreenDataPacket(pos, refreshOnly, tag));
        if (!refreshOnly && tag.getString("kind").equals("dashboard")) {
            MapLogic.sendMap(player, level, pos.getX(), pos.getZ(), true);
        }
    }

    private static CompoundTag deviceScreenData(ServerLevel level, DeviceBlockEntity d) {
        BlockPos pos = d.getBlockPos();
        DeviceType type = d.getDeviceType();
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "device");
        tag.putString("type", type.id);
        tag.putInt("guard", d.getGuard());
        tag.putInt("dong", d.getDong());
        tag.putInt("ho", d.getHo());
        tag.putBoolean("doorOpen", d.isDoorOpen());
        tag.putBoolean("linked", d.isLinked());
        tag.putBoolean("adminSet", d.hasAdminPw());
        if (type == DeviceType.GUARD_STATION) {
            tag.putBoolean("away", d.isAway());
            tag.putInt("bypass", d.getBypass());
            CallManager.writeExchange(level, pos, tag);
        }
        NetworkData.Device reg = NetworkData.get(level).getDevice(pos);
        tag.putInt("doorCount", reg == null ? 0 : reg.doors.size());
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(pos);
        NetworkData.Complex cx = data.getComplex(cid);
        tag.putString("complex", cx == null ? "" : cx.name);
        tag.putInt("complexId", cid);
        writeStatus(level, pos, tag);

        Call call = CallManager.find(level.dimension(), pos);
        ListTag msgs = new ListTag();
        if (call != null && call.answered != null) for (String s : call.messages) msgs.add(StringTag.valueOf(s));
        tag.put("messages", msgs);

        ListTag log = new ListTag();
        for (String s : d.getLog()) log.add(StringTag.valueOf(s));
        tag.put("log", log);

        ListTag incoming = new ListTag();
        for (Call c : CallManager.ringingAt(level.dimension(), pos)) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putString("label", c.callerLabel);
            t.putBoolean("video", c.hasVideo());
            incoming.add(t);
        }
        tag.put("incoming", incoming);

        if (cx != null) {
            boolean myGuardExists = false;
            ListTag cctv = new ListTag();
            BlockPos doorCam = null;
            for (NetworkData.Device o : data.devicesIn(cid)) {
                if (o.type == DeviceType.GUARD_STATION && o.guard == d.getGuard()) myGuardExists = true;
                if (o.type == DeviceType.LOBBY_PHONE) {
                    CompoundTag t = new CompoundTag();
                    t.putLong("pos", o.pos.asLong());
                    t.putString("label", label(o));
                    cctv.add(t);
                }
                if (o.type == DeviceType.DOOR_CAMERA && o.linked && o.dong == d.getDong() && o.ho == d.getHo() && doorCam == null) doorCam = o.pos;
            }
            tag.putBoolean("myGuardExists", myGuardExists);
            if (type == DeviceType.GUARD_STATION) tag.put("cctv", cctv);
            if (type.internal && doorCam != null) tag.putLong("doorCam", doorCam.asLong());
            if (type.internal) {
                tag.putString("notice", cx.notice);
                tag.putBoolean("hasPw", data.hasCustomHouseholdPassword(cid, d.getDong(), d.getHo()));
            }
            if (type == DeviceType.WALLPAD) HomeLogic.writeHousehold(level, data, cid, d.getDong(), d.getHo(), tag);
            if (type.internal) SecurityLogic.write(level, data, cid, d.getDong(), d.getHo(), tag);
            if (type == DeviceType.GUARD_STATION) SecurityLogic.writeAlerts(data, cid, tag);
            if (type == DeviceType.LOBBY_PHONE) {
                tag.putBoolean("commonEnabled", d.isCommonEnabled());
                tag.putBoolean("householdEnabled", d.isHouseholdEnabled());
                int scope = d.getDong() == OUTER_LOBBY_DONG ? NetworkData.UNSET : d.getDong();
                tag.putBoolean("commonSet", !cx.commonPassword.isEmpty());
                tag.putInt("cardCount", data.countCards(cid, scope));
            }
        }
        return tag;
    }

    // ------------------------------------------------------------------ 버튼 처리

    public static void handleAction(ServerPlayer player, BlockPos pos, String action, CompoundTag args) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 100.0) return;
        if (!level.isLoaded(pos)) return;

        if (action.startsWith("map") || action.startsWith("zone") || action.equals("merge") || action.equals("split") || action.equals("rename")) {
            if (level.getBlockState(pos).getBlock() instanceof DashboardBlock) MapLogic.handle(player, level, action, args);
            return;
        }
        if (action.startsWith("comp_")) {
            if (level.getBlockState(pos).getBlock() instanceof ComputerBlock) AdminLogic.handle(player, level, pos, action, args);
            openScreen(player, pos, true);
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ControlBlockEntity cbe) {
            HomeLogic.handleControlBlock(player, level, cbe, action, args);
            openScreen(player, pos, true);
            return;
        }
        if (!(be instanceof DeviceBlockEntity dev)) return;

        // 관리 비밀번호로 잠긴 설정 동작
        if ((action.equals("settings") || action.equals("set_pw")) && !dev.checkAdmin(args.getString("auth"))) {
            extra.putBoolean("setFail", true);
            msg(player, "관리 비밀번호가 필요합니다", ChatFormatting.RED);
            openScreen(player, pos, true);
            return;
        }
        String error = switch (action) {
            case "refresh" -> null;
            case "admin_check" -> {
                if (dev.checkAdmin(args.getString("pw"))) {
                    extra.putBoolean("setOk", true);
                    yield null;
                }
                extra.putBoolean("setFail", true);
                yield "관리 비밀번호가 틀립니다";
            }
            case "guard_away" -> guardAway(dev, args);
            case "alert_ack" -> {
                NetworkData nd = NetworkData.get(level);
                int acid = nd.complexAt(pos);
                if (args.getInt("id") > 0) nd.removeAlert(args.getInt("id"));
                else for (NetworkData.Alert al : nd.alertsOf(acid)) nd.removeAlert(al.id);
                yield null;
            }
            case "exchange" -> exchange(level, player, dev, args.getInt("dong"), args.getInt("ho"));
            case "exchange_cancel" -> {
                CallManager.cancelExchange(level, pos, "교환을 취소했습니다");
                yield null;
            }
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
            case "say" -> CallManager.say(level, pos, player, args.getString("text"));
            case "password" -> lobbyPassword(level, player, dev, args.getString("input"));
            case "household_entry" -> householdEntry(level, player, dev, args.getInt("dong"), args.getInt("ho"), args.getString("pw"));
            case "common_entry" -> commonEntry(level, player, dev, args.getString("pw"), args.getBoolean("silent"));
            case "lobby_pw_check_ho" -> lobbyPwCheckHo(level, dev, args.getInt("ho"));
            case "lobby_pw_check_old" -> lobbyPwCheckOld(level, dev, args.getInt("ho"), args.getString("old"));
            case "lobby_pw_change" -> lobbyPwChange(level, player, dev, args.getInt("ho"), args.getString("old"), args.getString("new"));
            case "lobby_admin", "lobby_common_pw", "lobby_card_register", "lobby_card_delete", "lobby_card_delete_all", "lobby_common_toggle", "lobby_household_toggle" ->
                    AdminLogic.lobbyAdmin(player, level, dev, action, args);
            default -> HomeLogic.handleWallpad(player, level, dev, action, args);
        };
        if (error != null) msg(player, error, ChatFormatting.RED);
        openScreen(player, pos, true);
    }

    private static String saveSettings(DeviceBlockEntity dev, CompoundTag args) {
        int guard = dev.getGuard(), dong = dev.getDong(), ho = dev.getHo();
        switch (dev.getDeviceType().settings) {
            case UNIT, LOBBY -> {
                guard = clamp(args.getInt("guard"));
                dong = clamp(args.getInt("dong"));
                ho = clamp(args.getInt("ho"));
            }
            case GUARD -> guard = clamp(args.getInt("guard"));
            case DOOR -> {
                return "도어카메라·도어폰은 홈 링커로 세대기와 연동하세요";
            }
        }
        String newPw = args.getString("adminPw").trim();
        if (!newPw.isEmpty() && !newPw.matches("\\d{4,8}")) return "관리 비밀번호는 숫자 4~8자리입니다";
        String newHousePw = args.getString("householdPw").trim();
        if (!newHousePw.isEmpty() && !newHousePw.matches("\\d{4,8}")) return "세대 비밀번호는 숫자 4~8자리입니다";
        dev.setNumbers(guard, dong, ho);
        if (!newHousePw.isEmpty() && dev.getLevel() instanceof ServerLevel sl) {
            NetworkData data = NetworkData.get(sl);
            int cid = data.complexAt(dev.getBlockPos());
            if (cid >= 0 && dong >= 0 && ho >= 0) data.setHouseholdPassword(cid, dong, ho, newHousePw);
        }
        if (args.getBoolean("adminClear")) dev.setAdminPw("");
        else if (!newPw.isEmpty()) dev.setAdminPw(newPw);
        return null;
    }

    /** 경비실기 외출/재실. 외출하려면 우회 경비실 번호가 있어야 한다 */
    private static String guardAway(DeviceBlockEntity dev, CompoundTag args) {
        if (dev.getDeviceType() != DeviceType.GUARD_STATION) return null;
        boolean away = args.getBoolean("away");
        if (!away) {
            dev.setAway(false, dev.getBypass());
            return null;
        }
        if (!args.contains("bypass")) return "우회 경비실 번호를 입력하세요";
        int bypass = args.getInt("bypass");
        if (bypass < 0 || bypass > MAX_NUMBER) return "우회 경비실 번호는 0~9999 입니다";
        if (bypass == dev.getGuard()) return "자기 경비실 번호로는 우회할 수 없습니다";
        dev.setAway(true, bypass);
        return null;
    }

    /** 경비실 교환: 통화 중인 세대를 다른 세대로 연결 */
    private static String exchange(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int ho) {
        if (dev.getDeviceType() != DeviceType.GUARD_STATION) return "경비실기에서만 교환할 수 있습니다";
        if (dong < 0 || dong > MAX_NUMBER || ho < 0 || ho > MAX_NUMBER) return "교환할 동·호를 입력하세요";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        List<NetworkData.Device> targets = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (d.type.internal && d.dong == dong && d.ho == ho) targets.add(d);
        }
        if (targets.isEmpty()) return "단지 안에 " + dong + "동 " + ho + "호 세대기가 없습니다";
        String err = CallManager.exchange(level, dev.getBlockPos(), myDevice(data, dev), targets);
        if (err == null) msg(player, dong + "동 " + ho + "호로 교환 호출 중...", ChatFormatting.AQUA);
        return err;
    }

    /** 경비실 호출: 관할 경비실 우선, 없거나 통화 중이면 단지 내 다른 경비실 */
    private static String callGuard(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev) {
        DeviceType type = dev.getDeviceType();
        if (type == DeviceType.GUARD_STATION || type.settings == DeviceType.SettingsKind.DOOR) return "이 기기는 경비실을 호출할 수 없습니다";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        NetworkData.Device me = myDevice(data, dev);
        List<NetworkData.Device> guards = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cid)) if (d.type == DeviceType.GUARD_STATION) guards.add(d);
        // 관할 경비실 (외출 중이면 우회 경비실로)
        List<NetworkData.Device> mine = new ArrayList<>();
        String route = null;
        for (NetworkData.Device g : guards) {
            if (dev.getGuard() < 0 || g.guard != dev.getGuard()) continue;
            if (!g.away) {
                if (!mine.contains(g)) mine.add(g);
            } else if (g.bypass >= 0) {
                route = "관할 경비실 외출 중 → 우회 경비실 " + g.bypass;
                for (NetworkData.Device b : guards) if (b.guard == g.bypass && !b.away && !mine.contains(b)) mine.add(b);
            }
        }
        List<NetworkData.Device> others = new ArrayList<>();
        for (NetworkData.Device g : guards) if (!g.away && !mine.contains(g)) others.add(g);
        if (mine.isEmpty() && others.isEmpty()) return guards.isEmpty() ? "단지에 경비실기가 없습니다" : "모든 경비실이 외출 중입니다";
        if (!mine.isEmpty()) {
            String err = CallManager.start(level, dev.getBlockPos(), me, mine, player);
            if (err == null) {
                msg(player, route != null ? route + " 호출 중..." : "관할 경비실 " + dev.getGuard() + " 호출 중...", ChatFormatting.AQUA);
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

    private static String callUnit(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int ho) {
        DeviceType type = dev.getDeviceType();
        if (dong < 0 || dong > MAX_NUMBER || ho < 0 || ho > MAX_NUMBER) return "동·호 번호는 0~9999 사이로 입력하세요";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;

        boolean requireSameGuard;
        switch (type) {
            case VIDEOPHONE_BASIC, INTERPHONE_BASIC -> {
                return "이 기기는 세대 직접 호출 기능이 없습니다. 경비실을 호출해 교환을 요청하세요";
            }
            case WALLPAD, VIDEOPHONE, INTERPHONE -> {
                if (dev.getGuard() < 0) return "먼저 관할경비실·세대번호를 설정하세요";
                requireSameGuard = true;
            }
            case LOBBY_PHONE -> {
                if (dev.getGuard() < 0 || dev.getDong() < 0) return "먼저 관할경비실·동/로비번호를 설정하세요";
                if (dev.getDong() == OUTER_LOBBY_DONG) {
                    requireSameGuard = false;
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
        for (NetworkData.Device d : data.devicesIn(cid)) {
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

    private static String callLobby(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int lobby) {
        if (dev.getDeviceType() != DeviceType.GUARD_STATION) return "경비실기에서만 로비폰을 호출할 수 있습니다";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        List<NetworkData.Device> targets = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (d.type == DeviceType.LOBBY_PHONE && d.dong == dong && d.ho == lobby) targets.add(d);
        }
        if (targets.isEmpty()) return "단지 안에 해당 로비폰이 없습니다";
        String err = CallManager.start(level, dev.getBlockPos(), myDevice(data, dev), targets, player);
        if (err == null) msg(player, label(DeviceType.LOBBY_PHONE, 0, dong, lobby) + " 호출 중...", ChatFormatting.AQUA);
        return err;
    }

    private static String ringHousehold(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev) {
        if (dev.getDeviceType().settings != DeviceType.SettingsKind.DOOR) return null;
        if (!dev.isLinked() || dev.getDong() < 0 || dev.getHo() < 0) return "연동된 세대가 없습니다. 홈 링커로 세대기와 연동하세요";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        List<NetworkData.Device> targets = new ArrayList<>();
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (d.type.internal && d.dong == dev.getDong() && d.ho == dev.getHo()) targets.add(d);
        }
        if (targets.isEmpty()) return dev.getDong() + "동 " + dev.getHo() + "호 세대 내부기기가 없습니다";
        String err = CallManager.start(level, dev.getBlockPos(), myDevice(data, dev), targets, player);
        if (err == null) msg(player, "딩동~ 세대를 호출했습니다", ChatFormatting.AQUA);
        return err;
    }

    private static String openDoor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Call c = CallManager.find(level.dimension(), pos);
        if (c == null) return "문을 열 수 있는 출입 호출이 없습니다";
        BlockPos door = null;
        if (!c.caller.equals(pos) && c.callerType.isEntrance()) door = c.caller;
        else if (c.caller.equals(pos) && c.answered != null) {
            NetworkData.Device other = NetworkData.get(level).getDevice(c.answered);
            if (other != null && other.type.isEntrance()) door = c.answered;
        }
        if (door == null) return "문을 열 수 있는 출입 호출이 없습니다";
        if (unlock(level, door, pos) == 0) return NO_DOOR;
        msg(player, "출입문을 열었습니다", ChatFormatting.GREEN);
        return null;
    }

    private static final String NO_DOOR = "연동된 문이 없습니다. 도어 링커로 기기와 문을 연동하세요";

    /**
     * 출입 기기의 문을 연다 (도어 링커로 연동한 문만).
     * 로비폰: 로비폰에 연동한 문 / 도어카메라·도어폰: 그 세대 월패드·비디오폰·인터폰에 연동한 문
     * @return 연 문의 수
     */
    public static int unlock(ServerLevel level, BlockPos entrance, BlockPos requester) {
        NetworkData data = NetworkData.get(level);
        NetworkData.Device e = data.getDevice(entrance);
        if (e == null) return 0;
        java.util.Set<BlockPos> doors = new java.util.LinkedHashSet<>();
        if (e.type == DeviceType.LOBBY_PHONE) {
            doors.addAll(e.doors);
        } else {
            NetworkData.Device req = requester == null ? null : data.getDevice(requester);
            if (req != null && req.type.internal) doors.addAll(req.doors);
            int cid = data.complexAt(entrance);
            for (NetworkData.Device d : data.devicesIn(cid)) {
                if (d.type.internal && d.dong == e.dong && d.ho == e.ho) doors.addAll(d.doors);
            }
        }
        if (level.isLoaded(entrance) && level.getBlockEntity(entrance) instanceof DeviceBlockEntity d) {
            d.openDoor(100);
            level.playSound(null, entrance, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.4f);
        }
        return DoorControl.open(level, doors, 100);
    }

    /**
     * 로비폰 비밀번호.
     * 공용: "비밀번호"
     * 세대별: 일반 로비 "호*비밀번호", 단지 외곽 로비 "동*호*비밀번호"
     */
    private static String lobbyPassword(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, String input) {
        if (dev.getDeviceType() != DeviceType.LOBBY_PHONE) return null;
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        NetworkData.Complex cx = data.getComplex(cid);
        input = input == null ? "" : input.trim();
        if (input.isEmpty()) return "비밀번호를 입력하세요";
        String[] parts = input.split("\\*");
        boolean ok = false;
        String who = "";
        try {
            if (parts.length == 1) {
                ok = cx != null && !cx.commonPassword.isEmpty() && cx.commonPassword.equals(parts[0]);
                who = "공용 비밀번호";
            } else if (parts.length == 2 && dev.getDong() != OUTER_LOBBY_DONG) {
                int ho = Integer.parseInt(parts[0]);
                String pw = data.householdPassword(cid, dev.getDong(), ho);
                ok = !pw.isEmpty() && pw.equals(parts[1]);
                who = dev.getDong() + "동 " + ho + "호";
            } else if (parts.length == 3) {
                int dong = Integer.parseInt(parts[0]);
                int ho = Integer.parseInt(parts[1]);
                if (dev.getDong() != OUTER_LOBBY_DONG && dong != dev.getDong()) return "이 로비는 " + dev.getDong() + "동 전용입니다";
                String pw = data.householdPassword(cid, dong, ho);
                ok = !pw.isEmpty() && pw.equals(parts[2]);
                who = dong + "동 " + ho + "호";
            }
        } catch (NumberFormatException e) {
            ok = false;
        }
        if (!ok) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return "비밀번호가 일치하지 않습니다";
        }
        if (unlock(level, dev.getBlockPos(), null) == 0) msg(player, who + " 인증 - " + NO_DOOR, ChatFormatting.YELLOW);
        else msg(player, who + " 인증 - 문이 열렸습니다", ChatFormatting.GREEN);
        return null;
    }

    /** 로비폰 세대 비밀번호 출입: (동) 호 [열쇠] 비밀번호 [호출] */
    private static String householdEntry(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int dong, int ho, String pw) {
        if (dev.getDeviceType() != DeviceType.LOBBY_PHONE) return null;
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        if (!dev.isHouseholdEnabled()) return "이 로비폰은 세대 비밀번호 출입을 사용하지 않습니다";
        if (dev.getDong() != OUTER_LOBBY_DONG) dong = dev.getDong();
        if (dong < 0 || ho < 0) return "동·호를 입력하세요";
        boolean exists = false;
        for (NetworkData.Device d : data.devicesIn(cid)) if (d.type.internal && d.dong == dong && d.ho == ho) exists = true;
        if (!exists || !data.householdPassword(cid, dong, ho).equals(pw == null ? "" : pw.trim())) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return "비밀번호가 일치하지 않습니다";
        }
        extra.putBoolean("entryOk", true);
        String who = dong + "동 " + ho + "호";
        if (unlock(level, dev.getBlockPos(), null) == 0) msg(player, who + " 인증 - " + NO_DOOR, ChatFormatting.YELLOW);
        else msg(player, who + " 인증 - 문이 열렸습니다", ChatFormatting.GREEN);
        return null;
    }

    /** 로비폰 공용 비밀번호 출입: [호출] 공용비밀번호 (맞으면 바로 열림) */
    private static String commonEntry(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, String pw, boolean silent) {
        if (dev.getDeviceType() != DeviceType.LOBBY_PHONE) return null;
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return silent ? null : OUTSIDE;
        NetworkData.Complex cx = data.getComplex(cid);
        if (!dev.isCommonEnabled()) return silent ? null : "이 로비폰은 공용 비밀번호를 사용하지 않습니다";
        if (cx == null || cx.commonPassword.isEmpty()) return silent ? null : "공용 비밀번호가 설정되어 있지 않습니다";
        if (!cx.commonPassword.equals(pw == null ? "" : pw.trim())) {
            if (silent) return null;
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return "공용 비밀번호가 일치하지 않습니다";
        }
        extra.putBoolean("entryOk", true);
        if (unlock(level, dev.getBlockPos(), null) == 0) msg(player, "공용 비밀번호 인증 - " + NO_DOOR, ChatFormatting.YELLOW);
        else msg(player, "공용 비밀번호 인증 - 문이 열렸습니다", ChatFormatting.GREEN);
        return null;
    }

    /** 세대에서 비밀번호를 바꿀 수 있는 기기 (월패드, 비디오폰, 다이얼 인터폰) */
    private static boolean canSetHouseholdPw(DeviceType t) {
        return t == DeviceType.WALLPAD || t == DeviceType.VIDEOPHONE || t == DeviceType.INTERPHONE;
    }

    /**
     * 로비폰에서 세대 비밀번호 변경: [열쇠] 호 [호출] 기존 [호출] 새 비밀번호 [호출].
     * 동 번호가 부여된 로비폰에서만, 그리고 세대 안에 비밀번호를 바꿀 수 있는 기기가 없을 때만.
     */
    /** 로비폰에서 세대 비밀번호를 바꿀 수 있는 세대인지 (존재 · 동 로비 · 세대 안에 변경 가능 기기 없음) */
    private static String checkChangeableHousehold(ServerLevel level, DeviceBlockEntity dev, int ho) {
        if (dev.getDeviceType() != DeviceType.LOBBY_PHONE) return "로비폰이 아닙니다";
        if (dev.getDong() < 0 || dev.getDong() == OUTER_LOBBY_DONG) return "단지 출입용 로비폰에서는 세대 비밀번호를 바꿀 수 없습니다";
        if (ho < 0 || ho > MAX_NUMBER) return "호 번호를 입력하세요";
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        if (cid < 0) return OUTSIDE;
        int dong = dev.getDong();
        boolean exists = false;
        for (NetworkData.Device d : data.devicesIn(cid)) {
            if (!d.type.internal || d.dong != dong || d.ho != ho) continue;
            exists = true;
            if (canSetHouseholdPw(d.type)) return "이 세대는 세대 내부기기(" + d.type.korean + ")에서 비밀번호를 변경하세요";
        }
        if (!exists) return dong + "동 " + ho + "호는 존재하지 않는 세대입니다";
        return null;
    }

    private static boolean oldPwMatches(ServerLevel level, DeviceBlockEntity dev, int ho, String old) {
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        return data.householdPassword(cid, dev.getDong(), ho).equals(old == null ? "" : old.trim());
    }

    /** 변경 1단계: 호 번호 확인 */
    private static String lobbyPwCheckHo(ServerLevel level, DeviceBlockEntity dev, int ho) {
        String err = checkChangeableHousehold(level, dev, ho);
        extra.putString(err == null ? "chgStep" : "chgFail", err == null ? "ho_ok" : "ho");
        return err;
    }

    /** 변경 2단계: 기존 비밀번호 확인 (틀리면 새 비밀번호 단계로 못 넘어감) */
    private static String lobbyPwCheckOld(ServerLevel level, DeviceBlockEntity dev, int ho, String old) {
        String err = checkChangeableHousehold(level, dev, ho);
        if (err == null && !oldPwMatches(level, dev, ho, old)) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            err = "기존 비밀번호가 일치하지 않습니다";
        }
        extra.putString(err == null ? "chgStep" : "chgFail", err == null ? "old_ok" : "old");
        return err;
    }

    /**
     * 변경 3단계: 로비폰에서 세대 비밀번호 변경. [열쇠] 호 [호출] 기존 [호출] 새 비밀번호 [호출].
     * 동 번호가 부여된 로비폰에서만, 세대가 존재하고 세대 안에 비밀번호를 바꿀 수 있는 기기가 없을 때만.
     */
    private static String lobbyPwChange(ServerLevel level, ServerPlayer player, DeviceBlockEntity dev, int ho, String old, String neu) {
        String err = checkChangeableHousehold(level, dev, ho);
        if (err != null) return err;
        if (!oldPwMatches(level, dev, ho, old)) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return "기존 비밀번호가 일치하지 않습니다";
        }
        neu = neu == null ? "" : neu.trim();
        if (!neu.matches("\\d{4,8}")) {
            extra.putString("chgFail", "new");
            return "새 비밀번호는 숫자 4~8자리입니다";
        }
        NetworkData data = NetworkData.get(level);
        int dong = dev.getDong();
        data.setHouseholdPassword(data.complexAt(dev.getBlockPos()), dong, ho, neu);
        extra.putString("chgStep", "done");
        level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7f, 1.8f);
        msg(player, dong + "동 " + ho + "호 세대 비밀번호를 변경했습니다", ChatFormatting.GREEN);
        return null;
    }

    /** 출입 카드 태그 (카드를 들고 로비폰 우클릭) */
    public static void tagCard(ServerPlayer player, ServerLevel level, DeviceBlockEntity dev, int cardId) {
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        NetworkData.Card card = data.getCard(cardId);
        boolean ok = cid >= 0 && card != null && !card.revoked && card.complex == cid
                && (card.dong < 0 || dev.getDong() == OUTER_LOBBY_DONG || card.dong == dev.getDong());
        if (!ok) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            String why = card == null ? "등록되지 않은 카드입니다"
                    : card.revoked ? "사용 중지된 카드입니다"
                    : card.complex != cid ? "다른 단지의 카드입니다" : "이 출입구에서 사용할 수 없는 카드입니다";
            msg(player, why, ChatFormatting.RED);
            return;
        }
        level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8f, 2.0f);
        if (unlock(level, dev.getBlockPos(), null) == 0) msg(player, "카드 인증 - " + NO_DOOR, ChatFormatting.YELLOW);
        else msg(player, "카드 인증 - 문이 열렸습니다", ChatFormatting.GREEN);
    }

    static NetworkData.Device myDevice(NetworkData data, DeviceBlockEntity dev) {
        dev.syncRegistry();
        return data.getDevice(dev.getBlockPos());
    }
}
