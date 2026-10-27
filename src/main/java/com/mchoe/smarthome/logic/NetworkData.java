package com.mchoe.smarthome.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 차원(월드)별 홈네트워크 등록부.
 * 청크가 언로드되어도 기기 번호와 단지 구획을 기억해서 호출 대상을 찾을 수 있게 한다.
 */
public class NetworkData extends SavedData {
    private static final String NAME = "smart_home_mod_network";

    public static final int UNSET = -1;

    public static final class Device {
        public final BlockPos pos;
        public DeviceType type;
        /** 관할경비실 번호 (경비실기는 자기 경비실번호) */
        public int guard = UNSET;
        public int dong = UNSET;
        /** 호 번호 (로비폰은 로비번호) */
        public int ho = UNSET;

        public Device(BlockPos pos, DeviceType type) {
            this.pos = pos.immutable();
            this.type = type;
        }

        boolean same(DeviceType t, int g, int d, int h) {
            return type == t && guard == g && dong == d && ho == h;
        }
    }

    public static final class Complex {
        public final BlockPos pos;
        public String name = "단지";
        public int x1, z1, x2, z2;

        public Complex(BlockPos pos) {
            this.pos = pos.immutable();
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= Math.min(x1, x2) && p.getX() <= Math.max(x1, x2)
                    && p.getZ() >= Math.min(z1, z2) && p.getZ() <= Math.max(z1, z2);
        }

        long area() {
            return (long) (Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1);
        }
    }

    private final Map<BlockPos, Device> devices = new LinkedHashMap<>();
    private final Map<BlockPos, Complex> complexes = new LinkedHashMap<>();

    public static NetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(NetworkData::load, NetworkData::new, NAME);
    }

    // ---------------- 기기 ----------------

    public Device getDevice(BlockPos pos) {
        return devices.get(pos);
    }

    public void putDevice(BlockPos pos, DeviceType type, int guard, int dong, int ho) {
        Device d = devices.get(pos);
        if (d != null && d.same(type, guard, dong, ho)) return;
        if (d == null) {
            d = new Device(pos, type);
            devices.put(d.pos, d);
        }
        d.type = type;
        d.guard = guard;
        d.dong = dong;
        d.ho = ho;
        setDirty();
    }

    public void removeDevice(BlockPos pos) {
        if (devices.remove(pos) != null) setDirty();
    }

    public List<Device> devicesIn(Complex c) {
        List<Device> out = new ArrayList<>();
        if (c == null) return out;
        for (Device d : devices.values()) {
            if (Objects.equals(complexAt(d.pos), c)) out.add(d);
        }
        return out;
    }

    // ---------------- 단지(구획) ----------------

    public Complex getComplex(BlockPos dashboardPos) {
        return complexes.get(dashboardPos);
    }

    public Complex putComplex(BlockPos pos, String name, int x1, int z1, int x2, int z2) {
        Complex c = complexes.computeIfAbsent(pos.immutable(), Complex::new);
        c.name = name;
        c.x1 = x1;
        c.z1 = z1;
        c.x2 = x2;
        c.z2 = z2;
        setDirty();
        return c;
    }

    public void removeComplex(BlockPos pos) {
        if (complexes.remove(pos) != null) setDirty();
    }

    /** 위치가 속한 단지. 구획이 겹치면 더 좁은 구획을 우선한다. */
    public Complex complexAt(BlockPos p) {
        Complex best = null;
        for (Complex c : complexes.values()) {
            if (c.contains(p) && (best == null || c.area() < best.area())) best = c;
        }
        return best;
    }

    // ---------------- 저장 ----------------

    public static NetworkData load(CompoundTag tag) {
        NetworkData data = new NetworkData();
        ListTag dl = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag t = dl.getCompound(i);
            BlockPos pos = BlockPos.of(t.getLong("pos"));
            Device d = new Device(pos, DeviceType.byId(t.getString("type")));
            d.guard = t.getInt("guard");
            d.dong = t.getInt("dong");
            d.ho = t.getInt("ho");
            data.devices.put(d.pos, d);
        }
        ListTag cl = tag.getList("complexes", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag t = cl.getCompound(i);
            Complex c = new Complex(BlockPos.of(t.getLong("pos")));
            c.name = t.getString("name");
            c.x1 = t.getInt("x1");
            c.z1 = t.getInt("z1");
            c.x2 = t.getInt("x2");
            c.z2 = t.getInt("z2");
            data.complexes.put(c.pos, c);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag dl = new ListTag();
        for (Device d : devices.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", d.pos.asLong());
            t.putString("type", d.type.id);
            t.putInt("guard", d.guard);
            t.putInt("dong", d.dong);
            t.putInt("ho", d.ho);
            dl.add(t);
        }
        tag.put("devices", dl);
        ListTag cl = new ListTag();
        for (Complex c : complexes.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", c.pos.asLong());
            t.putString("name", c.name);
            t.putInt("x1", c.x1);
            t.putInt("z1", c.z1);
            t.putInt("x2", c.x2);
            t.putInt("z2", c.z2);
            cl.add(t);
        }
        tag.put("complexes", cl);
        return tag;
    }
}
