package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.ControlKind;
import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 월패드: 홈네트워크 터치스크린.
 * 방문자(현관 영상)·조명·난방·가스·환기·엘리베이터·외출/방범·경비실·세대통화·기록·공지·설정.
 * 호출이 오면 통화 화면(실시간 영상)으로 바뀐다.
 */
public class WallpadGui extends DeviceGui {
    private enum Page {
        HOME("홈"), VISITOR("방문자"), LIGHT("조명"), HEAT("난방"), GAS("가스"), VENT("환기"), ELEVATOR("엘리베이터"),
        AWAY("외출/방범"), GUARD("경비실"), INTERCOM("세대 통화"), LOG("기록"), NOTICE("공지사항"), SETTINGS("설정");
        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private static final int GLASS = 0xFF15171A;
    private static final int BG = 0xFF0E1A2B;
    private static final int TILE = 0xFF173A63;

    private Page page = Page.HOME;
    private String input = "";
    private EditBox pwBox;

    public WallpadGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 400, 250);
    }

    @Override
    protected void initExtra() {
        String keep = pwBox != null ? pwBox.getValue() : "";
        pwBox = new EditBox(font, left + 140, top + 120, 90, 16, Component.literal("세대 비밀번호"));
        pwBox.setMaxLength(8);
        pwBox.setFilter(s -> s.matches("\\d{0,8}"));
        pwBox.setValue(keep);
        pwBox.setHint(Component.literal("숫자 4~8자리").withStyle(s -> s.withColor(0x707880)));
        addRenderableWidget(pwBox);
    }

    @Override
    public void tick() {
        super.tick();
        pwBox.tick();
    }

    @Override
    protected void onKey(char c) {
        if (page != Page.INTERCOM) return;
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            callPair("call_unit", input, data.getInt("dong"));
            input = "";
        } else if (input.length() < 10) {
            input += c;
        }
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        // 유리 전면 본체
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF3A3F46);
        g.fill(x, y, x + w, y + h, GLASS);
        g.fill(x, y, x + w, y + 1, 0xFF2E3238);
        g.drawString(font, "HOME NETWORK", x + w / 2 - font.width("HOME NETWORK") / 2, y + h - 12, 0xFF3A4654, false);
        circle(g, x + w - 14, y + h - 8, 2, idle() ? 0xFF2E7DD7 : 0xFF4CD27A);

        int sx = x + 12, sy = y + 10, sw = w - 24, sh = h - 26;
        g.fill(sx, sy, sx + sw, sy + sh, BG);
        pwBox.visible = page == Page.SETTINGS && idle();

        // 호출/통화 중이면 통화 화면
        if (!idle()) {
            drawCall(g, sx, sy, sw, sh, mx, my);
            return;
        }
        drawStatusBar(g, sx, sy, sw, mx, my);
        int cy = sy + 18, ch = sh - 18;
        switch (page) {
            case HOME -> drawHome(g, sx, cy, sw, ch, mx, my);
            case VISITOR -> drawVisitor(g, sx, cy, sw, ch, mx, my);
            case LIGHT, HEAT, GAS, VENT -> drawControls(g, sx, cy, sw, ch, mx, my);
            case ELEVATOR -> drawElevator(g, sx, cy, sw, ch, mx, my);
            case AWAY -> drawAway(g, sx, cy, sw, ch, mx, my);
            case GUARD -> drawGuard(g, sx, cy, sw, ch, mx, my);
            case INTERCOM -> drawIntercom(g, sx, cy, sw, ch, mx, my);
            case LOG -> drawLog(g, sx, cy, sw, ch);
            case NOTICE -> drawNotice(g, sx, cy, sw, ch);
            case SETTINGS -> drawSettings(g, sx, cy, sw, ch, mx, my);
        }
    }

    private void drawStatusBar(GuiGraphics g, int x, int y, int w, int mx, int my) {
        g.fill(x, y, x + w, y + 16, 0xFF0A1422);
        if (page != Page.HOME) {
            button(g, x + 2, y + 2, 40, 12, "◀ 홈", 0xFF1E4D8C, 0xFFFFFFFF, mx, my, () -> page = Page.HOME);
            g.drawString(font, page.title, x + 48, y + 4, 0xFFFFFF, false);
        } else {
            String cx = data.getString("complex");
            g.drawString(font, cx.isEmpty() ? "단지 구획 밖" : cx, x + 6, y + 4, cx.isEmpty() ? 0xFF6B6B : 0x9AD0FF, false);
        }
        String right = DeviceLogic.num(data.getInt("dong")) + "동 " + DeviceLogic.num(data.getInt("ho")) + "호   " + clock();
        g.drawString(font, right, x + w - 6 - font.width(right), y + 4, 0xFFFFFF, false);
        if (data.getBoolean("away")) g.drawString(font, "외출", x + w - 6 - font.width(right) - 30, y + 4, 0xFFD34E, false);
    }

    private void drawHome(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Page[] tiles = {Page.VISITOR, Page.LIGHT, Page.HEAT, Page.GAS, Page.VENT, Page.ELEVATOR,
                Page.AWAY, Page.GUARD, Page.INTERCOM, Page.LOG, Page.NOTICE, Page.SETTINGS};
        int[] colors = {0xFF2E7DD7, 0xFFE5A84D, 0xFFE5484D, 0xFF6C9A2E, 0xFF2EA8A0, 0xFF7A5AD0,
                0xFF4C5866, 0xFF2E6DB4, 0xFF2E9E4F, 0xFF8A6A3A, 0xFFB45A8A, 0xFF5A6470};
        int cols = 4, rows = 3, gap = 6;
        int tw = (w - gap * (cols + 1)) / cols, th = (h - gap * (rows + 1)) / rows;
        for (int i = 0; i < tiles.length; i++) {
            int tx = x + gap + (i % cols) * (tw + gap), ty = y + gap + (i / cols) * (th + gap);
            Page p = tiles[i];
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
            g.fill(tx, ty, tx + tw, ty + th, hover ? brighten(TILE, 18) : TILE);
            g.fill(tx + 6, ty + 6, tx + 22, ty + 22, colors[i]);
            g.drawString(font, p.title, tx + 6, ty + th - 14, 0xFFFFFF, false);
            String sub = tileSub(p);
            if (!sub.isEmpty()) g.drawString(font, font.plainSubstrByWidth(sub, tw - 30), tx + 26, ty + 10, 0x9AA7B8, false);
            touch(tx, ty, tw, th, () -> page = p);
        }
    }

    private String tileSub(Page p) {
        return switch (p) {
            case LIGHT -> count(ControlKind.LIGHT, true) + "/" + count(ControlKind.LIGHT, false) + " 켜짐";
            case HEAT -> count(ControlKind.THERMOSTAT, false) + "개 방";
            case GAS -> count(ControlKind.GAS_VALVE, true) > 0 ? "열림" : "잠김";
            case VENT -> count(ControlKind.VENTILATION, true) > 0 ? "가동" : "정지";
            case AWAY -> data.getBoolean("away") ? "설정" : "해제";
            case NOTICE -> data.getString("notice").isEmpty() ? "" : "새 공지";
            case VISITOR -> data.contains("doorCam") ? "현관 영상" : "";
            default -> "";
        };
    }

    private int count(ControlKind kind, boolean onlyOn) {
        int n = 0;
        ListTag l = data.getList("controls", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.getString("kind").equals(kind.id) && (!onlyOn || t.getBoolean("on"))) n++;
        }
        return n;
    }

    private void drawCall(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int st = status();
        g.fill(x, y, x + w, y + 16, st == DeviceLogic.STATUS_RINGING && (ticks / 8) % 2 == 0 ? 0xFF6A1F2A : 0xFF0A1422);
        g.drawString(font, statusText() + " · " + data.getString("peer"), x + 6, y + 4, 0xFFFFFF, false);
        g.drawString(font, clock(), x + w - 6 - font.width(clock()), y + 4, 0xFFFFFF, false);
        int vx = x + 6, vy = y + 22, vw = 240, vh = 180;
        g.fill(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, 0xFF000000);
        if (!drawFeed(g, camPos(), vx, vy, vw, vh)) {
            g.fill(vx, vy, vx + vw, vy + vh, 0xFF101820);
            centered(g, "음성 통화", vx + vw / 2, vy + vh / 2 - 10, 0x9AA7B8);
            centered(g, data.getString("peer"), vx + vw / 2, vy + vh / 2 + 4, 0xFFFFFF);
        }
        int bx = vx + vw + 10, bw = x + w - bx - 6;
        int by = vy;
        if (st == DeviceLogic.STATUS_RINGING) {
            button(g, bx, by, bw, 32, "통화", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> act("answer"));
            by += 38;
            button(g, bx, by, bw, 24, "거절", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
            by += 30;
        } else {
            button(g, bx, by, bw, 32, st == DeviceLogic.STATUS_CALLING ? "취소" : "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
            by += 38;
        }
        if (data.getBoolean("entrance")) {
            button(g, bx, by, bw, 32, "문열림", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> act("open_door"));
            by += 38;
        }
        if (st == DeviceLogic.STATUS_CONNECTED) g.drawString(font, "대화창 →", bx, by + 4, 0x9AD0FF, false);
    }

    private void drawVisitor(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int vw = 250, vh = 184;
        int vx = x + 6, vy = y + 4;
        g.fill(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, 0xFF000000);
        BlockPos cam = data.contains("doorCam") ? BlockPos.of(data.getLong("doorCam")) : null;
        if (!drawFeed(g, cam, vx, vy, vw, vh)) {
            g.fill(vx, vy, vx + vw, vy + vh, 0xFF101820);
            centered(g, "현관 도어카메라가 없습니다", vx + vw / 2, vy + vh / 2 - 4, 0x9AA7B8);
        }
        int bx = vx + vw + 10, bw = x + w - bx - 6;
        g.drawString(font, "현관 모니터", bx, vy + 2, 0xFFFFFF, false);
        ListTag log = data.getList("log", Tag.TAG_STRING);
        int ly = vy + 16;
        g.drawString(font, "최근 방문자", bx, ly, 0xFFD34E, false);
        ly += 12;
        for (int i = 0, n = 0; i < log.size() && n < 8; i++) {
            String s = log.getString(i);
            if (!s.contains("방문자") && !s.contains("부재중")) continue;
            fitText(g, s, bx, ly, bw, 0xC8D0DA);
            ly += 10;
            n++;
        }
    }

    private ControlKind pageKind() {
        return switch (page) {
            case LIGHT -> ControlKind.LIGHT;
            case HEAT -> ControlKind.THERMOSTAT;
            case GAS -> ControlKind.GAS_VALVE;
            default -> ControlKind.VENTILATION;
        };
    }

    private void drawControls(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        ControlKind kind = pageKind();
        int ry = y + 6;
        if (kind == ControlKind.LIGHT) {
            button(g, x + w - 96, ry, 90, 18, "일괄 소등", 0xFF4C5866, 0xFFFFFFFF, mx, my, () -> act("lights_off"));
        } else if (kind == ControlKind.GAS_VALVE) {
            button(g, x + w - 96, ry, 90, 18, "전체 잠금", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("gas_close"));
        }
        g.drawString(font, kind.korean + " 제어", x + 8, ry + 5, 0xFFFFFF, false);
        ry += 26;
        ListTag l = data.getList("controls", Tag.TAG_COMPOUND);
        int shown = 0;
        for (int i = 0; i < l.size() && shown < 7; i++) {
            CompoundTag t = l.getCompound(i);
            if (!t.getString("kind").equals(kind.id)) continue;
            shown++;
            long p = t.getLong("pos");
            boolean on = t.getBoolean("on");
            g.fill(x + 6, ry, x + w - 6, ry + 22, 0xFF13263F);
            circle(g, x + 16, ry + 11, 4, on ? (kind == ControlKind.GAS_VALVE ? 0xFFE5484D : 0xFFFFD34E) : 0xFF3A4654);
            g.drawString(font, t.getString("room"), x + 28, ry + 7, 0xFFFFFF, false);
            int bx = x + w - 12;
            switch (kind) {
                case LIGHT -> {
                    bx -= 70;
                    button(g, bx, ry + 3, 70, 16, on ? "켜짐" : "꺼짐", on ? 0xFFC98A2E : 0xFF3A4654, 0xFFFFFFFF, mx, my, () -> ctl("ctl_toggle", p, 0));
                }
                case GAS_VALVE -> {
                    bx -= 70;
                    g.drawString(font, on ? "열림" : "잠김", bx - 40, ry + 7, on ? 0xFFE5484D : 0xFF4CD27A, false);
                    button(g, bx, ry + 3, 70, 16, on ? "잠그기" : "열기", on ? 0xFFB43A3A : 0xFF3A4654, 0xFFFFFFFF, mx, my, () -> ctl("ctl_toggle", p, 0));
                }
                case THERMOSTAT -> {
                    bx -= 60;
                    button(g, bx, ry + 3, 60, 16, on ? "난방 켜짐" : "꺼짐", on ? 0xFFC9502E : 0xFF3A4654, 0xFFFFFFFF, mx, my, () -> ctl("ctl_toggle", p, 0));
                    bx -= 22;
                    button(g, bx, ry + 3, 18, 16, "+", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> ctl("ctl_temp", p, 1));
                    String temp = t.getInt("temp") + "℃";
                    g.drawString(font, temp, bx - 6 - font.width(temp), ry + 7, 0xFFFFB45A, false);
                    bx -= 50;
                    button(g, bx, ry + 3, 18, 16, "−", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> ctl("ctl_temp", p, -1));
                }
                default -> {
                    int lvl = on ? t.getInt("level") : 0;
                    for (int k = 3; k >= 0; k--) {
                        bx -= 34;
                        int kk = k;
                        button(g, bx, ry + 3, 32, 16, k == 0 ? "끔" : k + "단", lvl == k ? 0xFF2EA8A0 : 0xFF3A4654, 0xFFFFFFFF, mx, my, () -> ctl("ctl_level", p, kk));
                    }
                }
            }
            ry += 26;
        }
        if (shown == 0) {
            centered(g, "이 세대에 연결된 " + kind.korean + "가 없습니다", x + w / 2, y + h / 2 - 10, 0x9AA7B8);
            centered(g, kind.korean + " 블록을 설치하고 같은 동·호로 설정하세요", x + w / 2, y + h / 2 + 4, 0x707880);
        }
    }

    private void ctl(String action, long p, int value) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", p);
        t.putInt("level", value);
        t.putInt("delta", value);
        act(action, t);
    }

    private void drawElevator(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int n = count(ControlKind.ELEVATOR_LINK, false);
        centered(g, "엘리베이터를 우리 층으로 부릅니다", x + w / 2, y + 30, 0xFFFFFF);
        button(g, x + w / 2 - 70, y + 60, 140, 60, "▼ ▲  호출", 0xFF7A5AD0, 0xFFFFFFFF, mx, my, () -> act("elevator"));
        centered(g, n == 0 ? "연결된 엘리베이터 호출 연동기 없음" : "연동기 " + n + "대", x + w / 2, y + 134, n == 0 ? 0xFFD34E : 0x9AA7B8);
        centered(g, "연동기는 엘리베이터 호출 버튼 옆에 두면 레드스톤 신호를 보냅니다", x + w / 2, y + 150, 0x707880);
    }

    private void drawAway(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean away = data.getBoolean("away");
        centered(g, away ? "외출모드 설정됨" : "외출모드 해제됨", x + w / 2, y + 26, away ? 0xFFD34E : 0x4CD27A);
        button(g, x + w / 2 - 80, y + 50, 160, 50, away ? "외출 해제" : "외출 설정", away ? 0xFF4C5866 : 0xFFC98A2E, 0xFFFFFFFF, mx, my, () -> {
            CompoundTag t = new CompoundTag();
            t.putBoolean("on", !away);
            act("away", t);
        });
        centered(g, "설정하면 조명 일괄 소등, 가스밸브 잠금, 환기 정지", x + w / 2, y + 116, 0x9AA7B8);
    }

    private void drawGuard(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        centered(g, "관할 경비실 " + DeviceLogic.num(data.getInt("guard")), x + w / 2, y + 26, 0xFFFFFF);
        centered(g, data.getBoolean("myGuardExists") ? "관할 경비실기 연결됨" : "관할 경비실기 없음 · 단지 경비실로 연결",
                x + w / 2, y + 40, data.getBoolean("myGuardExists") ? 0x4CD27A : 0xFFD34E);
        button(g, x + w / 2 - 80, y + 62, 160, 50, "경비실 호출", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> act("call_guard"));
    }

    private void drawIntercom(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x + 10, y + 8, x + 170, y + 34, 0xFF050607);
        String shown = input.isEmpty() ? "동*호 입력" : input.replace("*", "동 ") + (input.contains("*") ? "호" : "호");
        g.drawString(font, shown, x + 16, y + 17, input.isEmpty() ? 0x707880 : 0x9AD0FF, false);
        keypad(g, x + 10, y + 40, 48, 30, 6, 0xFF1E3A5A, 0xFFFFFFFF, mx, my);
        int bx = x + 190, bw = w - 200;
        button(g, bx, y + 40, bw, 34, "호출", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> onKey('\n'));
        button(g, bx, y + 80, bw, 24, "지우기", 0xFF4C5866, 0xFFFFFFFF, mx, my, () -> input = "");
        g.drawString(font, "같은 관할경비실 세대와 통화", bx, y + 116, 0x9AA7B8, false);
        g.drawString(font, "호수만 누르면 같은 동", bx, y + 128, 0x707880, false);
    }

    private void drawLog(GuiGraphics g, int x, int y, int w, int h) {
        ListTag log = data.getList("log", Tag.TAG_STRING);
        int ly = y + 8;
        if (log.isEmpty()) centered(g, "통화 기록이 없습니다", x + w / 2, y + h / 2, 0x9AA7B8);
        for (int i = 0; i < log.size() && ly < y + h - 10; i++) {
            fitText(g, log.getString(i), x + 10, ly, w - 20, 0xC8D0DA);
            ly += 14;
        }
    }

    private void drawNotice(GuiGraphics g, int x, int y, int w, int h) {
        String n = data.getString("notice");
        if (n.isEmpty()) {
            centered(g, "공지사항이 없습니다", x + w / 2, y + h / 2, 0x9AA7B8);
            return;
        }
        g.drawString(font, "관리사무소 공지", x + 10, y + 8, 0xFFD34E, false);
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(n), w - 20));
        int ly = y + 24;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x + 10, ly, 0xFFFFFF, false);
            ly += 11;
        }
    }

    private void drawSettings(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.drawString(font, "기기 번호", x + 10, y + 10, 0xFFD34E, false);
        g.drawString(font, "관할경비실 " + DeviceLogic.num(data.getInt("guard")) + " · " + DeviceLogic.num(data.getInt("dong")) + "동 "
                + DeviceLogic.num(data.getInt("ho")) + "호", x + 10, y + 24, 0xFFFFFF, false);
        button(g, x + 10, y + 38, 200, 20, "관할경비실·세대번호 설정", 0xFF1E4D8C, 0xFFFFFFFF, mx, my, this::openSettings);

        g.drawString(font, "세대 비밀번호 (공동현관 출입용)", x + 10, y + 74, 0xFFD34E, false);
        g.drawString(font, data.getBoolean("hasPw") ? "설정됨" : "설정 안 됨", x + 10, y + 88, data.getBoolean("hasPw") ? 0x4CD27A : 0x9AA7B8, false);
        pwBox.setX(x + 10);
        pwBox.setY(y + 100);
        button(g, x + 110, y + 100, 50, 16, "저장", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("pw", pwBox.getValue());
            act("set_pw", t);
            pwBox.setValue("");
        });
        button(g, x + 166, y + 100, 50, 16, "삭제", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("pw", "");
            act("set_pw", t);
        });
        g.drawString(font, "로비폰에서 [열쇠] → 호*비밀번호 → #", x + 10, y + 124, 0x9AA7B8, false);
    }
}
