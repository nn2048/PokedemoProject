package win.pokedemo.bridge.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.pokedemo.bridge.client.model.render.BridgeCarrierWorldRenderer;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Shadow public abstract TextRenderer getTextRenderer();

    @Inject(method = "hasLabel(Lnet/minecraft/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
    private void pokedemo$forceBridgeLabel(T entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (BridgeCarrierWorldRenderer.getBridgeLabelInfo(entity) != null && squaredDistanceToCamera <= 4096.0D) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void pokedemo$renderBridgeLabel(T entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        BridgeCarrierWorldRenderer.BridgeLabelInfo info = BridgeCarrierWorldRenderer.getBridgeLabelInfo(entity);
        if (info == null) return;

        Text label = Text.literal(info.label());
        matrices.push();
        matrices.translate(0.0D, info.offsetY(), 0.0D);
        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        matrices.scale(0.025F, -0.025F, 0.025F);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        TextRenderer tr = this.getTextRenderer();
        float x = -tr.getWidth(label) / 2.0F;
        tr.draw(label, x, 0.0F, 0x20FFFFFF, false, mat, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, light);
        tr.draw(label, x, 0.0F, 0xFFFFFFFF, false, mat, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
        ci.cancel();
    }
}
