package site.leawsic.chess.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.BaseBoardBlockEntity;
import site.leawsic.chess.block.XiangqiBoardBlockEntity;

public final class ChessNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Chess.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id;
    private ChessNetwork() {}

    public static void init() {
        register(PlacePiecePacket.class, PlacePiecePacket::encode, PlacePiecePacket::decode, PlacePiecePacket::handle);
        register(ClearBoardPacket.class, ClearBoardPacket::encode, ClearBoardPacket::decode, ClearBoardPacket::handle);
        register(ToggleEditModePacket.class, ToggleEditModePacket::encode, ToggleEditModePacket::decode, ToggleEditModePacket::handle);
        register(PassTurnPacket.class, PassTurnPacket::encode, PassTurnPacket::decode, PassTurnPacket::handle);
        register(FinishGoGamePacket.class, FinishGoGamePacket::encode, FinishGoGamePacket::decode, FinishGoGamePacket::handle);
        register(JoinGamePacket.class, JoinGamePacket::encode, JoinGamePacket::decode, JoinGamePacket::handle);
        register(LeaveGamePacket.class, LeaveGamePacket::encode, LeaveGamePacket::decode, LeaveGamePacket::handle);
        register(SetPieceTypesPacket.class, SetPieceTypesPacket::encode, SetPieceTypesPacket::decode, SetPieceTypesPacket::handle);
        register(SetGameModePacket.class, SetGameModePacket::encode, SetGameModePacket::decode, SetGameModePacket::handle);
        register(ToggleAiPacket.class, ToggleAiPacket::encode, ToggleAiPacket::decode, ToggleAiPacket::handle);
        register(XiangqiMovePacket.class, XiangqiMovePacket::encode, XiangqiMovePacket::decode, XiangqiMovePacket::handle);
        register(XiangqiResetPacket.class, XiangqiResetPacket::encode, XiangqiResetPacket::decode, XiangqiResetPacket::handle);
        register(GuiNoticePacket.class, GuiNoticePacket::encode, GuiNoticePacket::decode, GuiNoticePacket::handle);
    }
    private static <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) { CHANNEL.messageBuilder(type, id++).encoder(encoder).decoder(decoder).consumerMainThread(handler).add(); }
    private static void server(Supplier<NetworkEvent.Context> supplier, java.util.function.Consumer<ServerPlayer> action) { NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> { ServerPlayer player = context.getSender(); if (player != null) action.accept(player); }); context.setPacketHandled(true); }
    private static void notice(ServerPlayer player, String key) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GuiNoticePacket(key)); }

    public record PlacePiecePacket(BlockPos pos, int x, int y, int pieceType) {
        static void encode(PlacePiecePacket packet, FriendlyByteBuf buffer) { buffer.writeBlockPos(packet.pos); buffer.writeVarInt(packet.x); buffer.writeVarInt(packet.y); buffer.writeVarInt(packet.pieceType); }
        static PlacePiecePacket decode(FriendlyByteBuf buffer) { return new PlacePiecePacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()); }
        static void handle(PlacePiecePacket packet, Supplier<NetworkEvent.Context> context) { server(context, player -> { if (player.level().getBlockEntity(packet.pos) instanceof BaseBoardBlockEntity board) board.placePiece(packet.x, packet.y, packet.pieceType, player.getUUID()); }); }
    }
    public record ClearBoardPacket(BlockPos pos) { static void encode(ClearBoardPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static ClearBoardPacket decode(FriendlyByteBuf b) { return new ClearBoardPacket(b.readBlockPos()); } static void handle(ClearBoardPacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.clearBoard(player.getUUID()); }); } }
    public record ToggleEditModePacket(BlockPos pos) { static void encode(ToggleEditModePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static ToggleEditModePacket decode(FriendlyByteBuf b) { return new ToggleEditModePacket(b.readBlockPos()); } static void handle(ToggleEditModePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.toggleEditMode(player.getUUID()); }); } }
    public record PassTurnPacket(BlockPos pos) { static void encode(PassTurnPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static PassTurnPacket decode(FriendlyByteBuf b) { return new PassTurnPacket(b.readBlockPos()); } static void handle(PassTurnPacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.passTurn(player.getUUID()); }); } }
    public record FinishGoGamePacket(BlockPos pos) { static void encode(FinishGoGamePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static FinishGoGamePacket decode(FriendlyByteBuf b) { return new FinishGoGamePacket(b.readBlockPos()); } static void handle(FinishGoGamePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.finishGoGame(player.getUUID()); }); } }
    public record JoinGamePacket(BlockPos pos) { static void encode(JoinGamePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static JoinGamePacket decode(FriendlyByteBuf b) { return new JoinGamePacket(b.readBlockPos()); } static void handle(JoinGamePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) { if (!board.joinGame(player.getUUID())) notice(player, "gui.chess.game_full"); } else if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board && !board.joinGame(player.getUUID())) notice(player, "gui.chess.xq.game_full"); }); } }
    public record LeaveGamePacket(BlockPos pos) { static void encode(LeaveGamePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static LeaveGamePacket decode(FriendlyByteBuf b) { return new LeaveGamePacket(b.readBlockPos()); } static void handle(LeaveGamePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.leaveGame(player.getUUID()); else if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board) board.leaveGame(player.getUUID()); }); } }
    public record SetPieceTypesPacket(BlockPos pos, int hostType, int guestType) { static void encode(SetPieceTypesPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.hostType); b.writeVarInt(p.guestType); } static SetPieceTypesPacket decode(FriendlyByteBuf b) { return new SetPieceTypesPacket(b.readBlockPos(), b.readVarInt(), b.readVarInt()); } static void handle(SetPieceTypesPacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.setPieceTypes(p.hostType, p.guestType, player.getUUID()); else if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board && !board.setPieceTypes(p.hostType, p.guestType, player.getUUID())) notice(player, "gui.chess.xq.invalid_colors"); }); } }
    public record SetGameModePacket(BlockPos pos, int mode) { static void encode(SetGameModePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.mode); } static SetGameModePacket decode(FriendlyByteBuf b) { return new SetGameModePacket(b.readBlockPos(), b.readVarInt()); } static void handle(SetGameModePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.setGameMode(p.mode, player.getUUID()); }); } }
    public record ToggleAiPacket(BlockPos pos) { static void encode(ToggleAiPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static ToggleAiPacket decode(FriendlyByteBuf b) { return new ToggleAiPacket(b.readBlockPos()); } static void handle(ToggleAiPacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof BaseBoardBlockEntity board) board.toggleAi(player.getUUID()); else if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board) board.toggleAi(player.getUUID()); }); } }
    public record XiangqiMovePacket(BlockPos pos, int fromX, int fromY, int toX, int toY) { static void encode(XiangqiMovePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.fromX); b.writeVarInt(p.fromY); b.writeVarInt(p.toX); b.writeVarInt(p.toY); } static XiangqiMovePacket decode(FriendlyByteBuf b) { return new XiangqiMovePacket(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()); } static void handle(XiangqiMovePacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board) { String error = board.tryMove(p.fromX, p.fromY, p.toX, p.toY, player.getUUID()); if (error != null) notice(player, error); } }); } }
    public record XiangqiResetPacket(BlockPos pos) { static void encode(XiangqiResetPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); } static XiangqiResetPacket decode(FriendlyByteBuf b) { return new XiangqiResetPacket(b.readBlockPos()); } static void handle(XiangqiResetPacket p, Supplier<NetworkEvent.Context> c) { server(c, player -> { if (player.level().getBlockEntity(p.pos) instanceof XiangqiBoardBlockEntity board) board.resetBoard(player.getUUID()); }); } }
    public record GuiNoticePacket(String translationKey) { static void encode(GuiNoticePacket p, FriendlyByteBuf b) { b.writeUtf(p.translationKey, 256); } static GuiNoticePacket decode(FriendlyByteBuf b) { return new GuiNoticePacket(b.readUtf(256)); } static void handle(GuiNoticePacket p, Supplier<NetworkEvent.Context> c) { NetworkEvent.Context context = c.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.translatable(p.translationKey), true); })); context.setPacketHandled(true); } }
}
