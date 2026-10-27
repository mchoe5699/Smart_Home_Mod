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
        if (!type.canDial() && c != '\n') return;
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
        body(g, tex("interphone"));
        int hx = x + 10, hw = 44;
        if (!idle()) icon(g, I_PHONE, hx + hw / 2 - 8, y + h / 2 - 8, 16, 0x2E9E4F);

        int rx = x + 66, rw = w - 76;
        // LCD
        int ly = y + 34;
                int st = status();
        boolean blink = st == DeviceLogic.STATUS_RINGING && (ticks / 8) % 2 == 0;
        g.fill(rx, ly, rx + rw, ly + 40, blink ? 0xFFE8D27A : 0xFF9DB596);
        int ink = 0xFF1C2A1C;
        if (st == DeviceLogic.STATUS_IDLE) {
            g.drawString(font, DeviceLogic.num(data.getInt("dong")) + "-" + DeviceLogic.num(data.getInt("ho")) + "  " + clock(), rx + 4, ly + 4, ink, false);
            g.drawString(font, !type.canDial() ? "경비실 호출 가능" : input.isEmpty() ? "동*호 → 통화" : input, rx + 4, ly + 22, ink, false);
        } else {
            g.drawString(font, statusText(), rx + 4, ly + 4, ink, false);
            fitText(g, data.getString("peer"), rx + 4, ly + 22, rw - 8, ink);
        }

        // 키패드 (단순형은 다이얼 없음)
        int fy;
        if (type.canDial()) {
            keypad(g, rx + 4, y + 86, 36, 22, 5, 0xFFD8D2C0, 0xFF30343A, mx, my);
            fy = y + 86 + 4 * 27 + 6;
        } else {
            fy = y + 96;
            centered(g, "다이얼 없음", rx + rw / 2, y + 80, 0xFF9C9686);
            centered(g, "세대 통화는 경비실 교환", rx + rw / 2, y + h - 40, 0xFF9C9686);
        }
        int fw = (rw - 6) / 2;
        buttonIcon(g, rx, fy, fw, 24, I_GUARD, "경비실", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> act("call_guard"));
        buttonIcon(g, rx + fw + 6, fy, fw, 24, I_CALL, "통화", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, this::talk);
        buttonIcon(g, rx, fy + 30, fw, 24, I_DOOR, "문열림", data.getBoolean("entrance") ? 0xFF2E6DB4 : 0xFFB8B2A0, 0xFFFFFFFF, mx, my, () -> act("open_door"));
        buttonIcon(g, rx + fw + 6, fy + 30, fw, 24, I_HANGUP, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> {
            if (!idle()) act("hangup");
            input = "";
        });
        g.drawString(font, "음성 전용", rx, y + h - 14, 0xFF9C9686, false);
        buttonIcon(g, x + w - 20, y + h - 18, 16, 14, I_GEAR, "", 0xFFD8D2C0, 0xFF30343A, mx, my, this::openSettings);
    }
}
