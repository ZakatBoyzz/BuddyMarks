package com.buddymarks.client.mixin;

import com.buddymarks.client.ClientConfig;
import com.buddymarks.client.render.ArmorTintContext;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @Inject(method = "getDyeColor(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$Layer;I)I", at = @At("RETURN"), cancellable = true)
    private static void buddymarks$tintLeatherLayer(EquipmentModel.Layer layer, int vanillaColor,
                                                     CallbackInfoReturnable<Integer> cir) {
        if (layer.dyeable().isEmpty()) return;
        ArmorTintContext.State state = ArmorTintContext.current();
        if (state.friendUuid() == null || state.slot() == null) return;
        ItemStack stack = state.stack();
        if (stack == null || stack.isEmpty() || !isLeatherArmor(stack)) return;

        ClientConfig.VisualSettings settings = ClientConfig.visualFor(state.friendUuid());
        if (!settings.tintLeatherArmor) return;
        int color = switch (state.slot()) {
            case HEAD -> settings.helmetColor;
            case CHEST -> settings.chestColor;
            case LEGS -> settings.legsColor;
            case FEET -> settings.bootsColor;
            default -> vanillaColor;
        };
        cir.setReturnValue(color & 0xFFFFFF);
    }

    private static boolean isLeatherArmor(ItemStack stack) {
        return stack.isOf(Items.LEATHER_HELMET) || stack.isOf(Items.LEATHER_CHESTPLATE)
                || stack.isOf(Items.LEATHER_LEGGINGS) || stack.isOf(Items.LEATHER_BOOTS);
    }
}
