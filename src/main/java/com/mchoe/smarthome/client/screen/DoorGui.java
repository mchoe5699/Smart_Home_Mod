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
        body(g, tex(cam ? "door_camera" : "door_phone"));
        int cx = x + w / 2;
        String who = data.getBoolean("linked") ? DeviceLogic.num(data.getInt("dong")) + "동 " + DeviceLogic.num(data.getInt("ho")) + "호" : "미연동";
        centered(g, who, cx, y + 84, cam ? 0xFFFFFF : 0x22252A);

        int st = status();
        boolean active = st != DeviceLogic.STATUS_IDLE;
        int bell = active ? 0xFFE5A84D : 0xFFE8EAEC;
        boolean hover = (mx - cx) * (mx - cx) + (my - (y + 130)) * (my - (y + 130)) < 28 * 28;
        circle(g, cx, y + 130, 27, hover ? brighten(bell, 10) : bell);
        circle(g, cx, y + 130, 22, brighten(bell, 12));
        icon(g, I_CALL, cx - 10, y + 120, 20, active ? 0xFFFFFF : 0x4C9AE5);
        touch(cx - 28, y + 102, 56, 56, () -> {
            if (idle()) act("ring");
        });
        String stText = data.getBoolean("doorOpen") ? "문이 열렸습니다" : active ? statusText() : "벨을 누르세요";
        centered(g, stText, cx, y + 170, data.getBoolean("doorOpen") ? 0x4CD27A : (cam ? 0xFFFFFF : 0x22252A));
        if (active) button(g, x + 20, y + 186, w - 40, 20, "종료", 0xFFB43A3A, 0xFFFFFFFF, mx, my, () -> act("hangup"));
        centered(g, cam ? "영상 + 음성 송출" : "음성 송출", cx, y + h - 26, 0xFF9AA7B8);
        if (!data.getBoolean("linked")) centered(g, "홈 링커로 세대기와 연동하세요", cx, y + h - 40, 0xFFFFD34E);
    }
}
