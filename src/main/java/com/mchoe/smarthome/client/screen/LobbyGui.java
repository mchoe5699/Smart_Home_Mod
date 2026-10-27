package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 로비폰 (공동현관 출입 통제기).
 * 스테인리스 패널 · 상단 카메라 · LCD · 3x4 키패드 · [호출][경비실][취소][열쇠] · RF 카드 리더.
 * [⚙] → 1.번호 설정 / 2.관리자 모드 (공용 비밀번호 변경, 카드 등록·삭제·전체삭제)
 */
public class LobbyGui extends DeviceGui {
    private static final int STEEL = 0xFFA9AEB4;

    private enum Admin { NONE, GEAR, AUTH, WAIT, MENU, COMMON, REGISTER, DELETE_ID, DELETE_ALL }

    private String input = "";
    private boolean pwMode;
    private Admin admin = Admin.NONE;
    private String auth = "";

    public LobbyGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 190, 312);
    }

    private boolean outer() {
        return data.getInt("dong") == DeviceLogic.OUTER_LOBBY_DONG;
    }

    @Override
    protected void onDataChanged() {
        if (data.getBoolean("adminOk")) {
            admin = Admin.MENU;
            input = "";
        } else if (data.getBoolean("adminFail")) {
            admin = Admin.NONE;
            auth = "";
            input = "";
        }
        if (data.contains("newAuth")) auth = data.getString("newAuth");
    }

    private CompoundTag authTag() {
        CompoundTag t = new CompoundTag();
        t.putString("auth", auth);
        return t;
    }

    // ------------------------------------------------------------------ 키 입력

    @Override
    protected void onKey(char c) {
        if (admin != Admin.NONE) {
            adminKey(c);
            return;
        }
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            if (pwMode) submitPassword();
            else if (c == '\n') pressCall();
        } else if (input.length() < 16) {
            input += c;
        }
    }

    private void adminKey(char c) {
        boolean confirm = c == '#' || c == '\n';
        switch (admin) {
            case GEAR -> {
                if (c == '1') {
                    admin = Admin.NONE;
                    openSettings();
                } else if (c == '2') {
                    input = "";
                    if (data.getBoolean("commonSet")) {
                        admin = Admin.AUTH;
                    } else {
                        auth = "";
                        admin = Admin.WAIT;
                        act("lobby_admin", authTag());
                    }
                }
            }
            case MENU -> {
                input = "";
                switch (c) {
                    case '1' -> admin = Admin.COMMON;
                    case '2' -> admin = Admin.REGISTER;
                    case '3' -> {
                        CompoundTag t = authTag();
                        t.putInt("id", 0);
                        act("lobby_card_delete", t);
                    }
                    case '4' -> admin = Admin.DELETE_ID;
                    case '5' -> admin = Admin.DELETE_ALL;
                    case '6' -> {
                        admin = Admin.NONE;
                        openSettings();
                    }
                    default -> { }
                }
            }
            case DELETE_ALL -> {
                if (confirm) {
                    act("lobby_card_delete_all", authTag());
                    admin = Admin.MENU;
                }
            }
            case WAIT -> { }
            default -> {
                // 숫자 입력 단계 (AUTH, COMMON, REGISTER, DELETE_ID)
                if (c == '<') {
                    if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                } else if (confirm) {
                    submitAdmin();
                } else if (input.length() < 12 && (c != '*' || admin == Admin.REGISTER)) {
                    input += c;
                }
            }
        }
    }

    private void submitAdmin() {
        switch (admin) {
            case AUTH -> {
                auth = input;
                admin = Admin.WAIT;
                act("lobby_admin", authTag());
            }
            case COMMON -> {
                CompoundTag t = authTag();
                t.putString("pw", input);
                act("lobby_common_pw", t);
                admin = Admin.MENU;
            }
            case REGISTER -> {
                CompoundTag t = authTag();
                if (outer() && input.equals("*")) {
                    t.putBoolean("all", true);
                } else {
                    int[] pr = parsePair(input);
                    if (pr == null && !outer() && input.matches("\\d{1,4}")) pr = new int[]{data.getInt("dong"), Integer.parseInt(input)};
                    if (pr == null) return;
                    t.putInt("dong", pr[0]);
                    t.putInt("ho", pr[1]);
                }
                act("lobby_card_register", t);
                admin = Admin.MENU;
            }
            case DELETE_ID -> {
                if (!input.matches("\\d+")) return;
                CompoundTag t = authTag();
                t.putInt("id", Integer.parseInt(input));
                act("lobby_card_delete", t);
                admin = Admin.MENU;
            }
            default -> { }
        }
        input = "";
    }

    private void cancel() {
        if (admin == Admin.NONE) {
            if (!idle()) act("hangup");
            else if (!input.isEmpty()) input = "";
            else pwMode = false;
            return;
        }
        input = "";
        admin = switch (admin) {
            case COMMON, REGISTER, DELETE_ID, DELETE_ALL -> Admin.MENU;
            default -> Admin.NONE;
        };
        if (admin == Admin.NONE) auth = "";
    }

    private void submitPassword() {
        CompoundTag t = new CompoundTag();
        t.putString("input", input);
        act("password", t);
        input = "";
        pwMode = false;
    }

    private void pressCall() {
        if (admin != Admin.NONE) {
            adminKey('#');
            return;
        }
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

    // ------------------------------------------------------------------ 그리기

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        body(g, tex("lobby"));
        int lx = x + 14, ly = y + 46, lw = w - 28, lh = 84;
        if (admin != Admin.NONE) drawAdminLcd(g, lx, ly, lw, lh);
        else drawLcd(g, lx, ly, lw, lh);

        int ky = y + 142;
        keypad(g, x + 14, ky, 36, 26, 6, 0xFF2A2E34, 0xFFE8ECF0, mx, my);
        int fx = x + 14 + 3 * 42 + 4, fw = w - (fx - x) - 14;
        buttonIcon(g, fx, ky, fw, 26, admin != Admin.NONE ? -1 : I_CALL, admin != Admin.NONE ? "확인" : "", 0xFF2E9E4F, 0xFFFFFFFF, mx, my, this::pressCall);
        buttonIcon(g, fx, ky + 32, fw, 26, I_GUARD, "", 0xFF2E6DB4, 0xFFFFFFFF, mx, my, () -> {
            if (admin == Admin.NONE) act("call_guard");
        });
        button(g, fx, ky + 64, fw, 26, "취소", 0xFFC98A2E, 0xFFFFFFFF, mx, my, this::cancel);
        buttonIcon(g, fx, ky + 96, fw, 26, I_KEY, "", pwMode ? 0xFFC98A2E : 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            if (admin != Admin.NONE) return;
            pwMode = !pwMode;
            input = "";
        });

        int ry = y + h - 46;
        g.drawString(font, "RF 카드", x + 56, ry + 6, 0xFF9AD0FF, false);
        g.drawString(font, "출입카드를 들고 우클릭", x + 56, ry + 19, 0xFF707880, false);

        button(g, x + w - 24, y + 1, 22, 10, "⚙", admin != Admin.NONE ? 0xFFC98A2E : 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            input = "";
            pwMode = false;
            admin = admin == Admin.NONE ? Admin.GEAR : Admin.NONE;
            if (admin == Admin.NONE) auth = "";
        });
    }

    private void drawAdminLcd(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 12, 0xFFC98A2E);
        g.drawString(font, admin == Admin.GEAR ? "설정" : "관리자 모드", x + 4, y + 2, 0xFFFFFF, false);
        int cx = x + w / 2;
        String masked = input.replaceAll("[0-9]", "●");
        switch (admin) {
            case GEAR -> {
                g.drawString(font, "1. 번호 설정", x + 8, y + 22, 0xFFFFFF, false);
                g.drawString(font, "2. 관리자 모드", x + 8, y + 36, 0xFFFFFF, false);
                g.drawString(font, "번호를 누르세요", x + 8, y + 64, 0x9AA7B8, false);
            }
            case AUTH -> {
                centered(g, "관리자 인증", cx, y + 18, 0xFFD34E);
                centered(g, "공용 비밀번호 입력 → #", cx, y + 32, 0x9AA7B8);
                centered(g, masked.isEmpty() ? "_" : masked, cx, y + 52, 0xFFFFFF);
            }
            case WAIT -> centered(g, "확인 중...", cx, y + 40, 0x9AA7B8);
            case MENU -> {
                String[] items = {"1 공용 비밀번호", "2 카드 등록", "3 손에 든 카드 삭제", "4 카드 번호로 삭제", "5 카드 전체 삭제", "6 번호 설정"};
                for (int i = 0; i < items.length; i++) g.drawString(font, items[i], x + 6, y + 15 + i * 11, 0xFFFFFF, false);
                String n = "카드 " + data.getInt("cardCount");
                g.drawString(font, n, x + w - 4 - font.width(n), y + 15, 0x4CD27A, false);
            }
            case COMMON -> {
                centered(g, "새 공용 비밀번호", cx, y + 18, 0xFFD34E);
                centered(g, "숫자 4~8자리 → #  (비우면 삭제)", cx, y + 32, 0x9AA7B8);
                centered(g, input.isEmpty() ? "_" : input, cx, y + 52, 0xFFFFFF);
            }
            case REGISTER -> {
                centered(g, "카드 등록", cx, y + 16, 0xFFD34E);
                centered(g, outer() ? "동*호 → #  (*만: 단지 전체)" : "호 → #  (" + DeviceLogic.num(data.getInt("dong")) + "동)", cx, y + 30, 0x9AA7B8);
                centered(g, input.isEmpty() ? "_" : input, cx, y + 48, 0xFFFFFF);
                centered(g, "카드등록용 카드가 필요합니다", cx, y + 68, 0x707880);
            }
            case DELETE_ID -> {
                centered(g, "삭제할 카드 번호 → #", cx, y + 18, 0xFFD34E);
                centered(g, input.isEmpty() ? "_" : "#" + input, cx, y + 40, 0xFFFFFF);
                centered(g, "카드 번호는 카드 설명에 있습니다", cx, y + 64, 0x707880);
            }
            case DELETE_ALL -> {
                centered(g, "카드 전체 삭제", cx, y + 18, 0xFF6B6B);
                centered(g, (outer() ? "단지 " : DeviceLogic.num(data.getInt("dong")) + "동 ") + "카드 " + data.getInt("cardCount") + "장", cx, y + 34, 0xFFFFFF);
                centered(g, "# 확인 / [취소]", cx, y + 56, 0x9AA7B8);
            }
            default -> { }
        }
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
        centered(g, "[⚙] 설정·관리자", cx, y + 68, 0x9AA7B8);
    }
}
