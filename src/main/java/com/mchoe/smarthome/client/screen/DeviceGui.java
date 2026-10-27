package com.mchoe.smarthome.client.screen;

import com.mchoe.smarthome.client.CameraFeed;
import com.mchoe.smarthome.client.ClientHooks;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 기기 조작화면 공통 바탕.
 * - 실물 기기 모양으로 직접 그리는 버튼(즉시 모드)
 * - 통화 중이면 오른쪽에 대화창(채팅으로 통화)
 * - 카메라 실시간 영상 그리기
 */
public abstract class DeviceGui extends Screen implements DataScreen {
    protected static final int CHAT_W = 176;

    // GUI 텍스처
    public static final ResourceLocation ICONS = tex("icons");
    public static final int I_CAMERA = 0, I_BULB = 1, I_FLAME = 2, I_GAS = 3, I_FAN = 4, I_ELEVATOR = 5, I_AWAY = 6, I_GUARD = 7,
            I_PHONE = 8, I_LOG = 9, I_NOTICE = 10, I_GEAR = 11, I_DOOR = 12, I_CALL = 13, I_HANGUP = 14, I_KEY = 15, I_CCTV = 16, I_BUILDING = 17;

    public static ResourceLocation tex(String name) {
        return new ResourceLocation(com.mchoe.smarthome.SmartHomeMod.MODID, "textures/gui/" + name + ".png");
    }

    protected final BlockPos pos;
    protected CompoundTag data;
    protected DeviceType type;
    protected final int panelW, panelH;
    protected int left, top;
    protected int ticks;
    protected EditBox chatBox;
    private boolean chatShown;
    private String chatKeep = "";

    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    protected DeviceGui(BlockPos pos, CompoundTag data, int panelW, int panelH) {
        super(Component.literal("Smart Home"));
        this.pos = pos;
        this.data = data;
        this.type = DeviceType.byId(data.getString("type"));
        this.panelW = panelW;
        this.panelH = panelH;
    }

    // ------------------------------------------------------------------ 데이터

    @Override
    public BlockPos getPos() {
        return pos;
    }

    public CompoundTag getData() {
        return data;
    }

    public DeviceType getType() {
        return type;
    }

    @Override
    public void updateData(CompoundTag newData) {
        this.data = newData;
        if (showChat() != chatShown && minecraft != null) rebuildWidgets();
        onDataChanged();
    }

    protected void onDataChanged() {}

    protected int status() {
        return data.getInt("status");
    }

    protected boolean idle() {
        return status() == DeviceLogic.STATUS_IDLE;
    }

    protected BlockPos camPos() {
        return data.contains("camPos") ? BlockPos.of(data.getLong("camPos")) : null;
    }

    /** 통화가 연결되면 대화창을 연다 */
    protected boolean showChat() {
        return status() == DeviceLogic.STATUS_CONNECTED;
    }

    public void act(String action, CompoundTag args) {
        ClientHooks.send(pos, action, args);
    }

    protected void act(String action) {
        act(action, null);
    }

    // ------------------------------------------------------------------ 레이아웃

    @Override
    protected void init() {
        chatShown = showChat();
        int totalW = panelW + (chatShown ? CHAT_W + 8 : 0);
        left = (width - totalW) / 2;
        top = (height - panelH) / 2;
        if (chatBox != null) chatKeep = chatBox.getValue();
        chatBox = new EditBox(font, left + panelW + 12, top + panelH - 22, CHAT_W - 8, 16, Component.literal("대화"));
        chatBox.setMaxLength(120);
        chatBox.setValue(chatKeep);
        chatBox.setHint(Component.literal("대화 입력 후 Enter").withStyle(s -> s.withColor(0x707880)));
        chatBox.visible = chatShown;
        addRenderableWidget(chatBox);
        if (chatShown) setInitialFocus(chatBox);
        initExtra();
    }

    protected void initExtra() {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        chatBox.tick();
        if (ticks % 10 == 0) act("refresh");
    }

