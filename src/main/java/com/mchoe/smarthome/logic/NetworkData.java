package com.mchoe.smarthome.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 차원(월드)별 홈네트워크 등록부.
 * - 기기 / 세대 설비(홈 제어기)
 * - 단지: 여러 구획(사각형)으로 이루어지며, 구획을 묶거나 분리할 수 있다
 * - 단지별 공용 비밀번호, 세대별 비밀번호, 등록 카드, 공지사항
 */
public class NetworkData extends SavedData {
    private static final String NAME = "smart_home_mod_network_v2";
    public static final int UNSET = -1;

    // ------------------------------------------------------------------ 자료형

    public static final class Device {
        public final BlockPos pos;
        public DeviceType type;
        public int guard = UNSET, dong = UNSET, ho = UNSET;

        Device(BlockPos pos, DeviceType type) {
            this.pos = pos.immutable();
            this.type = type;
        }
    }

    public static final class Control {
        public final BlockPos pos;
        public ControlKind kind;
        public int dong = UNSET, ho = UNSET;
        public String room = "";
        public boolean on;
        /** 환기 단계 0~3 */
        public int level = 1;
        /** 난방 설정 온도 */
        public int temp = 22;
        /** 엘리베이터 연동기 신호 남은 틱 (저장 안 함) */
        public int pulse;

        Control(BlockPos pos, ControlKind kind) {
            this.pos = pos.immutable();
            this.kind = kind;
        }
    }

    public static final class Zone {
        public final int id;
        public int x1, z1, x2, z2;
        public int complex;

        Zone(int id) {
            this.id = id;
        }

        public boolean contains(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        long area() {
            return (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        }
    }

    public static final class Complex {
        public final int id;
        public String name;
        public String commonPassword = "";
        public String notice = "";

        Complex(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Card {
        public final int id;
        public int complex;
        /** UNSET 이면 단지 전체 출입(관리자 카드) */
        public int dong = UNSET, ho = UNSET;
        public String holder = "";
        public boolean revoked;

        Card(int id) {
            this.id = id;
        }
    }

    private final Map<BlockPos, Device> devices = new LinkedHashMap<>();
    private final Map<BlockPos, Control> controls = new LinkedHashMap<>();
    private final Map<Integer, Zone> zones = new LinkedHashMap<>();
    private final Map<Integer, Complex> complexes = new LinkedHashMap<>();
    private final Map<String, String> householdPasswords = new HashMap<>();
    private final Set<String> awayHouseholds = new HashSet<>();
    private final Map<Integer, Card> cards = new LinkedHashMap<>();
    private int nextZone = 1, nextComplex = 1, nextCard = 1;

    public static NetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(NetworkData::load, NetworkData::new, NAME);
    }

    public static String householdKey(int complex, int dong, int ho) {
        return complex + ":" + dong + ":" + ho;
    }

    // ------------------------------------------------------------------ 기기

    public Device getDevice(BlockPos pos) {
        return devices.get(pos);
    }

    public void putDevice(BlockPos pos, DeviceType type, int guard, int dong, int ho) {
        Device d = devices.get(pos);
        if (d != null && d.type == type && d.guard == guard && d.dong == dong && d.ho == ho) return;
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

    public List<Device> devicesIn(int complex) {
        List<Device> out = new ArrayList<>();
        if (complex < 0) return out;
        for (Device d : devices.values()) if (complexAt(d.pos) == complex) out.add(d);
        return out;
    }

    // ------------------------------------------------------------------ 세대 설비

    public Control getControl(BlockPos pos) {
        return controls.get(pos);
    }

    public Control ensureControl(BlockPos pos, ControlKind kind) {
        Control c = controls.get(pos);
        if (c == null) {
            c = new Control(pos, kind);
            controls.put(c.pos, c);
            setDirty();
        } else if (c.kind != kind) {
            c.kind = kind;
            setDirty();
        }
        return c;
    }

    public void removeControl(BlockPos pos) {
        if (controls.remove(pos) != null) setDirty();
    }

    public List<Control> controlsOf(int complex, int dong, int ho) {
        List<Control> out = new ArrayList<>();
        if (complex < 0 || dong < 0 || ho < 0) return out;
        for (Control c : controls.values()) {
            if (c.dong == dong && c.ho == ho && complexAt(c.pos) == complex) out.add(c);
        }
        return out;
    }

    public Iterable<Control> allControls() {
        return controls.values();
    }

    public boolean isAway(int complex, int dong, int ho) {
        return awayHouseholds.contains(householdKey(complex, dong, ho));
    }

    public void setAway(int complex, int dong, int ho, boolean away) {
        String k = householdKey(complex, dong, ho);
        if (away ? awayHouseholds.add(k) : awayHouseholds.remove(k)) setDirty();
    }

    // ------------------------------------------------------------------ 단지 / 구획

    /** 위치가 속한 단지 번호 (없으면 -1). 겹치면 더 작은 구획 우선. */
    public int complexAt(BlockPos p) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (z.contains(p.getX(), p.getZ()) && (best == null || z.area() < best.area())) best = z;
        }
        return best == null ? -1 : best.complex;
    }

    public Complex getComplex(int id) {
        return complexes.get(id);
    }

    public Iterable<Zone> allZones() {
        return zones.values();
    }

    public Iterable<Complex> allComplexes() {
        return complexes.values();
    }

    public List<Zone> zonesOf(int complex) {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones.values()) if (z.complex == complex) out.add(z);
        return out;
    }

