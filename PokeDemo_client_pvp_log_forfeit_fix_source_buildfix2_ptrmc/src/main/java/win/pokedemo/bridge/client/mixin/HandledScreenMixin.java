package win.pokedemo.bridge.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow @Final protected T handler;

    @Inject(method = "render", at = @At("TAIL"))
    private void pokedemo$drawOfflinePokemonPortraits(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String title = "";
        Screen self = (Screen) (Object) this;
        try {
            Text t = self.getTitle();
            title = t == null ? "" : t.getString();
        } catch (Throwable ignored) {
        }
        int partyIndex = 0;
        java.util.List<win.pokedemo.bridge.common.PartySlotState> partySlots = win.pokedemo.bridge.client.PokeDemoBridgeClient.partyHudState().slots();
        boolean preferPartyOrder = isPartyLikeTitle(title);
        for (Slot slot : this.handler.slots) {
            if (slot == null || !slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            String species = win.pokedemo.bridge.client.hud.HudAssetResolver.findSpeciesForItemStack(stack);
            if (species == null && preferPartyOrder && partyIndex < partySlots.size()) {
                win.pokedemo.bridge.common.PartySlotState ps = partySlots.get(partyIndex++);
                if (ps != null && ps.occupied() && ps.species() != null && !ps.species().isBlank()) {
                    species = ps.species();
                }
            }
            if (species == null) continue;
            int drawX = this.x + slot.x;
            int drawY = this.y + slot.y;
            context.fill(drawX, drawY, drawX + 16, drawY + 16, 0xF0141820);
            if (!win.pokedemo.bridge.client.render.PortraitRenderUtil.drawSpeciesPortrait(context, species, drawX, drawY, 16, 16)) {
                win.pokedemo.bridge.client.render.PortraitRenderUtil.drawFallbackBadge(
                        context,
                        MinecraftClient.getInstance().textRenderer,
                        species,
                        drawX, drawY, 16, 16
                );
            }
        }
    }

    private static boolean isPartyLikeTitle(String title) {
        if (title == null || title.isBlank()) return false;
        String t = title.toLowerCase(java.util.Locale.ROOT);
        return t.contains("party") || t.contains("队伍") || t.contains("寶可夢") || t.contains("宝可梦") || t.contains("pokemon") || t.contains("精灵") || t.contains("ポケ") || t.contains("starter") || t.contains("御三家") || t.contains("pc") || t.contains("电脑") || t.contains("盒子") || t.contains("storage");
    }
}
