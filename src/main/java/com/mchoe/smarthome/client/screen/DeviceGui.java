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
    /** 버튼 눌림 표시 남은 틱 */
    protected int pressedTicks;

    // 관리 비밀번호 (설정 메뉴 잠금)
    private String adminAuth;
    private String pendingAuth;
    private Runnable pendingSettings;
    String adminError = "";
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
        if (pendingSettings != null && newData.getBoolean("setOk")) {
            adminAuth = pendingAuth;
            Runnable r = pendingSettings;
            pendingSettings = null;
            adminError = "";
            minecraft.setScreen(this);
            r.run();
            return;
        }
        if (newData.getBoolean("setFail")) {
            adminError = "관리 비밀번호가 틀립니다";
            adminAuth = null;
        }
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
        if (adminAuth != null) {
            if (args == null) args = new CompoundTag();
            args.putString("auth", adminAuth);
        }
        ClientHooks.send(pos, action, args);
    }

    /**
     * 설정 메뉴 진입. 관리 비밀번호가 없으면 바로, 있으면 비밀번호 확인 후 들어간다.
     */
    protected void requestSettings(Runnable open) {
        if (!data.getBoolean("adminSet") || adminAuth != null) {
            open.run();
            return;
        }
        pendingSettings = open;
        adminError = "";
        minecraft.setScreen(new AdminPwScreen(this));
    }

    /** 관리 비밀번호 확인 요청 (AdminPwScreen 에서 호출) */
    void checkAdmin(String pw) {
        pendingAuth = pw;
        adminError = "";
        CompoundTag t = new CompoundTag();
        t.putString("pw", pw);
        ClientHooks.send(pos, "admin_check", t);
    }

    void cancelAdmin() {
        pendingSettings = null;
        minecraft.setScreen(this);
    }

    /** 새 관리 비밀번호를 저장했으면 이후 인증에 그것을 쓴다 */
    void adminChanged(String newPw, boolean cleared) {
        if (cleared) adminAuth = null;
        else if (newPw != null && !newPw.isEmpty()) adminAuth = newPw;
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
        if (pressedTicks > 0) pressedTicks--;
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

    // ------------------------------------------------------------------ 부드러운 그리기 (고해상도 텍스처 + 선형 필터)

    public static final ResourceLocation ROUND = tex("round");
    public static final ResourceLocation DISC = tex("disc");
    /** GUI 본체 이미지 해상도 배율 */
    public static final int ART_SCALE = 3;

    /** 텍스처를 부드럽게(선형 필터) 그리도록 설정 */
    public static void smooth(ResourceLocation loc) {
        net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(loc).setFilter(true, false);
    }

    private static void tint(int argb) {
        float a = ((argb >>> 24) & 255) / 255f;
        if ((argb >>> 24) == 0) a = 1f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, a);
    }

    private static void untint() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** 모서리가 둥근 사각형 (반지름 자동) */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int color) {
        rrect(g, x, y, w, h, color, Math.min(4, Math.min(w, h) / 2));
    }

    /** 모서리가 둥근 사각형 (9-slice, 안티에일리어싱) */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int color, int r) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, (color >>> 24) == 0 ? color | 0xFF000000 : color);
            return;
        }
        smooth(ROUND);
        tint(color);
        int T = 64, c = 16, m = T - 2 * c;
        g.blit(ROUND, x, y, r, r, 0, 0, c, c, T, T);
        g.blit(ROUND, x + w - r, y, r, r, T - c, 0, c, c, T, T);
        g.blit(ROUND, x, y + h - r, r, r, 0, T - c, c, c, T, T);
        g.blit(ROUND, x + w - r, y + h - r, r, r, T - c, T - c, c, c, T, T);
        if (w > 2 * r) {
            g.blit(ROUND, x + r, y, w - 2 * r, r, c, 0, m, c, T, T);
            g.blit(ROUND, x + r, y + h - r, w - 2 * r, r, c, T - c, m, c, T, T);
        }
        if (h > 2 * r) {
            g.blit(ROUND, x, y + r, r, h - 2 * r, 0, c, c, m, T, T);
            g.blit(ROUND, x + w - r, y + r, r, h - 2 * r, T - c, c, c, m, T, T);
            if (w > 2 * r) g.blit(ROUND, x + r, y + r, w - 2 * r, h - 2 * r, c, c, m, m, T, T);
        }
        untint();
    }

    /** 둥근 카드(타일) — 위쪽에 은은한 하이라이트 */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int color) {
        rrect(g, x, y, w, h, color, 5);
        rrect(g, x + 1, y + 1, w - 2, Math.max(2, h / 3), 0x14FFFFFF, 4);
    }

    /** 실물 버튼. 누르면 action 실행 */
    protected void button(GuiGraphics g, int x, int y, int w, int h, String label, int color, int textColor, int mx, int my, Runnable action) {
        buttonIcon(g, x, y, w, h, -1, label, color, textColor, mx, my, action);
    }

    /** 아이콘이 있는 버튼 (icon < 0 이면 글자만): 둥근 모서리 · 그림자 · 광택 */
    protected void buttonIcon(GuiGraphics g, int x, int y, int w, int h, int icon, String label, int color, int textColor, int mx, int my, Runnable action) {
        boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;
        boolean down = hover && pressedTicks > 0;
        int base = (hover ? brighten(color, 16) : color) | 0xFF000000;
        int r = Math.max(2, Math.min(6, h / 3));
        int oy = down ? 1 : 0;
        rrect(g, x, y + 2, w, h, 0x38000000, r);
        rrect(g, x, y + oy, w, h, darken(base, 55), r);
        rrect(g, x + 1, y + 1 + oy, w - 2, h - 2, base, r - 1);
        rrect(g, x + 2, y + 1 + oy, w - 4, Math.max(2, (h - 2) / 2), 0x30FFFFFF, Math.max(1, r - 2));
        int tw = label.isEmpty() ? 0 : Math.min(font.width(label), w - 6);
        int iconSize = icon >= 0 ? Math.max(6, Math.min(16, h - 6)) : 0;
        int gap = icon >= 0 && tw > 0 ? 3 : 0;
        int total = iconSize + gap + tw;
        int cx = x + (w - total) / 2;
        if (icon >= 0) {
            icon(g, icon, cx, y + (h - iconSize) / 2 + oy, iconSize, textColor);
            cx += iconSize + gap;
        }
        if (tw > 0) {
            String cut = font.width(label) > w - 6 ? font.plainSubstrByWidth(label, w - 6) : label;
            g.drawString(font, cut, cx, y + (h - 8) / 2 + oy, textColor, false);
        }
        if (action != null) hits.add(new Hit(x, y, w, h, action));
    }

    /** 아이콘 (고해상도 시트, color 로 물들임) */
    public static void icon(GuiGraphics g, int index, int x, int y, int size, int color) {
        smooth(ICONS);
        tint(color | 0xFF000000);
        g.blit(ICONS, x, y, size, size, (index % 8) * 96, (index / 8) * 96, 96, 96, 768, 288);
        untint();
    }

    /** 기기 본체 이미지 (3배 해상도를 부드럽게 축소) */
    protected void body(GuiGraphics g, ResourceLocation texture) {
        art(g, texture, left, top, panelW, panelH);
    }

    /** 3배 해상도 그림을 (w x h) 크기로 */
    public static void art(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        smooth(texture);
        RenderSystem.enableBlend();
        int tw = w * ART_SCALE, th = h * ART_SCALE;
        g.blit(texture, x, y, w, h, 0, 0, tw, th, tw, th);
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

    /** 안티에일리어싱 원 */
    protected void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        disc(g, cx, cy, r, color);
    }

    public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        smooth(DISC);
        tint(color);
        g.blit(DISC, cx - r, cy - r, 2 * r, 2 * r, 0, 0, 128, 128, 128, 128);
        untint();
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

    /** 번호 설정 화면 (관리 비밀번호 확인 포함) */
    protected void openSettings() {
        requestSettings(() -> minecraft.setScreen(new SettingsScreen(this)));
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
                pressedTicks = 3;
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

    // ------------------------------------------------------------------ 외출/방범 (세대 내부기기 공용)

    protected String secInput = "";

    /** 외출/방범 화면의 키 입력 (해제용 세대 비밀번호) */
    protected void securityKey(char c) {
        if (data.getInt("secMode") == 0) return;
        if (c == '<') {
            if (!secInput.isEmpty()) secInput = secInput.substring(0, secInput.length() - 1);
        } else if (c == '\n' || c == '#') {
            disarm();
        } else if (c >= '0' && c <= '9' && secInput.length() < 8) {
            secInput += c;
        }
    }

    protected void arm(int mode) {
        CompoundTag t = new CompoundTag();
        t.putInt("mode", mode);
        act("secure_on", t);
    }

    protected void disarm() {
        CompoundTag t = new CompoundTag();
        t.putString("pw", secInput);
        act("secure_off", t);
        secInput = "";
    }

    /** 외출/방범 상태 + 설정 버튼 / 해제 키패드 */
    protected void securityPanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int mode = data.getInt("secMode");
        int remain = data.getInt("secRemain");
        boolean alarm = data.getBoolean("secAlarm");
        boolean blink = (ticks / 8) % 2 == 0;
        int color = mode == 0 ? 0x4CD27A : alarm ? (blink ? 0xFF4C4C : 0xFFFFFF) : remain > 0 ? 0xFFD34E : 0xFF8A4E;
        icon(g, I_AWAY, x + 4, y + 2, 16, color);
        String title = mode == 0 ? "외출/방범 해제 상태" : (mode == 1 ? "외출모드" : "방범모드") + (alarm ? " · 경보 발생!" : remain > 0 ? " · 유예 중" : " · 동체 감지 중");
        g.drawString(font, title, x + 24, y + 6, color, false);
        String sub = mode == 0 ? "동체감지기 " + data.getInt("motionCount") + "대 연동"
                : remain > 0 ? "감지 시작까지 " + remain + "초 — 집을 나가세요" : "감지되면 경비실에 통보됩니다";
        g.drawString(font, font.plainSubstrByWidth(sub, w - 8), x + 4, y + 22, 0x9AA7B8, false);
        if (mode == 0) {
            int bw = (w - 14) / 2, by = y + 40, bh = Math.min(44, h - 80);
            buttonIcon(g, x + 4, by, bw, bh, I_AWAY, "외출 설정", 0xFFC98A2E, 0xFFFFFF, mx, my, () -> arm(1));
            buttonIcon(g, x + 10 + bw, by, bw, bh, I_GUARD, "방범 설정", 0xFF2E6DB4, 0xFFFFFF, mx, my, () -> arm(2));
            int ty = by + bh + 8;
            g.drawString(font, font.plainSubstrByWidth("외출: 실내·외부 감지 + 조명/가스/환기 끔", w - 8), x + 4, ty, 0x9AA7B8, false);
            g.drawString(font, font.plainSubstrByWidth("방범: 외부 감지기만 (집 안은 감지 안 함)", w - 8), x + 4, ty + 11, 0x9AA7B8, false);
            g.drawString(font, font.plainSubstrByWidth("설정 후 90초 뒤 감지 시작 · 해제는 세대 비밀번호", w - 8), x + 4, ty + 22, 0x707880, false);
            return;
        }
        int ky = y + 38;
        int kw = Math.min(34, (w / 2 - 12) / 3), kh = Math.min(22, (h - 44) / 4 - 3);
        for (int i = 0; i < 12; i++) {
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "#"};
            char ch = i == 9 ? '<' : keys[i].charAt(0);
            button(g, x + 4 + (i % 3) * (kw + 3), ky + (i / 3) * (kh + 3), kw, kh, keys[i], 0xFF1E3A5A, 0xFFFFFFFF, mx, my, () -> securityKey(ch));
        }
        int rx = x + 4 + 3 * (kw + 3) + 8, rw = x + w - rx - 4;
        g.drawString(font, "세대 비밀번호", rx, ky, 0xFFFFFF, false);
        rrect(g, rx, ky + 12, rw, 18, 0xFF050607);
        String masked = secInput.replaceAll(".", "●");
        g.drawString(font, masked.isEmpty() ? "_" : masked, rx + 4, ky + 17, 0xFFFFFF, false);
        buttonIcon(g, rx, ky + 36, rw, 26, I_KEY, "해제", 0xFF2E9E4F, 0xFFFFFF, mx, my, this::disarm);
        if (!data.getBoolean("hasHouseholdPw")) g.drawString(font, font.plainSubstrByWidth("세대 비밀번호 없음: 바로 해제", rw), rx, ky + 68, 0xFFD34E, false);
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
