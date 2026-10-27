package com.mchoe.smarthome.client;

import com.mchoe.smarthome.block.DashboardBlock;
import com.mchoe.smarthome.blockentity.DashboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class DashboardRenderer implements BlockEntityRenderer<DashboardBlockEntity> {
    private final Font font;

    public DashboardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(DashboardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof DashboardBlock)) return;
        float x1 = 2, y1 = 3, x2 = 14, y2 = 13;
        float w = x2 - x1, h = y2 - y1;
        float ts = 0.09f;
        float line = 9 * ts;
        ScreenPainter p = new ScreenPainter(pose, buffers, font);
        p.begin(be.getBlockState().getValue(DashboardBlock.FACING), x1, y1, x2, y2, 15f);
        p.rect(0, 0, w, h, 0xFF101820);
        p.rect(0, 0, w, line + 0.4f, 0xFF2B6CB0);
        p.text("단지 대시보드", 0.4f, 0.25f, ts, 0xFFFFFFFF, false, w - 0.8f);
        float y = line + 1.0f;
        p.text(be.getComplexName(), w / 2f, y, ts * 1.6f, 0xFFFFFFFF, true, w - 0.8f);
        y += line * 1.6f + 0.8f;
        p.text("연동 기기 " + be.getDeviceCount() + "대", w / 2f, y, ts, 0xFF4CD27A, true, w - 0.8f);
        y += line + 0.6f;
        p.text("구획 X " + Math.min(be.getX1(), be.getX2()) + " ~ " + Math.max(be.getX1(), be.getX2()), w / 2f, y, ts * 0.85f, 0xFF9AA7B8, true, w - 0.8f);
        y += line + 0.2f;
        p.text("구획 Z " + Math.min(be.getZ1(), be.getZ2()) + " ~ " + Math.max(be.getZ1(), be.getZ2()), w / 2f, y, ts * 0.85f, 0xFF9AA7B8, true, w - 0.8f);
        // 구획 미니맵
        float mapY = y + line + 0.6f;
        if (mapY < h - 0.6f) {
            p.rect(w / 2f - 1.6f, mapY, w / 2f + 1.6f, h - 0.4f, 0xFF1E3A2A);
            p.rect(w / 2f - 0.15f, (mapY + h - 0.4f) / 2f - 0.15f, w / 2f + 0.15f, (mapY + h - 0.4f) / 2f + 0.15f, 0xFFFFD34E);
        }
        p.end();
    }
}
