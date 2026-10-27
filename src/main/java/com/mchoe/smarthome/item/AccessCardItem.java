package com.mchoe.smarthome.item;

import com.mchoe.smarthome.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 출입용 카드: 로비폰에 대면(들고 우클릭) 공동현관이 열린다 */
public class AccessCardItem extends Item {
    public AccessCardItem(Properties props) {
        super(props);
    }

    public static ItemStack create(int cardId, String complex, int dong, int ho, String holder) {
        ItemStack s = new ItemStack(ModRegistry.ACCESS_CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putInt("cardId", cardId);
        t.putString("complex", complex);
        t.putInt("dong", dong);
        t.putInt("ho", ho);
        t.putString("holder", holder);
        return s;
    }

    public static int cardId(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt("cardId") : 0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t == null || !t.contains("cardId")) {
            tooltip.add(Component.literal("미등록 카드").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.literal("카드 #" + t.getInt("cardId") + " · " + t.getString("complex")).withStyle(ChatFormatting.AQUA));
        int dong = t.getInt("dong");
        tooltip.add(Component.literal(dong < 0 ? "단지 전체 출입" : dong + "동 " + t.getInt("ho") + "호").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("소유자: " + t.getString("holder")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("로비폰에 들고 우클릭").withStyle(ChatFormatting.DARK_GRAY));
    }
}
