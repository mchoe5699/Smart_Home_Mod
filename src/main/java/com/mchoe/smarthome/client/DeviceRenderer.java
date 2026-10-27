package com.mchoe.smarthome.client;

import com.mchoe.smarthome.block.DeviceBlock;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 기기 앞면 화면(LCD) 표시. 카메라 호출이면 실시간 영상을 띄운다. */
public class DeviceRenderer implements BlockEntityRenderer<DeviceBlockEntity> {
    private static final int BG_NAVY = 0xFF0B1A2E;
    private static final int BAR_BLUE = 0xFF1E4D8C;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF9AA7B8;
    private static final int GREEN = 0xFF4CD27A;
    private static final int RED = 0xFFE5484D;
    private static final int YELLOW = 0xFFFFD34E;

    private final Font font;

    public DeviceRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public void render(DeviceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DeviceBlock block)) return;
        DeviceType type = block.type;
        Direction facing = state.getValue(DeviceBlock.FACING);
        float[] s = type.screen;
        float w = s[2] - s[0];
        float h = s[3] - s[1];
        boolean blink = (be.getLevel().getGameTime() / 8) % 2 == 0;
        boolean doorOpen = state.getValue(DeviceBlock.POWERED);

        ScreenPainter p = new ScreenPainter(pose, buffers, font);
        p.begin(facing, s[0], s[1], s[2], s[3], s[4]);
        float ts = type.textScale;
        switch (type) {
            case WALLPAD, VIDEOPHONE, VIDEOPHONE_BASIC, GUARD_STATION -> drawVideoScreen(p, be, type, w, h, ts, blink);
            case LOBBY_PHONE -> drawLobby(p, be, w, h, ts, blink, doorOpen);
            case INTERPHONE, INTERPHONE_BASIC -> drawInterphone(p, be, w, h, ts, blink);
            case DOOR_CAMERA, DOOR_PHONE -> drawDoorLed(p, be, w, h, ts, blink, doorOpen);
        }
        p.end();
    }

    private static boolean playerNear(BlockPos pos, double range) {
        var player = Minecraft.getInstance().player;
        return player != null && player.distanceToSqr(Vec3.atCenterOf(pos)) < range * range;
    }

    private static String status(int st) {
        return switch (st) {
            case DeviceLogic.STATUS_RINGING -> "호출 수신";
            case DeviceLogic.STATUS_CALLING -> "호출 중...";
            case DeviceLogic.STATUS_CONNECTED -> "통화 중";
            default -> "대기";
        };
    }

    private void drawVideoScreen(ScreenPainter p, DeviceBlockEntity be, DeviceType type, float w, float h, float ts, boolean blink) {
        int st = be.getStatus();
        boolean idle = st == DeviceLogic.STATUS_IDLE;
        float line = 9 * ts;

        if (idle && type.isVideophone()) {
            p.rect(0, 0, w, h, 0xFF050607);
            p.text(be.getClock(), w - 0.3f - font.width(be.getClock()) * ts * 0.7f, h - line - 0.2f, ts * 0.7f, 0xFF3A4654, false, 0);
            return;
        }
        p.rect(0, 0, w, h, BG_NAVY);

        // 실시간 영상
        BlockPos cam = be.getCamPos();
        if (!idle && cam != null && playerNear(be.getBlockPos(), 16)) {
            CameraFeed feed = CameraFeed.get(cam, 6);
            if (feed != null) {
                p.feed(feed, 0, 0, w, h);
                float bar = line + 0.4f;
                p.rect(0, h - bar, w, h, 0xC0000000);
                int c = st == DeviceLogic.STATUS_CONNECTED ? GREEN : (blink ? YELLOW : WHITE);
                p.text("● " + status(st) + "  " + be.getPeer(), 0.3f, h - bar + 0.2f, ts, c, false, w - 0.6f);
                if (blink) p.rect(w - 0.8f, 0.3f, w - 0.3f, 0.8f, RED);
                return;
            }
        }

        float bar = line + 0.3f;
        p.rect(0, 0, w, bar, BAR_BLUE);
        String title = type == DeviceType.GUARD_STATION
                ? "경비실 " + DeviceLogic.num(be.getGuard())
                : DeviceLogic.num(be.getDong()) + "동 " + DeviceLogic.num(be.getHo()) + "호";
        p.text(title, 0.3f, 0.2f, ts, WHITE, false, w * 0.55f);
        p.text(be.getClock(), w - 0.3f - font.width(be.getClock()) * ts, 0.2f, ts, WHITE, false, 0);

        float y = bar + 0.4f;
        if (idle) {
            p.text(be.getClock(), w / 2f, y + 0.2f, ts * 2.2f, WHITE, true, w * 0.8f);
            y += line * 2.4f + 0.4f;
            String cx = be.getComplexName().isEmpty() ? "단지 구획 밖" : be.getComplexName();
            p.text(cx, w / 2f, y, ts, GREY, true, w * 0.9f);
            float tileY = h - line * 1.8f - 0.3f;
            String[] menu = type == DeviceType.GUARD_STATION
                    ? new String[]{"세대", "로비", "CCTV", "기록"}
                    : new String[]{"방문자", "조명", "난방", "가스", "환기", "경비실"};
            float tw = (w - 0.4f) / menu.length;
            for (int i = 0; i < menu.length; i++) {
                float tx = 0.2f + i * tw;
                p.rect(tx + 0.1f, tileY, tx + tw - 0.1f, h - 0.3f, 0xFF173A63);
                p.text(menu[i], tx + tw / 2f, tileY + 0.35f, ts * 0.8f, WHITE, true, tw - 0.3f);
            }
            return;
        }
        int stColor = st == DeviceLogic.STATUS_CONNECTED ? GREEN : st == DeviceLogic.STATUS_RINGING ? (blink ? YELLOW : RED) : YELLOW;
        p.text(status(st), w / 2f, y + line, ts * 1.6f, stColor, true, w - 0.6f);
        p.text(be.getPeer(), w / 2f, y + line * 3f, ts, WHITE, true, w - 0.6f);
        p.text("음성 통화", w / 2f, y + line * 4.4f, ts * 0.8f, GREY, true, w - 0.6f);
    }

    private void drawLobby(ScreenPainter p, DeviceBlockEntity be, float w, float h, float ts, boolean blink, boolean doorOpen) {
        int st = be.getStatus();
        float line = 9 * ts;
        p.rect(0, 0, w, h, BG_NAVY);
        float bar = line + 0.3f;
        p.rect(0, 0, w, bar, BAR_BLUE);
        String title = be.getDong() == DeviceLogic.OUTER_LOBBY_DONG
                ? "단지 출입구 " + DeviceLogic.num(be.getHo())
                : DeviceLogic.num(be.getDong()) + "동 로비 " + DeviceLogic.num(be.getHo());
        p.text(title, 0.3f, 0.2f, ts, WHITE, false, w * 0.65f);
        p.text(be.getClock(), w - 0.3f - font.width(be.getClock()) * ts, 0.2f, ts, WHITE, false, 0);
        float y = bar + 0.6f;
        if (doorOpen) {
            p.rect(0.3f, y, w - 0.3f, h - 0.3f, 0xFF0F3D24);
            p.text("문이 열렸습니다", w / 2f, (y + h) / 2f - line / 2f, ts * 1.2f, GREEN, true, w - 0.8f);
            return;
        }
        if (st == DeviceLogic.STATUS_IDLE) {
            p.text("호수를 누르고 [호출]", w / 2f, y, ts, WHITE, true, w - 0.6f);
            p.text("[열쇠] 비밀번호 · 카드 태그", w / 2f, y + line + 0.3f, ts * 0.85f, GREY, true, w - 0.6f);
            p.text("[경비실] 경비실 호출", w / 2f, h - line - 0.3f, ts * 0.8f, GREY, true, w - 0.6f);
            return;
        }
        int c = st == DeviceLogic.STATUS_CONNECTED ? GREEN : (blink ? YELLOW : WHITE);
        p.text(status(st), w / 2f, y, ts * 1.4f, c, true, w - 0.6f);
        p.text(be.getPeer(), w / 2f, y + line * 1.7f, ts, WHITE, true, w - 0.6f);
    }

    private void drawInterphone(ScreenPainter p, DeviceBlockEntity be, float w, float h, float ts, boolean blink) {
        int st = be.getStatus();
        int lcd = st == DeviceLogic.STATUS_RINGING && blink ? 0xFFE8D27A : 0xFF9DB596;
        p.rect(0, 0, w, h, lcd);
        int ink = 0xFF1C2A1C;
        String top = st == DeviceLogic.STATUS_IDLE
                ? DeviceLogic.num(be.getDong()) + "-" + DeviceLogic.num(be.getHo())
                : status(st);
        p.text(top, w / 2f, 0.25f, ts, ink, true, w - 0.3f);
        String bottom = st == DeviceLogic.STATUS_IDLE ? be.getClock() : be.getPeer();
        p.text(bottom, w / 2f, h / 2f + 0.1f, ts * 0.8f, ink, true, w - 0.3f);
    }

    private void drawDoorLed(ScreenPainter p, DeviceBlockEntity be, float w, float h, float ts, boolean blink, boolean doorOpen) {
        int st = be.getStatus();
        int color;
        String text;
        if (doorOpen) {
            color = 0xFF1B8F5A;
            text = "OPEN";
        } else if (st == DeviceLogic.STATUS_CONNECTED) {
            color = 0xFF2E9E4F;
            text = "통화중";
        } else if (st == DeviceLogic.STATUS_CALLING) {
            color = blink ? 0xFFD9A400 : 0xFF6E5300;
            text = "호출중";
        } else {
            color = 0xFF10304A;
            text = "";
        }
        p.rect(0, 0, w, h, color);
        p.text(text, w / 2f, (h - 9 * ts) / 2f, ts, WHITE, true, w - 0.2f);
    }
}
