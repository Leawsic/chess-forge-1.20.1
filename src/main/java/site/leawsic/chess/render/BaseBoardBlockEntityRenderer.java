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
        float yBase = 0.05f, boardSize = 3f, boardStart = -1f;
        float texW = config.getBoardTextureWidth(), texH = config.getBoardTextureHeight();
        float pieceSize = config.getPieceDrawSize() / texW * boardSize;
        float thickness = 1 / 64f;
        Matrix4f mat = pose.last().pose();
        for (int row = 0; row < config.getRows(); row++) for (int col = 0; col < config.getCols(); col++) {
            int piece = board[row][col]; if (piece == config.getEmptyValue()) continue;
            ResourceLocation texture = new ResourceLocation(Chess.MODID, "textures/" + config.getPieceTexture(piece).getPath() + ".png");
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(texture));
            float xCenter = boardStart + (config.getPieceCenterU(col) / texW) * boardSize;
            float zCenter = boardStart + (config.getPieceCenterV(row) / texH) * boardSize;
            float minX = xCenter - pieceSize / 2, maxX = xCenter + pieceSize / 2;
            float minZ = zCenter - pieceSize / 2, maxZ = zCenter + pieceSize / 2;
            float yTop = yBase + thickness;
            buffer.vertex(mat, minX, yTop, minZ).color(255, 255, 255, 255).uv(0, 0).overlayCoords(overlay).uv2(light).normal(0, 1, 0).endVertex();
            buffer.vertex(mat, maxX, yTop, minZ).color(255, 255, 255, 255).uv(1, 0).overlayCoords(overlay).uv2(light).normal(0, 1, 0).endVertex();
            buffer.vertex(mat, maxX, yTop, maxZ).color(255, 255, 255, 255).uv(1, 1).overlayCoords(overlay).uv2(light).normal(0, 1, 0).endVertex();
            buffer.vertex(mat, minX, yTop, maxZ).color(255, 255, 255, 255).uv(0, 1).overlayCoords(overlay).uv2(light).normal(0, 1, 0).endVertex();
            buffer.vertex(mat, minX, yBase, minZ).color(255, 255, 255, 255).uv(0, 0).overlayCoords(overlay).uv2(light).normal(0, -1, 0).endVertex();
            buffer.vertex(mat, maxX, yBase, minZ).color(255, 255, 255, 255).uv(1, 0).overlayCoords(overlay).uv2(light).normal(0, -1, 0).endVertex();
            buffer.vertex(mat, maxX, yBase, maxZ).color(255, 255, 255, 255).uv(1, 1).overlayCoords(overlay).uv2(light).normal(0, -1, 0).endVertex();
            buffer.vertex(mat, minX, yBase, maxZ).color(255, 255, 255, 255).uv(0, 1).overlayCoords(overlay).uv2(light).normal(0, -1, 0).endVertex();
        }
        pose.popPose();
    }
    private static void applyRotation(PoseStack pose, Direction facing) { float d = facing == Direction.EAST ? 270 : facing == Direction.SOUTH ? 180 : facing == Direction.WEST ? 90 : 0; if (d != 0) { pose.translate(.5, 0, .5); pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(d)); pose.translate(-.5, 0, -.5); } }
    @Override public boolean shouldRenderOffScreen(BaseBoardBlockEntity entity) { return true; }
}