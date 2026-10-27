package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 로비폰 (공동현관 출입 통제기).
 * 스테인리스 패널 · 상단 카메라 · LCD · 3x4 키패드 · [호출][경비실][취소][열쇠] · RF 카드 리더.
 *
 * 단지 출입용(9999동): 호출 = 동 [호출] 호 [호출] / 세대 비밀번호 = 동 [호출] 호 [열쇠] 비밀번호 [호출]
 * 동 로비: 호출 = 호 [호출] / 세대 비밀번호 = 호 [열쇠] 비밀번호 [호출]
 * 공용 비밀번호: 대기 상태에서 [호출] 공용비밀번호 (맞으면 바로 열림)
 * 세대 비밀번호 변경(동 로비만): 대기 상태에서 [열쇠] 호 [호출] 기존 [호출] 새 비밀번호 [호출]
 * [⚙] → 1.번호 설정 / 2.관리자 모드 (공용 비밀번호 변경, 카드, 공용·세대 비밀번호 사용 여부)
 */
public class LobbyGui extends DeviceGui {
    private enum Admin { NONE, GEAR, WAIT, MENU, COMMON, REGISTER, DELETE_ID, DELETE_ALL }

    /** 일반 사용 단계 */
    private enum Step { IDLE, HO, ENTRY_PW, COMMON, CHG_HO, CHG_HO_WAIT, CHG_OLD, CHG_OLD_WAIT, CHG_NEW, CHG_NEW_WAIT }

    private String input = "";
    private Step step = Step.IDLE;
    private int enteredDong = -1, enteredHo = -1;
    private String chgOld = "";
    private Admin admin = Admin.NONE;

