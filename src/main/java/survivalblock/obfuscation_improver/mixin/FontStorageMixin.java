package survivalblock.obfuscation_improver.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public class FontStorageMixin {

    @Shadow @Final
    private Int2ObjectMap<IntList> glyphsByWidth;

    /**
     * Minecraft 26.2 already stores the random/obfuscated glyph pool in
     * FontSet#glyphsByWidth. Rebuild that vanilla table directly so every
     * caller sees the restricted pool, even if other text mods transform
     * getRandomGlyph().
     */
    @Inject(method = "selectProviders", at = @At("RETURN"))
    private void obfuscation_improver$rebuildRandomGlyphPool(
            List<GlyphProvider.Conditional> providers,
            Set<FontOption> options,
            CallbackInfoReturnable<List<GlyphProvider>> cir
    ) {
        List<GlyphProvider> selectedProviders = cir.getReturnValue();
        if (selectedProviders == null || selectedProviders.isEmpty()) {
            return;
        }

        IntSet supportedGlyphs = new IntOpenHashSet();
        List<GlyphProvider> obfuscationFonts = new ArrayList<>();

        for (GlyphProvider provider : selectedProviders) {
            supportedGlyphs.addAll(provider.getSupportedGlyphs());
            if (!(provider instanceof UnihexProvider)) {
                obfuscationFonts.add(provider);
            }
        }

        Collections.reverse(obfuscationFonts);
        this.glyphsByWidth.clear();

        supportedGlyphs.forEach(codePoint -> {
            for (GlyphProvider provider : obfuscationFonts) {
                UnbakedGlyph glyph = provider.getGlyph(codePoint);
                if (glyph != null && glyph.info() != SpecialGlyphs.MISSING) {
                    this.glyphsByWidth
                            .computeIfAbsent(
                                    Mth.ceil(glyph.info().getAdvance(false)),
                                    (Int2ObjectFunction<? extends IntList>) (width -> new IntArrayList())
                            )
                            .add(codePoint);
                }

                // Keep the original mod's provider-priority behavior.
                break;
            }
        });

        ObfuscatedTextImprover.LOGGER.debug(
                "Rebuilt 26.2 obfuscated glyph pool: {} widths, {} non-Unihex providers",
                this.glyphsByWidth.size(),
                obfuscationFonts.size()
        );
    }
}