    // ------------------------------------------------------------------ 그리기

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        drawDevice(g, mx, my);
        if (chatShown) drawChat(g);
        super.render(g, mx, my, pt);
    }

    protected abstract void drawDevice(GuiGraphics g, int mx, int my);

    private void drawChat(GuiGraphics g) {
        int x = left + panelW + 8, y = top, w = CHAT_W, h = panelH;
        rrect(g, x - 1, y - 1, w + 2, h + 2, 0xFF3A3F46);
        g.fillGradient(x, y, x + w, y + h, 0xF0141C28, 0xF00A0E14);
        g.fillGradient(x, y, x + w, y + 16, 0xFF2B6CB0, 0xFF1E4D8C);
        icon(g, I_PHONE, x + 4, y + 2, 12, 0xFFFFFF);
        g.drawString(font, "통화 대화", x + 19, y + 4, 0xFFFFFF, false);
        String peer = data.getString("peer");
        g.drawString(font, font.plainSubstrByWidth(peer, w - 70), x + w - 4 - Math.min(font.width(peer), w - 70), y + 4, 0x9AD0FF, false);

        ListTag msgs = data.getList("messages", Tag.TAG_STRING);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 0; i < msgs.size(); i++) lines.addAll(font.split(Component.literal(msgs.getString(i)), w - 10));
        int maxLines = (h - 16 - 28) / 10;
        int start = Math.max(0, lines.size() - maxLines);
        int ly = y + 20;
        for (int i = start; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 5, ly, 0xFFFFFF, false);
            ly += 10;
        }
        if (msgs.isEmpty()) g.drawString(font, "아래에 입력하면 상대에게 전달됩니다", x + 5, y + 20, 0x707880, false);
    }

    /** 실물 버튼(둥근 모서리 · 위아래 그라데이션 · 눌림 그림자). 누르면 action 실행 */
    protected void button(GuiGraphics g, int x, int y, int w, int h, String label, int color, int textColor, int mx, int my, Runnable action) {
        buttonIcon(g, x, y, w, h, -1, label, color, textColor, mx, my, action);
    }

    /** 아이콘이 있는 버튼 (icon < 0 이면 글자만) */
    protected void buttonIcon(GuiGraphics g, int x, int y, int w, int h, int icon, String label, int color, int textColor, int mx, int my, Runnable action) {
        boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;
        int base = hover ? brighten(color, 18) : color;
        // 그림자
        rrect(g, x, y + 1, w, h, 0x50000000);
        // 테두리 + 그라데이션 몸통
        rrect(g, x, y, w, h, darken(base, 45));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, brighten(base, 26), darken(base, 10));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, brighten(base, 55) & 0x80FFFFFF | 0x60000000);
        int tw = label.isEmpty() ? 0 : Math.min(font.width(label), w - 6);
        int iconSize = icon >= 0 ? Math.min(16, h - 4) : 0;
        int gap = icon >= 0 && tw > 0 ? 3 : 0;
        int total = iconSize + gap + tw;
        int cx = x + (w - total) / 2;
        if (icon >= 0) {
            icon(g, icon, cx, y + (h - iconSize) / 2, iconSize, textColor);
            cx += iconSize + gap;
        }
        if (tw > 0) {
            String cut = font.width(label) > w - 6 ? font.plainSubstrByWidth(label, w - 6) : label;
            g.drawString(font, cut, cx, y + (h - 8) / 2 + 1, textColor, false);
        }
        if (action != null) hits.add(new Hit(x, y, w, h, action));
    }

    /** 모서리를 1픽셀 깎은 사각형 */
    protected static void rrect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 반투명 카드(타일) */
    protected static void card(GuiGraphics g, int x, int y, int w, int h, int color) {
        rrect(g, x, y, w, h, color);
        g.fill(x + 1, y, x + w - 1, y + 1, 0x30FFFFFF);
    }

    /** 아이콘 (24px 시트에서 size 크기로, color 로 물들임) */
    protected static void icon(GuiGraphics g, int index, int x, int y, int size, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        g.blit(ICONS, x, y, size, size, (index % 8) * 24, (index / 8) * 24, 24, 24, 192, 72);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** 기기 본체 이미지 */
    protected void body(GuiGraphics g, ResourceLocation texture) {
        RenderSystem.enableBlend();
        g.blit(texture, left, top, 0, 0, panelW, panelH, panelW, panelH);
        RenderSystem.disableBlend();
    }

    /** 큰 글씨 */
    protected void bigText(GuiGraphics g, String s, int x, int y, float scale, int color, boolean center) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, center ? -font.width(s) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

    /** 화면 터치 영역(그리기 없이) */
    protected void touch(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    protected static int brighten(int c, int d) {
        int a = c & 0xFF000000;
        int r = Math.min(255, ((c >> 16) & 255) + d), gg = Math.min(255, ((c >> 8) & 255) + d), b = Math.min(255, (c & 255) + d);
        return a | (r << 16) | (gg << 8) | b;
    }

    protected static int darken(int c, int d) {
        int a = c & 0xFF000000;
        int r = Math.max(0, ((c >> 16) & 255) - d), gg = Math.max(0, ((c >> 8) & 255) - d), b = Math.max(0, (c & 255) - d);
        return a | (r << 16) | (gg << 8) | b;
    }

    protected void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    protected void centered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    protected void fitText(GuiGraphics g, String s, int x, int y, int maxW, int color) {
        g.drawString(font, font.plainSubstrByWidth(s, maxW), x, y, color, false);
    }

    /** 카메라 실시간 영상 */
    protected boolean drawFeed(GuiGraphics g, BlockPos cam, int x, int y, int w, int h) {
        if (cam == null) return false;
        CameraFeed f = CameraFeed.get(cam, 3);
        if (f == null) return false;
        g.blit(f.loc, x, y, w, h, 0, 0, CameraFeed.W, CameraFeed.H, CameraFeed.W, CameraFeed.H);
        for (CameraFeed.Mark m : f.marks) {
            int s = Math.max(4, (int) (m.size() * h));
            drawFace(g, m.id(), (int) (x + m.u() * w - s / 2f), (int) (y + m.v() * h - s / 2f), s);
        }
        if (!f.signal) centered(g, "신호 없음 (거리가 멀거나 카메라 없음)", x + w / 2, y + h / 2 - 4, 0xFFFFFF);
        if ((ticks / 10) % 2 == 0) {
            circle(g, x + w - 10, y + 8, 3, 0xFFE5484D);
        }
        g.drawString(font, "LIVE", x + w - 38, y + 4, 0xFFFFFF, true);
        return true;
    }

    protected void drawFace(GuiGraphics g, UUID id, int x, int y, int size) {
        ResourceLocation skin = ClientHooks.skin(id);
        RenderSystem.enableBlend();
        g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    protected String clock() {
        if (minecraft == null || minecraft.level == null) return "";
        long t = minecraft.level.getDayTime();
        int h = (int) ((t / 1000L + 6) % 24);
        int m = (int) ((t % 1000L) * 60 / 1000L);
        return String.format("%02d:%02d", h, m);
    }

    protected String statusText() {
        return switch (status()) {
            case DeviceLogic.STATUS_RINGING -> "호출 수신";
            case DeviceLogic.STATUS_CALLING -> "호출 중...";
            case DeviceLogic.STATUS_CONNECTED -> "통화 중";
            default -> "대기";
        };
    }

    protected void openSettings() {
        minecraft.setScreen(new SettingsScreen(this));
    }

    // ------------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                h.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (chatShown && chatBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                String text = chatBox.getValue().trim();
                if (!text.isEmpty()) {
                    CompoundTag t = new CompoundTag();
                    t.putString("text", text);
                    act("say", t);
                    chatBox.setValue("");
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
            return chatBox.keyPressed(key, scan, mods) || true;
        }
        if (getFocused() instanceof EditBox eb && eb.visible && eb.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            onKey('<');
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            onKey('\n');
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (chatShown && chatBox.isFocused()) return chatBox.charTyped(c, mods);
        if (getFocused() instanceof EditBox eb && eb.visible && eb.isFocused()) return super.charTyped(c, mods);
        if ((c >= '0' && c <= '9') || c == '*' || c == '#') {
            onKey(c);
            return true;
        }
        return super.charTyped(c, mods);
    }

    /** 키패드 입력 (숫자, *, #, '<' 지우기, '\n' 확인) */
    protected void onKey(char c) {}

    // ------------------------------------------------------------------ 키패드 공용

    protected static int[] parsePair(String input) {
        String[] p = input.split("\\*");
        try {
            if (p.length == 2) return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    protected void callPair(String action, String input, int defaultDong) {
        int[] pr = parsePair(input);
        if (pr == null && defaultDong >= 0 && input.matches("\\d{1,4}")) pr = new int[]{defaultDong, Integer.parseInt(input)};
        if (pr == null) {
            minecraft.player.displayClientMessage(Component.literal("동*호 형식으로 입력하세요 (예: 101*1001)"), true);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("dong", pr[0]);
        t.putInt("ho", pr[1]);
        act(action, t);
    }

    /** 3x4 키패드 그리기 */
    protected void keypad(GuiGraphics g, int x, int y, int kw, int kh, int gap, int color, int text, int mx, int my) {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < 12; i++) {
            int c = i % 3, r = i / 3;
            char ch = keys[i].charAt(0);
            button(g, x + c * (kw + gap), y + r * (kh + gap), kw, kh, keys[i], color, text, mx, my, () -> onKey(ch));
        }
    }
}
