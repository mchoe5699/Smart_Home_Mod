package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** 도어카메라(영상+음성) / 도어폰(음성): 세대 현관 밖 초인종 */
public class DoorGui extends DeviceGui {
    public DoorGui(BlockPos pos, CompoundTag data) {
        super(pos, data, 150, 240);
    }

    @Override
    protected void drawDevice(GuiGraphics g, int mx, int my) {
        int x = left, y = top, w = panelW, h = panelH;
        boolean cam = type == DeviceType.DOOR_CAMERA;
        int body = cam ? 0xFF3A3D42 : 0xFFB9BCC0;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, darken(body, 30));
        g.fill(x, y, x + w, y + h, body);
        int cx = x + w / 2;
        if (cam) {
            circle(g, cx, y + 40, 22, 0xFF111214);
            circle(g, cx, y + 40, 12, 0xFF1E2B4A);
            circle(g, cx - 4, y + 36, 3, 0xFFA0C0FF);
            for (int i = 0; i < 4; i++) circle(g, cx - 26 + i * 52 / 3, y + 70, 2, 0xFF7A1F1F);
        } else {
            for (int r = 0; r < 6; r++) for (int c = 0; c < 8; c++) circle(g, cx - 28 + c * 8, y + 22 + r * 8, 1, 0xFF3A3D42);
        }
        String who = DeviceLogic.num(data.getInt("dong")) + "동 " + DeviceLogic.num(data.getInt("ho")) + "호";
        centered(g, who, cx, y + 84, cam ? 0xFFFFFF : 0x22252A);

        int st = status();
        boolean active = st != DeviceLogic.STATUS_IDLE;
        int bell = active ? 0xFFE5A84D : 0xFFE8EAEC;
        boolean hover = (mx - cx) * (mx - cx) + (my - (y + 130)) * (my - (y + 130)) < 28 * 28;
        circle(g, cx, y + 130, 30, 0xFF6C7076);
        circle(g, cx, y + 130, 27, hover ? brighten(bell, 10) : bell);
        circle(g, cx, y + 130, 8, 0xFF4C9AE5);
        touch(cx - 28, y + 102, 56, 56, () -> {
            if (idle()) act("ring");
        });
        String stText = data.getBoolean("doorOpen") ? "문이 열렸습니다" : active ? statusText() : "벨을 누르세요";
        centered(g, stText, cx, y + 170, data.getBoolean("doorOpen") ? 0x4CD27A : (cam ? 0xFFFFFF : 0x22252A));
        if (active) button(g, x + 20, y + 186, w - 40, 20, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
        centered(g, cam ? "영상 + 음성 송출" : "음성 송출", cx, y + h - 26, 0xFF9AA7B8);
        button(g, x + w - 18, y + h - 16, 14, 12, "⚙", darken(body, 10), 0xFFFFFFFF, mx, my, this::openSettings);
    }
}