    public LobbyGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 190, 312);
    }

    private boolean outer() {
        return data.getInt("dong") == DeviceLogic.OUTER_LOBBY_DONG;
    }

    @Override
    protected void onDataChanged() {
        if (data.getBoolean("entryOk")) reset();
        // 세대 비밀번호 변경: 단계마다 서버 확인을 통과해야 다음 단계로
        String ok = data.getString("chgStep"), fail = data.getString("chgFail");
        if (step == Step.CHG_HO_WAIT) {
            if (ok.equals("ho_ok")) step = Step.CHG_OLD;
            else if (!fail.isEmpty()) step = Step.CHG_HO;
        } else if (step == Step.CHG_OLD_WAIT) {
            if (ok.equals("old_ok")) step = Step.CHG_NEW;
            else if (!fail.isEmpty()) step = Step.CHG_OLD;
        } else if (step == Step.CHG_NEW_WAIT) {
            if (ok.equals("done")) reset();
            else if (fail.equals("new")) step = Step.CHG_NEW;
            else if (!fail.isEmpty()) reset();
        }
        if (data.getBoolean("adminOk")) {
            admin = Admin.MENU;
            input = "";
        } else if (data.getBoolean("adminFail")) {
            admin = Admin.NONE;
            input = "";
        }
    }

    /** 관리자 요청 인자. 인증(관리 비밀번호)은 DeviceGui.act 가 자동으로 붙인다 */
    private CompoundTag authTag() {
        return new CompoundTag();
    }

    private void reset() {
        step = Step.IDLE;
        input = "";
        enteredDong = -1;
        enteredHo = -1;
        chgOld = "";
    }

    private void tell(String s) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(s), true);
    }

    private static int num(String s) {
        try {
            return s.isEmpty() ? -1 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void callUnit(int dong, int ho) {
        CompoundTag t = new CompoundTag();
        t.putInt("dong", dong);
        t.putInt("ho", ho);
        act("call_unit", t);
    }

    // ------------------------------------------------------------------ 키 입력

    @Override
    protected void onKey(char c) {
        if (admin != Admin.NONE) {
            adminKey(c);
            return;
        }
        if (!idle() && status() != DeviceLogic.STATUS_RINGING) return;
        if (step == Step.CHG_HO_WAIT || step == Step.CHG_OLD_WAIT || step == Step.CHG_NEW_WAIT) return;
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '\n' || c == '#') {
            pressCall();
        } else if (c >= '0' && c <= '9' && input.length() < 8) {
            input += c;
            // 공용 비밀번호는 맞으면 바로 열림
            if (step == Step.COMMON && input.length() >= 4) {
                CompoundTag t = new CompoundTag();
                t.putString("pw", input);
                t.putBoolean("silent", true);
                act("common_entry", t);
            }
        }
    }

    /** [호출] */
    private void pressCall() {
        if (admin != Admin.NONE) {
            adminKey('#');
            return;
        }
        if (status() == DeviceLogic.STATUS_RINGING) {
            act("answer");
            return;
        }
        if (!idle()) return;
        switch (step) {
            case IDLE -> {
                if (input.isEmpty()) {
                    if (!data.getBoolean("commonEnabled")) {
                        tell("이 로비폰은 공용 비밀번호를 사용하지 않습니다");
                        return;
                    }
                    step = Step.COMMON;
                } else if (outer()) {
                    enteredDong = num(input);
                    input = "";
                    step = Step.HO;
                } else {
                    callUnit(data.getInt("dong"), num(input));
                    reset();
                }
            }
            case HO -> {
                if (input.isEmpty()) return;
                callUnit(enteredDong, num(input));
                reset();
            }
            case ENTRY_PW -> {
                CompoundTag t = new CompoundTag();
                t.putInt("dong", enteredDong);
                t.putInt("ho", enteredHo);
                t.putString("pw", input);
                act("household_entry", t);
                reset();
            }
            case COMMON -> {
                CompoundTag t = new CompoundTag();
                t.putString("pw", input);
                act("common_entry", t);
                reset();
            }
            case CHG_HO -> {
                if (input.isEmpty()) return;
                enteredHo = num(input);
                input = "";
                step = Step.CHG_HO_WAIT;
                CompoundTag t = new CompoundTag();
                t.putInt("ho", enteredHo);
                act("lobby_pw_check_ho", t);
            }
            case CHG_OLD -> {
                if (input.isEmpty()) return;
                chgOld = input;
                input = "";
                step = Step.CHG_OLD_WAIT;
                CompoundTag t = new CompoundTag();
                t.putInt("ho", enteredHo);
                t.putString("old", chgOld);
                act("lobby_pw_check_old", t);
            }
            case CHG_NEW -> {
                if (input.isEmpty()) return;
                CompoundTag t = new CompoundTag();
                t.putInt("ho", enteredHo);
                t.putString("old", chgOld);
                t.putString("new", input);
                input = "";
                step = Step.CHG_NEW_WAIT;
                act("lobby_pw_change", t);
            }
            default -> { }
        }
    }

    /** [열쇠] */
    private void pressKey() {
        if (admin != Admin.NONE || !idle()) return;
        switch (step) {
            case IDLE -> {
                if (input.isEmpty()) {
                    // 세대 비밀번호 변경 (동 번호가 부여된 로비폰만)
                    if (outer() || data.getInt("dong") < 0) {
                        tell("단지 출입용 로비폰에서는 세대 비밀번호를 변경할 수 없습니다");
                        return;
                    }
                    step = Step.CHG_HO;
                } else if (outer()) {
                    tell("동 번호를 누르고 [호출], 호 번호를 누른 뒤 [열쇠]를 누르세요");
                } else if (!data.getBoolean("householdEnabled")) {
                    tell("이 로비폰은 세대 비밀번호 출입을 사용하지 않습니다");
                    input = "";
                } else {
                    enteredDong = data.getInt("dong");
                    enteredHo = num(input);
                    input = "";
                    step = Step.ENTRY_PW;
                }
            }
            case HO -> {
                if (input.isEmpty()) return;
                if (!data.getBoolean("householdEnabled")) {
                    tell("이 로비폰은 세대 비밀번호 출입을 사용하지 않습니다");
                    reset();
                    return;
                }
                enteredHo = num(input);
                input = "";
                step = Step.ENTRY_PW;
            }
            default -> { }
        }
    }

    private void cancel() {
        if (admin == Admin.NONE) {
            if (!idle()) act("hangup");
            reset();
            return;
        }
        input = "";
        admin = switch (admin) {
            case COMMON, REGISTER, DELETE_ID, DELETE_ALL -> Admin.MENU;
            default -> Admin.NONE;
        };
    }

    private void adminKey(char c) {
        boolean confirm = c == '#' || c == '\n';
        switch (admin) {
            case GEAR -> {
                if (c == '1') {
                    admin = Admin.NONE;
                    openSettings();
                } else if (c == '2') {
                    // [⚙] 진입 때 관리 비밀번호를 이미 확인했으므로 바로 관리자 모드
                    input = "";
                    admin = Admin.WAIT;
                    act("lobby_admin", authTag());
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
                    case '7' -> act("lobby_common_toggle", authTag());
                    case '8' -> act("lobby_household_toggle", authTag());
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
                // 숫자 입력 단계 (COMMON, REGISTER, DELETE_ID)
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
        boolean keyStep = step != Step.IDLE && step != Step.HO && step != Step.COMMON;
        buttonIcon(g, fx, ky + 96, fw, 26, I_KEY, "", keyStep ? 0xFFC98A2E : 0xFF6C7076, 0xFFFFFFFF, mx, my, this::pressKey);

        int ry = y + h - 46;
        g.drawString(font, "RF 카드", x + 56, ry + 6, 0xFF9AD0FF, false);
        g.drawString(font, "출입카드를 들고 우클릭", x + 56, ry + 19, 0xFF707880, false);

        button(g, x + w - 24, y + 1, 22, 10, "⚙", admin != Admin.NONE ? 0xFFC98A2E : 0xFF6C7076, 0xFFFFFFFF, mx, my, () -> {
            reset();
            if (admin == Admin.NONE) requestSettings(() -> admin = Admin.GEAR);
            else admin = Admin.NONE;
        });
    }

    private void drawAdminLcd(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 12, 0xFFC98A2E);
        g.drawString(font, admin == Admin.GEAR ? "설정" : "관리자 모드", x + 4, y + 2, 0xFFFFFF, false);
        int cx = x + w / 2;
        switch (admin) {
            case GEAR -> {
                g.drawString(font, "1. 번호 설정", x + 8, y + 22, 0xFFFFFF, false);
                g.drawString(font, "2. 관리자 모드", x + 8, y + 36, 0xFFFFFF, false);
                g.drawString(font, "번호를 누르세요", x + 8, y + 64, 0x9AA7B8, false);
            }
            case WAIT -> centered(g, "확인 중...", cx, y + 40, 0x9AA7B8);
            case MENU -> {
                String[] items = {"1 공용비번 변경", "2 카드 등록", "3 든 카드 삭제", "4 번호로 삭제",
                        "5 카드 전체삭제", "6 번호 설정",
                        "7 공용비번 " + (data.getBoolean("commonEnabled") ? "사용" : "미사용"),
                        "8 세대비번 " + (data.getBoolean("householdEnabled") ? "사용" : "미사용")};
                int colW = (w - 8) / 2;
                for (int i = 0; i < items.length; i++) {
                    int col = i / 4, row = i % 4;
                    fitText(g, items[i], x + 4 + col * colW, y + 16 + row * 12, colW - 2, i >= 6 ? 0x9AD0FF : 0xFFFFFF);
                }
                fitText(g, "등록 카드 " + data.getInt("cardCount") + "장 · 번호를 누르세요", x + 4, y + 70, w - 8, 0x4CD27A);
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
        String masked = input.replaceAll(".", "●");
        switch (step) {
            case IDLE -> {
                centered(g, outer() ? "동 번호 → [호출]" : "호 번호 → [호출]", cx, y + 18, 0xFFFFFF);
                centered(g, input.isEmpty() ? "_" : input + (outer() ? "동" : "호"), cx, y + 32, 0x9AD0FF);
                if (data.getBoolean("householdEnabled")) {
                    fitText(g, outer() ? "세대비번: 동[호출]호[열쇠]" : "세대비번: 호 [열쇠] 비번", x + 4, y + 48, w - 8, 0x9AA7B8);
                }
                if (data.getBoolean("commonEnabled")) fitText(g, "공용비번: [호출] 비번", x + 4, y + 59, w - 8, 0x9AA7B8);
                if (!outer()) fitText(g, "비번 변경: [열쇠]", x + 4, y + 70, w - 8, 0x9AA7B8);
            }
            case HO -> {
                centered(g, enteredDong + "동 · 호 번호", cx, y + 18, 0xFFD34E);
                centered(g, input.isEmpty() ? "_" : input + "호", cx, y + 34, 0x9AD0FF);
                centered(g, "[호출] 세대 호출", cx, y + 52, 0x9AA7B8);
                if (data.getBoolean("householdEnabled")) centered(g, "[열쇠] 세대 비밀번호", cx, y + 64, 0x9AA7B8);
            }
            case ENTRY_PW -> {
                centered(g, (enteredDong >= 0 ? enteredDong + "동 " : "") + enteredHo + "호", cx, y + 18, 0xFFD34E);
                centered(g, "세대 비밀번호 → [호출]", cx, y + 32, 0x9AA7B8);
                centered(g, masked.isEmpty() ? "_" : masked, cx, y + 50, 0xFFFFFF);
            }
            case COMMON -> {
                centered(g, "공용 비밀번호", cx, y + 18, 0xFFD34E);
                centered(g, masked.isEmpty() ? "_" : masked, cx, y + 38, 0xFFFFFF);
                centered(g, "맞으면 바로 열립니다", cx, y + 60, 0x9AA7B8);
            }
            case CHG_HO -> {
                centered(g, "세대 비밀번호 변경", cx, y + 18, 0xFFD34E);
                centered(g, "호 번호 → [호출]", cx, y + 32, 0x9AA7B8);
                centered(g, input.isEmpty() ? "_" : input + "호", cx, y + 50, 0x9AD0FF);
            }
            case CHG_OLD -> {
                centered(g, enteredHo + "호 · 기존 비밀번호", cx, y + 18, 0xFFD34E);
                centered(g, "→ [호출]  (초기 1234)", cx, y + 32, 0x9AA7B8);
                centered(g, masked.isEmpty() ? "_" : masked, cx, y + 50, 0xFFFFFF);
            }
            case CHG_HO_WAIT, CHG_OLD_WAIT, CHG_NEW_WAIT -> centered(g, "확인 중...", cx, y + 40, 0x9AA7B8);
            case CHG_NEW -> {
                centered(g, enteredHo + "호 · 새 비밀번호", cx, y + 18, 0xFFD34E);
                centered(g, "숫자 4~8자리 → [호출]", cx, y + 32, 0x9AA7B8);
                centered(g, masked.isEmpty() ? "_" : masked, cx, y + 50, 0xFFFFFF);
            }
        }
    }
}
