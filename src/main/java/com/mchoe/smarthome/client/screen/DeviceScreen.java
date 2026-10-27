package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.client.ClientHooks;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 로비폰·인터폰·비디오폰·월패드·도어카메라·도어폰·경비실기 공통 조작화면 */
public class DeviceScreen extends Screen implements DataScreen {
    private static final int W = 330;
    private static final int H = 216;

    private final BlockPos pos;
    private CompoundTag data;
    private DeviceType type;
    private String signature = "";
    private int ticks;
    private String localError = "";

    private final Map<String, String> fieldValues = new HashMap<>();
    private final Map<String, EditBox> fields = new HashMap<>();

    private int left, top;

    public DeviceScreen(BlockPos pos, CompoundTag data) {
        super(Component.literal("Smart Home"));
        this.pos = pos;
        setData(data);
    }

    private void setData(CompoundTag data) {
        this.data = data;
        this.type = DeviceType.byId(data.getString("type"));
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    public CompoundTag getData() {
        return data;
    }

    public DeviceType getType() {
        return type;
    }

    @Override
    public void updateData(CompoundTag newData) {
        setData(newData);
        if (!computeSignature().equals(signature)) rebuildWidgets();
    }

    private String computeSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(data.getInt("status")).append('|').append(data.getBoolean("entrance")).append('|')
                .append(data.getInt("guard")).append(',').append(data.getInt("dong")).append(',').append(data.getInt("ho"));
        ListTag inc = data.getList("incoming", Tag.TAG_COMPOUND);
        for (int i = 0; i < inc.size(); i++) sb.append('|').append(inc.getCompound(i).getInt("id"));
        return sb.toString();
    }

    private int status() {
        return data.getInt("status");
    }

    // ------------------------------------------------------------------ 위젯

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        fields.clear();
        signature = computeSignature();

        int rx = left + 192;
        int rw = W - 200;
        int y = top + 28;
        int st = status();

