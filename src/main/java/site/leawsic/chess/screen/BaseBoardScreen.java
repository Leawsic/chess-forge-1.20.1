package site.leawsic.chess.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.BaseBoardBlockEntity;
import site.leawsic.chess.config.ChessGameConfig;
import site.leawsic.chess.config.GomokuConfig;
import site.leawsic.chess.network.ChessNetwork;
import site.leawsic.chess.screen.handler.BaseBoardMenu;

public class BaseBoardScreen extends AbstractContainerScreen<BaseBoardMenu> {
    private final ChessGameConfig initial;
    private int boardLeft, boardTop, boardPixels, boardWidth, boardHeight;
    private float scale; private long escapeAt, noticeUntil; private Component notice;
    private boolean leftGame; private int selectedPiece = 1;
    private Button clear, edit, ai, pass, finish, join, leave, hostBlack, hostWhite, gomoku, go, blackPiece, whitePiece;
    public BaseBoardScreen(BaseBoardMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); initial = menu.board() == null ? GomokuConfig.CONFIG : menu.board().getConfig(); imageWidth=320; imageHeight=280; }
    public void showNotice(Component value) { notice=value; noticeUntil=System.currentTimeMillis()+5000; }
    private BaseBoardBlockEntity board() { return menu.board(); }
    private void send(Object packet) { ChessNetwork.CHANNEL.sendToServer(packet); }
    private void simple(java.util.function.Function<net.minecraft.core.BlockPos, ?> packet) { if(board()!=null) send(packet.apply(board().getBlockPos())); }
    @Override protected void init() {
        super.init(); imageWidth=Math.min(width-32,Math.max(280,initial.getBoardTextureWidth()+32)); imageHeight=Math.min(height-32,Math.max(220,initial.getBoardTextureHeight()+80)); leftPos=(width-imageWidth)/2; topPos=(height-imageHeight)/2;
        scale=Math.min(1f,Math.min((imageWidth-16)/(float)initial.getBoardTextureWidth(),(imageHeight-72)/(float)initial.getBoardTextureHeight())); boardPixels=Math.round(initial.getBoardTextureWidth()*scale); boardLeft=leftPos+(imageWidth-boardPixels)/2; boardTop=topPos+8; boardWidth=Math.round((initial.getCols()-1)*initial.getBoardCellPixelSize()*scale); boardHeight=Math.round((initial.getRows()-1)*initial.getBoardCellPixelSize()*scale);
        int y1=topPos+imageHeight-48,y2=topPos+imageHeight-24;
        clear=button("gui.chess.clear",leftPos+10,y1,60,b->simple(ChessNetwork.ClearBoardPacket::new)); edit=button("gui.chess.edit_mode",leftPos+75,y1,80,b->simple(ChessNetwork.ToggleEditModePacket::new)); ai=button("gui.chess.ai",leftPos+10,y2,70,b->simple(ChessNetwork.ToggleAiPacket::new)); pass=button("gui.chess.pass",leftPos+85,y2,55,b->simple(ChessNetwork.PassTurnPacket::new)); finish=button("gui.chess.go.finish",leftPos+145,y2,70,b->simple(ChessNetwork.FinishGoGamePacket::new));
        join=button("gui.chess.join",leftPos+imageWidth-120,y1,45,b->{leftGame=false;simple(ChessNetwork.JoinGamePacket::new);}); leave=button("gui.chess.leave",leftPos+imageWidth-70,y1,60,b->{leftGame=true;simple(ChessNetwork.LeaveGamePacket::new);}); hostBlack=button("gui.chess.host_black",leftPos+imageWidth-205,y2,95,b->setColors(1,2)); hostWhite=button("gui.chess.host_white",leftPos+imageWidth-105,y2,95,b->setColors(2,1)); gomoku=button("gui.chess.mode.gomoku",leftPos+imageWidth-115,topPos+4,60,b->mode(0)); go=button("gui.chess.mode.go",leftPos+imageWidth-50,topPos+4,40,b->mode(1)); blackPiece=button("gui.chess.piece.black",leftPos+10,y2,60,b->selectedPiece=1); whitePiece=button("gui.chess.piece.white",leftPos+75,y2,60,b->selectedPiece=2);
    }
    private Button button(String key,int x,int y,int w,java.util.function.Consumer<Button> action){Button b=Button.builder(Component.translatable(key),action::accept).bounds(x,y,w,20).build();addRenderableWidget(b);return b;}
    private void setColors(int h,int g){if(board()!=null)send(new ChessNetwork.SetPieceTypesPacket(board().getBlockPos(),h,g));}
    private void mode(int mode){if(board()!=null)send(new ChessNetwork.SetGameModePacket(board().getBlockPos(),mode));}
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){BaseBoardBlockEntity b=board();ChessGameConfig c=b==null?initial:b.getConfig();ResourceLocation tex=new ResourceLocation(Chess.MODID,"textures/"+c.getBoardTopTexture().getPath()+".png");g.blit(tex,boardLeft,boardTop,boardPixels,boardPixels,0,0,c.getBoardTextureWidth(),c.getBoardTextureHeight(),c.getBoardTextureWidth(),c.getBoardTextureHeight());if(b==null)return;for(int y=0;y<c.getRows();y++)for(int x=0;x<c.getCols();x++){int p=b.getBoard()[y][x];if(p==0)continue;ResourceLocation pt=new ResourceLocation(Chess.MODID,"textures/"+c.getPieceTexture(p).getPath()+".png");int s=Math.round(c.getPieceDrawSize()*scale),cx=Math.round(boardLeft+scale*(c.getPieceCenterU(x)-c.getPieceDrawSize()/2)),cy=Math.round(boardTop+scale*(c.getPieceCenterV(y)-c.getPieceDrawSize()/2));g.blit(pt,cx,cy,s,s,0,0,c.getPieceTextureSize(),c.getPieceTextureSize(),c.getPieceTextureSize(),c.getPieceTextureSize());}if(b.getLastMoveX()>=0){int cx=Math.round(boardLeft+scale*c.getPieceCenterU(b.getLastMoveX())),cy=Math.round(boardTop+scale*c.getPieceCenterV(b.getLastMoveY()));g.fill(cx-2,cy-2,cx+2,cy+2,0xFFE53935);}}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){BaseBoardBlockEntity b=board();if(b==null)return;updateButtons(b);Component status=b.isGameOver()?Component.translatable(b.getWinner()>0?"gui.chess.winner_suffix":"gui.chess.draw"):b.isAiThinking()?Component.translatable("gui.chess.ai_thinking"):b.isEditMode()?Component.translatable("gui.chess.edit_mode"):Component.translatable("gui.chess.turn_format",Component.translatable("gui.chess.piece."+(b.getCurrentPlayer()==1?"black":"white")));g.drawString(font,status,10,10,0xFFFFFFFF,false);if(b.isGameOver())ChessScreenUi.gameOver(g,font,imageWidth,imageHeight,status);if(b.getGameMode()==1){GomokuConfig.Score s=GomokuConfig.calculateScore(b.getBoard(),b.getConfig().getRows(),b.getConfig().getCols());g.drawString(font,Component.translatable("gui.chess.go.black_score",s.blackScore()),10,28,0xFFAAAAAA,false);g.drawString(font,Component.translatable("gui.chess.go.white_score",s.whiteScore()),10,40,0xFFFFFFFF,false);}}
    private void updateButtons(BaseBoardBlockEntity b){boolean host=minecraft.player!=null&&b.isHost(minecraft.player.getUUID()), player=minecraft.player!=null&&b.isInGame(minecraft.player.getUUID()), active=player&&!b.isGameOver()&&!b.isAiThinking();clear.active=host&&!b.isMultiplayer();edit.active=host&&!b.isMultiplayer()&&!b.isGameOver();ai.active=host&&!b.isMultiplayer()&&!b.isGameOver();pass.active=active&&b.getGameMode()==1&&!b.isEditMode();finish.active=host&&b.getGameMode()==1&&active&&!b.isEditMode();join.active=!player&&b.getGuestPlayer()==null;leave.active=player;hostBlack.active=host&&b.isMultiplayer();hostWhite.active=host&&b.isMultiplayer();gomoku.active=b.getGameMode()!=0;go.active=b.getGameMode()!=1;blackPiece.active=b.isEditMode()&&host;whitePiece.active=b.isEditMode()&&host;}
    @Override public boolean mouseClicked(double mx,double my,int button){BaseBoardBlockEntity b=board();ChessGameConfig c=b==null?initial:b.getConfig();int col=Math.round((float)((mx-boardLeft)/scale-c.getBoardLeftU())/c.getBoardCellPixelSize()),row=Math.round((float)((my-boardTop)/scale-c.getBoardTopV())/c.getBoardCellPixelSize());if(b!=null&&minecraft.player!=null&&col>=0&&col<c.getCols()&&row>=0&&row<c.getRows()&&b.isInGame(minecraft.player.getUUID())){int p=b.isEditMode()?selectedPiece:b.isMultiplayer()?b.getPlayerPieceType(minecraft.player.getUUID()):b.getCurrentPlayer();if(p!=0&&!b.isGameOver()&&(!b.isAiThinking()&&(b.isEditMode()||!b.isMultiplayer()||p==b.getCurrentPlayer()))){send(new ChessNetwork.PlacePiecePacket(b.getBlockPos(),col,row,p));return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key!=GLFW.GLFW_KEY_ESCAPE)return super.keyPressed(key,scan,mods);long now=System.currentTimeMillis();if(now-escapeAt>1500){escapeAt=now;showNotice(Component.translatable("gui.chess.exit_confirm"));return true;}if(board()!=null&&minecraft.player!=null&&board().isInGame(minecraft.player.getUUID()))simple(ChessNetwork.LeaveGamePacket::new);onClose();return true;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){renderBackground(g);super.render(g,mx,my,delta);if(notice!=null&&noticeUntil>System.currentTimeMillis())g.drawCenteredString(font,notice,width/2,height/2,0xFFFFFFFF);}
}
