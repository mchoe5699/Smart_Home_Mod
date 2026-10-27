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
        body(g, tex("guard"));
        icon(g, I_GUARD, x + 8, y + 3, 10, 0xFFFFFF);
        g.drawString(font, "경비실 " + DeviceLogic.num(data.getInt("guard")), x + 21, y + 4, 0xFFFFFF, false);
        String cx = data.getString("complex");
        g.drawString(font, cx.isEmpty() ? "단지 구획 밖" : cx, x + 90, y + 4, cx.isEmpty() ? 0xFF6B6B : 0x9AD0FF, false);
        g.drawString(font, clock(), x + w - 8 - font.width(clock()), y + 4, 0xFFFFFF, false);

        // 모니터
        int mxp = x + 8, myp = y + 16, mw = 250, mh = 150;
        drawMonitor(g, mxp, myp, mw, mh);

        // 호출 대기 목록
        int lx = x + 268, ly = y + 16, lw = w - 276;
        g.fillGradient(lx, ly, lx + lw, ly + 14, 0xFF8A4E4E, 0xFF6A3E3E);
        ListTag inc = data.getList("incoming", Tag.TAG_COMPOUND);
        g.drawString(font, "호출 대기 " + inc.size() + "건", lx + 4, ly + 3, 0xFFFFFF, false);
        for (int i = 0; i < Math.min(5, inc.size()); i++) {
            CompoundTag c = inc.getCompound(i);
            int ry = ly + 18 + i * 26;
            boolean blink = (ticks / 8) % 2 == 0;
            g.fill(lx + 2, ry, lx + lw - 2, ry + 24, blink ? 0xFF3A1520 : 0xFF2A1A20);
            fitText(g, (c.getBoolean("video") ? "▶ " : "") + c.getString("label"), lx + 5, ry + 3, lw - 52, 0xFFFFFF);
            int id = c.getInt("id");
            buttonIcon(g, lx + lw - 44, ry + 3, 40, 18, I_CALL, "응답", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> {
                CompoundTag a = new CompoundTag();
                a.putInt("callId", id);
                act("answer", a);
            });
        }
        if (inc.isEmpty()) g.drawString(font, "대기 중인 호출 없음", lx + 5, ly + 22, 0x707880, false);

        // 하단 데크: 수화기 · 키패드 · 입력창 · 기능키
        int dy = y + 178;
        keypad(g, x + 48, dy, 30, 18, 3, 0xFFC8CCD2, 0xFF22252A, mx, my);
        int ix = x + 156;
        g.fill(ix, dy, ix + 120, dy + 20, 0xFF050607);
        String hint = data.getInt("exch") == 0 && status() == DeviceLogic.STATUS_CONNECTED ? "교환: 동*호 → [교환]"
                : data.getBoolean("away") ? "동*호 / 동*로비" : "동*호 / 동*로비 / 우회번호";
        String shown = input.isEmpty() ? hint : input;
        g.drawString(font, shown, ix + 4, dy + 6, input.isEmpty() ? 0x707880 : 0x9AD0FF, false);
        button(g, ix + 124, dy, 30, 20, "←", 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> onKey('<'));

        int fy = dy + 26, fw = 58, fg = 6;
        buttonIcon(g, ix, fy, fw, 24, I_BUILDING, "세대", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> {
            callPair("call_unit", input, -1);
            input = "";
        });
        buttonIcon(g, ix + (fw + fg), fy, fw, 24, I_CCTV, "로비", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> {
            callPair("call_lobby", input, -1);
            input = "";
        });
        buttonIcon(g, ix + 2 * (fw + fg), fy, fw, 24, I_CALL, "통화", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> act("answer"));
        buttonIcon(g, ix + 3 * (fw + fg), fy, fw, 24, I_HANGUP, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
        buttonIcon(g, ix, fy + 30, fw, 24, I_DOOR, "문열림", data.getBoolean("entrance") || status() == DeviceLogic.STATUS_CONNECTED ? 0xFF2E6DB4 : 0xFF6C7076,
                0xFFFFFFFF, mx, my, () -> act("open_door"));
        ListTag cams = data.getList("cctv", Tag.TAG_COMPOUND);
        buttonIcon(g, ix + (fw + fg), fy + 30, fw, 24, I_CCTV, cctv >= 0 ? "" + (cctv + 1) : "CCTV", cctv >= 0 ? 0xFF7A5AD0 : 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            cctv = cams.isEmpty() ? -1 : (cctv + 1 >= cams.size() ? -1 : cctv + 1);
        });
        int exch = data.getInt("exch");
        buttonIcon(g, ix + 2 * (fw + fg), fy + 30, fw, 24, I_PHONE, exch == 1 ? "취소" : "교환", exch > 0 ? 0xFFC98A2E : 0xFF2E7DD7, 0xFFFFFFFF, mx, my, this::pressExchange);
        boolean away = data.getBoolean("away");
        buttonIcon(g, ix + 3 * (fw + fg), fy + 30, fw, 24, I_AWAY, away ? "재실" : "외출", away ? 0xFFC98A2E : 0xFF4C5866, 0xFFFFFFFF, mx, my, this::pressAway);
        // 설정
        int gx = x + w - 8 - font.width(clock()) - 26;
        buttonIcon(g, gx, y + 2, 20, 11, I_GEAR, "", 0xFF4C5866, 0xFFFFFFFF, mx, my, this::openSettings);
        if (away) {
            String a = "외출 · 우회 " + DeviceLogic.num(data.getInt("bypass"));
            g.drawString(font, a, gx - 6 - font.width(a), y + 4, 0xFFD34E, false);
        }
    }

    private void tell(String s) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(s), true);
    }

    /** 교환: 통화 중 동*호 입력 → [교환]. 상대가 받기 전 다시 누르면 취소 후 재입력 */
    private void pressExchange() {
        int exch = data.getInt("exch");
        if (exch == 1) {
            act("exchange_cancel");
            return;
        }
        if (exch == 2) {
            tell("[종료]를 누르면 두 세대가 연결됩니다");
            return;
        }
        if (status() != DeviceLogic.STATUS_CONNECTED) {
            tell("세대와 통화 중일 때 교환할 수 있습니다");
            return;
        }
        int[] pr = parsePair(input);
        if (pr == null) {
            tell("교환할 세대를 동*호로 입력하고 [교환]을 누르세요");
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("dong", pr[0]);
        t.putInt("ho", pr[1]);
        act("exchange", t);
        input = "";
    }

    /** 외출/재실. 외출하려면 우회 경비실 번호를 먼저 입력 */
    private void pressAway() {
        CompoundTag t = new CompoundTag();
        if (data.getBoolean("away")) {
            t.putBoolean("away", false);
            act("guard_away", t);
            return;
        }
        if (!input.matches("\\d{1,4}")) {
            tell("우회 경비실 번호를 입력하고 [외출]을 누르세요");
            return;
        }
        t.putBoolean("away", true);
        t.putInt("bypass", Integer.parseInt(input));
        act("guard_away", t);
        input = "";
    }

    private void drawMonitor(GuiGraphics g, int x, int y, int w, int h) {
        int st = status();
        int exch = data.getInt("exch");
        if (exch > 0) {
            // 교환 중
            if (exch == 2 && !drawFeed(g, camPos(), x, y, w, h)) g.fillGradient(x, y, x + w, y + h, 0xFF14202E, 0xFF0A1018);
            if (exch == 1) g.fillGradient(x, y, x + w, y + h, 0xFF2A2010, 0xFF140E06);
            icon(g, I_PHONE, x + w / 2 - 16, y + 24, 32, exch == 2 ? 0x4CD27A : 0xFFD34E);
            centered(g, exch == 1 ? "교환 호출 중" : "교환 세대와 통화 중", x + w / 2, y + 64, exch == 2 ? 0x4CD27A : 0xFFD34E);
            centered(g, font.plainSubstrByWidth(data.getString("exchPeer"), w - 10), x + w / 2, y + 78, 0xFFFFFF);
            centered(g, "보류: " + data.getString("exchHeld"), x + w / 2, y + 96, 0x9AA7B8);
            centered(g, exch == 1 ? "[취소]로 교환 취소 후 다시 입력" : "[종료] → 두 세대 연결", x + w / 2, y + h - 22, 0x9AD0FF);
            return;
        }
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
        if (data.getBoolean("away")) {
            g.fillGradient(x, y, x + w, y + h, 0xFF2A2010, 0xFF140E06);
            icon(g, I_AWAY, x + w / 2 - 16, y + 30, 32, 0xFFD34E);
            centered(g, "경비실 외출 중", x + w / 2, y + 72, 0xFFD34E);
            centered(g, "호출은 우회 경비실 " + DeviceLogic.num(data.getInt("bypass")) + "(으)로 연결", x + w / 2, y + 88, 0xFFFFFF);
            centered(g, "[재실]을 누르면 복귀", x + w / 2, y + 110, 0x9AA7B8);
            return;
        }
        centered(g, "경비실 관제", x + w / 2, y + 40, 0xFFFFFF);
        centered(g, "로비폰 카메라 " + cams.size() + "대", x + w / 2, y + 60, 0x9AA7B8);
        centered(g, "[CCTV]로 공동현관 영상 보기", x + w / 2, y + 80, 0x707880);
        if (st == DeviceLogic.STATUS_RINGING) centered(g, "호출이 들어왔습니다 →", x + w / 2, y + 110, 0xFFD34E);
    }
}
