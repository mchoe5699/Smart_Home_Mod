package com.mchoe.smarthome.item;

import com.mchoe.smarthome.block.ControlBlock;
import com.mchoe.smarthome.block.DeviceBlock;
import com.mchoe.smarthome.blockentity.ControlBlockEntity;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mchoe.smarthome.logic.DoorControl;
import com.mchoe.smarthome.logic.HomeLogic;
import com.mchoe.smarthome.logic.NetworkData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 링커.
 * 홈 링커: 월패드·비디오폰·인터폰을 우클릭해 선택 → 도어카메라·도어폰·조명·가스밸브 등 스마트홈 설비를 우클릭하면 그 세대와 연동.
 * 도어 링커: 비디오폰·월패드·인터폰·로비폰을 우클릭해 선택 → 문(문·다락문·울타리 문)을 우클릭하면 연동.
 * 웅크리고 대상을 우클릭하면 연동 해제, 웅크리고 허공에 우클릭하면 선택 해제.
 */
public class LinkerItem extends Item {
    public enum Mode { HOME, DOOR }

    private static final double MAX_DIST = 64;
    public final Mode mode;

    public LinkerItem(Mode mode, Properties props) {
        super(props);
        this.mode = mode;
    }

    private static void say(Player p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    private boolean selectable(DeviceType t) {
        return mode == Mode.HOME ? t.internal : (t.internal || t == DeviceType.LOBBY_PHONE);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        BlockState st = level.getBlockState(pos);
        boolean handled = st.getBlock() instanceof DeviceBlock || st.getBlock() instanceof ControlBlock
                || (mode == Mode.DOOR && DoorControl.isDoor(st));
        if (!handled) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;

        // 1) 기준 기기 선택
        if (st.getBlock() instanceof DeviceBlock db && selectable(db.type)) {
            NetworkData.Device d = NetworkData.get(sl).getDevice(pos);
            if (d == null) {
                say(player, "기기를 불러오는 중입니다. 잠시 후 다시 시도하세요", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
            if (mode == Mode.HOME && (d.dong < 0 || d.ho < 0)) {
                say(player, "먼저 이 기기의 관할경비실·세대번호를 설정하세요", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
            CompoundTag t = stack.getOrCreateTag();
            t.putLong("sel", pos.asLong());
            t.putString("dim", level.dimension().location().toString());
            t.putString("label", DeviceLogic.label(d));
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
            say(player, "선택: " + DeviceLogic.label(d) + (mode == Mode.HOME ? " → 연동할 설비를 우클릭" : " → 연동할 문을 우클릭"), ChatFormatting.AQUA);
            return InteractionResult.SUCCESS;
        }

        // 2) 대상 연동
        NetworkData data = NetworkData.get(sl);
        NetworkData.Device sel = selected(stack, sl, data);
        if (sel == null) {
            say(player, mode == Mode.HOME ? "먼저 월패드·비디오폰·인터폰을 우클릭해 선택하세요"
                    : "먼저 월패드·비디오폰·인터폰·로비폰을 우클릭해 선택하세요", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (Vec3.atCenterOf(sel.pos).distanceTo(Vec3.atCenterOf(pos)) > MAX_DIST) {
            say(player, "선택한 기기와 너무 멉니다 (최대 " + (int) MAX_DIST + "블록)", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        boolean unlink = player.isShiftKeyDown();
        return mode == Mode.HOME ? linkHome(sl, player, pos, st, sel, data, unlink) : linkDoor(sl, player, pos, sel, data);
    }

    private InteractionResult linkHome(ServerLevel level, Player player, BlockPos pos, BlockState st, NetworkData.Device sel,
                                       NetworkData data, boolean unlink) {
        if (data.complexAt(pos) < 0 || data.complexAt(pos) != data.complexAt(sel.pos)) {
            say(player, "선택한 세대기와 같은 단지 구획 안에 있어야 합니다", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        String house = sel.dong + "동 " + sel.ho + "호";
        if (st.getBlock() instanceof DeviceBlock db && db.type.settings == DeviceType.SettingsKind.DOOR
                && level.getBlockEntity(pos) instanceof DeviceBlockEntity be) {
            if (unlink) {
                be.setLink(NetworkData.UNSET, NetworkData.UNSET, false);
                say(player, db.type.korean + " 연동 해제", ChatFormatting.YELLOW);
            } else {
                be.setLink(sel.dong, sel.ho, true);
                say(player, db.type.korean + " ↔ " + house + " 연동 완료", ChatFormatting.GREEN);
            }
        } else if (st.getBlock() instanceof ControlBlock cb && level.getBlockEntity(pos) instanceof ControlBlockEntity be) {
            NetworkData.Control c = be.control();
            if (unlink) {
                c.linked = false;
                say(player, cb.kind.korean + " 연동 해제", ChatFormatting.YELLOW);
            } else {
                c.dong = sel.dong;
                c.ho = sel.ho;
                c.linked = true;
                say(player, cb.kind.korean + " ↔ " + house + " 연동 완료", ChatFormatting.GREEN);
            }
            HomeLogic.apply(level, data, c);
        } else {
            say(player, "홈 링커로 연동할 수 없는 기기입니다 (도어카메라·도어폰·스마트홈 설비)", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, unlink ? 0.8f : 1.8f);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult linkDoor(ServerLevel level, Player player, BlockPos pos, NetworkData.Device sel, NetworkData data) {
        if (!DoorControl.isDoor(level.getBlockState(pos))) {
            say(player, "도어 링커는 문·다락문·울타리 문에 사용합니다", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        BlockPos door = DoorControl.normalize(level, pos);
        boolean added = data.toggleDoor(sel.pos, door);
        int n = data.getDevice(sel.pos).doors.size();
        say(player, (added ? "문 연동 완료: " : "문 연동 해제: ") + DeviceLogic.label(sel) + " (연동된 문 " + n + "개)",
                added ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, added ? 1.8f : 0.8f);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static NetworkData.Device selected(ItemStack stack, ServerLevel level, NetworkData data) {
        CompoundTag t = stack.getTag();
        if (t == null || !t.contains("sel")) return null;
        if (!t.getString("dim").equals(level.dimension().location().toString())) return null;
        return data.getDevice(BlockPos.of(t.getLong("sel")));
    }

    /** 웅크리고 허공에 우클릭: 선택 해제 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                stack.removeTagKey("sel");
                stack.removeTagKey("label");
                stack.removeTagKey("dim");
                say(player, "선택을 해제했습니다", ChatFormatting.GRAY);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("sel");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("label")) {
            tooltip.add(Component.literal("선택: " + t.getString("label")).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal("선택된 기기 없음").withStyle(ChatFormatting.GRAY));
        }
        if (mode == Mode.HOME) {
            tooltip.add(Component.literal("1. 월패드·비디오폰·인터폰 우클릭 (선택)").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("2. 도어카메라·도어폰·조명·가스밸브 등 우클릭").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("1. 월패드·비디오폰·인터폰·로비폰 우클릭 (선택)").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("2. 문 우클릭 (다시 누르면 해제)").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("웅크리고 대상 우클릭: 연동 해제 · 허공: 선택 해제").withStyle(ChatFormatting.DARK_GRAY));
    }
}
