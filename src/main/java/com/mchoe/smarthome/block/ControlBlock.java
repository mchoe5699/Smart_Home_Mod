package com.mchoe.smarthome.block;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.blockentity.ControlBlockEntity;
import com.mchoe.smarthome.logic.ControlKind;
import com.mchoe.smarthome.logic.DeviceLogic;
import com.mchoe.smarthome.logic.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** 월패드로 제어하는 세대 설비 (조명·온도조절기·가스밸브·환기장치·엘리베이터 호출 연동기) */
public class ControlBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public final ControlKind kind;
    private final Map<Direction, VoxelShape> shapes;

    public ControlBlock(ControlKind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.shapes = ShapeUtil.horizontal(switch (kind) {
            case LIGHT -> box(3, 15, 3, 13, 16, 13);
            case VENTILATION -> box(2, 15, 2, 14, 16, 14);
            case THERMOSTAT -> box(5.5, 5.5, 15.25, 10.5, 10.5, 16);
            case GAS_VALVE -> Shapes.or(box(7, 0, 13, 9, 16, 15), box(5, 5, 11.5, 11, 11, 15.5));
            case ELEVATOR_LINK -> box(5, 5, 14.5, 11, 11, 16);
            case MOTION_OUTDOOR -> box(5, 6, 13, 11, 10.5, 16);
            case MOTION_INDOOR -> box(6, 9, 13.8, 10, 12.5, 16);
        });
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction facing = !kind.ceiling && clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite();
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
        if (!level.isClientSide && player instanceof ServerPlayer sp) DeviceLogic.openScreen(sp, pos, false);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) NetworkData.get(sl).removeControl(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return kind == ControlKind.ELEVATOR_LINK;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) && dir == state.getValue(FACING) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide ? null : createTickerHelper(beType, ModRegistry.CONTROL_BE.get(), ControlBlockEntity::serverTick);
    }
}
