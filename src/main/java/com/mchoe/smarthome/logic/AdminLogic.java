package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.item.AccessCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 관리 컴퓨터: 단지 현황, 카드 등록/관리, 비밀번호, 공지사항 */
public final class AdminLogic {
    private AdminLogic() {}

    public static CompoundTag computerScreenData(ServerLevel level, BlockPos pos) {
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(pos);
        NetworkData.Complex cx = data.getComplex(cid);
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "computer");
        tag.putInt("complexId", cid);
        if (cx == null) return tag;
        tag.putString("complex", cx.name);
        tag.putString("notice", cx.notice);
        tag.putString("commonPw", cx.commonPassword);
        tag.putInt("zones", data.zonesOf(cid).size());

        Map<DeviceType, Integer> counts = new EnumMap<>(DeviceType.class);
        ListTag devices = new ListTag();
        List<NetworkData.Device> ds = data.devicesIn(cid);
        ds.sort((a, b) -> {
            int c = Integer.compare(a.type.ordinal(), b.type.ordinal());
            if (c != 0) return c;
            c = Integer.compare(a.dong, b.dong);
            return c != 0 ? c : Integer.compare(a.ho, b.ho);
        });
        for (NetworkData.Device d : ds) {
            counts.merge(d.type, 1, Integer::sum);
            String g = d.type == DeviceType.GUARD_STATION || d.type.settings == DeviceType.SettingsKind.DOOR ? "" : "  관할 " + DeviceLogic.num(d.guard);
            devices.add(StringTag.valueOf(DeviceLogic.label(d) + g + "  (" + d.pos.getX() + ", " + d.pos.getY() + ", " + d.pos.getZ() + ")"));
        }
        tag.put("devices", devices);
        CompoundTag cnt = new CompoundTag();
        for (DeviceType t : DeviceType.values()) cnt.putInt(t.id, counts.getOrDefault(t, 0));
        tag.put("counts", cnt);
        int controls = 0;
        for (NetworkData.Control c : data.allControls()) if (data.complexAt(c.pos) == cid) controls++;
        tag.putInt("controls", controls);

        ListTag cards = new ListTag();
        for (NetworkData.Card c : data.cardsOf(cid)) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putInt("dong", c.dong);
            t.putInt("ho", c.ho);
            t.putString("holder", c.holder);
            t.putBoolean("revoked", c.revoked);
            cards.add(t);
        }
        tag.put("cards", cards);

        ListTag pws = new ListTag();
        for (Map.Entry<String, String> e : data.householdPasswordsOf(cid).entrySet()) {
            String[] k = e.getKey().split(":");
            CompoundTag t = new CompoundTag();
            t.putInt("dong", Integer.parseInt(k[0]));
            t.putInt("ho", Integer.parseInt(k[1]));
            pws.add(t);
        }
        tag.put("householdPws", pws);
        return tag;
    }

    public static void handle(ServerPlayer player, ServerLevel level, BlockPos pos, String action, CompoundTag args) {
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(pos);
        if (cid < 0 || data.getComplex(cid) == null) {
            DeviceLogic.msg(player, "컴퓨터가 단지 구획 밖에 있습니다", ChatFormatting.RED);
            return;
        }
        switch (action) {
            case "comp_common_pw" -> {
                String pw = args.getString("pw").trim();
                if (!pw.isEmpty() && !pw.matches("\\d{4,8}")) {
                    DeviceLogic.msg(player, "공용 비밀번호는 숫자 4~8자리입니다", ChatFormatting.RED);
                    return;
                }
                data.setCommonPassword(cid, pw);
                DeviceLogic.msg(player, pw.isEmpty() ? "공용 비밀번호를 삭제했습니다" : "공용 비밀번호를 저장했습니다", ChatFormatting.GREEN);
            }
            case "comp_notice" -> {
                String n = args.getString("text");
                if (n.length() > 200) n = n.substring(0, 200);
                data.setNotice(cid, n);
                DeviceLogic.msg(player, "공지사항을 월패드에 전송했습니다", ChatFormatting.GREEN);
            }
            case "comp_set_pw" -> {
                String pw = args.getString("pw").trim();
                if (!pw.isEmpty() && !pw.matches("\\d{4,8}")) {
                    DeviceLogic.msg(player, "세대 비밀번호는 숫자 4~8자리입니다", ChatFormatting.RED);
                    return;
                }
                data.setHouseholdPassword(cid, DeviceLogic.clamp(args.getInt("dong")), DeviceLogic.clamp(args.getInt("ho")), pw);
                DeviceLogic.msg(player, "세대 비밀번호를 변경했습니다", ChatFormatting.GREEN);
            }
            case "comp_card_register" -> registerCard(player, level, data, cid, args);
            case "comp_card_revoke" -> data.setCardRevoked(args.getInt("id"), args.getBoolean("revoked"));
            case "comp_card_delete" -> data.deleteCard(args.getInt("id"));
            default -> { }
        }
    }

    private static void registerCard(ServerPlayer player, ServerLevel level, NetworkData data, int cid, CompoundTag args) {
        // 카드등록용 카드(공카드)를 찾는다: 주 손 우선, 없으면 인벤토리
        ItemStack blank = ItemStack.EMPTY;
        if (player.getMainHandItem().is(ModRegistry.BLANK_CARD.get())) blank = player.getMainHandItem();
        else {
            for (ItemStack s : player.getInventory().items) {
                if (s.is(ModRegistry.BLANK_CARD.get())) {
                    blank = s;
                    break;
                }
            }
        }
        if (blank.isEmpty()) {
            DeviceLogic.msg(player, "카드등록용 카드가 필요합니다 (인벤토리에 넣고 등록하세요)", ChatFormatting.RED);
            return;
        }
        boolean all = args.getBoolean("all");
        int dong = all ? NetworkData.UNSET : DeviceLogic.clamp(args.getInt("dong"));
        int ho = all ? NetworkData.UNSET : DeviceLogic.clamp(args.getInt("ho"));
        String holder = args.getString("holder").trim();
        if (holder.isEmpty()) holder = player.getGameProfile().getName();
        if (holder.length() > 16) holder = holder.substring(0, 16);

        NetworkData.Card card = data.registerCard(cid, dong, ho, holder);
        if (!player.getAbilities().instabuild) blank.shrink(1);
        ItemStack access = AccessCardItem.create(card.id, data.getComplex(cid).name, dong, ho, holder);
        if (!player.getInventory().add(access)) player.drop(access, false);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 2.0f);
        DeviceLogic.msg(player, "출입 카드 #" + card.id + " 등록 완료", ChatFormatting.GREEN);
    }
}
