package com.mchoe.smarthome.logic;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
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
            case "comp_card_delete_all" -> {
                int n = data.deleteCards(cid, NetworkData.UNSET);
                DeviceLogic.msg(player, "단지 카드 " + n + "장을 모두 삭제했습니다", ChatFormatting.YELLOW);
            }
            default -> { }
        }
    }

    static void registerCard(ServerPlayer player, ServerLevel level, NetworkData data, int cid, CompoundTag args) {
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

    // ------------------------------------------------------------------ 로비폰 관리자 모드

    /**
     * 로비폰 관리자 기능. 인증은 로비폰의 관리 비밀번호로 한다 (공용 비밀번호와는 별개).
     * 관리 비밀번호가 없으면 인증 없이 들어갈 수 있다.
     * @return 실패 사유 (성공이면 null)
     */
    static String lobbyAdmin(ServerPlayer player, ServerLevel level, DeviceBlockEntity dev, String action, CompoundTag args) {
        if (dev.getDeviceType() != DeviceType.LOBBY_PHONE) return null;
        NetworkData data = NetworkData.get(level);
        int cid = data.complexAt(dev.getBlockPos());
        NetworkData.Complex cx = data.getComplex(cid);
        if (cx == null) return "단지 구획 밖입니다";
        if (!dev.checkAdmin(args.getString("auth"))) {
            level.playSound(null, dev.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            DeviceLogic.extra().putBoolean("adminFail", true);
            return "관리자 인증 실패 (관리 비밀번호가 다릅니다)";
        }
        // 일반 로비는 그 동의 세대 카드만, 단지 외곽 로비(9999동)는 단지 전체 카드를 관리
        int scopeDong = dev.getDong() == DeviceLogic.OUTER_LOBBY_DONG ? NetworkData.UNSET : dev.getDong();
        switch (action) {
            case "lobby_admin" -> {
                DeviceLogic.extra().putBoolean("adminOk", true);
                DeviceLogic.msg(player, "관리자 모드", ChatFormatting.AQUA);
            }
            case "lobby_common_pw" -> {
                String pw = args.getString("pw").trim();
                if (!pw.isEmpty() && !pw.matches("\\d{4,8}")) return "공용 비밀번호는 숫자 4~8자리입니다";
                data.setCommonPassword(cid, pw);
                DeviceLogic.msg(player, pw.isEmpty() ? "공용 비밀번호를 삭제했습니다" : "공용 비밀번호를 변경했습니다", ChatFormatting.GREEN);
            }
            case "lobby_card_register" -> {
                boolean all = args.getBoolean("all");
                if (!all && scopeDong >= 0 && args.getInt("dong") != scopeDong) {
                    return "이 로비폰에서는 " + scopeDong + "동 카드만 등록할 수 있습니다";
                }
                if (all && scopeDong >= 0) return "단지 전체 카드는 단지 외곽 로비폰이나 관리 컴퓨터에서 등록하세요";
                registerCard(player, level, data, cid, args);
            }
            case "lobby_card_delete" -> {
                int id = args.getInt("id");
                ItemStack held = player.getMainHandItem();
                boolean fromHand = id <= 0;
                if (fromHand) {
                    if (!(held.getItem() instanceof AccessCardItem)) return "삭제할 출입용 카드를 손에 들어 주세요";
                    id = AccessCardItem.cardId(held);
                }
                NetworkData.Card card = data.getCard(id);
                if (card == null || card.complex != cid) return "이 단지에 등록된 카드가 아닙니다";
                if (scopeDong >= 0 && card.dong != scopeDong) return "이 로비폰에서 관리하는 카드가 아닙니다";
                data.deleteCard(id);
                if (fromHand) {
                    // 등록이 지워진 카드는 다시 카드등록용 카드가 된다
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.BLANK_CARD.get()));
                }
                DeviceLogic.msg(player, "카드 #" + id + " 삭제", ChatFormatting.YELLOW);
            }
            case "lobby_common_toggle" -> {
                dev.setCommonEnabled(!dev.isCommonEnabled());
                DeviceLogic.msg(player, "공용 비밀번호 " + (dev.isCommonEnabled() ? "사용" : "미사용"), ChatFormatting.AQUA);
            }
            case "lobby_household_toggle" -> {
                dev.setHouseholdEnabled(!dev.isHouseholdEnabled());
                DeviceLogic.msg(player, "세대 비밀번호 출입 " + (dev.isHouseholdEnabled() ? "사용" : "미사용"), ChatFormatting.AQUA);
            }
            case "lobby_card_delete_all" -> {
                int n = data.deleteCards(cid, scopeDong);
                DeviceLogic.msg(player, (scopeDong >= 0 ? scopeDong + "동 " : "단지 ") + "카드 " + n + "장을 모두 삭제했습니다", ChatFormatting.YELLOW);
            }
            default -> { }
        }
        return null;
    }
}
