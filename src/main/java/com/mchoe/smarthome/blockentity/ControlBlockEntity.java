package com.mchoe.smarthome.blockentity;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.block.ControlBlock;
import com.mchoe.smarthome.logic.ControlKind;
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

/** 세대 설비. 상태의 원본은 NetworkData(청크가 언로드돼도 월패드에서 제어 가능) */
public class ControlBlockEntity extends BlockEntity {
    // 클라이언트 표시용
    private boolean on;
    private int lvl, temp = 22;
    private String room = "";
    private int dong = -1, ho = -1;
    private int tickCounter;

    public ControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CONTROL_BE.get(), pos, state);
    }

    public ControlKind kind() {
        return getBlockState().getBlock() instanceof ControlBlock b ? b.kind : ControlKind.LIGHT;
    }

    public boolean isOn() { return on; }
    public int getLevelSetting() { return lvl; }
    public int getTemp() { return temp; }
    public String getRoom() { return room; }

    /** 서버 전용: 등록부의 설비 데이터 */
    public NetworkData.Control control() {
        return NetworkData.get((ServerLevel) level).ensureControl(worldPosition, kind());
    }

    /** 등록부 상태를 블록 상태(조명 밝기, 밸브 위치, 레드스톤)와 화면에 반영 */
    public void applyFromRegistry() {
        if (!(level instanceof ServerLevel)) return;
        NetworkData.Control c = control();
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof ControlBlock)) return;
        boolean powered = c.pulse > 0;
        BlockState ns = st.setValue(ControlBlock.ON, c.on).setValue(ControlBlock.POWERED, powered);
        if (ns != st) {
            level.setBlock(worldPosition, ns, 3);
            if (st.getValue(ControlBlock.POWERED) != powered) {
                level.updateNeighborsAt(worldPosition, ns.getBlock());
                level.updateNeighborsAt(worldPosition.relative(ns.getValue(ControlBlock.FACING).getOpposite()), ns.getBlock());
            }
        }
        if (on != c.on || lvl != c.level || temp != c.temp || !room.equals(c.room) || dong != c.dong || ho != c.ho) {
            on = c.on;
            lvl = c.level;
            temp = c.temp;
            room = c.room;
            dong = c.dong;
            ho = c.ho;
            level.sendBlockUpdated(worldPosition, ns, ns, 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        NetworkData.Control c = be.control();
        if (c.pulse > 0) {
            c.pulse--;
            if (c.pulse == 0 || c.pulse == 19) be.applyFromRegistry();
            return;
        }
        if (++be.tickCounter % 10 == 0) {
            if (c.kind.isMotion()) com.mchoe.smarthome.logic.SecurityLogic.sensorTick((ServerLevel) level, pos, state, c);
            be.applyFromRegistry();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("on", on);
        t.putInt("lvl", lvl);
        t.putInt("temp", temp);
        t.putString("room", room);
        t.putInt("dong", dong);
        t.putInt("ho", ho);
        return t;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("temp")) {
            on = tag.getBoolean("on");
            lvl = tag.getInt("lvl");
            temp = tag.getInt("temp");
            room = tag.getString("room");
            dong = tag.getInt("dong");
            ho = tag.getInt("ho");
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
