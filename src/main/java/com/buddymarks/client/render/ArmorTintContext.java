package com.buddymarks.client.render;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/** Render-thread context used to tint only a friend's leather armor for this client. */
public final class ArmorTintContext {
    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::empty);

    private ArmorTintContext() {}

    public static void begin(String friendUuid) {
        CURRENT.set(new State(friendUuid, ItemStack.EMPTY, null));
    }

    public static void setPiece(ItemStack stack, EquipmentSlot slot) {
        State state = CURRENT.get();
        if (state.friendUuid != null) CURRENT.set(new State(state.friendUuid, stack, slot));
    }

    public static void clearPiece() {
        State state = CURRENT.get();
        if (state.friendUuid != null) CURRENT.set(new State(state.friendUuid, ItemStack.EMPTY, null));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static State current() {
        return CURRENT.get();
    }

    public record State(String friendUuid, ItemStack stack, EquipmentSlot slot) {
        private static State empty() { return new State(null, ItemStack.EMPTY, null); }
    }
}
