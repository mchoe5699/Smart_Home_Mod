package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 로비폰 (공동현관 출입 통제기).
 * 스테인리스 패널 · 상단 카메라 · LCD · 3x4 키패드 · [호출][경비실][취소][열쇠] · RF 카드 리더.
 */
public class LobbyGui extends DeviceGui {
    private static final int STEEL = 0xFFA9AEB4;
    private String input = "";
    private boolean pwMode;

    public LobbyGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 190, 312);
    }

    private boolean outer() {
        return data.getInt("dong") == DeviceLogic.OUTER_LOBBY_DONG;
    }

    @Override
    protected void onKey(char c) {
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            if (pwMode) submitPassword();
            else if (c == '\n') pressCall();
        } else if (input.length() < 16) {
            input += c;
        }
    }

    private void submitPassword() {
        CompoundTag t = new CompoundTag();
        t.putString("input", input);
        act("password", t);
        input = "";
        pwMode = false;
    }

    private void pressCall() {
        if (status() == DeviceLogic.STATUS_RINGING) {
            act("answer");
            return;
        }
        if (pwMode) {
            submitPassword();
            return;
        }
        if (input.isEmpty()) return;
        callPair("call_unit", input, outer() ? -1 : data.getInt("dong"));
        input = "";
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        // 본체 (헤어라인 스테인리스)
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF4A4E54);
        for (int i = 0; i < h; i++) {
            int shade = (i % 3 == 0) ? 6 : (i % 5 == 0 ? -5 : 0);
            g.fill(x, y + i, x + w, y + i + 1, brighten(darken(STEEL, 0), shade));
        }
        // 상단 차양 + 카메라
        g.fill(x, y, x + w, y + 12, 0xFF6C7076);
        g.fill(x + w / 2 - 14, y + 14, x + w / 2 + 14, y + 34, 0xFF2A2E34);
        circle(g, x + w / 2, y + 24, 8, 0xFF111214);
        circle(g, x + w / 2, y + 24, 4, 0xFF1E2B4A);
        g.fill(x + w / 2 - 2, y + 22, x + w / 2, y + 24, 0xFFA0C0FF);
        for (int i = 0; i < 4; i++) circle(g, x + w / 2 - 22 + i * 44 / 3, y + 24, 1, 0xFF7A1F1F);
        // 스피커
        for (int i = 0; i < 9; i++) g.fill(x + w / 2 - 40 + i * 10, y + 38, x + w / 2 - 34 + i * 10, y + 40, 0xFF5A5E64);

        // LCD
        int lx = x + 14, ly = y + 46, lw = w - 28, lh = 84;
        g.fill(lx - 2, ly - 2, lx + lw + 2, ly + lh + 2, 0xFF2A2E34);
        g.fill(lx, ly, lx + lw, ly + lh, 0xFF0B1A2E);
        drawLcd(g, lx, ly, lw, lh);

        // 키패드 + 기능키
        int ky = y + 142;
        keypad(g, x + 14, ky, 36, 26, 6, 0xFF2A2E34, 0xFFE8ECF0, mx, my);
        int fx = x + 14 + 3 * 42 + 4, fw = w - (fx - x) - 14;
        button(g, fx, ky, fw, 26, "호출", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, this::pressCall);
        button(g, fx, ky + 32, fw, 26, "경비실", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> act("call_guard"));
        button(g, fx, ky + 64, fw, 26, "취소", 0xFFC98A2E, 0xFFFFFFFF, mx, my, () -> {
            if (!idle()) act("hangup");
            else if (!input.isEmpty()) input = "";
            else pwMode = false;
        });
        button(g, fx, ky + 96, fw, 26, pwMode ? "열쇠●" : "열쇠", 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            pwMode = !pwMode;
            input = "";
        });

        // RF 카드 리더
        int ry = y + h - 46;
        g.fill(x + 14, ry, x + w - 14, ry + 34, 0xFF2A2E34);
        g.fill(x + 15, ry + 1, x + w - 15, ry + 33, 0xFF1A1D22);
        for (int i = 0; i < 3; i++) {
            int r = 4 + i * 4;
            g.fill(x + 30 + r, ry + 17 - r, x + 31 + r, ry + 17 + r, 0xFF4C9AE5);
        }
        g.drawString(font, "RF 카드", x + 52, ry + 6, 0xFF9AD0FF, false);
        g.drawString(font, "출입카드를 들고 우클릭", x + 52, ry + 19, 0xFF707880, false);

        // 관리(설정)
        button(g, x + w - 24, y + 1, 22, 10, "⚙", 0xFF6C7076, 0xFFFFFFFF, mx, my, this::openSettings);
    }

    private void drawLcd(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 12, 0xFF1E4D8C);
        String title = outer() ? "단지 출입구 " + DeviceLogic.num(data.getInt("ho"))
                : DeviceLogic.num(data.getInt("dong")) + "동 로비 " + DeviceLogic.num(data.getInt("ho"));
        g.drawString(font, title, x + 4, y + 2, 0xFFFFFF, false);
        g.drawString(font, clock(), x + w - 4 - font.width(clock()), y + 2, 0xFFFFFF, false);
        int cx = x + w / 2;
        if (data.getString("complex").isEmpty()) {
            centered(g, "단지 구획 밖", cx, y + 30, 0xFF6B6B);
            centered(g, "대시보드에서 구획 설정", cx, y + 44, 0x9AA7B8);
            return;
        }
        if (data.getBoolean("doorOpen")) {
            g.fill(x + 4, y + 18, x + w - 4, y + h - 4, 0xFF0F3D24);
            centered(g, "문이 열렸습니다", cx, y + 42, 0x4CD27A);
            return;
        }
        int st = status();
        if (st != DeviceLogic.STATUS_IDLE) {
            int c = st == DeviceLogic.STATUS_CONNECTED ? 0x4CD27A : ((ticks / 8) % 2 == 0 ? 0xFFD34E : 0xFFFFFF);
            centered(g, st == DeviceLogic.STATUS_RINGING ? "경비실 호출 수신" : statusText(), cx, y + 24, c);
            centered(g, font.plainSubstrByWidth(data.getString("peer"), w - 8), cx, y + 38, 0xFFFFFF);
            String hint = st == DeviceLogic.STATUS_RINGING ? "[호출]로 받기" : st == DeviceLogic.STATUS_CONNECTED ? "오른쪽 창에서 대화" : "[취소]로 끊기";
            centered(g, hint, cx, y + 64, 0x9AA7B8);
            return;
        }
        if (pwMode) {
            centered(g, "비밀번호 입력", cx, y + 18, 0xFFD34E);
            String masked = input.replaceAll("[0-9]", "●");
            centered(g, masked.isEmpty() ? "_" : masked, cx, y + 36, 0xFFFFFF);
            centered(g, "공용: 번호 → #", cx, y + 56, 0x9AA7B8);
            centered(g, outer() ? "세대: 동*호*번호 → #" : "세대: 호*번호 → #", cx, y + 68, 0x9AA7B8);
            return;
        }
        centered(g, outer() ? "동*호 입력 후 [호출]" : "호수 입력 후 [호출]", cx, y + 18, 0xFFFFFF);
        String shown = input.isEmpty() ? "_" : (outer() ? input.replace("*", "동 ") + (input.contains("*") ? "호" : "") : input + "호");
        centered(g, shown, cx, y + 36, 0x9AD0FF);
        centered(g, "[열쇠] 비밀번호", cx, y + 56, 0x9AA7B8);
        centered(g, "[경비실] 경비실 호출", cx, y + 68, 0x9AA7B8);
    }
}
