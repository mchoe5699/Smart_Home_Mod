package com.mchoe.smarthome.client;

import com.mchoe.smarthome.SmartHomeMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * 블록 앞면의 화면 영역에 그리기.
 * begin() 이후 좌표 단위는 모델 픽셀(1/16 블록), x 는 보는 사람 기준 오른쪽, y 는 아래쪽.
 */
public class ScreenPainter {
    public static final ResourceLocation WHITE = new ResourceLocation(SmartHomeMod.MODID, "textures/misc/white.png");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Font font;
    private float layer = 0f;

    public ScreenPainter(PoseStack pose, MultiBufferSource buffers, Font font) {
        this.pose = pose;
        this.buffers = buffers;
        this.font = font;
    }

    /** 북쪽 기준 모델 좌표의 화면 사각형(x1,y1,x2,y2,앞면z)으로 좌표계를 맞춘다. pushPose 포함. */
    public void begin(Direction facing, float x1, float y1, float x2, float y2, float zFront) {
        pose.pushPose();
        float yRot = (facing.toYRot() + 180f) % 360f;
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(x2 / 16f, y2 / 16f, zFront / 16f - 0.002f);
        pose.scale(-1f / 16f, -1f / 16f, -1f);
        layer = 0f;
    }

    public void end() {
        pose.popPose();
    }

    private float nextLayer() {
        layer += 0.0004f;
        return layer;
    }

    public void rect(float x1, float y1, float x2, float y2, int argb) {
        quad(WHITE, x1, y1, x2, y2, 0, 0, 1, 1, argb);
    }

    private void quad(ResourceLocation tex, float x1, float y1, float x2, float y2,
                      float u1, float v1, float u2, float v2, int argb) {
        float z = nextLayer();
        VertexConsumer vc = buffers.getBuffer(RenderType.text(tex));
        Matrix4f m = pose.last().pose();
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        // 앞뒤 모두 보이도록 두 방향으로 그린다
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y2, z).color(r, g, b, a).uv(u1, v2).uv2(LIGHT).endVertex();
        vc.vertex(m, x2, y2, z).color(r, g, b, a).uv(u2, v2).uv2(LIGHT).endVertex();
        vc.vertex(m, x2, y1, z).color(r, g, b, a).uv(u2, v1).uv2(LIGHT).endVertex();

        vc.vertex(m, x2, y1, z).color(r, g, b, a).uv(u2, v1).uv2(LIGHT).endVertex();
        vc.vertex(m, x2, y2, z).color(r, g, b, a).uv(u2, v2).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y2, z).color(r, g, b, a).uv(u1, v2).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(LIGHT).endVertex();
    }

    /** 방문자 얼굴(플레이어 스킨) */
    public void face(UUID id, float x, float y, float size) {
        ResourceLocation skin = ClientHooks.skin(id);
        quad(skin, x, y, x + size, y + size, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
        quad(skin, x, y, x + size, y + size, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
    }

    /** 글자. scale 은 픽셀당 글꼴 단위 배율, maxWidth 를 넘으면 줄여서 맞춘다. */
    public void text(String s, float x, float y, float scale, int argb, boolean center, float maxWidth) {
        if (s == null || s.isEmpty()) return;
        int w = font.width(s);
        float sc = scale;
        if (maxWidth > 0 && w * sc > maxWidth) sc = maxWidth / w;
        pose.pushPose();
        pose.translate(x, y, nextLayer() + 0.0005f);
        pose.scale(sc, sc, 1f);
        float dx = center ? -w / 2f : 0f;
        font.drawInBatch(s, dx, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
        pose.popPose();
    }
}
