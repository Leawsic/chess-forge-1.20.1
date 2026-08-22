package site.leawsic.chess;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import site.leawsic.chess.block.*;
import site.leawsic.chess.network.ChessNetwork;
import site.leawsic.chess.screen.handler.*;
import net.minecraft.network.chat.Component;

@Mod(Chess.MODID)
public class Chess {
    public static final String MODID = "chess";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static net.minecraft.resources.ResourceLocation id(String path) { return new net.minecraft.resources.ResourceLocation(MODID, path); }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    /** 音效资源由 {@code tools/generate_sounds.py} 合成，全部为程序生成，无外部素材。 */
    public static final RegistryObject<SoundEvent> PIECE_PLACE = sound("piece_place");
    public static final RegistryObject<SoundEvent> PIECE_CAPTURE = sound("piece_capture");
    public static final RegistryObject<SoundEvent> GAME_WIN = sound("game_win");
    public static final RegistryObject<SoundEvent> GAME_LOSE = sound("game_lose");
    public static final RegistryObject<SoundEvent> CHECK_ALERT = sound("check_alert");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    public static final RegistryObject<Block> PLACEHOLDER = BLOCKS.register("placeholder", BoardPlaceholderBlock::new);
    public static final RegistryObject<Block> GOMOKU_BOARD = BLOCKS.register("gomoku_board", BaseBoardBlock::gomoku);
    public static final RegistryObject<Block> XIANGQI_BOARD = BLOCKS.register("xiangqi_board", XiangqiBoardBlock::new);
    public static final RegistryObject<Item> GOMOKU_BOARD_ITEM = ITEMS.register("gomoku_board", () -> new BlockItem(GOMOKU_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> XIANGQI_BOARD_ITEM = ITEMS.register("xiangqi_board", () -> new BlockItem(XIANGQI_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<BaseBoardBlockEntity>> BASE_BOARD_ENTITY = BLOCK_ENTITIES.register("base_board", () -> BlockEntityType.Builder.of(BaseBoardBlockEntity::new, GOMOKU_BOARD.get()).build(null));
    public static final RegistryObject<BlockEntityType<XiangqiBoardBlockEntity>> XIANGQI_ENTITY = BLOCK_ENTITIES.register("xiangqi_board", () -> BlockEntityType.Builder.of(XiangqiBoardBlockEntity::new, XIANGQI_BOARD.get()).build(null));
    public static final RegistryObject<MenuType<BaseBoardMenu>> BASE_BOARD_MENU = MENUS.register("base_board", () -> IForgeMenuType.create(BaseBoardMenu::fromNetwork));
    public static final RegistryObject<MenuType<XiangqiMenu>> XIANGQI_MENU = MENUS.register("xiangqi", () -> IForgeMenuType.create(XiangqiMenu::fromNetwork));
    public static final RegistryObject<CreativeModeTab> CHESS_GROUP = CREATIVE_MODE_TABS.register("chess_group", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chess.chess_group"))
            .icon(() -> GOMOKU_BOARD_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GOMOKU_BOARD_ITEM.get());
                output.accept(XIANGQI_BOARD_ITEM.get());
            }).build());

    public Chess(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); CREATIVE_MODE_TABS.register(bus); SOUNDS.register(bus);
        ChessNetwork.init();
        LOGGER.info("Chess Forge port initialized");
    }
}
