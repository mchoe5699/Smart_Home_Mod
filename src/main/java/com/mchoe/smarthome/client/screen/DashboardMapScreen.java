package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.SmartHomeMod;
import com.mchoe.smarthome.client.ClientHooks;
import com.mchoe.smarthome.network.MapDataPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 단지 대시보드 지도.
 * - 지도 위를 누르고 끌면 새 구획(단지) 생성
 * - 구획을 클릭해 여러 개 선택 → [묶기] 같은 단지로 / [분리] 각각 다른 단지로
 * - 같은 단지의 구획이 맞닿으면 맞닿은 부분의 경계선이 사라져 하나의 모양으로 보인다
 */
public class DashboardMapScreen extends Screen implements DataScreen {
    private static final ResourceLocation MAP_TEX = new ResourceLocation(SmartHomeMod.MODID, "dashboard_map");
    private static final int[] PALETTE = {0xFF4C9AE5, 0xFFE5A84D, 0xFF4CD27A, 0xFFE5484D, 0xFFB45AD0, 0xFF2EC8C0,
            0xFFF0E04C, 0xFFE57AB4, 0xFF8AD04C, 0xFF7A8AF0};
    private static final int SIDE = 190;

    private record ZoneV(int id, int x1, int z1, int x2, int z2, int complex) {
        boolean contains(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }
    }

    private record CompV(int id, String name, int devices) {}

    private final BlockPos pos;
    private int originX, originZ, size = 256;
    private DynamicTexture tex;
    private boolean hasMap;
    private final List<ZoneV> zones = new ArrayList<>();
    private final Map<Integer, CompV> complexes = new HashMap<>();
    private final Set<Integer> selected = new LinkedHashSet<>();

    private int mapX, mapY, mapS;
    private boolean dragging;
    private double pressX, pressY;
    private int dragBX, dragBZ;
    private EditBox nameBox;
    private int lastNameComplex = Integer.MIN_VALUE;

