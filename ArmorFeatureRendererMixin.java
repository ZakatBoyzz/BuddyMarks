package com.buddymarks.client.mixin;

import com.buddymarks.client.ClientState;
import com.buddymarks.client.render.ArmorTintContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"))
    private void buddymarks$beginArmorRender(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                              BipedEntityRenderState state, float limbAngle, float limbDistance,
                                              CallbackInfo ci) {
        ArmorTintContext.end();
        String uuid = null;
        if (state instanceof PlayerEntityRenderState playerState) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                var entity = client.world.getEntityById(playerState.id);
                if (entity instanceof PlayerEntity player && ClientState.isFriend(player.getUuid())) {
                    uuid = player.getUuidAsString();
                }
            }
        }
        ArmorTintContext.begin(uuid);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("RETURN"))
    private void buddymarks$endArmorRender(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                            BipedEntityRenderState state, float limbAngle, float limbDistance,
                                            CallbackInfo ci) {
        ArmorTintContext.end();
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V", at = @At("HEAD"))
    private void buddymarks$setArmorPiece(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack stack,
                                           EquipmentSlot slot, int light, BipedEntityModel<?> model, CallbackInfo ci) {
        ArmorTintContext.setPiece(stack, slot);
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V", at = @At("RETURN"))
    private void buddymarks$clearArmorPiece(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack stack,
                                             EquipmentSlot slot, int light, BipedEntityModel<?> model, CallbackInfo ci) {
        ArmorTintContext.clearPiece();
    }
}
