package com.mchoe.smarthome.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 도어 링커로 연동한 문을 열고, 잠시 뒤 닫는다 (문·다락문·울타리 문) */
public final class DoorControl {
    private record Pending(ResourceKey<Level> dim, BlockPos pos, long closeAt) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static long now;

    private DoorControl() {}

    /** 링커로 연동할 수 있는 블록인가 (문이면 아래쪽 칸 위치로 맞춘다) */
    public static BlockPos normalize(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return pos.below();
        return pos;
    }

    public static boolean isDoor(BlockState st) {
        return st.hasProperty(BlockStateProperties.OPEN);
    }

    /** 문들을 열고 ticks 뒤 닫는다. 연 문의 수를 돌려준다 */
    public static int open(ServerLevel level, Collection<BlockPos> doors, int ticks) {
        int n = 0;
        for (BlockPos p : doors) {
            if (!level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!isDoor(st)) continue;
            setOpen(level, p, st, true);
            PENDING.removeIf(e -> e.dim.equals(level.dimension()) && e.pos.equals(p));
            PENDING.add(new Pending(level.dimension(), p.immutable(), now + ticks));
            n++;
        }
        return n;
    }

    private static void setOpen(ServerLevel level, BlockPos p, BlockState st, boolean open) {
        if (st.getValue(BlockStateProperties.OPEN) == open) return;
        if (st.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, st, p, open);
        } else {
            level.setBlock(p, st.setValue(BlockStateProperties.OPEN, open), 10);
            level.playSound(null, p, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    public static void tick(MinecraftServer server) {
        now++;
        if (PENDING.isEmpty()) return;
        PENDING.removeIf(e -> {
            if (e.closeAt > now) return false;
            ServerLevel level = server.getLevel(e.dim);
            if (level != null && level.isLoaded(e.pos)) {
                BlockState st = level.getBlockState(e.pos);
                if (isDoor(st)) setOpen(level, e.pos, st, false);
            }
            return true;
        });
    }
}