        switch (type) {
            case WALLPAD, VIDEOPHONE, INTERPHONE -> {
                y = callControls(rx, rw, y, st);
                if (st == DeviceLogic.STATUS_IDLE) {
                    button("경비실 호출", rx, y, rw, () -> act("call_guard", null));
                    y += 30;
                    y += 11;
                    numberBox("dong", rx, y, 58, "동");
                    numberBox("ho", rx + rw - 58, y, 58, "호");
                    y += 20;
                    button("세대 호출", rx, y, rw, () -> callWith("call_unit", "dong", "ho"));
                }
                settingsButton("관할경비실·세대번호 설정");
            }
            case LOBBY_PHONE -> {
                y = callControls(rx, rw, y, st);
                if (st == DeviceLogic.STATUS_IDLE) {
                    y += 11;
                    boolean outer = data.getInt("dong") == DeviceLogic.OUTER_LOBBY_DONG;
                    if (outer) numberBox("dong", rx, y, 58, "동");
                    numberBox("ho", rx + rw - 58, y, 58, "호");
                    y += 20;
                    button("세대 호출", rx, y, rw, () -> {
                        if (outer) callWith("call_unit", "dong", "ho");
                        else callWithFixedDong();
                    });
                    y += 26;
                    button("경비실 호출", rx, y, rw, () -> act("call_guard", null));
                }
                settingsButton("관할경비실·동/로비번호 설정");
            }
            case DOOR_CAMERA, DOOR_PHONE -> {
                if (st == DeviceLogic.STATUS_IDLE) {
                    button("호출 (딩동)", rx, y, rw, () -> act("ring", null));
                } else {
                    button("종료", rx, y, rw, () -> act("hangup", null));
                }
                settingsButton("세대번호 설정");
            }
            case GUARD_STATION -> {
                // 들어온 호출 목록의 받기 버튼
                ListTag inc = data.getList("incoming", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(4, inc.size()); i++) {
                    int id = inc.getCompound(i).getInt("id");
                    addRenderableWidget(Button.builder(Component.literal("받기"), b -> {
                        CompoundTag a = new CompoundTag();
                        a.putInt("callId", id);
                        act("answer", a);
                    }).bounds(left + 138, top + 96 + i * 20, 42, 18).build());
                }
                if (st == DeviceLogic.STATUS_CONNECTED || st == DeviceLogic.STATUS_CALLING) {
                    button(st == DeviceLogic.STATUS_CALLING ? "호출 취소" : "통화 종료", rx, y, rw, () -> act("hangup", null));
                    y += 26;
                    if (data.getBoolean("entrance") && st == DeviceLogic.STATUS_CONNECTED) {
                        button("문열림", rx, y, rw, () -> act("open_door", null));
                    }
                } else {
                    y += 11;
                    numberBox("dong", rx, y, 58, "동");
                    numberBox("ho", rx + rw - 58, y, 58, "호");
                    y += 20;
                    button("세대 호출", rx, y, rw, () -> callWith("call_unit", "dong", "ho"));
                    y += 36;
                    numberBox("ldong", rx, y, 58, "동");
                    numberBox("llobby", rx + rw - 58, y, 58, "로비");
                    y += 20;
                    button("로비폰 호출", rx, y, rw, () -> callWith("call_lobby", "ldong", "llobby"));
                }
                settingsButton("경비실번호 설정");
            }
        }
    }

    /** 수신/통화/발신 중일 때의 공통 버튼. 버튼을 놓은 뒤 다음 y 를 돌려준다. */
    private int callControls(int rx, int rw, int y, int st) {
        switch (st) {
            case DeviceLogic.STATUS_RINGING -> {
                int half = (rw - 6) / 2;
                addRenderableWidget(Button.builder(Component.literal("받기").withStyle(ChatFormatting.GREEN),
                        b -> act("answer", null)).bounds(rx, y, half, 20).build());
                addRenderableWidget(Button.builder(Component.literal("거절").withStyle(ChatFormatting.RED),
                        b -> act("hangup", null)).bounds(rx + rw - half, y, half, 20).build());
                y += 26;
                if (data.getBoolean("entrance")) {
                    button("문열림", rx, y, rw, () -> act("open_door", null));
                    y += 26;
                }
            }
            case DeviceLogic.STATUS_CONNECTED -> {
                addRenderableWidget(Button.builder(Component.literal("통화 종료").withStyle(ChatFormatting.RED),
                        b -> act("hangup", null)).bounds(rx, y, rw, 20).build());
                y += 26;
                if (data.getBoolean("entrance")) {
                    button("문열림", rx, y, rw, () -> act("open_door", null));
                    y += 26;
                }
            }
            case DeviceLogic.STATUS_CALLING -> {
                button("호출 취소", rx, y, rw, () -> act("hangup", null));
                y += 26;
            }
            default -> { }
        }
        return y;
    }

    private void button(String text, int x, int y, int w, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> r.run()).bounds(x, y, w, 20).build());
    }

    private void settingsButton(String text) {
        addRenderableWidget(Button.builder(Component.literal("⚙ " + text), b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(left + 8, top + H - 28, W - 16, 20).build());
    }

    private EditBox numberBox(String key, int x, int y, int w, String hint) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.literal(hint));
        box.setMaxLength(4);
        box.setFilter(s -> s.matches("\\d{0,4}"));
        box.setValue(fieldValues.getOrDefault(key, ""));
        box.setResponder(v -> fieldValues.put(key, v));
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(box);
        fields.put(key, box);
        return box;
    }

    private Integer read(String key) {
        String v = fieldValues.getOrDefault(key, "").trim();
        if (v.isEmpty()) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void callWith(String action, String k1, String k2) {
        Integer a = read(k1), b = read(k2);
        if (a == null || b == null) {
            localError = "번호를 모두 입력하세요 (0~9999)";
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("dong", a);
        t.putInt("ho", b);
        act(action, t);
    }

    private void callWithFixedDong() {
        Integer b = read("ho");
        if (b == null) {
            localError = "호 번호를 입력하세요 (0~9999)";
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("dong", data.getInt("dong"));
        t.putInt("ho", b);
        act("call_unit", t);
    }

    void act(String action, CompoundTag args) {
        localError = "";
        ClientHooks.send(pos, action, args);
    }

    @Override
    public void tick() {
        super.tick();
        for (EditBox b : fields.values()) b.tick();
        if (++ticks % 20 == 0) ClientHooks.send(pos, "refresh", null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ 그리기

    private int accent() {
        return switch (type) {
            case WALLPAD -> 0xFF1E4D8C;
            case VIDEOPHONE -> 0xFF2D5F7A;
            case INTERPHONE -> 0xFF4A5A3E;
            case DOOR_CAMERA, DOOR_PHONE -> 0xFF5A4A3E;
            case LOBBY_PHONE -> 0xFF3E4A6A;
            case GUARD_STATION -> 0xFF6A3E3E;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        // 본체
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF3A3F46);
        g.fill(left, top, left + W, top + H, 0xFF1B1F24);
        g.fill(left, top, left + W, top + 20, accent());
        String head = type.korean + "  ·  " + DeviceLogic.label(type, data.getInt("guard"), data.getInt("dong"), data.getInt("ho"));
        g.drawString(font, font.plainSubstrByWidth(head, W - 16), left + 8, top + 6, 0xFFFFFF, false);

        // 왼쪽 표시부
        int lx = left + 8, ly = top + 26, lw = 176, lh = H - 26 - 36;
        g.fill(lx, ly, lx + lw, ly + lh, 0xFF0B1A2E);
        int y = ly + 5;
        String cx = data.getString("complex");
        if (cx.isEmpty()) {
            g.drawString(font, "단지 구획 밖 (대시보드 구획 안에 설치)", lx + 5, y, 0xFF6B6B, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth("단지: " + cx, lw - 10), lx + 5, y, 0x9AA7B8, false);
        }
        y += 11;
        y = drawNumbers(g, lx + 5, y, lw - 10);
        y += 4;

        int st = status();
        if (type == DeviceType.GUARD_STATION) {
            drawGuardList(g, lx, y, lw);
        } else {
            drawStatus(g, lx + 5, y, lw - 10, st);
        }

        // 오른쪽 라벨
        int rx = left + 192;
        drawRightLabels(g, rx, st);

        if (!localError.isEmpty()) {
            g.drawCenteredString(font, localError, left + W / 2, top + H - 40, 0xFF6B6B);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int drawNumbers(GuiGraphics g, int x, int y, int w) {
        int guard = data.getInt("guard"), dong = data.getInt("dong"), ho = data.getInt("ho");
        switch (type.settings) {
            case UNIT -> {
                g.drawString(font, "관할경비실 " + DeviceLogic.num(guard) + "  |  " + DeviceLogic.num(dong) + "동 " + DeviceLogic.num(ho) + "호", x, y, 0xFFFFFF, false);
                y += 11;
                if (data.contains("guards")) {
                    String s = data.getBoolean("myGuardExists") ? "관할 경비실기 연결됨" : "관할 경비실기 없음 (단지 경비실로 연결)";
                    g.drawString(font, font.plainSubstrByWidth(s, w), x, y, data.getBoolean("myGuardExists") ? 0x4CD27A : 0xFFD34E, false);
                    y += 11;
                }
            }
            case LOBBY -> {
                String d = dong == DeviceLogic.OUTER_LOBBY_DONG ? "단지 외곽(9999동)" : DeviceLogic.num(dong) + "동";
                g.drawString(font, "관할경비실 " + DeviceLogic.num(guard) + "  |  " + d + " 로비 " + DeviceLogic.num(ho), x, y, 0xFFFFFF, false);
                y += 11;
                String s = dong == DeviceLogic.OUTER_LOBBY_DONG ? "단지 안 모든 동·호 호출 가능" : "같은 동·같은 관할 세대만 호출";
                g.drawString(font, s, x, y, 0x9AA7B8, false);
                y += 11;
            }
            case GUARD -> {
                g.drawString(font, "경비실번호 " + DeviceLogic.num(guard), x, y, 0xFFFFFF, false);
                y += 11;
            }
            case DOOR -> {
                g.drawString(font, "연결 세대 " + DeviceLogic.num(dong) + "동 " + DeviceLogic.num(ho) + "호", x, y, 0xFFFFFF, false);
                y += 11;
                String s = type == DeviceType.DOOR_CAMERA ? "영상 + 음성 송출" : "음성만 송출";
                g.drawString(font, s, x, y, 0x9AA7B8, false);
                y += 11;
            }
        }
        return y;
    }

    private void drawStatus(GuiGraphics g, int x, int y, int w, int st) {
        String text = switch (st) {
            case DeviceLogic.STATUS_RINGING -> "● 호출 수신";
            case DeviceLogic.STATUS_CALLING -> "◌ 호출 중...";
            case DeviceLogic.STATUS_CONNECTED -> "● 통화 중";
            default -> "대기 중";
        };
        int color = switch (st) {
            case DeviceLogic.STATUS_RINGING -> 0xFFD34E;
            case DeviceLogic.STATUS_CONNECTED -> 0x4CD27A;
            case DeviceLogic.STATUS_CALLING -> 0x7FB2FF;
            default -> 0x9AA7B8;
        };
        g.drawString(font, text, x, y, color, false);
        y += 11;
        String peer = data.getString("peer");
        if (!peer.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(peer, w), x, y, 0xFFFFFF, false);
            y += 11;
        }
        // 영상
        boolean video = data.getBoolean("video") && st != DeviceLogic.STATUS_CALLING && st != DeviceLogic.STATUS_IDLE;
        if (video && type.videoDisplay && data.hasUUID("visitorId")) {
            drawFace(g, data.getUUID("visitorId"), x, y + 2, 40);
            g.drawString(font, "영상: " + data.getString("visitor"), x + 46, y + 6, 0xFF8A8A, false);
            g.drawString(font, "카메라 앞 방문자", x + 46, y + 18, 0x9AA7B8, false);
            y += 46;
        } else if (st == DeviceLogic.STATUS_RINGING || st == DeviceLogic.STATUS_CONNECTED) {
            String s = data.getBoolean("video") && !type.videoDisplay ? "음성 전용 기기 (영상 표시 불가)" : "음성 통화";
            g.drawString(font, s, x, y, 0x9AA7B8, false);
            y += 12;
        }
        if (st == DeviceLogic.STATUS_CONNECTED) {
            g.drawString(font, "근처에서 채팅하면 상대에게 전달됩니다", x, y, 0x7FB2FF, false);
            y += 12;
        }
        // 통화 기록
        if (type.internal && st == DeviceLogic.STATUS_IDLE) {
            ListTag log = data.getList("log", Tag.TAG_STRING);
            if (!log.isEmpty()) {
                g.drawString(font, "통화 기록", x, y, 0xFFD34E, false);
                y += 11;
                int maxLines = type == DeviceType.WALLPAD ? 5 : 3;
                for (int i = 0; i < Math.min(maxLines, log.size()); i++) {
                    g.drawString(font, font.plainSubstrByWidth(log.getString(i), w), x, y, 0xC8D0DA, false);
                    y += 10;
                }
            }
        }
    }

    private void drawGuardList(GuiGraphics g, int lx, int y, int lw) {
        int st = status();
        int x = lx + 5;
        if (st == DeviceLogic.STATUS_CONNECTED || st == DeviceLogic.STATUS_CALLING) {
            drawStatus(g, x, y, lw - 10, st);
            return;
        }
        ListTag inc = data.getList("incoming", Tag.TAG_COMPOUND);
        g.drawString(font, inc.isEmpty() ? "들어온 호출 없음" : "들어온 호출 " + inc.size() + "건", x, top + 84, inc.isEmpty() ? 0x9AA7B8 : 0xFFD34E, false);
        for (int i = 0; i < Math.min(4, inc.size()); i++) {
            CompoundTag c = inc.getCompound(i);
            String s = (c.getBoolean("video") ? "[영상] " : "") + c.getString("label");
            g.drawString(font, font.plainSubstrByWidth(s, 124), x, top + 101 + i * 20, 0xFFFFFF, false);
        }
    }

    private void drawRightLabels(GuiGraphics g, int rx, int st) {
        if (st != DeviceLogic.STATUS_IDLE && !(type == DeviceType.GUARD_STATION && st == DeviceLogic.STATUS_RINGING)) return;
        int y = top + 28;
        switch (type) {
            case WALLPAD, VIDEOPHONE, INTERPHONE -> g.drawString(font, "세대 호출 (동 / 호)", rx, y + 30, 0x9AA7B8, false);
            case LOBBY_PHONE -> {
                if (data.getInt("dong") == DeviceLogic.OUTER_LOBBY_DONG) {
                    g.drawString(font, "호출할 동 / 호", rx, y, 0x9AA7B8, false);
                } else {
                    g.drawString(font, DeviceLogic.num(data.getInt("dong")) + "동  /  호 입력", rx, y, 0x9AA7B8, false);
                }
            }
            case GUARD_STATION -> {
                g.drawString(font, "세대 호출 (동 / 호)", rx, y, 0x9AA7B8, false);
                g.drawString(font, "로비폰 호출 (동 / 로비)", rx, y + 56, 0x9AA7B8, false);
            }
            default -> { }
        }
    }

    private void drawFace(GuiGraphics g, UUID id, int x, int y, int size) {
        ResourceLocation skin = ClientHooks.skin(id);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        RenderSystem.enableBlend();
        g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }
}
