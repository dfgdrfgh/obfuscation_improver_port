package survivalblock.obfuscation_improver.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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

import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public class FontStorageMixin {

    private static final String MODERNFIX_LAZY_GLYPH_PROVIDER =
            "org.embeddedt.modernfix.render.font.LazyGlyphProvider";

    @Shadow @Final
    private Int2ObjectMap<IntList> glyphsByWidth;

    /*
     * Keep the original mod's provider-selection behavior on 26.2:
     * obfuscated text uses the last active non-Unihex provider rather than
     * Minecraft's enormous Unihex glyph pool.
     *
     * ModernFix wraps UnihexProvider in LazyGlyphProvider, so an instanceof
     * UnihexProvider check alone is not enough in a real modpack. Treat that
     * wrapper as Unihex too.
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

        for (GlyphProvider provider : selectedProviders) {
            if (obfuscation_improver$isUnihex(provider)) {
                continue;
            }

            // The upstream implementation reverses the non-Unihex provider
            // list and only checks the first entry, which is equivalent to
            // keeping the final active non-Unihex provider here.
            restrictedProvider = provider;
        }

        if (restrictedProvider == null) {
            return;
        }

        this.glyphsByWidth.clear();

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
        }
    }

    private static boolean obfuscation_improver$isUnihex(GlyphProvider provider) {
        return provider instanceof UnihexProvider
                || provider.getClass().getName().equals(MODERNFIX_LAZY_GLYPH_PROVIDER);
    }
}
