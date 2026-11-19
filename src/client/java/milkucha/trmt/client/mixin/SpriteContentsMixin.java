package milkucha.trmt.client.mixin;

import milkucha.trmt.client.render.MipmapFix;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Only active while the block atlas is built with mipmapFix on (see MipmapFix); otherwise the
 * vanilla mips (MipmapGenerator, CUTOUT strategy) are left untouched.
 *
 * Replaces the vanilla-generated mipmaps of the eroded grass top overlay sprites with a
 * coverage-preserving, dithered downsample. On this version the CUTOUT layer is already mipmapped
 * by vanilla (solidify on level 0, alpha scaled to preserve coverage), so the layer stays CUTOUT
 * in both states; the rebuild here adds the per-copy stratified far levels.
 *
 * This mixin rebuilds mip levels 1..n from level 0 for the
 * trmt:block/eroded_grass_block_top_* sprites only (five stages, each in TRMT$VARIANTS
 * copies, see below):
 * - RGB = gamma-correct average over the covered source texels only (no darkening);
 * - alpha = binary (255/0), with the texels kept per level chosen by highest coverage so
 *   that the visible-texel ratio of each mip matches level 0 (coverage preservation, as in
 *   alpha-tested mip generation in other engines).
 *
 * Transparent texels (level 0 included, rewritten in memory only) additionally get the
 * overlay's average colour as RGB while keeping alpha 0. cutout_mipped samples with
 * GL_NEAREST_MIPMAP_LINEAR, which blends the two nearest mip levels; in the crossfade
 * bands a visible texel of one level is averaged with the corresponding texel of the
 * other level even when that texel is transparent, so transparent-black RGB darkens the
 * result by up to half — visible in-game as a shadow-like fringe over the dirt. Alpha
 * stays 0, so the bled colour is only ever seen through that blend.
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {

    @Shadow @Final private Identifier name;
    @Shadow private NativeImage[] byMipLevel;

    @Unique private static final double TRMT$GAMMA = 2.2;
    /** Copies per stage sprite; defined in MipmapFix. */
    @Unique private static final int TRMT$VARIANTS = MipmapFix.VARIANTS;

    @Inject(method = "increaseMipLevel", at = @At("TAIL"))
    private void trmt$regenerateCutoutMips(int mipLevel, CallbackInfo ci) {
        if (!MipmapFix.inAtlas()) return;
        if (!"trmt".equals(this.name.getNamespace())
                || !this.name.getPath().startsWith("block/eroded_grass_block_top_")) return;
        if (this.byMipLevel.length < 2) return;

        // Dithered far levels: the five stage sprites each exist in TRMT$VARIANTS copies
        // (added to the block atlas by MipmapFix as "single" sources "..._top_<s>_d<v>" reading
        // the same PNG, v = 1..7; the plain sprite is v = 0).
        // Level 0 is identical for all copies; the number of texels kept at each mip level
        // is floor(n * coverage + r_v) with a per-copy offset r_v = (v + 0.5) / N, so over
        // the copies the kept fraction averages to the true coverage, also at the 2x2 and
        // 1x1 levels (where a single sprite can only round). ErodedGrassBlockModel picks the copy per block from a position hash,
        // so far away a path is a fine grass/dirt dither whose density follows coverage: the
        // grass texels are tinted like any grass and the dirt is the real (untinted, vanilla
        // or resource-pack) dirt base showing through, so the mix is right in every biome.
        int variant = 0;
        String path = this.name.getPath();
        int d = path.lastIndexOf("_d");
        if (d > 0) {
            try {
                variant = Integer.parseInt(path.substring(d + 2));
            } catch (NumberFormatException e) {
                return;
            }
            if (variant < 1 || variant >= TRMT$VARIANTS) return;
        }
        double offset = (variant + 0.5) / TRMT$VARIANTS;

        NativeImage base = this.byMipLevel[0];
        int w = base.getWidth();
        int h = base.getHeight();

        // Per-texel coverage fraction and coverage-weighted linear-space channels.
        double[] cov = new double[w * h];
        double[][] chan = new double[3][w * h];
        int visibleBase = 0;
        double totalCov = 0.0;
        double[] totalLin = new double[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = base.getPixel(x, y);
                int a = (color >>> 24) & 0xFF;
                int i = y * w + x;
                cov[i] = a / 255.0;
                totalCov += cov[i];
                if (a >= 128) visibleBase++;
                if (a != 0) {
                    for (int c = 0; c < 3; c++) {
                        int v = (color >>> (c * 8)) & 0xFF;
                        chan[c][i] = cov[i] * Math.pow(v / 255.0, TRMT$GAMMA);
                        totalLin[c] += chan[c][i];
                    }
                }
            }
        }
        if (totalCov <= 0.0) return;
        double targetRatio = (double) visibleBase / (w * h);

        // Overlay-average colour with alpha 0, bled into transparent texels at every level
        // so the trilinear crossfade between mip levels never blends toward black.
        int bleedPacked = 0;
        for (int c = 0; c < 3; c++) {
            int v = (int) Math.round(Math.pow(totalLin[c] / totalCov, 1.0 / TRMT$GAMMA) * 255.0);
            bleedPacked |= Math.min(255, Math.max(0, v)) << (c * 8);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((base.getPixel(x, y) >>> 24) == 0) base.setPixel(x, y, bleedPacked);
            }
        }

        for (int level = 1; level < this.byMipLevel.length; level++) {
            NativeImage img = this.byMipLevel[level];
            int nw = img.getWidth();
            int nh = img.getHeight();
            double[] nCov = new double[nw * nh];
            double[][] nChan = new double[3][nw * nh];
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    int i = y * nw + x;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int src = Math.min(y * 2 + dy, h - 1) * w + Math.min(x * 2 + dx, w - 1);
                            nCov[i] += cov[src];
                            for (int c = 0; c < 3; c++) nChan[c][i] += chan[c][src];
                        }
                    }
                    nCov[i] /= 4.0;
                    for (int c = 0; c < 3; c++) nChan[c][i] /= 4.0;
                }
            }

            // Keep the K highest-coverage texels so this level's visible ratio matches level 0.
            // Ties (equal coverage) are broken by texel index rotated by the copy number, so
            // the copies also differ in *which* tied texels they keep.
            Integer[] order = new Integer[nw * nh];
            for (int i = 0; i < order.length; i++) order[i] = i;
            final int n = nw * nh;
            final int rot = variant;
            Arrays.sort(order, Comparator.<Integer>comparingDouble(i -> -nCov[i])
                    .thenComparingInt(i -> (i + rot) % n));
            int nonZero = 0;
            for (double v : nCov) if (v > 0.0) nonZero++;
            // Stratified rounding: floor(x + (v + 0.5) / N) rounds x up in exactly
            // round(N * frac(x)) of the N copies, so the mean kept count over the copies is
            // the unrounded target (to within 1/(2N) texel), even at 2x2/1x1 where a single
            // sprite could only round to none or all.
            int keep = Math.min((int) Math.floor(targetRatio * n + offset), nonZero);

            boolean[] kept = new boolean[nw * nh];
            for (int r = 0; r < keep; r++) kept[order[r]] = true;
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    int i = y * nw + x;
                    if (kept[i]) {
                        int packed = 0xFF << 24;
                        for (int c = 0; c < 3; c++) {
                            int v = (int) Math.round(Math.pow(nChan[c][i] / nCov[i], 1.0 / TRMT$GAMMA) * 255.0);
                            packed |= Math.min(255, Math.max(0, v)) << (c * 8);
                        }
                        img.setPixel(x, y, packed);
                    } else if (nCov[i] > 0.0) {
                        // Dropped but partially covered: keep its own average colour, alpha 0.
                        int packed = 0;
                        for (int c = 0; c < 3; c++) {
                            int v = (int) Math.round(Math.pow(nChan[c][i] / nCov[i], 1.0 / TRMT$GAMMA) * 255.0);
                            packed |= Math.min(255, Math.max(0, v)) << (c * 8);
                        }
                        img.setPixel(x, y, packed);
                    } else {
                        img.setPixel(x, y, bleedPacked);
                    }
                }
            }

            cov = nCov;
            chan = nChan;
            w = nw;
            h = nh;
        }
    }
}