    public DashboardMapScreen(BlockPos pos) {
        super(Component.literal("단지 대시보드"));
        this.pos = pos;
        this.originX = pos.getX() - 128;
        this.originZ = pos.getZ() - 128;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void updateData(CompoundTag data) {}

    public void applyMap(MapDataPacket p) {
        if (p.rgb.length == p.size * p.size * 3) {
            originX = p.originX;
            originZ = p.originZ;
            if (tex == null || size != p.size) {
                if (tex != null) minecraft.getTextureManager().release(MAP_TEX);
                tex = new DynamicTexture(p.size, p.size, true);
                minecraft.getTextureManager().register(MAP_TEX, tex);
            }
            size = p.size;
            NativeImage img = tex.getPixels();
            if (img != null) {
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        int i = (z * size + x) * 3;
                        int r = p.rgb[i] & 255, g = p.rgb[i + 1] & 255, b = p.rgb[i + 2] & 255;
                        img.setPixelRGBA(x, z, 0xFF000000 | (b << 16) | (g << 8) | r);
                    }
                }
                tex.upload();
            }
            hasMap = true;
        }
        zones.clear();
        complexes.clear();
        ListTag zl = p.zones.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zl.size(); i++) {
            CompoundTag t = zl.getCompound(i);
            zones.add(new ZoneV(t.getInt("id"), t.getInt("x1"), t.getInt("z1"), t.getInt("x2"), t.getInt("z2"), t.getInt("complex")));
        }
        ListTag cl = p.zones.getList("complexes", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag t = cl.getCompound(i);
            complexes.put(t.getInt("id"), new CompV(t.getInt("id"), t.getString("name"), t.getInt("devices")));
        }
        selected.removeIf(id -> zones.stream().noneMatch(z -> z.id == id));
        lastNameComplex = Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ 조작

    private CompoundTag base() {
        CompoundTag t = new CompoundTag();
        t.putInt("cx", originX + size / 2);
        t.putInt("cz", originZ + size / 2);
        return t;
    }

    private void send(String action, CompoundTag t) {
        ClientHooks.send(pos, action, t);
    }

    private int[] selectedIds() {
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }

    private void pan(int dx, int dz) {
        CompoundTag t = new CompoundTag();
        t.putInt("cx", originX + size / 2 + dx);
        t.putInt("cz", originZ + size / 2 + dz);
        send("map", t);
    }

    private ZoneV firstSelected() {
        for (int id : selected) for (ZoneV z : zones) if (z.id == id) return z;
        return null;
    }

    @Override
    protected void init() {
        mapS = Math.max(120, Math.min(height - 40, width - SIDE - 40));
        mapX = (width - mapS - SIDE - 10) / 2;
        mapY = (height - mapS) / 2;
        int sx = mapX + mapS + 10, sw = SIDE;
        int y = mapY + 64;
        addRenderableWidget(Button.builder(Component.literal("묶기 (같은 단지로)"), b -> {
            if (selected.size() < 2) return;
            CompoundTag t = base();
            t.putIntArray("ids", selectedIds());
            send("merge", t);
        }).bounds(sx, y, sw, 18).build());
        y += 21;
        addRenderableWidget(Button.builder(Component.literal("분리 (각각 다른 단지로)"), b -> {
            if (selected.isEmpty()) return;
            CompoundTag t = base();
            t.putIntArray("ids", selectedIds());
            send("split", t);
        }).bounds(sx, y, sw, 18).build());
        y += 21;
        addRenderableWidget(Button.builder(Component.literal("선택 구획 삭제").withStyle(ChatFormatting.RED), b -> {
            if (selected.isEmpty()) return;
            CompoundTag t = base();
            t.putIntArray("ids", selectedIds());
            send("zone_delete", t);
            selected.clear();
        }).bounds(sx, y, (sw - 4) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("선택 해제"), b -> selected.clear())
                .bounds(sx + (sw + 4) / 2, y, (sw - 4) / 2, 18).build());
        y += 30;
        nameBox = new EditBox(font, sx, y, sw - 54, 16, Component.literal("단지 이름"));
        nameBox.setMaxLength(20);
        nameBox.setHint(Component.literal("단지 이름").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("변경"), b -> {
            ZoneV z = firstSelected();
            if (z == null) return;
            CompoundTag t = base();
            t.putInt("complex", z.complex);
            t.putString("name", nameBox.getValue());
            send("rename", t);
        }).bounds(sx + sw - 50, y - 1, 50, 18).build());
        y += 28;
        int pw = 30;
        int pcx = sx + sw / 2;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> pan(0, -128)).bounds(pcx - pw / 2, y, pw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> pan(-128, 0)).bounds(pcx - pw / 2 - pw - 2, y + 18, pw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("⌂"), b -> {
            CompoundTag t = new CompoundTag();
            t.putInt("cx", pos.getX());
            t.putInt("cz", pos.getZ());
            send("map", t);
        }).bounds(pcx - pw / 2, y + 18, pw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> pan(128, 0)).bounds(pcx + pw / 2 + 2, y + 18, pw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> pan(0, 128)).bounds(pcx - pw / 2, y + 36, pw, 16).build());
    }

    @Override
    public void tick() {
        nameBox.tick();
        ZoneV z = firstSelected();
        int c = z == null ? Integer.MIN_VALUE : z.complex;
        if (c != lastNameComplex) {
            lastNameComplex = c;
            CompV cv = complexes.get(c);
            nameBox.setValue(cv == null ? "" : cv.name);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (tex != null) minecraft.getTextureManager().release(MAP_TEX);
        tex = null;
    }

    // ------------------------------------------------------------------ 좌표

    private int blockX(double gx) {
        return originX + (int) Math.floor((gx - mapX) * size / mapS);
    }

    private int blockZ(double gy) {
        return originZ + (int) Math.floor((gy - mapY) * size / mapS);
    }

    private int guiX(double bx) {
        return mapX + (int) Math.round((bx - originX) * mapS / size);
    }

    private int guiY(double bz) {
        return mapY + (int) Math.round((bz - originZ) * mapS / size);
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapS && my >= mapY && my < mapY + mapS;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && inMap(mx, my)) {
            dragging = true;
            pressX = mx;
            pressY = my;
            dragBX = blockX(mx);
            dragBZ = blockZ(my);
            return true;
        }
        // 오른쪽 목록에서 단지 클릭 → 그 단지 구획 모두 선택
        int sx = mapX + mapS + 10;
        int ly = mapY + 248;
        int i = 0;
        for (CompV c : complexes.values()) {
            int ry = ly + 12 + i * 11;
            if (mx >= sx && mx < sx + SIDE && my >= ry && my < ry + 11) {
                for (ZoneV z : zones) if (z.complex == c.id) selected.add(z.id);
                return true;
            }
            i++;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            int bx = blockX(Math.max(mapX, Math.min(mapX + mapS - 1, mx)));
            int bz = blockZ(Math.max(mapY, Math.min(mapY + mapS - 1, my)));
            if (Math.abs(mx - pressX) < 4 && Math.abs(my - pressY) < 4) {
                ZoneV hit = null;
                for (ZoneV z : zones) {
                    if (z.contains(bx, bz) && (hit == null || area(z) < area(hit))) hit = z;
                }
                if (hit != null) {
                    if (!selected.remove(hit.id)) selected.add(hit.id);
                }
            } else {
                CompoundTag t = base();
                t.putInt("x1", dragBX);
                t.putInt("z1", dragBZ);
                t.putInt("x2", bx);
                t.putInt("z2", bz);
                send("zone_add", t);
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private static long area(ZoneV z) {
        return (long) (z.x2 - z.x1 + 1) * (z.z2 - z.z1 + 1);
    }

    // ------------------------------------------------------------------ 그리기

    private int colorOf(int complex) {
        return PALETTE[Math.floorMod(complex, PALETTE.length)];
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(mapX - 4, mapY - 4, mapX + mapS + SIDE + 14, mapY + mapS + 4, 0xFF15181D);
        g.fill(mapX - 1, mapY - 1, mapX + mapS + 1, mapY + mapS + 1, 0xFF3A3F46);
        if (hasMap) {
            g.blit(MAP_TEX, mapX, mapY, mapS, mapS, 0, 0, size, size, size, size);
        } else {
            g.fill(mapX, mapY, mapX + mapS, mapY + mapS, 0xFF20242A);
            g.drawCenteredString(font, "지도 불러오는 중...", mapX + mapS / 2, mapY + mapS / 2, 0x9AA7B8);
        }

        g.enableScissor(mapX, mapY, mapX + mapS, mapY + mapS);
        // 구획 채우기
        for (ZoneV z : zones) {
            int c = colorOf(z.complex);
            boolean sel = selected.contains(z.id);
            int fillA = sel ? 0x70 : 0x38;
            g.fill(guiX(z.x1), guiY(z.z1), guiX(z.x2 + 1), guiY(z.z2 + 1), (fillA << 24) | (c & 0xFFFFFF));
        }
        // 단지별 합쳐진 외곽선 (맞닿은 경계는 지운다)
        for (ZoneV z : zones) drawOutline(g, z);
        // 선택 구획 강조
        for (ZoneV z : zones) {
            if (!selected.contains(z.id)) continue;
            int x1 = guiX(z.x1), y1 = guiY(z.z1), x2 = guiX(z.x2 + 1), y2 = guiY(z.z2 + 1);
            dashed(g, x1, y1, x2, y2, 0xFFFFFFFF);
        }
        // 단지 이름
        Set<Integer> labeled = new LinkedHashSet<>();
        for (ZoneV z : zones) {
            if (!labeled.add(z.complex)) continue;
            CompV c = complexes.get(z.complex);
            if (c == null) continue;
            int cx = guiX((z.x1 + z.x2 + 1) / 2.0), cy = guiY((z.z1 + z.z2 + 1) / 2.0);
            g.drawCenteredString(font, c.name, cx, cy - 4, 0xFFFFFF);
        }
        // 대시보드·내 위치
        int dx = guiX(pos.getX() + 0.5), dz = guiY(pos.getZ() + 0.5);
        g.fill(dx - 2, dz - 2, dx + 3, dz + 3, 0xFFE5484D);
        if (minecraft.player != null) {
            int px = guiX(minecraft.player.getX()), pz = guiY(minecraft.player.getZ());
            g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFFFFFF);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFF2E7DD7);
        }
        // 드래그 미리보기
        if (dragging && (Math.abs(mx - pressX) >= 4 || Math.abs(my - pressY) >= 4)) {
            int bx = blockX(Math.max(mapX, Math.min(mapX + mapS - 1, mx))), bz = blockZ(Math.max(mapY, Math.min(mapY + mapS - 1, my)));
            int x1 = guiX(Math.min(dragBX, bx)), y1 = guiY(Math.min(dragBZ, bz));
            int x2 = guiX(Math.max(dragBX, bx) + 1), y2 = guiY(Math.max(dragBZ, bz) + 1);
            g.fill(x1, y1, x2, y2, 0x40FFD34E);
            dashed(g, x1, y1, x2, y2, 0xFFFFD34E);
            String sz = (Math.abs(bx - dragBX) + 1) + " × " + (Math.abs(bz - dragBZ) + 1);
            g.drawString(font, sz, x2 + 3, y2 + 2, 0xFFD34E, true);
        }
        g.disableScissor();

        // 좌표 표시
        if (inMap(mx, my)) {
            g.drawString(font, "X " + blockX(mx) + "  Z " + blockZ(my), mapX + 4, mapY + mapS - 12, 0xFFFFFF, true);
        }

        drawSide(g, mx, my);
        super.render(g, mx, my, pt);
    }

    private void drawSide(GuiGraphics g, int mx, int my) {
        int sx = mapX + mapS + 10;
        g.fill(sx, mapY, sx + SIDE, mapY + 14, 0xFF2B6CB0);
        g.drawString(font, "단지 대시보드", sx + 4, mapY + 3, 0xFFFFFF, false);
        g.drawString(font, "드래그: 새 구획 만들기", sx, mapY + 20, 0xC8D0DA, false);
        g.drawString(font, "클릭: 구획 선택 (여러 개)", sx, mapY + 31, 0xC8D0DA, false);
        String selText = selected.isEmpty() ? "선택 없음" : "선택 " + selected.size() + "개";
        ZoneV f = firstSelected();
        if (f != null && complexes.containsKey(f.complex)) selText += " · " + complexes.get(f.complex).name;
        g.drawString(font, font.plainSubstrByWidth(selText, SIDE), sx, mapY + 46, 0xFFD34E, false);

        int ly = mapY + 248;
        g.drawString(font, "단지 목록 (클릭 시 선택)", sx, ly, 0xFFD34E, false);
        int i = 0;
        for (CompV c : complexes.values()) {
            int ry = ly + 12 + i * 11;
            if (ry > mapY + mapS - 10) break;
            int zc = 0;
            for (ZoneV z : zones) if (z.complex == c.id) zc++;
            g.fill(sx, ry + 2, sx + 6, ry + 8, colorOf(c.id));
            g.drawString(font, font.plainSubstrByWidth(c.name + "  구획 " + zc + " · 기기 " + c.devices, SIDE - 10), sx + 9, ry, 0xFFFFFF, false);
            i++;
        }
        if (complexes.isEmpty()) g.drawString(font, "아직 단지가 없습니다", sx, ly + 12, 0x707880, false);
    }

    private void dashed(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        for (int x = x1; x < x2; x += 4) {
            g.fill(x, y1, Math.min(x + 2, x2), y1 + 1, color);
            g.fill(x, y2 - 1, Math.min(x + 2, x2), y2, color);
        }
        for (int y = y1; y < y2; y += 4) {
            g.fill(x1, y, x1 + 1, Math.min(y + 2, y2), color);
            g.fill(x2 - 1, y, x2, Math.min(y + 2, y2), color);
        }
    }

    /** 같은 단지의 다른 구획과 맞닿은 부분을 뺀 경계선만 그린다 */
    private void drawOutline(GuiGraphics g, ZoneV z) {
        int color = colorOf(z.complex);
        List<ZoneV> same = new ArrayList<>();
        for (ZoneV o : zones) if (o.complex == z.complex && o.id != z.id) same.add(o);

        // 북쪽 (z1 위쪽 줄과 맞닿음)
        for (int[] s : remaining(z.x1, z.x2 + 1, same, o -> o.z1 <= z.z1 - 1 && z.z1 - 1 <= o.z2, true)) {
            int y = guiY(z.z1);
            g.fill(guiX(s[0]), y - 1, guiX(s[1]), y + 1, color);
        }
        // 남쪽
        for (int[] s : remaining(z.x1, z.x2 + 1, same, o -> o.z1 <= z.z2 + 1 && z.z2 + 1 <= o.z2, true)) {
            int y = guiY(z.z2 + 1);
            g.fill(guiX(s[0]), y - 1, guiX(s[1]), y + 1, color);
        }
        // 서쪽
        for (int[] s : remaining(z.z1, z.z2 + 1, same, o -> o.x1 <= z.x1 - 1 && z.x1 - 1 <= o.x2, false)) {
            int x = guiX(z.x1);
            g.fill(x - 1, guiY(s[0]), x + 1, guiY(s[1]), color);
        }
        // 동쪽
        for (int[] s : remaining(z.z1, z.z2 + 1, same, o -> o.x1 <= z.x2 + 1 && z.x2 + 1 <= o.x2, false)) {
            int x = guiX(z.x2 + 1);
            g.fill(x - 1, guiY(s[0]), x + 1, guiY(s[1]), color);
        }
    }

    /** [a,b) 구간에서 이웃 구획이 덮는 부분을 뺀 나머지 구간들 */
    private static List<int[]> remaining(int a, int b, List<ZoneV> others, java.util.function.Predicate<ZoneV> adjacent, boolean alongX) {
        List<int[]> segs = new ArrayList<>();
        segs.add(new int[]{a, b});
        for (ZoneV o : others) {
            if (!adjacent.test(o)) continue;
            int ca = alongX ? o.x1 : o.z1;
            int cb = (alongX ? o.x2 : o.z2) + 1;
            List<int[]> next = new ArrayList<>();
            for (int[] s : segs) {
                if (cb <= s[0] || ca >= s[1]) {
                    next.add(s);
                    continue;
                }
                if (ca > s[0]) next.add(new int[]{s[0], ca});
                if (cb < s[1]) next.add(new int[]{cb, s[1]});
            }
            segs = next;
        }
        return segs;
    }
}
