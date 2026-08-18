package site.leawsic.chess.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.XiangqiBoardBlock;
import site.leawsic.chess.block.XiangqiBoardBlockEntity;
import site.leawsic.chess.config.XiangqiConfig;

public class XiangqiBoardBlockEntityRenderer implements BlockEntityRenderer<XiangqiBoardBlockEntity> {
    public XiangqiBoardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(XiangqiBoardBlockEntity entity, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); applyRotation(pose, entity.getBlockState().getValue(XiangqiBoardBlock.FACING));
        float size = 3f, piece = (float) XiangqiConfig.PIECE_PIXELS / XiangqiConfig.BOARD_TEXTURE_SIZE * size;
        for (int row=0; row<XiangqiConfig.ROWS; row++) for (int col=0; col<XiangqiConfig.COLS; col++) { int p=entity.getBoard()[row][col]; if(p==0) continue; ResourceLocation t=new ResourceLocation(Chess.MODID,"textures/"+XiangqiConfig.pieceTexture(p).getPath()+".png"); float cx=-1+XiangqiConfig.textureX(col)/(float)XiangqiConfig.BOARD_TEXTURE_SIZE*size, cz=-1+XiangqiConfig.textureY(row)/(float)XiangqiConfig.BOARD_TEXTURE_SIZE*size; quad(pose,buffers.getBuffer(RenderType.entityTranslucent(t)),cx-piece/2,.065f,cz-piece/2,cx+piece/2,cz+piece/2,light,overlay); }
        pose.popPose();
    }
    private static void quad(PoseStack p, VertexConsumer v,float a,float y,float b,float c,float d,int l,int o){var m=p.last().pose();v.vertex(m,a,y,b).color(255,255,255,255).uv(0,0).overlayCoords(o).uv2(l).normal(0,1,0).endVertex();v.vertex(m,c,y,b).color(255,255,255,255).uv(1,0).overlayCoords(o).uv2(l).normal(0,1,0).endVertex();v.vertex(m,c,y,d).color(255,255,255,255).uv(1,1).overlayCoords(o).uv2(l).normal(0,1,0).endVertex();v.vertex(m,a,y,d).color(255,255,255,255).uv(0,1).overlayCoords(o).uv2(l).normal(0,1,0).endVertex();}
    private static void applyRotation(PoseStack p,Direction f){float d=f==Direction.EAST?270:f==Direction.SOUTH?180:f==Direction.WEST?90:0;if(d!=0){p.translate(.5,0,.5);p.mulPose(com.mojang.math.Axis.YP.rotationDegrees(d));p.translate(-.5,0,-.5);}}
    @Override public boolean shouldRenderOffScreen(XiangqiBoardBlockEntity entity){return true;}
}
