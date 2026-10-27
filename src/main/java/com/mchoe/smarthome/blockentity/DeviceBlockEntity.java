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

public class DeviceBlockEntity extends BlockEntity {
    private static final int MAX_LOG = 12;

    private int guard = NetworkData.UNSET;
    private int dong = NetworkData.UNSET;
    private int ho = NetworkData.UNSET;
    /** 도어카메라·도어폰: 홈 링커로 연동됨 */
    private boolean linked;
    /** 관리 비밀번호 (설정되면 설정 메뉴 진입에 필요) */
    private String adminPw = "";
    /** 경비실기 외출 / 우회 경비실 번호 */
    private boolean away;
    private int bypass = NetworkData.UNSET;
    private final List<String> log = new ArrayList<>();
    private int doorTicks;

    // ---- 화면 표시용 (서버 계산 → 클라이언트 동기화)
    private int status;
    private String peer = "";
    private long camPos = Long.MIN_VALUE;
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
    public boolean isLinked() { return linked; }
    public boolean isAway() { return away; }
    public int getBypass() { return bypass; }
    public boolean hasAdminPw() { return !adminPw.isEmpty(); }

    public boolean checkAdmin(String pw) {
        return adminPw.isEmpty() || adminPw.equals(pw);
    }

    public void setAdminPw(String pw) {
        adminPw = pw == null ? "" : pw;
        setChanged();
    }

    public void setAway(boolean away, int bypass) {
        this.away = away;
        this.bypass = bypass;
        syncRegistry();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** 홈 링커: 세대기의 동·호로 연동(또는 해제) */
    public void setLink(int dong, int ho, boolean linked) {
        this.linked = linked;
        setNumbers(guard, dong, ho);
    }
    public List<String> getLog() { return log; }
    public int getStatus() { return status; }
    public String getPeer() { return peer; }
    public String getComplexName() { return complexName; }
    public String getClock() { return clock; }

    /** 영상을 보내는 카메라 위치 (없으면 null) */
    public BlockPos getCamPos() {
        return camPos == Long.MIN_VALUE ? null : BlockPos.of(camPos);
    }

    public boolean isDoorOpen() {
        return getBlockState().hasProperty(DeviceBlock.POWERED) && getBlockState().getValue(DeviceBlock.POWERED);
    }

    public void setNumbers(int guard, int dong, int ho) {
        this.guard = guard;
        this.dong = dong;
        this.ho = ho;
        syncRegistry();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
            NetworkData data = NetworkData.get(sl);
            data.putDevice(worldPosition, getDeviceType(), guard, dong, ho, linked);
            data.setGuardState(worldPosition, away, bypass);
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
            b.setPowered(level, pos, level.getBlockState(pos), false);
        }
        if (be.tickCounter % 100 == 1) be.syncRegistry();
        if (be.tickCounter % 10 != 0) return;

        CompoundTag t = new CompoundTag();
        DeviceLogic.writeStatus(sl, pos, t);
        NetworkData data = NetworkData.get(sl);
        NetworkData.Complex cx = data.getComplex(data.complexAt(pos));
        String cname = cx == null ? "" : cx.name;
        String clk = DeviceLogic.timeString(sl);
        clk = clk.substring(clk.indexOf(' ') + 1);
        long cam = t.contains("camPos") ? t.getLong("camPos") : Long.MIN_VALUE;

        boolean dirty = be.status != t.getInt("status") || !be.peer.equals(t.getString("peer"))
                || be.camPos != cam || !be.complexName.equals(cname) || !be.clock.equals(clk);
        if (dirty) {
            be.status = t.getInt("status");
            be.peer = t.getString("peer");
            be.camPos = cam;
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
        tag.putBoolean("linked", linked);
        tag.putString("adminPw", adminPw);
        tag.putBoolean("away", away);
        tag.putInt("bypass", bypass);
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
        linked = tag.getBoolean("linked");
        if (tag.contains("adminPw")) adminPw = tag.getString("adminPw");
        away = tag.getBoolean("away");
        bypass = tag.contains("bypass") ? tag.getInt("bypass") : NetworkData.UNSET;
        if (tag.contains("log")) {
            log.clear();
            ListTag l = tag.getList("log", Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) log.add(l.getString(i));
        }
        if (tag.contains("status")) {
            status = tag.getInt("status");
            peer = tag.getString("peer");
            camPos = tag.contains("camPos") ? tag.getLong("camPos") : Long.MIN_VALUE;
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
        tag.putBoolean("away", away);
        tag.putInt("bypass", bypass);
        tag.putInt("status", status);
        tag.putString("peer", peer);
        if (camPos != Long.MIN_VALUE) tag.putLong("camPos", camPos);
        tag.putString("complexName", complexName);
        tag.putString("clock", clock);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