    public Zone addZone(int x1, int z1, int x2, int z2) {
        Zone z = new Zone(nextZone++);
        z.x1 = Math.min(x1, x2);
        z.z1 = Math.min(z1, z2);
        z.x2 = Math.max(x1, x2);
        z.z2 = Math.max(z1, z2);
        Complex c = new Complex(nextComplex++, "단지 " + (nextComplex - 1));
        complexes.put(c.id, c);
        z.complex = c.id;
        zones.put(z.id, z);
        setDirty();
        return z;
    }

    public void deleteZone(int zoneId) {
        Zone z = zones.remove(zoneId);
        if (z == null) return;
        if (zonesOf(z.complex).isEmpty()) removeComplexData(z.complex);
        setDirty();
    }

    /** 선택한 구획들이 속한 단지를 하나로 묶는다 (첫 번째 단지로 합침) */
    public void merge(List<Integer> zoneIds) {
        Integer target = null;
        Set<Integer> others = new HashSet<>();
        for (int id : zoneIds) {
            Zone z = zones.get(id);
            if (z == null) continue;
            if (target == null) target = z.complex;
            else if (z.complex != target) others.add(z.complex);
        }
        if (target == null || others.isEmpty()) return;
        for (int other : others) {
            for (Zone z : zones.values()) if (z.complex == other) z.complex = target;
            for (Card c : cards.values()) if (c.complex == other) c.complex = target;
            rekey(householdPasswords, other, target);
            Set<String> away = new HashSet<>(awayHouseholds);
            for (String k : away) {
                if (k.startsWith(other + ":")) {
                    awayHouseholds.remove(k);
                    awayHouseholds.add(target + k.substring(k.indexOf(':')));
                }
            }
            complexes.remove(other);
        }
        setDirty();
    }

    private static void rekey(Map<String, String> map, int from, int to) {
        for (String k : new ArrayList<>(map.keySet())) {
            if (k.startsWith(from + ":")) {
                String nk = to + k.substring(k.indexOf(':'));
                String v = map.remove(k);
                map.putIfAbsent(nk, v);
            }
        }
    }

    /** 선택한 구획을 각각 별도 단지로 분리한다 */
    public void split(List<Integer> zoneIds) {
        Set<Integer> keptOriginal = new HashSet<>();
        for (int id : zoneIds) {
            Zone z = zones.get(id);
            if (z == null) continue;
            int remaining = 0;
            for (Zone o : zones.values()) if (o.complex == z.complex && !zoneIds.contains(o.id)) remaining++;
            // 단지의 모든 구획이 선택됐다면 첫 구획은 원래 단지를 유지
            if (remaining == 0 && keptOriginal.add(z.complex)) continue;
            Complex old = complexes.get(z.complex);
            Complex c = new Complex(nextComplex++, (old == null ? "단지" : old.name) + "-" + z.id);
            if (old != null) c.commonPassword = old.commonPassword;
            complexes.put(c.id, c);
            z.complex = c.id;
        }
        setDirty();
    }

    public void renameComplex(int id, String name) {
        Complex c = complexes.get(id);
        if (c != null) {
            c.name = name;
            setDirty();
        }
    }

    private void removeComplexData(int complex) {
        complexes.remove(complex);
        householdPasswords.keySet().removeIf(k -> k.startsWith(complex + ":"));
        awayHouseholds.removeIf(k -> k.startsWith(complex + ":"));
        cards.values().removeIf(c -> c.complex == complex);
    }

    // ------------------------------------------------------------------ 비밀번호

    public String householdPassword(int complex, int dong, int ho) {
        return householdPasswords.getOrDefault(householdKey(complex, dong, ho), "");
    }

    public void setHouseholdPassword(int complex, int dong, int ho, String pw) {
        String k = householdKey(complex, dong, ho);
        if (pw == null || pw.isEmpty()) householdPasswords.remove(k);
        else householdPasswords.put(k, pw);
        setDirty();
    }

