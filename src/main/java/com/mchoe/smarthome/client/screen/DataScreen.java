package com.mchoe.smarthome.client.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** 서버에서 받은 데이터로 갱신되는 화면 */
public interface DataScreen {
    BlockPos getPos();

    void updateData(CompoundTag data);
}
