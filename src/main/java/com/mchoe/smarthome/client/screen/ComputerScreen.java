package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.client.ClientHooks;
import com.mchoe.smarthome.logic.DeviceType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/** 관리 컴퓨터: 단지 현황 · 카드 등록/관리 · 비밀번호 · 공지사항 */
public class ComputerScreen extends Screen implements DataScreen {
    private enum Tab {
        STATUS("단지 현황"), CARDS("카드 관리"), PASSWORDS("비밀번호"), NOTICE("공지사항");
        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final int W = 440, H = 276;
    private static final int ROWS = 7;

    private final BlockPos pos;
    private CompoundTag data;
    private Tab tab = Tab.STATUS;
    private int page;
    private boolean allAccess;
    private String signature = "";
    private int ticks;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, EditBox> boxes = new HashMap<>();
    private int left, top;

    public ComputerScreen(BlockPos pos, CompoundTag data) {
        super(Component.literal("관리 컴퓨터"));
        this.pos = pos;
        this.data = data;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void updateData(CompoundTag d) {
        this.data = d;
        String sig = signature();
        if (!sig.equals(signature)) rebuildWidgets();
    }

    private String signature() {
        StringBuilder sb = new StringBuilder(tab.name()).append(page).append(allAccess).append(data.getInt("complexId"));
        ListTag cards = data.getList("cards", Tag.TAG_COMPOUND);
        for (int i = 0; i < cards.size(); i++) sb.append(cards.getCompound(i).getInt("id")).append(cards.getCompound(i).getBoolean("revoked"));
        return sb.toString();
    }

    private void send(String action, CompoundTag t) {
        ClientHooks.send(pos, action, t);
    }

    private EditBox box(String key, int x, int y, int w, int max, String filter, String hint) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.literal(hint));
        b.setMaxLength(max);
        if (filter != null) b.setFilter(s -> s.matches(filter));
        b.setValue(values.getOrDefault(key, ""));
        b.setResponder(v -> values.put(key, v));
        b.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(b);
        boxes.put(key, b);
        return b;
    }

    private String val(String key) {
        return values.getOrDefault(key, "").trim();
    }

