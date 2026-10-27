package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 번호 설정 화면 (모든 번호 0~9999).
 * 세대기(월패드·비디오폰·인터폰): 관할경비실 + 동 + 호
 * 로비폰: 관할경비실 + 동 + 로비번호 (동 9999 = 단지 외곽 로비폰)
 * 경비실기: 경비실번호 · 도어카메라·도어폰: 동 + 호
 */
public class SettingsScreen extends Screen implements DataScreen {
    private static final int W = 260;

    private final DeviceGui parent;
    private final DeviceType type;
    private final List<Row> rows = new ArrayList<>();
    private String error = "";
    private boolean clearAdmin;
    private int left, top, h;

    private record Row(String key, String label, EditBox box) {}

    public SettingsScreen(DeviceGui parent) {
        super(Component.literal("설정"));
        this.parent = parent;
        this.type = parent.getType();
    }

    @Override
    public BlockPos getPos() {
        return parent.getPos();
    }

    @Override
    public void updateData(CompoundTag data) {
        parent.updateData(data);
    }

    private String title() {
        return switch (type.settings) {
            case UNIT -> "관할경비실 및 세대번호(동·호) 설정";
            case LOBBY -> "관할경비실 및 동·로비번호 설정";
            case GUARD -> "경비실번호 설정";
            case DOOR -> "세대번호(동·호) 설정";
        };
    }

    @Override
    protected void init() {
        List<String> keep = new ArrayList<>();
        for (Row r : rows) keep.add(r.box.getValue());
        rows.clear();

        CompoundTag d = parent.getData();
        List<String[]> spec = new ArrayList<>();
        switch (type.settings) {
            case UNIT -> {
                spec.add(new String[]{"guard", "관할경비실 번호"});
                spec.add(new String[]{"dong", "동 번호"});
                spec.add(new String[]{"ho", "호 번호"});
            }
            case LOBBY -> {
                spec.add(new String[]{"guard", "관할경비실 번호"});
                spec.add(new String[]{"dong", "동 번호 (9999=단지 외곽)"});
                spec.add(new String[]{"ho", "로비 번호"});
            }
            case GUARD -> spec.add(new String[]{"guard", "경비실 번호"});
            case DOOR -> {
                spec.add(new String[]{"dong", "동 번호"});
                spec.add(new String[]{"ho", "호 번호"});
            }
        }
        spec.add(new String[]{"adminPw", "관리 비밀번호 (변경 시만)"});
        h = 56 + spec.size() * 26 + 40 + 24;
        left = (width - W) / 2;
        top = (height - h) / 2;

        for (int i = 0; i < spec.size(); i++) {
            String key = spec.get(i)[0];
            EditBox box = new EditBox(font, left + W - 90, top + 34 + i * 26, 78, 18, Component.literal(spec.get(i)[1]));
            boolean admin = key.equals("adminPw");
            box.setMaxLength(admin ? 8 : 4);
            box.setFilter(admin ? s -> s.matches("\\d{0,8}") : s -> s.matches("\\d{0,4}"));
            String init = i < keep.size() ? keep.get(i) : admin ? "" : (d.getInt(key) >= 0 ? String.valueOf(d.getInt(key)) : "");
            box.setValue(init);
            box.setHint(Component.literal(admin ? "4~8자리" : "0~9999").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(box);
            rows.add(new Row(key, spec.get(i)[1], box));
        }
        if (!rows.isEmpty()) setInitialFocus(rows.get(0).box);

        if (d.getBoolean("adminSet")) {
            addRenderableWidget(Button.builder(Component.literal(clearAdmin ? "관리 비밀번호 해제: 예" : "관리 비밀번호 해제: 아니오"), b -> {
                clearAdmin = !clearAdmin;
                b.setMessage(Component.literal(clearAdmin ? "관리 비밀번호 해제: 예" : "관리 비밀번호 해제: 아니오"));
            }).bounds(left + 12, top + h - 56, W - 24, 18).build());
        }
        int by = top + h - 30;
        addRenderableWidget(Button.builder(Component.literal("저장").withStyle(ChatFormatting.GREEN), b -> save())
                .bounds(left + 12, by, (W - 30) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("취소"), b -> onClose())
                .bounds(left + W - 12 - (W - 30) / 2, by, (W - 30) / 2, 20).build());
    }

    private void save() {
        CompoundTag args = new CompoundTag();
        String newAdmin = "";
        for (Row r : rows) {
            String v = r.box.getValue().trim();
            if (r.key.equals("adminPw")) {
                if (!v.isEmpty() && v.length() < 4) {
                    error = "관리 비밀번호는 4~8자리입니다";
                    return;
                }
                newAdmin = v;
                args.putString("adminPw", v);
                continue;
            }
            if (v.isEmpty()) {
                error = r.label + "를 입력하세요";
                return;
            }
            int n = Integer.parseInt(v);
            if (n < 0 || n > DeviceLogic.MAX_NUMBER) {
                error = "0~9999 사이로 입력하세요";
                return;
            }
            args.putInt(r.key, n);
        }
        args.putBoolean("adminClear", clearAdmin);
        parent.act("settings", args);
        parent.adminChanged(newAdmin, clearAdmin);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        for (Row r : rows) r.box.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + W + 2, top + h + 2, 0xFF3A3F46);
        g.fill(left, top, left + W, top + h, 0xFF1B1F24);
        g.fill(left, top, left + W, top + 20, 0xFF1E4D8C);
        g.drawString(font, title(), left + 8, top + 6, 0xFFFFFF, false);
        for (int i = 0; i < rows.size(); i++) {
            g.drawString(font, rows.get(i).label, left + 12, top + 39 + i * 26, 0xC8D0DA, false);
        }
        String help = switch (type.settings) {
            case UNIT -> "같은 관할경비실 번호의 기기끼리 연동됩니다";
            case LOBBY -> "동 번호가 같은 세대 호출 · 9999동은 전체";
            case GUARD -> "이 번호를 관할로 설정한 기기가 우선 연결";
            case DOOR -> "이 동·호의 세대 내부기기로 송출됩니다";
        };
        g.drawCenteredString(font, help, left + W / 2, top + h - 72, 0x9AA7B8);
        if (!error.isEmpty()) g.drawCenteredString(font, error, left + W / 2, top + 24, 0xFF6B6B);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