    public Map<String, String> householdPasswordsOf(int complex) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : householdPasswords.entrySet()) {
            if (e.getKey().startsWith(complex + ":")) out.put(e.getKey().substring(e.getKey().indexOf(':') + 1), e.getValue());
        }
        return out;
    }

    public void setCommonPassword(int complex, String pw) {
        Complex c = complexes.get(complex);
        if (c != null) {
            c.commonPassword = pw;
            setDirty();
        }
    }

    public void setNotice(int complex, String notice) {
        Complex c = complexes.get(complex);
        if (c != null) {
            c.notice = notice;
            setDirty();
        }
    }

    // ------------------------------------------------------------------ 카드

    public Card registerCard(int complex, int dong, int ho, String holder) {
        Card c = new Card(nextCard++);
        c.complex = complex;
        c.dong = dong;
        c.ho = ho;
        c.holder = holder;
        cards.put(c.id, c);
        setDirty();
        return c;
    }

    public Card getCard(int id) {
        return cards.get(id);
    }

    public List<Card> cardsOf(int complex) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards.values()) if (c.complex == complex) out.add(c);
        return out;
    }

    public void setCardRevoked(int id, boolean revoked) {
        Card c = cards.get(id);
        if (c != null) {
            c.revoked = revoked;
            setDirty();
        }
    }

    public void deleteCard(int id) {
        if (cards.remove(id) != null) setDirty();
    }

    // ------------------------------------------------------------------ 저장

    public static NetworkData load(CompoundTag tag) {
        NetworkData data = new NetworkData();
        data.nextZone = Math.max(1, tag.getInt("nextZone"));
        data.nextComplex = Math.max(1, tag.getInt("nextComplex"));
        data.nextCard = Math.max(1, tag.getInt("nextCard"));

        ListTag dl = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag t = dl.getCompound(i);
            Device d = new Device(BlockPos.of(t.getLong("pos")), DeviceType.byId(t.getString("type")));
            d.guard = t.getInt("guard");
            d.dong = t.getInt("dong");
            d.ho = t.getInt("ho");
            data.devices.put(d.pos, d);
        }
        ListTag cl = tag.getList("controls", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag t = cl.getCompound(i);
            Control c = new Control(BlockPos.of(t.getLong("pos")), ControlKind.byId(t.getString("kind")));
            c.dong = t.getInt("dong");
            c.ho = t.getInt("ho");
            c.room = t.getString("room");
            c.on = t.getBoolean("on");
            c.level = t.getInt("level");
            c.temp = t.getInt("temp");
            data.controls.put(c.pos, c);
        }
        ListTag zl = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zl.size(); i++) {
            CompoundTag t = zl.getCompound(i);
            Zone z = new Zone(t.getInt("id"));
            z.x1 = t.getInt("x1");
            z.z1 = t.getInt("z1");
            z.x2 = t.getInt("x2");
            z.z2 = t.getInt("z2");
            z.complex = t.getInt("complex");
            data.zones.put(z.id, z);
        }
        ListTag xl = tag.getList("complexes", Tag.TAG_COMPOUND);
        for (int i = 0; i < xl.size(); i++) {
            CompoundTag t = xl.getCompound(i);
            Complex c = new Complex(t.getInt("id"), t.getString("name"));
            c.commonPassword = t.getString("common");
            c.notice = t.getString("notice");
            data.complexes.put(c.id, c);
        }
        CompoundTag pw = tag.getCompound("householdPw");
        for (String k : pw.getAllKeys()) data.householdPasswords.put(k, pw.getString(k));
        ListTag aw = tag.getList("away", Tag.TAG_STRING);
        for (int i = 0; i < aw.size(); i++) data.awayHouseholds.add(aw.getString(i));
        ListTag kl = tag.getList("cards", Tag.TAG_COMPOUND);
        for (int i = 0; i < kl.size(); i++) {
            CompoundTag t = kl.getCompound(i);
            Card c = new Card(t.getInt("id"));
            c.complex = t.getInt("complex");
            c.dong = t.getInt("dong");
            c.ho = t.getInt("ho");
            c.holder = t.getString("holder");
            c.revoked = t.getBoolean("revoked");
            data.cards.put(c.id, c);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextZone", nextZone);
        tag.putInt("nextComplex", nextComplex);
        tag.putInt("nextCard", nextCard);
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
        for (Control c : controls.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", c.pos.asLong());
            t.putString("kind", c.kind.id);
            t.putInt("dong", c.dong);
            t.putInt("ho", c.ho);
            t.putString("room", c.room);
            t.putBoolean("on", c.on);
            t.putInt("level", c.level);
            t.putInt("temp", c.temp);
            cl.add(t);
        }
        tag.put("controls", cl);
        ListTag zl = new ListTag();
        for (Zone z : zones.values()) {
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
        ListTag xl = new ListTag();
        for (Complex c : complexes.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putString("name", c.name);
            t.putString("common", c.commonPassword);
            t.putString("notice", c.notice);
            xl.add(t);
        }
        tag.put("complexes", xl);
        CompoundTag pw = new CompoundTag();
        for (Map.Entry<String, String> e : householdPasswords.entrySet()) pw.putString(e.getKey(), e.getValue());
        tag.put("householdPw", pw);
        ListTag aw = new ListTag();
        for (String s : awayHouseholds) aw.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("away", aw);
        ListTag kl = new ListTag();
        for (Card c : cards.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putInt("complex", c.complex);
            t.putInt("dong", c.dong);
            t.putInt("ho", c.ho);
            t.putString("holder", c.holder);
            t.putBoolean("revoked", c.revoked);
            kl.add(t);
        }
        tag.put("cards", kl);
        return tag;
    }
}