    private int num(String key) {
        try {
            return Integer.parseInt(val(key));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void btn(String label, int x, int y, int w, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, 18).build());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        boxes.clear();
        signature = signature();
        int tx = left + 10;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.title), x -> {
                tab = t;
                page = 0;
                rebuildWidgets();
            }).bounds(tx, top + 22, 80, 18).build();
            b.active = t != tab;
            addRenderableWidget(b);
            tx += 84;
        }
        if (data.getInt("complexId") < 0 || !data.contains("complex")) return;
        int x = left + 14, y = top + 50;
        switch (tab) {
            case STATUS -> {
                btn("◀", left + W - 80, top + H - 26, 30, () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
                btn("▶", left + W - 44, top + H - 26, 30, () -> {
                    page++;
                    rebuildWidgets();
                });
            }
            case CARDS -> {
                box("cdong", x + 24, y, 50, 4, "\\d{0,4}", "동");
                box("cho", x + 100, y, 50, 4, "\\d{0,4}", "호");
                box("holder", x + 196, y, 90, 16, null, "소유자");
                btn(allAccess ? "단지 전체: 켬" : "단지 전체: 끔", x + 292, y - 1, 120, () -> {
                    allAccess = !allAccess;
                    rebuildWidgets();
                });
                btn("카드 등록 (카드등록용 카드 1장 사용)", x, y + 22, 300, () -> {
                    if (!allAccess && (num("cdong") < 0 || num("cho") < 0)) return;
                    CompoundTag t = new CompoundTag();
                    t.putInt("dong", Math.max(0, num("cdong")));
                    t.putInt("ho", Math.max(0, num("cho")));
                    t.putBoolean("all", allAccess);
                    t.putString("holder", val("holder"));
                    send("comp_card_register", t);
                });
                ListTag cards = data.getList("cards", Tag.TAG_COMPOUND);
                int start = page * ROWS;
                for (int i = 0; i < ROWS && start + i < cards.size(); i++) {
                    CompoundTag c = cards.getCompound(start + i);
                    int ry = y + 66 + i * 20;
                    int id = c.getInt("id");
                    boolean revoked = c.getBoolean("revoked");
                    btn(revoked ? "재개" : "중지", left + W - 110, ry - 2, 44, () -> {
                        CompoundTag t = new CompoundTag();
                        t.putInt("id", id);
                        t.putBoolean("revoked", !revoked);
                        send("comp_card_revoke", t);
                    });
                    btn("삭제", left + W - 60, ry - 2, 44, () -> {
                        CompoundTag t = new CompoundTag();
                        t.putInt("id", id);
                        send("comp_card_delete", t);
                    });
                }
                btn("◀", left + 14, top + H - 26, 30, () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
                btn("▶", left + 50, top + H - 26, 30, () -> {
                    if ((page + 1) * ROWS < cards.size()) page++;
                    rebuildWidgets();
                });
            }
            case PASSWORDS -> {
                box("common", x + 90, y, 90, 8, "\\d{0,8}", "숫자 4~8자리");
                btn("저장", x + 186, y - 1, 50, () -> {
                    CompoundTag t = new CompoundTag();
                    t.putString("pw", val("common"));
                    send("comp_common_pw", t);
                });
                btn("삭제", x + 240, y - 1, 50, () -> {
                    CompoundTag t = new CompoundTag();
                    t.putString("pw", "");
                    send("comp_common_pw", t);
                });
                int y2 = y + 46;
                box("pdong", x + 24, y2, 50, 4, "\\d{0,4}", "동");
                box("pho", x + 100, y2, 50, 4, "\\d{0,4}", "호");
                box("ppw", x + 176, y2, 90, 8, "\\d{0,8}", "새 비밀번호");
                btn("변경", x + 272, y2 - 1, 50, () -> {
                    if (num("pdong") < 0 || num("pho") < 0) return;
                    CompoundTag t = new CompoundTag();
                    t.putInt("dong", num("pdong"));
                    t.putInt("ho", num("pho"));
                    t.putString("pw", val("ppw"));
                    send("comp_set_pw", t);
                });
                btn("초기화", x + 326, y2 - 1, 50, () -> {
                    if (num("pdong") < 0 || num("pho") < 0) return;
                    CompoundTag t = new CompoundTag();
                    t.putInt("dong", num("pdong"));
                    t.putInt("ho", num("pho"));
                    t.putString("pw", "");
                    send("comp_set_pw", t);
                });
            }
            case NOTICE -> {
                if (!values.containsKey("notice")) values.put("notice", data.getString("notice"));
                box("notice", x, y, W - 28, 200, null, "공지 내용");
                btn("월패드로 전송", x, y + 22, 120, () -> {
                    CompoundTag t = new CompoundTag();
                    t.putString("text", val("notice"));
                    send("comp_notice", t);
                });
                btn("공지 내리기", x + 126, y + 22, 100, () -> {
                    values.put("notice", "");
                    if (boxes.get("notice") != null) boxes.get("notice").setValue("");
                    CompoundTag t = new CompoundTag();
                    t.putString("text", "");
                    send("comp_notice", t);
                });
            }
        }
    }

    @Override
    public void tick() {
        for (EditBox b : boxes.values()) b.tick();
        if (++ticks % 40 == 0) send("refresh", null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // 모니터 베젤
        g.fill(left - 6, top - 6, left + W + 6, top + H + 6, 0xFF1A1C20);
        g.fill(left, top, left + W, top + H, 0xFF0E1620);
        g.fill(left, top, left + W, top + 18, 0xFF2B6CB0);
        String cx = data.getString("complex");
        g.drawString(font, "단지 관리 프로그램  ·  " + (cx.isEmpty() ? "단지 구획 밖" : cx), left + 8, top + 5, 0xFFFFFF, false);
        if (data.getInt("complexId") < 0 || cx.isEmpty()) {
            g.drawCenteredString(font, "이 컴퓨터는 단지 구획 밖에 있습니다.", left + W / 2, top + 110, 0xFF6B6B);
            g.drawCenteredString(font, "단지 대시보드 지도에서 구획을 만든 뒤 사용하세요.", left + W / 2, top + 126, 0x9AA7B8);
            super.render(g, mx, my, pt);
            return;
        }
        int x = left + 14, y = top + 50;
        switch (tab) {
            case STATUS -> drawStatus(g, x, y);
            case CARDS -> drawCards(g, x, y);
            case PASSWORDS -> drawPasswords(g, x, y);
            case NOTICE -> drawNotice(g, x, y);
        }
        super.render(g, mx, my, pt);
    }

    private void drawStatus(GuiGraphics g, int x, int y) {
        CompoundTag cnt = data.getCompound("counts");
        g.drawString(font, "구획 " + data.getInt("zones") + "개  ·  홈 설비 " + data.getInt("controls") + "대", x, y, 0xFFD34E, false);
        int i = 0;
        for (DeviceType t : DeviceType.values()) {
            int cxp = x + (i % 4) * 104, cyp = y + 14 + (i / 4) * 14;
            g.drawString(font, t.korean + " " + cnt.getInt(t.id), cxp, cyp, 0xFFFFFF, false);
            i++;
        }
        ListTag list = data.getList("devices", Tag.TAG_STRING);
        int ly = y + 48;
        g.fill(x - 4, ly - 4, left + W - 10, top + H - 32, 0xFF0A1018);
        int start = page * 10;
        for (int k = 0; k < 10 && start + k < list.size(); k++) {
            g.drawString(font, font.plainSubstrByWidth(list.getString(start + k), W - 40), x, ly + k * 14, 0xC8D0DA, false);
        }
        if (list.isEmpty()) g.drawString(font, "등록된 기기가 없습니다", x, ly, 0x707880, false);
        g.drawString(font, (page + 1) + " / " + Math.max(1, (list.size() + 9) / 10), left + W - 140, top + H - 21, 0x9AA7B8, false);
    }

    private void drawCards(GuiGraphics g, int x, int y) {
        g.drawString(font, "동", x, y + 4, 0xC8D0DA, false);
        g.drawString(font, "호", x + 80, y + 4, 0xC8D0DA, false);
        g.drawString(font, "이름", x + 160, y + 4, 0xC8D0DA, false);
        g.drawString(font, "인벤토리에 카드등록용 카드가 있어야 합니다", x, y + 44, 0x707880, false);
        ListTag cards = data.getList("cards", Tag.TAG_COMPOUND);
        int start = page * ROWS;
        for (int i = 0; i < ROWS && start + i < cards.size(); i++) {
            CompoundTag c = cards.getCompound(start + i);
            int ry = y + 66 + i * 20;
            String who = c.getInt("dong") < 0 ? "단지 전체" : c.getInt("dong") + "동 " + c.getInt("ho") + "호";
            boolean revoked = c.getBoolean("revoked");
            g.drawString(font, "#" + c.getInt("id") + "  " + who + "  " + c.getString("holder"), x, ry + 3, revoked ? 0x707880 : 0xFFFFFF, false);
            g.drawString(font, revoked ? "중지됨" : "사용 중", left + W - 160, ry + 3, revoked ? 0xFF6B6B : 0x4CD27A, false);
        }
        if (cards.isEmpty()) g.drawString(font, "등록된 카드가 없습니다", x, y + 69, 0x707880, false);
    }

    private void drawPasswords(GuiGraphics g, int x, int y) {
        g.drawString(font, "공용 비밀번호", x, y + 4, 0xC8D0DA, false);
        String common = data.getString("commonPw");
        g.drawString(font, common.isEmpty() ? "현재: 없음" : "현재: " + common, x + 300, y + 4, 0x9AA7B8, false);
        g.drawString(font, "세대 비밀번호 변경", x, y + 32, 0xFFD34E, false);
        g.drawString(font, "동", x, y + 50, 0xC8D0DA, false);
        g.drawString(font, "호", x + 80, y + 50, 0xC8D0DA, false);
        g.drawString(font, "세대 비밀번호가 설정된 세대", x, y + 76, 0xFFD34E, false);
        ListTag pws = data.getList("householdPws", Tag.TAG_COMPOUND);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pws.size(); i++) {
            if (i > 0) sb.append(",  ");
            sb.append(pws.getCompound(i).getInt("dong")).append("동 ").append(pws.getCompound(i).getInt("ho")).append("호");
        }
        int ly = y + 90;
        for (FormattedCharSequence l : font.split(Component.literal(pws.isEmpty() ? "없음" : sb.toString()), W - 40)) {
            g.drawString(font, l, x, ly, 0xFFFFFF, false);
            ly += 11;
        }
        g.drawString(font, "로비폰: [열쇠] → 공용번호 # / 호*세대번호 #", x, top + H - 20, 0x707880, false);
    }

    private void drawNotice(GuiGraphics g, int x, int y) {
        g.drawString(font, "현재 월패드 공지", x, y + 50, 0xFFD34E, false);
        String n = data.getString("notice");
        int ly = y + 64;
        for (FormattedCharSequence l : font.split(Component.literal(n.isEmpty() ? "없음" : n), W - 40)) {
            g.drawString(font, l, x, ly, 0xFFFFFF, false);
            ly += 11;
        }
    }
}
