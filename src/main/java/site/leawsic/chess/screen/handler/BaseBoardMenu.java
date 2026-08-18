package site.leawsic.chess.screen.handler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.BaseBoardBlockEntity;

public class BaseBoardMenu extends AbstractContainerMenu {
    private final BaseBoardBlockEntity board;
    public BaseBoardMenu(int id, Inventory inventory, BaseBoardBlockEntity board) { super(Chess.BASE_BOARD_MENU.get(), id); this.board = board; }
    public static BaseBoardMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf data) { BaseBoardBlockEntity board = data != null && inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof BaseBoardBlockEntity value ? value : null; return new BaseBoardMenu(id, inventory, board); }
    public BaseBoardBlockEntity board() { return board; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return board != null && !board.isRemoved() && board.canUse(player); }
    @Override public void removed(Player player) { if (board != null && board.isEditMode() && !player.level().isClientSide) board.setEditMode(false); super.removed(player); }
}
