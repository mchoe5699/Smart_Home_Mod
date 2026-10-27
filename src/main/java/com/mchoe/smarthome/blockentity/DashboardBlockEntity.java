package com.mchoe.smarthome.blockentity;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.logic.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 단지 대시보드: 단지 이름과 구획(X/Z 범위)을 정한다. 구획 안의 기기끼리만 연동된다. */
public class DashboardBlockEntity extends BlockEntity {
    public static final int DEFAULT_RADIUS = 48;

    private String name = "단지";
    private int x1, z1, x2, z2;
    private boolean initialized;
    private int deviceCount;
    private int tickCounter;

    public DashboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DASHBOARD_BE.get(), pos, state);
    }

    public String getComplexName() { return name; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public int getDeviceCount() { return deviceCount; }

    public void setArea(String name, int x1, int z1, int x2, int z2) {
        this.name = name;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.initialized = true;
        syncRegistry();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void ensureInit() {
        if (!initialized) {
            initialized = true;
            x1 = worldPosition.getX() - DEFAULT_RADIUS;
            z1 = worldPosition.getZ() - DEFAULT_RADIUS;
            x2 = worldPosition.getX() + DEFAULT_RADIUS;
            z2 = worldPosition.getZ() + DEFAULT_RADIUS;
            setChanged();
        }
    }

    public void syncRegistry() {
        if (level instanceof ServerLevel sl) {
            ensureInit();
            NetworkData.get(sl).putComplex(worldPosition, name, x1, z1, x2, z2);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncRegistry();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DashboardBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.tickCounter++ % 40 != 0) return;
        NetworkData data = NetworkData.get(sl);
        if (data.getComplex(pos) == null) be.syncRegistry();
        int count = data.devicesIn(data.getComplex(pos)).size();
        if (count != be.deviceCount) {
            be.deviceCount = count;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("name", name);
        tag.putInt("x1", x1);
        tag.putInt("z1", z1);
        tag.putInt("x2", x2);
        tag.putInt("z2", z2);
        tag.putBoolean("init", initialized);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        name = tag.contains("name") ? tag.getString("name") : "단지";
        x1 = tag.getInt("x1");
        z1 = tag.getInt("z1");
        x2 = tag.getInt("x2");
        z2 = tag.getInt("z2");
        initialized = tag.getBoolean("init");
        if (tag.contains("count")) deviceCount = tag.getInt("count");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        tag.putInt("count", deviceCount);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
