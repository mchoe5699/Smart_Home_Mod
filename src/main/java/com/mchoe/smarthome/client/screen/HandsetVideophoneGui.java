package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.ControlKind;
import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 수화기형 비디오폰: 왼쪽 수화기 · 작은 화면 · 3x4 다이얼 · [모니터][문열림][경비실][종료] · [화재][가스][외출][방범] · [통화].
 * - 다이얼로 세대 호출(동*호, 호만 누르면 같은 동)
 * - 화재: 경비실에 화재 비상 통보 (두 번 눌러 확인)
 * - 가스: 세대 가스밸브 잠금
 * - 외출/방범: 바로 설정, 해제는 다이얼로 세대 비밀번호를 누른 뒤 같은 버튼
 */
public class HandsetVideophoneGui extends DeviceGui {
    private String input = "";
    private boolean monitor;
    private int fireConfirm;

    public HandsetVideophoneGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 250, 330);
    }

    private boolean securing() {
        return data.getInt("secMode") != 0;
    }

    @Override
    protected void onDataChanged() {
        if (!idle()) monitor = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (fireConfirm > 0) fireConfirm--;
    }

    @Override
    protected void onKey(char c) {
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n') {
            talk();
        } else if (input.length() < 10 && ((c >= '0' && c <= '9') || c == '*')) {
            input += c;
        }
    }

    private void tell(String s) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(s), true);
    }

    private void talk() {
        if (status() == DeviceLogic.STATUS_RINGING) {
            act("answer");
        } else if (idle() && !input.isEmpty() && !securing()) {
            callPair("call_unit", input, data.getInt("dong"));
            input = "";
        }
    }

    private void security(int mode) {
        if (!idle()) return;
        int cur = data.getInt("secMode");
        if (cur == 0) {
            input = "";
            arm(mode);
        } else {
            if (input.isEmpty()) {
                tell("다이얼로 세대 비밀번호를 누른 뒤 [" + (cur == 1 ? "외출" : "방범") + "]을 누르세요");
                return;
            }
            secInput = input;
            input = "";
            disarm();
        }
    }

    private int gasOpen() {
        int n = 0;
        ListTag l = data.getList("controls", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.getString("kind").equals(ControlKind.GAS_VALVE.id) && t.getBoolean("on")) n++;
        }
        return n;
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        body(g, tex("videophone_handset"));
        if (!idle()) icon(g, I_PHONE, x + 27, y + h / 2 - 8, 16, 0x2E9E4F);

        // 화면
        int sx = x + 78, sy = y + 16, sw = 158, sh = 112;
        drawScreen(g, sx, sy, sw, sh);

        // 다이얼
        int kx = x + 78, ky = y + 140;
        keypad(g, kx, ky, 34, 22, 5, 0xFFEDEDE9, 0xFF30343A, mx, my);
        // 기능키
        int fx = x + 196, fw = 40;
        buttonIcon(g, fx, ky, fw, 22, I_CCTV, "", monitor ? 0xFF2E7DD7 : 0xFFEDEDE9, monitor ? 0xFFFFFF : 0x2E7DD7, mx, my, () -> {
            if (idle()) monitor = !monitor;
        });
        buttonIcon(g, fx, ky + 27, fw, 22, I_DOOR, "", data.getBoolean("entrance") ? 0xFF2E6DB4 : 0xFFEDEDE9,
                data.getBoolean("entrance") ? 0xFFFFFF : 0x2E6DB4, mx, my, () -> act("open_door"));
        buttonIcon(g, fx, ky + 54, fw, 22, I_GUARD, "", 0xFFEDEDE9, 0x2E6DB4, mx, my, () -> act("call_guard"));
        buttonIcon(g, fx, ky + 81, fw, 22, I_HANGUP, "", 0xFFEDEDE9, 0xC0392B, mx, my, () -> {
            if (!idle()) act("hangup");
            monitor = false;
            input = "";
        });

        // 화재 · 가스 · 외출 · 방범
        int ry = y + 252, bw = 36;
        int sm = data.getInt("secMode");
        buttonIcon(g, kx, ry, bw, 24, I_FLAME, "", fireConfirm > 0 ? 0xFFFF5A4A : 0xFFD9433A, 0xFFFFFF, mx, my, () -> {
            if (fireConfirm > 0) {
                act("fire");
                fireConfirm = 0;
            } else {
                fireConfirm = 60;
                tell("화재 비상 신고: 3초 안에 [화재]를 한 번 더 누르세요");
            }
        });
        buttonIcon(g, kx + 41, ry, bw, 24, I_GAS, "", 0xFFE0A63A, 0xFFFFFF, mx, my, () -> act("gas_close"));
        buttonIcon(g, kx + 82, ry, bw, 24, I_AWAY, "", sm == 1 ? 0xFFE5784D : 0xFFC98A2E, 0xFFFFFF, mx, my, () -> security(1));
        buttonIcon(g, kx + 123, ry, bw, 24, I_GUARD, "", sm == 2 ? 0xFF4C8AE5 : 0xFF2E6DB4, 0xFFFFFF, mx, my, () -> security(2));
        String[] labels = {"화재", "가스", "외출", "방범"};
        for (int i = 0; i < 4; i++) centered(g, labels[i], kx + i * 41 + bw / 2, ry + 26, 0x707880);

        // 통화
        int st = status();
        buttonIcon(g, kx, y + 290, 158, 26, I_CALL, st == DeviceLogic.STATUS_RINGING ? "받기" : "통화",
                st == DeviceLogic.STATUS_RINGING && (ticks / 8) % 2 == 0 ? 0xFF3FBF63 : 0xFF2E9E4F, 0xFFFFFF, mx, my, this::talk);

        // 설정
        buttonIcon(g, x + w - 22, y + 3, 16, 11, I_GEAR, "", 0xFFE4E4E0, 0x5A6470, mx, my, this::openSettings);
    }

    private void drawScreen(GuiGraphics g, int x, int y, int w, int h) {
        int st = status();
        if (st != DeviceLogic.STATUS_IDLE) {
            if (!drawFeed(g, camPos(), x, y, w, h)) {
                g.fillGradient(x, y, x + w, y + h, 0xFF14202E, 0xFF0A1018);
                icon(g, I_PHONE, x + w / 2 - 14, y + h / 2 - 26, 28, 0x4CD27A);
                centered(g, "음성 통화", x + w / 2, y + h / 2 + 8, 0x9AA7B8);
            }
            g.fill(x, y + h - 13, x + w, y + h, 0xC0000000);
            int c = st == DeviceLogic.STATUS_CONNECTED ? 0x4CD27A : ((ticks / 8) % 2 == 0 ? 0xFFD34E : 0xFFFFFF);
            fitText(g, "● " + statusText() + "  " + data.getString("peer"), x + 4, y + h - 10, w - 8, c);
            return;
        }
        if (monitor) {
            BlockPos cam = data.contains("doorCam") ? BlockPos.of(data.getLong("doorCam")) : null;
            if (!drawFeed(g, cam, x, y, w, h)) centered(g, "현관 도어카메라가 없습니다", x + w / 2, y + h / 2 - 4, 0x9AA7B8);
            g.drawString(font, "모니터 · 현관", x + 4, y + 4, 0xFFFFFF, true);
            return;
        }
        // 대기 화면
        g.fillGradient(x, y, x + w, y + h, 0xFF14294A, 0xFF0A1426);
        bigText(g, clock(), x + w / 2, y + 12, 2.2f, 0xFFFFFF, true);
        centered(g, DeviceLogic.num(data.getInt("dong")) + "동 " + DeviceLogic.num(data.getInt("ho")) + "호", x + w / 2, y + 36, 0x9AD0FF);
        int sm = data.getInt("secMode");
        int ly = y + 52;
        if (sm != 0) {
            int rem = data.getInt("secRemain");
            boolean alarm = data.getBoolean("secAlarm");
            int col = alarm ? ((ticks / 8) % 2 == 0 ? 0xFF4C4C : 0xFFFFFF) : rem > 0 ? 0xFFD34E : 0xFF8A4E;
            centered(g, (sm == 1 ? "외출모드" : "방범모드") + (alarm ? " · 경보!" : rem > 0 ? " · " + rem + "초 뒤 감지" : " · 감지 중"), x + w / 2, ly, col);
            ly += 12;
            String masked = input.replaceAll(".", "●");
            centered(g, input.isEmpty() ? "해제: 세대 비밀번호 → [" + (sm == 1 ? "외출" : "방범") + "]" : masked, x + w / 2, ly, 0xFFFFFF);
        } else {
            int gas = gasOpen();
            card(g, x + 6, ly - 2, w / 2 - 9, 26, 0x30FFFFFF);
            icon(g, I_GAS, x + 10, ly + 3, 14, gas > 0 ? 0xFF6B6B : 0x4CD27A);
            g.drawString(font, gas > 0 ? "가스 열림" : "가스 잠김", x + 27, ly + 6, gas > 0 ? 0xFF6B6B : 0x4CD27A, false);
            card(g, x + w / 2 + 3, ly - 2, w / 2 - 9, 26, 0x30FFFFFF);
            icon(g, I_AWAY, x + w / 2 + 7, ly + 3, 14, 0x9AA7B8);
            g.drawString(font, "방범 해제", x + w / 2 + 24, ly + 6, 0x9AA7B8, false);
            ly += 32;
            String entry = input.isEmpty() ? "다이얼 → [통화]로 세대 호출" : input.replace("*", "동 ") + "호";
            centered(g, entry, x + w / 2, ly, input.isEmpty() ? 0x707880 : 0x9AD0FF);
        }
        if (data.getString("complex").isEmpty()) centered(g, "단지 구획 밖", x + w / 2, y + h - 12, 0xFF6B6B);
    }
}
