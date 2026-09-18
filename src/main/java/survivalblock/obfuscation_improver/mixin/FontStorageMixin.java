package survivalblock.obfuscation_improver.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import survivalblock.obfuscation_improver.ObfuscatedTextImprover;

import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public class FontStorageMixin {

    @Shadow @Final
    private Int2ObjectMap<IntList> glyphsByWidth;

    /*
     * In 26.2 the default font still contains the large non-Latin bitmap
     * providers in addition to the small minecraft:font/ascii.png provider.
     * The original mod effectively restricts obfuscation to that small
     * provider. Do that explicitly here instead of relying on provider order.
     */
    @Inject(method = "selectProviders", at = @At("RETURN"))
    private void obfuscation_improver$restrictRandomGlyphPool(
            List<GlyphProvider.Conditional> providers,
            Set<FontOption> options,
            CallbackInfoReturnable<List<GlyphProvider>> cir
    ) {
        List<GlyphProvider> selectedProviders = cir.getReturnValue();
        if (selectedProviders == null || selectedProviders.isEmpty()) {
            return;
        }

        GlyphProvider restrictedProvider = null;
        GlyphProvider fallbackProvider = null;

        for (GlyphProvider provider : selectedProviders) {
            if (provider instanceof UnihexProvider) {
                continue;
            }

            fallbackProvider = provider;

            // minecraft:font/ascii.png is the normal provider containing the
            // complete printable ASCII range. Selecting it directly keeps
            // obfuscated text from wandering into the huge Unicode providers.
            IntSet supported = provider.getSupportedGlyphs();
            if (supported.contains('!') &&
                    supported.contains('0') &&
                    supported.contains('9') &&
                    supported.contains('A') &&
                    supported.contains('Z') &&
                    supported.contains('a') &&
                    supported.contains('z') &&
                    supported.contains('~')) {
                restrictedProvider = provider;
            }
        }

        if (restrictedProvider == null) {
            restrictedProvider = fallbackProvider;
        }
        if (restrictedProvider == null) {
            return;
        }

        this.glyphsByWidth.clear();

        int added = 0;
        for (int codePoint : restrictedProvider.getSupportedGlyphs()) {
            UnbakedGlyph glyph = restrictedProvider.getGlyph(codePoint);
            if (glyph == null || glyph.info() == SpecialGlyphs.MISSING) {
                continue;
            }

            this.glyphsByWidth
                    .computeIfAbsent(
                            Mth.ceil(glyph.info().getAdvance(false)),
                            (Int2ObjectFunction<? extends IntList>) (width -> new IntArrayList())
                    )
                    .add(codePoint);
            added++;
        }

        ObfuscatedTextImprover.LOGGER.info(
                "Restricted obfuscated glyph pool to {} glyphs across {} widths using {}",
                added,
                this.glyphsByWidth.size(),
                restrictedProvider.getClass().getSimpleName()
        );
    }
}
