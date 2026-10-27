package com.mchoe.smarthome.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** 북쪽을 바라보는 기준 모양을 4방향으로 회전 */
public final class ShapeUtil {
    private ShapeUtil() {}

    public static Map<Direction, VoxelShape> horizontal(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, north);
        VoxelShape s = north;
        Direction d = Direction.NORTH;
        for (int i = 0; i < 3; i++) {
            s = rotateCW(s);
            d = d.getClockWise();
            map.put(d, s);
        }
        return map;
    }

    private static VoxelShape rotateCW(VoxelShape shape) {
        VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        for (AABB b : shape.toAabbs()) {
            out = net.minecraft.world.phys.shapes.Shapes.or(out,
                    net.minecraft.world.phys.shapes.Shapes.box(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX));
        }
        return out;
    }
}
