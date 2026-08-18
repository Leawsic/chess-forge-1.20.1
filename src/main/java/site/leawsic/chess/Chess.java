package site.leawsic.chess;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Chess.MODID)
public class Chess {
    public static final String MODID = "chess";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Temporary MDK anchor until the original board registrations are ported.
    public static final RegistryObject<Block> PLACEHOLDER = BLOCKS.register(
            "placeholder", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    public static final RegistryObject<Item> PLACEHOLDER_ITEM = ITEMS.register(
            "placeholder", () -> new BlockItem(PLACEHOLDER.get(), new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> CHESS_GROUP = CREATIVE_MODE_TABS.register(
            "chess_group", () -> CreativeModeTab.builder()
                    .icon(() -> PLACEHOLDER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(PLACEHOLDER_ITEM.get()))
                    .build());

    public Chess(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        LOGGER.info("Chess Forge port initialized");
    }
}
