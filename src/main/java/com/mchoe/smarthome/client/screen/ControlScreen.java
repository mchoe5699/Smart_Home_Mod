package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.client.ClientHooks;
import com.mchoe.smarthome.logic.ControlKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** 세대 설비(조명·온도조절기·가스밸브·환기·엘리베이터 연동기) 설정·직접 조작 */
public class ControlScreen extends Screen implements DataScreen {
    private static final int W = 260, H = 196;
    private final BlockPos pos;
    private CompoundTag data;
    private final ControlKind kind;
    private EditBox room;
    private Button stateBtn;
    private int left, top;

    public ControlScreen(BlockPos pos, CompoundTag data) {
        super(Component.literal("설비"));
        this.pos = pos;
        this.data = data;
        this.kind = ControlKind.byId(data.getString("ckind"));
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void updateData(CompoundTag d) {
        this.data = d;
        if (stateBtn != null) stateBtn.setMessage(Component.literal(stateLabel()));
    }

    private String stateLabel() {
        boolean on = data.getBoolean("on");
        return switch (kind) {
            case LIGHT -> on ? "켜짐 (끄기)" : "꺼짐 (켜기)";
            case GAS_VALVE -> on ? "열림 (잠그기)" : "잠김 (열기)";
            case THERMOSTAT -> on ? "난방 켜짐 " + data.getInt("temp") + "℃" : "난방 꺼짐";
            case VENTILATION -> on ? data.getInt("level") + "단 가동 (정지)" : "정지 (가동)";
            case ELEVATOR_LINK -> "호출 신호 보내기";
            case MOTION_OUTDOOR, MOTION_INDOOR -> on ? "감지 중 (외출/방범모드)" : "대기 (외출/방범모드에서 작동)";
        };
    }

    private EditBox box(int x, int y, int w, int max, String filter, String value) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.empty());
        b.setMaxLength(max);
        if (filter != null) b.setFilter(s -> s.matches(filter));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        room = box(left + 70, top + 58, 170, 12, null, data.getString("room"));
        room.setHint(Component.literal("예: 거실, 안방").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(Button.builder(Component.literal("방 이름 저장").withStyle(ChatFormatting.GREEN), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("room", room.getValue());
            ClientHooks.send(pos, "ctl_settings", t);
        }).bounds(left + 12, top + 82, W - 24, 20).build());

        stateBtn = addRenderableWidget(Button.builder(Component.literal(stateLabel()), b -> {
            if (kind == ControlKind.VENTILATION) {
                CompoundTag t = new CompoundTag();
                t.putInt("level", data.getBoolean("on") ? 0 : Math.max(1, data.getInt("level")));
                ClientHooks.send(pos, "ctl_level", t);
            } else {
                ClientHooks.send(pos, "ctl_toggle", null);
            }
        }).bounds(left + 12, top + 120, W - 24, 20).build());
        if (kind == ControlKind.THERMOSTAT) {
            addRenderableWidget(Button.builder(Component.literal("− 1℃"), b -> temp(-1)).bounds(left + 12, top + 144, (W - 30) / 2, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+ 1℃"), b -> temp(1)).bounds(left + W - 12 - (W - 30) / 2, top + 144, (W - 30) / 2, 20).build());
        } else if (kind == ControlKind.VENTILATION) {
            for (int i = 1; i <= 3; i++) {
                int lv = i;
                addRenderableWidget(Button.builder(Component.literal(i + "단"), b -> {
                    CompoundTag t = new CompoundTag();
                    t.putInt("level", lv);
                    ClientHooks.send(pos, "ctl_level", t);
                }).bounds(left + 12 + (i - 1) * 80, top + 144, 76, 20).build());
            }
        }
    }

    private void temp(int d) {
        CompoundTag t = new CompoundTag();
        t.putInt("delta", d);
        ClientHooks.send(pos, "ctl_temp", t);
    }

    @Override
    public void tick() {
        room.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF3A3F46);
        g.fill(left, top, left + W, top + H, 0xFF1B1F24);
        g.fill(left, top, left + W, top + 20, 0xFF2EA8A0);
        g.drawString(font, kind.korean, left + 8, top + 6, 0xFFFFFF, false);
        String cx = data.getString("complex");
        g.drawString(font, cx.isEmpty() ? "단지 구획 밖" : cx, left + W - 8 - font.width(cx.isEmpty() ? "단지 구획 밖" : cx), top + 6,
                cx.isEmpty() ? 0xFF6B6B : 0xFFFFFF, false);
        boolean linked = data.getBoolean("linked");
        g.drawString(font, "연동 세대", left + 12, top + 38, 0xC8D0DA, false);
        g.drawString(font, linked ? data.getInt("dong") + "동 " + data.getInt("ho") + "호" : "미연동 (홈 링커로 세대기와 연동)",
                left + 70, top + 38, linked ? 0x4CD27A : 0xFFD34E, false);
        g.drawString(font, "방 이름", left + 12, top + 62, 0xC8D0DA, false);
        g.drawString(font, "연동된 세대의 월패드에서 제어됩니다", left + 12, top + 106, 0x9AA7B8, false);
        if (kind == ControlKind.ELEVATOR_LINK) {
            g.drawString(font, "엘리베이터 호출 버튼 옆에 설치 (레드스톤)", left + 12, top + 172, 0x707880, false);
        }
        super.render(g, mx, my, pt);
    }
}
