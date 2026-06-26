package win.pokedemo.bridge.client.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import win.pokedemo.bridge.client.hud.HudAssetResolver;

public final class PortraitRenderUtil {
    private PortraitRenderUtil() {}

    public static boolean drawSpeciesPortrait(DrawContext dc, String species, int x, int y, int w, int h) {
        HudAssetResolver.PortraitSlice portrait = HudAssetResolver.findSpeciesPortraitSlice(species);
        if (portrait == null) return false;
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, portrait.texture(), x, y,
                (float) portrait.u(), (float) portrait.v(), w, h,
                portrait.width(), portrait.height(), portrait.textureWidth(), portrait.textureHeight());
        return true;
    }

    public static boolean drawPortraitForItem(DrawContext dc, ItemStack stack, int x, int y, int w, int h) {
        String species = HudAssetResolver.findSpeciesForItemStack(stack);
        return species != null && drawSpeciesPortrait(dc, species, x, y, w, h);
    }

    public static void drawFallbackBadge(DrawContext dc, net.minecraft.client.font.TextRenderer tr, String species, int x, int y, int w, int h) {
        dc.fill(x, y, x + w, y + h, speciesColor(species));
        String abbrev = speciesAbbrev(species);
        int tw = tr.getWidth(abbrev);
        dc.drawText(tr, Text.literal(abbrev), x + Math.max(0, (w - tw) / 2), y + Math.max(0, (h - 8) / 2), 0xFFFFFFFF, false);
    }

    private static String speciesAbbrev(String species) {
        if (species == null || species.isBlank()) return "?";
        String compact = species.replace("_", " ").trim();
        if (compact.isEmpty()) return "?";
        String[] parts = compact.split("\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
        }
        return compact.substring(0, Math.min(2, compact.length())).toUpperCase(java.util.Locale.ROOT);
    }

    private static int speciesColor(String species) {
        if (species == null) return 0xFF44515F;
        int hash = Math.abs(species.toLowerCase(java.util.Locale.ROOT).hashCode());
        int r = 70 + (hash & 0x3F);
        int g = 88 + ((hash >> 6) & 0x3F);
        int b = 108 + ((hash >> 12) & 0x3F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
