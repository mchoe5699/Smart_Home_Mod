package com.mchoe.smarthome.client;

import com.mchoe.smarthome.block.ControlBlock;
import com.mchoe.smarthome.blockentity.ControlBlockEntity;
import com.mchoe.smarthome.logic.ControlKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** 온도조절기·엘리베이터 연동기 표시창 */
public class ControlRenderer implements BlockEntityRenderer<ControlBlockEntity> {
    private final Font font;

    public ControlRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ControlBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ControlKind kind = be.kind();
        if (kind != ControlKind.THERMOSTAT) return;
        ScreenPainter p = new ScreenPainter(pose, buffers, font);
        p.begin(be.getBlockState().getValue(ControlBlock.FACING), 6.3f, 7.3f, 9.7f, 9.8f, 15.25f);
        float w = 3.4f, h = 2.5f;
        p.rect(0, 0, w, h, be.isOn() ? 0xFF1A3A5A : 0xFF101418);
        if (be.isOn()) {
            p.text(be.getTemp() + "℃", w / 2f, 0.35f, 0.12f, 0xFFFFB45A, true, w - 0.4f);
            p.text("난방", w / 2f, 1.65f, 0.06f, 0xFF9AA7B8, true, w - 0.4f);
        } else {
            p.text("OFF", w / 2f, 0.8f, 0.08f, 0xFF3A4654, true, w - 0.4f);
        }
        p.end();
    }
}
