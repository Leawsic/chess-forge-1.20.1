package site.leawsic.chess.screen.handler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.XiangqiBoardBlockEntity;

public class XiangqiMenu extends AbstractContainerMenu {
    private final XiangqiBoardBlockEntity board;
    public XiangqiMenu(int id, Inventory inventory, XiangqiBoardBlockEntity board) { super(Chess.XIANGQI_MENU.get(), id); this.board = board; }
    public static XiangqiMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf data) { XiangqiBoardBlockEntity board = data != null && inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof XiangqiBoardBlockEntity value ? value : null; return new XiangqiMenu(id, inventory, board); }
    public XiangqiBoardBlockEntity board() { return board; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return board != null && !board.isRemoved() && board.canUse(player); }
    @Override public void removed(Player player) { super.removed(player); }
}
