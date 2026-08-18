package site.leawsic.chess.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.BaseBoardBlock;
import site.leawsic.chess.block.BaseBoardBlockEntity;
import site.leawsic.chess.config.ChessGameConfig;

public class BaseBoardBlockEntityRenderer implements BlockEntityRenderer<BaseBoardBlockEntity> {
    public BaseBoardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(BaseBoardBlockEntity entity, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int[][] board = entity.getBoard();
        ChessGameConfig config = entity.getConfig();
        if (board == null) return;
        pose.pushPose(); applyRotation(pose, entity.getBlockState().getValue(BaseBoardBlock.FACING));
        float y = 0.05f, start = -1f, size = 3f, texW = config.getBoardTextureWidth();
        float pieceSize = config.getPieceDrawSize() / texW * size;
        for (int row = 0; row < config.getRows(); row++) for (int col = 0; col < config.getCols(); col++) {
            int piece = board[row][col]; if (piece == config.getEmptyValue()) continue;
            ResourceLocation texture = new ResourceLocation(Chess.MODID, "textures/" + config.getPieceTexture(piece).getPath() + ".png");
            float cx = start + config.getPieceCenterU(col) / texW * size;
            float cz = start + config.getPieceCenterV(row) / config.getBoardTextureHeight() * size;
            quad(pose.last().pose(), buffers.getBuffer(RenderType.entityTranslucent(texture)), cx - pieceSize / 2, y, cz - pieceSize / 2, cx + pieceSize / 2, y, cz + pieceSize / 2, light, overlay);
        }
        pose.popPose();
    }
    private static void quad(Matrix4f m, VertexConsumer v, float minX, float y, float minZ, float maxX, float ignored, float maxZ, int light, int overlay) {
        v.vertex(m, minX, y, minZ).color(255,255,255,255).uv(0,0).overlayCoords(overlay).uv2(light).normal(0,1,0).endVertex();
        v.vertex(m, maxX, y, minZ).color(255,255,255,255).uv(1,0).overlayCoords(overlay).uv2(light).normal(0,1,0).endVertex();
        v.vertex(m, maxX, y, maxZ).color(255,255,255,255).uv(1,1).overlayCoords(overlay).uv2(light).normal(0,1,0).endVertex();
        v.vertex(m, minX, y, maxZ).color(255,255,255,255).uv(0,1).overlayCoords(overlay).uv2(light).normal(0,1,0).endVertex();
    }
    private static void applyRotation(PoseStack pose, Direction facing) { float d = facing == Direction.EAST ? 270 : facing == Direction.SOUTH ? 180 : facing == Direction.WEST ? 90 : 0; if (d != 0) { pose.translate(.5,0,.5); pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(d)); pose.translate(-.5,0,-.5); } }
    @Override public boolean shouldRenderOffScreen(BaseBoardBlockEntity entity) { return true; }
}
