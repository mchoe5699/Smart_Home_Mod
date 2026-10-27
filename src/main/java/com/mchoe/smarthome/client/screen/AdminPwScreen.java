package com.mchoe.smarthome.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/** 관리 비밀번호 입력 (설정 메뉴 진입 전) — 숫자 키패드 */
public class AdminPwScreen extends Screen implements DataScreen {
    private static final int W = 170, H = 236;
    private final DeviceGui parent;
    private String input = "";
    private int left, top;

    public AdminPwScreen(DeviceGui parent) {
        super(Component.literal("관리 비밀번호"));
        this.parent = parent;
    }

    @Override
    public BlockPos getPos() {
        return parent.getPos();
    }

    @Override
    public void updateData(CompoundTag data) {
        parent.updateData(data);
        if (!parent.adminError.isEmpty()) input = "";
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        parent.cancelAdmin();
    }

    private void press(char c) {
        if (c == '<') {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (c == '#') {
            if (!input.isEmpty()) parent.checkAdmin(input);
        } else if (c >= '0' && c <= '9' && input.length() < 8) {
            input += c;
            parent.adminError = "";
        }
    }

    private int keyX(int i) {
        return left + 18 + (i % 3) * 46;
    }

    private int keyY(int i) {
        return top + 70 + (i / 3) * 34;
    }

    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "<", "0", "#"};

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        DeviceGui.rrect(g, left - 2, top - 2, W + 4, H + 4, 0xFF3A3F46);
        g.fillGradient(left, top, left + W, top + H, 0xFF1E2430, 0xFF10141A);
        g.fillGradient(left, top, left + W, top + 18, 0xFFC98A2E, 0xFFA06A1E);
        DeviceGui.icon(g, DeviceGui.I_KEY, left + 5, top + 3, 12, 0xFFFFFF);
        g.drawString(font, "관리 비밀번호", left + 21, top + 5, 0xFFFFFF, false);
        g.drawCenteredString(font, "설정 메뉴에 들어가려면 입력", left + W / 2, top + 24, 0x9AA7B8);
        DeviceGui.rrect(g, left + 18, top + 38, W - 36, 22, 0xFF050607);
        String masked = input.replaceAll(".", "●");
        g.drawCenteredString(font, masked.isEmpty() ? "_" : masked, left + W / 2, top + 45, 0xFFFFFF);
        for (int i = 0; i < 12; i++) {
            int x = keyX(i), y = keyY(i);
            boolean hover = mx >= x && mx < x + 40 && my >= y && my < y + 28;
            int base = i == 11 ? 0xFF2E9E4F : i == 9 ? 0xFF6C7076 : 0xFF2A2E34;
            if (hover) base = base + 0x00161616;
            DeviceGui.rrect(g, x, y, 40, 28, 0xFF15171A);
            g.fillGradient(x + 1, y + 1, x + 39, y + 27, base + 0x00181818, base);
            String k = i == 9 ? "←" : i == 11 ? "확인" : KEYS[i];
            g.drawCenteredString(font, k, x + 20, y + 10, 0xFFFFFF);
        }
        if (!parent.adminError.isEmpty()) g.drawCenteredString(font, parent.adminError, left + W / 2, top + H - 14, 0xFF6B6B);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 12; i++) {
            int x = keyX(i), y = keyY(i);
            if (mx >= x && mx < x + 40 && my >= y && my < y + 28) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                press(KEYS[i].charAt(0));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (c >= '0' && c <= '9') {
            press(c);
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            press('<');
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            press('#');
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
