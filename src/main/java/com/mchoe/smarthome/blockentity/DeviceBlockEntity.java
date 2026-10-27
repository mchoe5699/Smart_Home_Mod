package com.mchoe.smarthome.blockentity;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.block.DeviceBlock;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mchoe.smarthome.logic.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceBlockEntity extends BlockEntity {
    private static final int MAX_LOG = 10;

    private int guard = NetworkData.UNSET;
    private int dong = NetworkData.UNSET;
    private int ho = NetworkData.UNSET;
    private final List<String> log = new ArrayList<>();
    private int doorTicks;

    // ---- 화면 표시용 (서버에서 계산 → 클라이언트 동기화)
    private int status;
    private String peer = "";
    private boolean video;
    private String visitor = "";
    private UUID visitorId;
    private String complexName = "";
    private String clock = "";

    private int tickCounter;

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DEVICE_BE.get(), pos, state);
    }

    public DeviceType getDeviceType() {
        return getBlockState().getBlock() instanceof DeviceBlock b ? b.type : DeviceType.WALLPAD;
    }

    public int getGuard() { return guard; }
    public int getDong() { return dong; }
    public int getHo() { return ho; }
    public List<String> getLog() { return log; }
    public int getStatus() { return status; }
    public String getPeer() { return peer; }
    public boolean isVideo() { return video; }
    public String getVisitor() { return visitor; }
    public UUID getVisitorId() { return visitorId; }
    public String getComplexName() { return complexName; }
    public String getClock() { return clock; }

    public void setNumbers(int guard, int dong, int ho) {
        this.guard = guard;
        this.dong = dong;
        this.ho = ho;
        syncRegistry();
        changed();
    }

    public void addLog(String line) {
        log.add(0, line);
        while (log.size() > MAX_LOG) log.remove(log.size() - 1);
        setChanged();
    }

    public void openDoor(int ticks) {
        doorTicks = ticks;
        if (level != null && getBlockState().getBlock() instanceof DeviceBlock b) b.setPowered(level, worldPosition, getBlockState(), true);
    }

    public void syncRegistry() {
        if (level instanceof ServerLevel sl) {
            NetworkData.get(sl).putDevice(worldPosition, getDeviceType(), guard, dong, ho);
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncRegistry();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeviceBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.tickCounter++;
        if (be.doorTicks > 0 && --be.doorTicks == 0 && state.getBlock() instanceof DeviceBlock b) {
            b.setPowered(level, pos, state, false);
        }
        if (be.tickCounter % 100 == 1) be.syncRegistry();
        if (be.tickCounter % 10 != 0) return;

        CompoundTag t = new CompoundTag();
        DeviceLogic.writeStatus(sl, pos, t);
        NetworkData.Complex cx = NetworkData.get(sl).complexAt(pos);
        String cname = cx == null ? "" : cx.name;
        String clk = DeviceLogic.timeString(sl);
        clk = clk.substring(clk.indexOf(' ') + 1);
        UUID vid = t.hasUUID("visitorId") ? t.getUUID("visitorId") : null;

        boolean dirty = be.status != t.getInt("status") || !be.peer.equals(t.getString("peer"))
                || be.video != t.getBoolean("video") || !be.visitor.equals(t.getString("visitor"))
                || !java.util.Objects.equals(be.visitorId, vid) || !be.complexName.equals(cname)
                || !be.clock.equals(clk);
        if (dirty) {
            be.status = t.getInt("status");
            be.peer = t.getString("peer");
            be.video = t.getBoolean("video");
            be.visitor = t.getString("visitor");
            be.visitorId = vid;
            be.complexName = cname;
            be.clock = clk;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("guard", guard);
        tag.putInt("dong", dong);
        tag.putInt("ho", ho);
        tag.putInt("doorTicks", doorTicks);
        ListTag l = new ListTag();
        for (String s : log) l.add(StringTag.valueOf(s));
        tag.put("log", l);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        guard = tag.contains("guard") ? tag.getInt("guard") : NetworkData.UNSET;
        dong = tag.contains("dong") ? tag.getInt("dong") : NetworkData.UNSET;
        ho = tag.contains("ho") ? tag.getInt("ho") : NetworkData.UNSET;
        doorTicks = tag.getInt("doorTicks");
        log.clear();
        ListTag l = tag.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) log.add(l.getString(i));
        if (tag.contains("status")) {
            status = tag.getInt("status");
            peer = tag.getString("peer");
            video = tag.getBoolean("video");
            visitor = tag.getString("visitor");
            visitorId = tag.hasUUID("visitorId") ? tag.getUUID("visitorId") : null;
            complexName = tag.getString("complexName");
            clock = tag.getString("clock");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("guard", guard);
        tag.putInt("dong", dong);
        tag.putInt("ho", ho);
        tag.putInt("status", status);
        tag.putString("peer", peer);
        tag.putBoolean("video", video);
        tag.putString("visitor", visitor);
        if (visitorId != null) tag.putUUID("visitorId", visitorId);
        tag.putString("complexName", complexName);
        tag.putString("clock", clock);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
