package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 인터폰: 벽걸이 수화기형 음성 전용 기기.
 * 왼쪽 수화기 · 작은 LCD · 키패드 · [경비실][통화][문열림][종료].
 */
public class InterphoneGui extends DeviceGui {
    private static final int IVORY = 0xFFEDE7D6;
    private String input = "";

    public InterphoneGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 200, 300);
    }

    @Override
    protected void onKey(char c) {
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            talk();
        } else if (input.length() < 10) {
            input += c;
        }
    }

    private void talk() {
        if (status() == DeviceLogic.STATUS_RINGING) act("answer");
        else if (idle() && !input.isEmpty()) {
            callPair("call_unit", input, data.getInt("dong"));
            input = "";
        }
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9C9686);
        g.fill(x, y, x + w, y + h, IVORY);

        // 수화기
        int hx = x + 10, hw = 44;
        g.fill(hx, y + 14, hx + hw, y + h - 14, 0xFFD8D2C0);
        g.fill(hx + 6, y + 30, hx + hw - 6, y + h - 30, 0xFFE4DECC);
        g.fill(hx - 2, y + 10, hx + hw + 2, y + 50, 0xFFCFC8B4);
        g.fill(hx - 2, y + h - 50, hx + hw + 2, y + h - 10, 0xFFCFC8B4);
        for (int i = 0; i < 4; i++) g.fill(hx + 10, y + 18 + i * 7, hx + hw - 10, y + 20 + i * 7, 0xFF9C9686);
        for (int i = 0; i < 4; i++) g.fill(hx + 10, y + h - 44 + i * 7, hx + hw - 10, y + h - 42 + i * 7, 0xFF9C9686);
        if (!idle()) g.drawString(font, "통화", hx + 10, y + h / 2 - 4, 0xFF2E9E4F, false);

        int rx = x + 66, rw = w - 76;
        // 스피커 슬롯
        for (int i = 0; i < 8; i++) g.fill(rx + 8 + i * 14, y + 14, rx + 12 + i * 14, y + 26, 0xFFB8B2A0);
        // LCD
        int ly = y + 34;
        g.fill(rx - 1, ly - 1, rx + rw + 1, ly + 41, 0xFF5A6050);
        int st = status();
        boolean blink = st == DeviceLogic.STATUS_RINGING && (ticks / 8) % 2 == 0;
        g.fill(rx, ly, rx + rw, ly + 40, blink ? 0xFFE8D27A : 0xFF9DB596);
        int ink = 0xFF1C2A1C;
        if (st == DeviceLogic.STATUS_IDLE) {
            g.drawString(font, DeviceLogic.num(data.getInt("dong")) + "-" + DeviceLogic.num(data.getInt("ho")) + "  " + clock(), rx + 4, ly + 4, ink, false);
            g.drawString(font, input.isEmpty() ? "동*호 → 통화" : input, rx + 4, ly + 22, ink, false);
        } else {
            g.drawString(font, statusText(), rx + 4, ly + 4, ink, false);
            fitText(g, data.getString("peer"), rx + 4, ly + 22, rw - 8, ink);
        }

        // 키패드
        keypad(g, rx + 4, y + 86, 36, 22, 5, 0xFFD8D2C0, 0xFF30343A, mx, my);
        // 기능 버튼
        int fy = y + 86 + 4 * 27 + 6, fw = (rw - 6) / 2;
        button(g, rx, fy, fw, 24, "경비실", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> act("call_guard"));
        button(g, rx + fw + 6, fy, fw, 24, "통화", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, this::talk);
        button(g, rx, fy + 30, fw, 24, "문열림", data.getBoolean("entrance") ? 0xFF2E6DB4 : 0xFFB8B2A0, 0xFFFFFFFF, mx, my, () -> act("open_door"));
        button(g, rx + fw + 6, fy + 30, fw, 24, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> {
            if (!idle()) act("hangup");
            input = "";
        });
        g.drawString(font, "음성 전용", rx, y + h - 14, 0xFF9C9686, false);
        button(g, x + w - 20, y + h - 18, 16, 14, "⚙", 0xFFD8D2C0, 0xFF30343A, mx, my, this::openSettings);
    }
}
