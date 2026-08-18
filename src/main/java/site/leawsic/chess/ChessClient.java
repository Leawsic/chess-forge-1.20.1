package site.leawsic.chess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import site.leawsic.chess.block.BaseBoardBlock;
import site.leawsic.chess.render.BaseBoardBlockEntityRenderer;
import site.leawsic.chess.render.XiangqiBoardBlockEntityRenderer;
import site.leawsic.chess.screen.BaseBoardScreen;
import site.leawsic.chess.screen.XiangqiScreen;

@Mod.EventBusSubscriber(modid = Chess.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChessClient {
    private ChessClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Chess.BASE_BOARD_MENU.get(), BaseBoardScreen::new);
            MenuScreens.register(Chess.XIANGQI_MENU.get(), XiangqiScreen::new);
            BlockEntityRenderers.register(Chess.BASE_BOARD_ENTITY.get(), BaseBoardBlockEntityRenderer::new);
            BlockEntityRenderers.register(Chess.XIANGQI_ENTITY.get(), XiangqiBoardBlockEntityRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(Chess.GOMOKU_BOARD.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(Chess.XIANGQI_BOARD.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(Chess.PLACEHOLDER.get(), RenderType.translucent());
            site.leawsic.chess.network.ChessNetwork.setClientNoticeHandler(ChessClient::showNotice);
        });
    }

    public static void showNotice(String key) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Component notice = Component.translatable(key);
            if (client.screen instanceof BaseBoardScreen screen) screen.showNotice(notice);
            else if (client.screen instanceof XiangqiScreen screen) screen.showNotice(notice);
            else if (client.player != null) client.player.displayClientMessage(notice, true);
        });
    }
}
