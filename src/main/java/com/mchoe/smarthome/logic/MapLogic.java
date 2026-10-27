package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.network.MapDataPacket;
import com.mchoe.smarthome.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** 단지 대시보드 지도: 지형 색상과 구획 편집 */
public final class MapLogic {
    public static final int SIZE = 256;
    private static final int MAX_ZONE = 2048;

    private MapLogic() {}

    public static void handle(ServerPlayer player, ServerLevel level, String action, CompoundTag args) {
        NetworkData data = NetworkData.get(level);
        switch (action) {
            case "map" -> {
                sendMap(player, level, args.getInt("cx"), args.getInt("cz"), true);
                return;
            }
            case "zone_add" -> {
                int x1 = args.getInt("x1"), z1 = args.getInt("z1"), x2 = args.getInt("x2"), z2 = args.getInt("z2");
                if (Math.abs(x2 - x1) > MAX_ZONE || Math.abs(z2 - z1) > MAX_ZONE) {
                    DeviceLogic.msg(player, "구획이 너무 큽니다", ChatFormatting.RED);
                } else {
                    NetworkData.Zone z = data.addZone(x1, z1, x2, z2);
                    DeviceLogic.msg(player, data.getComplex(z.complex).name + " 구획을 만들었습니다", ChatFormatting.GREEN);
                }
            }
            case "zone_delete" -> {
                for (int id : args.getIntArray("ids")) data.deleteZone(id);
            }
            case "merge" -> {
                data.merge(toList(args.getIntArray("ids")));
                DeviceLogic.msg(player, "선택한 단지를 하나로 묶었습니다", ChatFormatting.GREEN);
            }
            case "split" -> {
                data.split(toList(args.getIntArray("ids")));
                DeviceLogic.msg(player, "선택한 구획을 분리했습니다", ChatFormatting.GREEN);
            }
            case "rename" -> {
                String name = args.getString("name").trim();
                if (name.isEmpty()) name = "단지";
                if (name.length() > 20) name = name.substring(0, 20);
                data.renameComplex(args.getInt("complex"), name);
            }
            default -> { }
        }
        sendMap(player, level, args.getInt("cx"), args.getInt("cz"), false);
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int i : a) l.add(i);
        return l;
    }

    public static void sendMap(ServerPlayer player, ServerLevel level, int cx, int cz, boolean colors) {
        int ox = cx - SIZE / 2, oz = cz - SIZE / 2;
        byte[] rgb = colors ? terrain(level, ox, oz) : new byte[0];
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MapDataPacket(ox, oz, SIZE, rgb, zonesTag(level)));
    }

    public static CompoundTag zonesTag(ServerLevel level) {
        NetworkData data = NetworkData.get(level);
        CompoundTag tag = new CompoundTag();
        ListTag zl = new ListTag();
        for (NetworkData.Zone z : data.allZones()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", z.id);
            t.putInt("x1", z.x1);
            t.putInt("z1", z.z1);
            t.putInt("x2", z.x2);
            t.putInt("z2", z.z2);
            t.putInt("complex", z.complex);
            zl.add(t);
        }
        tag.put("zones", zl);
        ListTag cl = new ListTag();
        for (NetworkData.Complex c : data.allComplexes()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putString("name", c.name);
            t.putInt("devices", data.devicesIn(c.id).size());
            cl.add(t);
        }
        tag.put("complexes", cl);
        return tag;
    }

    /** 위에서 내려다본 지형 색 (불러온 청크만, 나머지는 회색) */
    private static byte[] terrain(ServerLevel level, int ox, int oz) {
        byte[] out = new byte[SIZE * SIZE * 3];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int[] prevH = new int[SIZE];
        for (int dz = -1; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int x = ox + dx, z = oz + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                int h;
                int color;
                if (chunk == null) {
                    h = 0;
                    color = ((x >> 4) + (z >> 4)) % 2 == 0 ? 0x2A2E34 : 0x30343A;
                } else {
                    h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    mp.set(x, h, z);
                    BlockState st = chunk.getBlockState(mp);
                    int depth = 0;
                    while (mp.getY() > level.getMinBuildHeight() && (st.isAir() || st.getMapColor(level, mp) == MapColor.NONE) && depth < 64) {
                        mp.move(0, -1, 0);
                        st = chunk.getBlockState(mp);
                        depth++;
                    }
                    h = mp.getY();
                    color = st.getMapColor(level, mp).col;
                    if (!st.getFluidState().isEmpty()) {
                        int wd = 0;
                        BlockPos.MutableBlockPos wp = mp.mutable();
                        while (wd < 10 && !chunk.getBlockState(wp).getFluidState().isEmpty()) {
                            wp.move(0, -1, 0);
                            wd++;
                        }
                        color = shade(color, 1.1 - wd * 0.05);
                    }
                }
                if (dz >= 0) {
                    double f = 1.0;
                    if (chunk != null) {
                        if (h > prevH[dx]) f = 1.12;
                        else if (h < prevH[dx]) f = 0.82;
                    }
                    int c = shade(color, f);
                    int i = (dz * SIZE + dx) * 3;
                    out[i] = (byte) (c >> 16);
                    out[i + 1] = (byte) (c >> 8);
                    out[i + 2] = (byte) c;
                }
                prevH[dx] = h;
            }
        }
        return out;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
        int b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
