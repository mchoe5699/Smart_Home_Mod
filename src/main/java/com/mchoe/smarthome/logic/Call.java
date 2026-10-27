package com.mchoe.smarthome.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 진행 중인 호출/통화 한 건 */
public class Call {
    public static final int MAX_MESSAGES = 40;

    public final int id;
    public final ResourceKey<Level> dim;
    public final BlockPos caller;
    public final DeviceType callerType;
    public final String callerLabel;
    public final List<BlockPos> targets = new ArrayList<>();
    public final List<String> targetLabels = new ArrayList<>();
    public BlockPos answered;
    public String answeredLabel = "";
    public long createdTick;
    public long answeredTick;
    /** 통화 중 주고받은 대화 */
    public final List<String> messages = new ArrayList<>();

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

    /** 카메라 기기(도어카메라·로비폰)에서 건 호출은 영상이 송출된다 */
    public boolean hasVideo() {
        return callerType.camera;
    }

    public void addMessage(String line) {
        messages.add(line);
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
    }
}
