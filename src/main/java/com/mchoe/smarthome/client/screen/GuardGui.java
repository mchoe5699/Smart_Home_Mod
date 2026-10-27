package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 경비실기 (책상형 관제 콘솔).
 * 모니터(호출 영상 · CCTV) · 호출 대기 목록 · 키패드 · [세대][로비][통화][종료][문열림][CCTV] · 수화기.
 */
public class GuardGui extends DeviceGui {
    private String input = "";
    private int cctv = -1;

    public GuardGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 430, 270);
    }

    @Override
    protected void onKey(char c) {
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n') {
            callPair("call_unit", input, -1);
            input = "";
        } else if (input.length() < 10) {
            input += c;
        }
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A2E34);
        g.fill(x, y, x + w, y + h, 0xFF55595F);
        g.fill(x, y + 172, x + w, y + h, 0xFF4A4E54);
        g.drawString(font, "경비실 " + DeviceLogic.num(data.getInt("guard")), x + 8, y + 4, 0xFFFFFF, false);
        String cx = data.getString("complex");
        g.drawString(font, cx.isEmpty() ? "단지 구획 밖" : cx, x + 90, y + 4, cx.isEmpty() ? 0xFF6B6B : 0x9AD0FF, false);
        g.drawString(font, clock(), x + w - 8 - font.width(clock()), y + 4, 0xFFFFFF, false);

        // 모니터
        int mxp = x + 8, myp = y + 16, mw = 250, mh = 150;
        g.fill(mxp - 3, myp - 3, mxp + mw + 3, myp + mh + 3, 0xFF15171A);
        g.fill(mxp, myp, mxp + mw, myp + mh, 0xFF050607);
        drawMonitor(g, mxp, myp, mw, mh);

        // 호출 대기 목록
        int lx = x + 268, ly = y + 16, lw = w - 276;
        g.fill(lx, ly, lx + lw, ly + 150, 0xFF0B1A2E);
        g.fill(lx, ly, lx + lw, ly + 14, 0xFF6A3E3E);
        ListTag inc = data.getList("incoming", Tag.TAG_COMPOUND);
        g.drawString(font, "호출 대기 " + inc.size() + "건", lx + 4, ly + 3, 0xFFFFFF, false);
        for (int i = 0; i < Math.min(5, inc.size()); i++) {
            CompoundTag c = inc.getCompound(i);
            int ry = ly + 18 + i * 26;
            boolean blink = (ticks / 8) % 2 == 0;
            g.fill(lx + 2, ry, lx + lw - 2, ry + 24, blink ? 0xFF3A1520 : 0xFF2A1A20);
            fitText(g, (c.getBoolean("video") ? "▶ " : "") + c.getString("label"), lx + 5, ry + 3, lw - 52, 0xFFFFFF);
            int id = c.getInt("id");
            button(g, lx + lw - 44, ry + 3, 40, 18, "응답", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> {
                CompoundTag a = new CompoundTag();
                a.putInt("callId", id);
                act("answer", a);
            });
        }
        if (inc.isEmpty()) g.drawString(font, "대기 중인 호출 없음", lx + 5, ly + 22, 0x707880, false);

        // 하단 데크: 수화기 · 키패드 · 입력창 · 기능키
        int dy = y + 178;
        g.fill(x + 8, dy, x + 40, y + h - 8, 0xFF22252A);
        g.fill(x + 12, dy + 4, x + 36, dy + 16, 0xFF15171A);
        g.fill(x + 12, y + h - 24, x + 36, y + h - 12, 0xFF15171A);
        keypad(g, x + 48, dy, 30, 18, 3, 0xFFC8CCD2, 0xFF22252A, mx, my);
        int ix = x + 156;
        g.fill(ix, dy, ix + 120, dy + 20, 0xFF050607);
        String shown = input.isEmpty() ? "동*호 / 동*로비" : input;
        g.drawString(font, shown, ix + 4, dy + 6, input.isEmpty() ? 0x707880 : 0x9AD0FF, false);
        button(g, ix + 124, dy, 30, 20, "←", 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> onKey('<'));

        int fy = dy + 26, fw = 58, fg = 6;
        button(g, ix, fy, fw, 24, "세대", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> {
            callPair("call_unit", input, -1);
            input = "";
        });
        button(g, ix + (fw + fg), fy, fw, 24, "로비", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> {
            callPair("call_lobby", input, -1);
            input = "";
        });
        button(g, ix + 2 * (fw + fg), fy, fw, 24, "통화", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> act("answer"));
        button(g, ix + 3 * (fw + fg), fy, fw, 24, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
        button(g, ix, fy + 30, fw, 24, "문열림", data.getBoolean("entrance") || status() == DeviceLogic.STATUS_CONNECTED ? 0xFF2E6DB4 : 0xFF6C7076,
                0xFFFFFFFF, mx, my, () -> act("open_door"));
        ListTag cams = data.getList("cctv", Tag.TAG_COMPOUND);
        button(g, ix + (fw + fg), fy + 30, fw, 24, cctv >= 0 ? "CCTV " + (cctv + 1) : "CCTV", cctv >= 0 ? 0xFF7A5AD0 : 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            cctv = cams.isEmpty() ? -1 : (cctv + 1 >= cams.size() ? -1 : cctv + 1);
        });
        button(g, ix + 2 * (fw + fg), fy + 30, fw * 2 + fg, 24, "경비실번호 설정", 0xFF6C7076, 0xFFFFFFFF, mx, my, this::openSettings);
    }

    private void drawMonitor(GuiGraphics g, int x, int y, int w, int h) {
        int st = status();
        if (st != DeviceLogic.STATUS_IDLE && status() != DeviceLogic.STATUS_RINGING) {
            if (!drawFeed(g, camPos(), x, y, w, h)) {
                g.fill(x, y, x + w, y + h, 0xFF101820);
                centered(g, "음성 통화", x + w / 2, y + h / 2 - 10, 0x9AA7B8);
            }
            g.fill(x, y + h - 14, x + w, y + h, 0xC0000000);
            fitText(g, "● " + statusText() + "  " + data.getString("peer"), x + 4, y + h - 11, w - 8,
                    st == DeviceLogic.STATUS_CONNECTED ? 0x4CD27A : 0xFFD34E);
            return;
        }
        ListTag cams = data.getList("cctv", Tag.TAG_COMPOUND);
        if (st == DeviceLogic.STATUS_RINGING && camPos() != null) {
            drawFeed(g, camPos(), x, y, w, h);
            g.fill(x, y + h - 14, x + w, y + h, 0xC0000000);
            fitText(g, "● 호출 수신  " + data.getString("peer"), x + 4, y + h - 11, w - 8, (ticks / 8) % 2 == 0 ? 0xFFD34E : 0xFFFFFF);
            return;
        }
        if (cctv >= 0 && cctv < cams.size()) {
            CompoundTag c = cams.getCompound(cctv);
            drawFeed(g, BlockPos.of(c.getLong("pos")), x, y, w, h);
            g.drawString(font, "CCTV " + (cctv + 1) + "/" + cams.size() + "  " + c.getString("label"), x + 4, y + 4, 0xFFFFFF, true);
            return;
        }
        centered(g, "경비실 관제", x + w / 2, y + 40, 0xFFFFFF);
        centered(g, "로비폰 카메라 " + cams.size() + "대", x + w / 2, y + 60, 0x9AA7B8);
        centered(g, "[CCTV]로 공동현관 영상 보기", x + w / 2, y + 80, 0x707880);
        if (st == DeviceLogic.STATUS_RINGING) centered(g, "호출이 들어왔습니다 →", x + w / 2, y + 110, 0xFFD34E);
    }
}
