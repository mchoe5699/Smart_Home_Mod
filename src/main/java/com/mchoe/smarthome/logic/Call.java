package com.mchoe.smarthome.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 진행 중인 호출/통화 한 건 */
public class Call {
    public final int id;
    public final ResourceKey<Level> dim;
    public final BlockPos caller;
    public final DeviceType callerType;
    public final String callerLabel;
    /** 호출을 받는 기기들 (응답 전까지 모두 울림) */
    public final List<BlockPos> targets = new ArrayList<>();
    public final List<String> targetLabels = new ArrayList<>();
    /** 응답한 기기 (null 이면 아직 울리는 중) */
    public BlockPos answered;
    public String answeredLabel = "";
    public long createdTick;
    public long answeredTick;
    /** 방문자 영상 (카메라 기기에서 건 호출) */
    public UUID visitorId;
    public String visitorName = "";

    public Call(int id, ResourceKey<Level> dim, BlockPos caller, DeviceType callerType, String callerLabel) {
        this.id = id;
        this.dim = dim;
        this.caller = caller.immutable();
        this.callerType = callerType;
        this.callerLabel = callerLabel;
    }

    public boolean isConnected() {
        return answered != null;
    }

    public boolean hasVideo() {
        return callerType.camera;
    }

    /** 이 위치의 기기가 이 통화에 관여하고 있는가 */
    public boolean involves(BlockPos pos) {
        if (caller.equals(pos)) return true;
        if (answered != null) return answered.equals(pos);
        return targets.contains(pos);
    }
}
