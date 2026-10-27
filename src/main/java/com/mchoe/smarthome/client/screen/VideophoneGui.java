package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 비디오폰: 흰색 본체, 왼쪽 컬러 화면, 오른쪽 물리 버튼
 * [모니터][통화][문열림][경비실][인터폰][종료].
 */
public class VideophoneGui extends DeviceGui {
    private static final int BODY = 0xFFE6E6E2;
    private boolean monitor;
    private boolean intercom;
    private String input = "";

    public VideophoneGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 330, 214);
    }

    @Override
    protected void onDataChanged() {
        if (!idle()) {
            monitor = false;
            intercom = false;
        }
    }

    @Override
    protected void onKey(char c) {
        if (!intercom) return;
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            talk();
        } else if (input.length() < 10) {
            input += c;
        }
    }

    private void talk() {
        if (status() == DeviceLogic.STATUS_RINGING) {
            act("answer");
        } else if (intercom && !input.isEmpty()) {
            callPair("call_unit", input, data.getInt("dong"));
            input = "";
            intercom = false;
        }
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9A9E9F);
        g.fill(x, y, x + w, y + h, BODY);
        g.fill(x, y, x + w, y + 2, 0xFFF4F4F2);
        g.fill(x, y + h - 2, x + w, y + h, 0xFFC8C8C4);

        // 화면
        int sx = x + 14, sy = y + 14, sw = 222, sh = 166;
        g.fill(sx - 3, sy - 3, sx + sw + 3, sy + sh + 3, 0xFF9A9E9F);
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF050607);
        drawScreen(g, sx, sy, sw, sh, mx, my);

        // 스피커 · 상표
        for (int i = 0; i < 14; i++) g.fill(sx + 40 + i * 10, y + h - 20, sx + 46 + i * 10, y + h - 18, 0xFF8A8E90);
        g.drawString(font, "VIDEO PHONE", sx, y + h - 14, 0xFF9A9E9F, false);

        // 오른쪽 버튼
        int bx = x + w - 80, bw = 66, by = y + 14, bh = 24, gap = 6;
        int st = status();
        button(g, bx, by, bw, bh, "모니터", monitor ? 0xFF2E7DD7 : 0xFFCFCFCB, monitor ? 0xFFFFFFFF : 0xFF30343A, mx, my, () -> {
            if (idle()) {
                monitor = !monitor;
                intercom = false;
            }
        });
        by += bh + gap;
        button(g, bx, by, bw, bh, "통화", st == DeviceLogic.STATUS_RINGING ? 0xFF2E9E4F : 0xFFCFCFCB,
                st == DeviceLogic.STATUS_RINGING ? 0xFFFFFFFF : 0xFF30343A, mx, my, this::talk);
        by += bh + gap;
        button(g, bx, by, bw, bh, "문열림", data.getBoolean("entrance") ? 0xFF2E6DB4 : 0xFFCFCFCB,
                data.getBoolean("entrance") ? 0xFFFFFFFF : 0xFF30343A, mx, my, () -> act("open_door"));
        by += bh + gap;
        button(g, bx, by, bw, bh, "경비실", 0xFFCFCFCB, 0xFF30343A, mx, my, () -> act("call_guard"));
        by += bh + gap;
        button(g, bx, by, bw, bh, "인터폰", intercom ? 0xFF2E9E4F : 0xFFCFCFCB, intercom ? 0xFFFFFFFF : 0xFF30343A, mx, my, () -> {
            if (idle()) {
                intercom = !intercom;
                monitor = false;
                input = "";
            }
        });
        by += bh + gap;
        button(g, bx, by, bw, bh, "종료", 0xFFCFCFCB, 0xFFB43A3A, mx, my, () -> {
            if (!idle()) act("hangup");
            monitor = false;
            intercom = false;
        });
        button(g, x + w - 18, y + h - 16, 14, 12, "⚙", 0xFFCFCFCB, 0xFF30343A, mx, my, this::openSettings);
    }

    private void drawScreen(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int st = status();
        if (st != DeviceLogic.STATUS_IDLE) {
            if (!drawFeed(g, camPos(), x, y, w, h)) {
                g.fill(x, y, x + w, y + h, 0xFF101820);
                centered(g, "음성 통화", x + w / 2, y + h / 2 - 12, 0x9AA7B8);
            }
            g.fill(x, y + h - 14, x + w, y + h, 0xC0000000);
            int c = st == DeviceLogic.STATUS_CONNECTED ? 0x4CD27A : ((ticks / 8) % 2 == 0 ? 0xFFD34E : 0xFFFFFF);
            fitText(g, "● " + statusText() + "  " + data.getString("peer"), x + 4, y + h - 11, w - 8, c);
            return;
        }
        if (monitor) {
            BlockPos cam = data.contains("doorCam") ? BlockPos.of(data.getLong("doorCam")) : null;
            if (!drawFeed(g, cam, x, y, w, h)) centered(g, "현관 도어카메라가 없습니다", x + w / 2, y + h / 2 - 4, 0x9AA7B8);
            g.drawString(font, "모니터 · 현관", x + 4, y + 4, 0xFFFFFF, true);
            return;
        }
        if (intercom) {
            g.drawString(font, "인터폰 (세대 통화)", x + 6, y + 6, 0xFFFFFF, false);
            g.fill(x + 6, y + 18, x + w - 6, y + 34, 0xFF101820);
            String shown = input.isEmpty() ? "동*호 입력 후 [통화]" : input.replace("*", "동 ") + "호";
            g.drawString(font, shown, x + 10, y + 22, input.isEmpty() ? 0x707880 : 0x9AD0FF, false);
            keypad(g, x + 30, y + 40, 50, 26, 4, 0xFF1E3A5A, 0xFFFFFFFF, mx, my);
            return;
        }
        // 대기: 화면 꺼짐
        g.drawString(font, clock(), x + w - 6 - font.width(clock()), y + h - 12, 0xFF3A4654, false);
        if (data.getString("complex").isEmpty()) centered(g, "단지 구획 밖", x + w / 2, y + h / 2, 0xFF6B6B);
    }
}
