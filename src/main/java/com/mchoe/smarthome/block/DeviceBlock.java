package com.mchoe.smarthome.block;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import com.mchoe.smarthome.logic.CallManager;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.DeviceType;
import com.mchoe.smarthome.logic.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.mchoe.smarthome.item.AccessCardItem;
import com.mchoe.smarthome.item.BlankCardItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DeviceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 문열림 신호 (레드스톤 출력) */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public final DeviceType type;
    private final Map<Direction, VoxelShape> shapes;

    public DeviceBlock(DeviceType type, Properties props) {
        super(props);
        this.type = type;
        this.shapes = ShapeUtil.horizontal(baseShape(type));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    private static VoxelShape baseShape(DeviceType type) {
        return switch (type) {
            case WALLPAD -> box(0.5, 3, 15.25, 15.5, 12.5, 16);
            case VIDEOPHONE -> box(2, 4, 14.75, 14, 11.5, 16);
            case INTERPHONE -> box(5, 2.5, 13.6, 11, 13.5, 16);
            case DOOR_CAMERA -> box(6, 3.5, 14, 10, 12.5, 16);
            case DOOR_PHONE -> box(5.5, 5, 14.75, 10.5, 11, 16);
            case LOBBY_PHONE -> box(2.5, 0.5, 13.5, 13.5, 16, 16);
            case GUARD_STATION -> Shapes.or(box(1, 0, 1, 15, 1.5, 15), box(2, 1.5, 9, 14, 11.5, 11), box(1.5, 1.5, 1.5, 11, 3, 7.5), box(12, 1.5, 2, 14.5, 3.5, 8));
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing;
        Direction clicked = ctx.getClickedFace();
        if (type.wallMounted && clicked.getAxis().isHorizontal()) {
            facing = clicked;
        } else {
            facing = ctx.getHorizontalDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        // 출입 카드: 로비폰에 카드를 대면(들고 우클릭) 인증
        if (type == DeviceType.LOBBY_PHONE && held.getItem() instanceof AccessCardItem) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DeviceBlockEntity be) {
                DeviceLogic.tagCard(sp, (ServerLevel) level, be, AccessCardItem.cardId(held));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (type == DeviceType.LOBBY_PHONE && held.getItem() instanceof BlankCardItem) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("등록되지 않은 카드입니다. 관리 컴퓨터에서 카드를 등록하세요")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            DeviceLogic.openScreen(sp, pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel sl) {
                NetworkData.get(sl).removeDevice(pos);
                CallManager.deviceRemoved(sl, pos);
            }
            if (state.getValue(POWERED)) updateNeighbours(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ------------------------------------------------------------------ 레드스톤 (문열림)

    public void setPowered(Level level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(POWERED) == powered) return;
        BlockState ns = state.setValue(POWERED, powered);
        level.setBlock(pos, ns, 3);
        updateNeighbours(level, pos, ns);
    }

    private Direction attachDirection(BlockState state) {
        return type.wallMounted ? state.getValue(FACING) : Direction.UP;
    }

    private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(attachDirection(state).getOpposite()), this);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return 0;
    }

    // ------------------------------------------------------------------ 블록엔티티

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide ? null : createTickerHelper(beType, ModRegistry.DEVICE_BE.get(), DeviceBlockEntity::serverTick);
    }
}
