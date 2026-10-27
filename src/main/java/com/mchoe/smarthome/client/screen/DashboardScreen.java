package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.blockentity.DashboardBlockEntity;
import com.mchoe.smarthome.client.ClientHooks;
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

/** 단지 대시보드: 단지 이름·구획 설정과 구획 안 기기 목록 */
public class DashboardScreen extends Screen implements DataScreen {
    private static final int W = 360;
    private static final int H = 230;
    private static final int LIST_LINES = 10;

    private final BlockPos pos;
    private CompoundTag data;
    private int page;
    private int ticks;
    private String error = "";

    private EditBox name, x1, z1, x2, z2;
    private int left, top;

    public DashboardScreen(BlockPos pos, CompoundTag data) {
        super(Component.literal("단지 대시보드"));
        this.pos = pos;
        this.data = data;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void updateData(CompoundTag newData) {
        this.data = newData;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int fx = left + 10;
        int y = top + 38;

        String keepName = name != null ? name.getValue() : data.getString("name");
        name = new EditBox(font, fx + 50, y, 110, 16, Component.literal("단지 이름"));
        name.setMaxLength(24);
        name.setValue(keepName);
        addRenderableWidget(name);

        y += 24;
        x1 = coordBox(fx + 22, y, x1, data.getInt("x1"));
        z1 = coordBox(fx + 22 + 82, y, z1, data.getInt("z1"));
        y += 22;
        x2 = coordBox(fx + 22, y, x2, data.getInt("x2"));
        z2 = coordBox(fx + 22 + 82, y, z2, data.getInt("z2"));

        y += 26;
        addRenderableWidget(Button.builder(Component.literal("이 위치 기준 ±" + DashboardBlockEntity.DEFAULT_RADIUS), b -> {
            int r = DashboardBlockEntity.DEFAULT_RADIUS;
            x1.setValue(String.valueOf(pos.getX() - r));
            z1.setValue(String.valueOf(pos.getZ() - r));
            x2.setValue(String.valueOf(pos.getX() + r));
            z2.setValue(String.valueOf(pos.getZ() + r));
        }).bounds(fx, y, 160, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("내 위치를 시작점으로"), b -> {
            if (minecraft.player == null) return;
            x1.setValue(String.valueOf(minecraft.player.getBlockX()));
            z1.setValue(String.valueOf(minecraft.player.getBlockZ()));
        }).bounds(fx, y, 160, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("내 위치를 끝점으로"), b -> {
            if (minecraft.player == null) return;
            x2.setValue(String.valueOf(minecraft.player.getBlockX()));
            z2.setValue(String.valueOf(minecraft.player.getBlockZ()));
        }).bounds(fx, y, 160, 20).build());

        addRenderableWidget(Button.builder(Component.literal("구획 저장").withStyle(ChatFormatting.GREEN), b -> save())
                .bounds(fx, top + H - 28, 160, 20).build());

        int lx = left + 184;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> page = Math.max(0, page - 1))
                .bounds(lx, top + H - 28, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> page = Math.min(maxPage(), page + 1))
                .bounds(left + W - 40, top + H - 28, 30, 20).build());
    }

    private EditBox coordBox(int x, int y, EditBox old, int value) {
        EditBox b = new EditBox(font, x, y, 56, 16, Component.literal("좌표"));
        b.setMaxLength(8);
        b.setFilter(s -> s.matches("-?\\d{0,7}"));
        b.setValue(old != null ? old.getValue() : String.valueOf(value));
        addRenderableWidget(b);
        return b;
    }

    private int maxPage() {
        int n = data.getList("devices", Tag.TAG_STRING).size();
        return Math.max(0, (n - 1) / LIST_LINES);
    }

    private void save() {
        try {
            CompoundTag t = new CompoundTag();
            t.putString("name", name.getValue());
            t.putInt("x1", Integer.parseInt(x1.getValue()));
            t.putInt("z1", Integer.parseInt(z1.getValue()));
            t.putInt("x2", Integer.parseInt(x2.getValue()));
            t.putInt("z2", Integer.parseInt(z2.getValue()));
            error = "";
            ClientHooks.send(pos, "dash_save", t);
        } catch (NumberFormatException e) {
            error = "좌표를 정확히 입력하세요";
        }
    }

    @Override
    public void tick() {
        name.tick();
        x1.tick();
        z1.tick();
        x2.tick();
        z2.tick();
        if (++ticks % 40 == 0) ClientHooks.send(pos, "refresh", null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF3A3F46);
        g.fill(left, top, left + W, top + H, 0xFF101820);
        g.fill(left, top, left + W, top + 20, 0xFF2B6CB0);
        g.drawString(font, "단지 대시보드 · " + data.getString("name"), left + 8, top + 6, 0xFFFFFF, false);

        int fx = left + 10;
        g.drawString(font, "단지 구획 설정 (구획 안 기기끼리만 연동)", fx, top + 26, 0xFFD34E, false);
        g.drawString(font, "이름", fx, top + 42, 0xC8D0DA, false);
        g.drawString(font, "시작", fx - 2, top + 66, 0x9AA7B8, false);
        g.drawString(font, "X", fx + 14, top + 66, 0xC8D0DA, false);
        g.drawString(font, "Z", fx + 96, top + 66, 0xC8D0DA, false);
        g.drawString(font, "끝", fx, top + 88, 0x9AA7B8, false);
        g.drawString(font, "X", fx + 14, top + 88, 0xC8D0DA, false);
        g.drawString(font, "Z", fx + 96, top + 88, 0xC8D0DA, false);

        // 기기 목록
        int lx = left + 184, ly = top + 26, lw = W - 194;
        g.fill(lx, ly, lx + lw, top + H - 34, 0xFF0B1A2E);
        ListTag list = data.getList("devices", Tag.TAG_STRING);
        g.drawString(font, "연동 기기 " + list.size() + "대  (" + (page + 1) + "/" + (maxPage() + 1) + ")", lx + 5, ly + 5, 0x4CD27A, false);
        int start = page * LIST_LINES;
        for (int i = 0; i < LIST_LINES && start + i < list.size(); i++) {
            String s = list.getString(start + i);
            g.drawString(font, font.plainSubstrByWidth(s, lw - 10), lx + 5, ly + 19 + i * 15, 0xFFFFFF, false);
        }
        if (list.isEmpty()) g.drawString(font, "구획 안에 기기가 없습니다", lx + 5, ly + 19, 0x9AA7B8, false);

        if (!error.isEmpty()) g.drawString(font, error, fx, top + H - 40, 0xFF6B6B, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
