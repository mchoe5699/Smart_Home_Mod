package com.mchoe.smarthome;

import com.mchoe.smarthome.block.ComputerBlock;
import com.mchoe.smarthome.block.ControlBlock;
import com.mchoe.smarthome.block.DashboardBlock;
import com.mchoe.smarthome.block.DeviceBlock;
import com.mchoe.smarthome.blockentity.ControlBlockEntity;
import com.mchoe.smarthome.blockentity.DeviceBlockEntity;
import com.mchoe.smarthome.item.AccessCardItem;
import com.mchoe.smarthome.item.BlankCardItem;
import com.mchoe.smarthome.logic.ControlKind;
import com.mchoe.smarthome.logic.DeviceType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SmartHomeMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SmartHomeMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SmartHomeMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SmartHomeMod.MODID);

    public static final Map<DeviceType, RegistryObject<DeviceBlock>> DEVICES = new EnumMap<>(DeviceType.class);
    public static final Map<DeviceType, RegistryObject<Item>> DEVICE_ITEMS = new EnumMap<>(DeviceType.class);
    public static final Map<ControlKind, RegistryObject<ControlBlock>> CONTROLS = new EnumMap<>(ControlKind.class);
    public static final Map<ControlKind, RegistryObject<Item>> CONTROL_ITEMS = new EnumMap<>(ControlKind.class);

    static {
        for (DeviceType type : DeviceType.values()) {
            RegistryObject<DeviceBlock> block = BLOCKS.register(type.id, () -> new DeviceBlock(type, props()));
            DEVICES.put(type, block);
            DEVICE_ITEMS.put(type, ITEMS.register(type.id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        for (ControlKind kind : ControlKind.values()) {
            RegistryObject<ControlBlock> block = BLOCKS.register(kind.id, () -> new ControlBlock(kind,
                    kind == ControlKind.LIGHT ? props().lightLevel(s -> s.getValue(ControlBlock.ON) ? 15 : 0) : props()));
            CONTROLS.put(kind, block);
            CONTROL_ITEMS.put(kind, ITEMS.register(kind.id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<DashboardBlock> DASHBOARD = BLOCKS.register("complex_dashboard", () -> new DashboardBlock(props()));
    public static final RegistryObject<Item> DASHBOARD_ITEM = ITEMS.register("complex_dashboard",
            () -> new BlockItem(DASHBOARD.get(), new Item.Properties()));
    public static final RegistryObject<ComputerBlock> COMPUTER = BLOCKS.register("admin_computer", () -> new ComputerBlock(props()));
    public static final RegistryObject<Item> COMPUTER_ITEM = ITEMS.register("admin_computer",
            () -> new BlockItem(COMPUTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLANK_CARD = ITEMS.register("blank_card", () -> new BlankCardItem(new Item.Properties()));
    public static final RegistryObject<Item> ACCESS_CARD = ITEMS.register("access_card", () -> new AccessCardItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<DeviceBlockEntity>> DEVICE_BE = BLOCK_ENTITIES.register("device",
            () -> BlockEntityType.Builder.of(DeviceBlockEntity::new,
                    DEVICES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<ControlBlockEntity>> CONTROL_BE = BLOCK_ENTITIES.register("control",
            () -> BlockEntityType.Builder.of(ControlBlockEntity::new,
                    CONTROLS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.smart_home_mod"))
            .icon(() -> new ItemStack(DEVICE_ITEMS.get(DeviceType.WALLPAD).get()))
            .displayItems((params, output) -> {
                output.accept(DASHBOARD_ITEM.get());
                output.accept(COMPUTER_ITEM.get());
                for (DeviceType t : DeviceType.values()) output.accept(DEVICE_ITEMS.get(t).get());
                for (ControlKind k : ControlKind.values()) output.accept(CONTROL_ITEMS.get(k).get());
                output.accept(BLANK_CARD.get());
            })
            .build());

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
