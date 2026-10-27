package com.mchoe.smarthome.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 카드등록용 카드: 관리 컴퓨터에서 등록하면 출입용 카드가 된다 */
public class BlankCardItem extends Item {
    public BlankCardItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("관리 컴퓨터 → 카드 관리에서 등록").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("등록하면 출입용 카드로 바뀝니다").withStyle(ChatFormatting.DARK_GRAY));
    }
}
