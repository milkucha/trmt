package milkucha.trmt.client.render;

import milkucha.trmt.TRMTNeoForge;
import milkucha.trmt.client.TRMTClientConfig;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The optional worn-grass mipmapping fix ({@code mipmapFix} in trmt-client.json, default off).
 *
 * <p>On: the five eroded-grass top overlay sprites each get {@link #VARIANTS} - 1 extra copies in
 * the block atlas, SpriteContentsMixin rebuilds the mips of all of them (coverage-preserving,
 * stratified far levels), and ErodedGrassBlockModel moves each block's top overlay onto a
 * per-block copy. The render layer stays CUTOUT in both states (vanilla already mipmaps CUTOUT on
 * this version). Off: none of that happens; the atlas, the mips and the overlay are exactly as
 * without the fix.
 *
 * <p>When each half switches:
 * <ul>
 *   <li>Atlas copies and mip rebuilding: decided when the block atlas is built (game start and
 *       every resource reload, F3+T), from the config re-read at that moment.</li>
 *   <li>Per-block copy: follows only whether the current atlas holds the copies, never the live
 *       setting. Vanilla mips CUTOUT here, so a block moved back to copy 0 while the atlas still
 *       holds the rebuilt (dithered) mips would look wrong. F3+A therefore never changes the look;
 *       turning the fix on or off both need F3+T.</li>
 * </ul>
 */
public final class MipmapFix {

    /** Copies per stage sprite (copy 0 = the plain sprite). Must match SpriteContentsMixin. */
    public static final int VARIANTS = 8;
    public static final int STAGES = 5;

    private static final Identifier BLOCKS_ATLAS_INFO = AtlasIds.BLOCKS;

    /** Whether the block atlas built last was built with the fix (copies added, mips rebuilt). */
    private static volatile boolean inAtlas = false;

    private MipmapFix() {}

    public static Identifier spriteId(int stage, int variant) {
        return Identifier.fromNamespaceAndPath(TRMTNeoForge.MOD_ID, "block/eroded_grass_block_top_" + stage + (variant == 0 ? "" : "_d" + variant));
    }

    /**
     * Copy index 0..VARIANTS-1 for a block position (home ErodedGrassBlockModel.overlayRemap).
     * The coordinates are mixed with large odd constants and run through an integer avalanche
     * finalizer, so neighbouring blocks get uncorrelated copies and the choice is stable across
     * re-meshes. Copy 0 is the plain stage sprite.
     */
    public static int copyIndex(int x, int y, int z) {
        int h = x * 0x9E3779B1 ^ z * 0x85EBCA77 ^ y * 0xC2B2AE3D;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 29) & (VARIANTS - 1);
    }

    /** Whether SpriteContentsMixin should rebuild the overlay mips (set at atlas build time). */
    public static boolean inAtlas() {
        return inAtlas;
    }

    /**
     * SpriteSourceList.load RETURN (resource reload worker thread): for the block atlas, re-read the
     * setting and, when on, add the overlay copies as "single" sources reading the same PNGs.
     */
    public static void onAtlasSources(Identifier atlasInfo, List<SpriteSource> sources) {
        if (!BLOCKS_ATLAS_INFO.equals(atlasInfo)) return;
        boolean on = TRMTClientConfig.refreshMipmapFix();
        inAtlas = on;
        if (on) {
            for (int stage = 0; stage < STAGES; stage++) {
                Identifier resource = spriteId(stage, 0);
                for (int v = 1; v < VARIANTS; v++) {
                    sources.add(new SingleFile(resource, Optional.of(spriteId(stage, v))));
                }
            }
        }
        TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix={}: block atlas built {}", on,
                on ? "with mipmapped, dithered worn-grass overlay" : "without changes");
    }

    /** LevelRenderer.allChanged HEAD (F3+A, also the end of every resource reload): re-read the setting. */
    public static void onChunkReload() {
        boolean before = TRMTClientConfig.get().mipmapFix;
        boolean now = TRMTClientConfig.refreshMipmapFix();
        if (now == before) return;
        if (now != inAtlas) {
            TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix={}: press F3+T (reload resources) to apply it", now);
        } else {
            TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix={}: matches the current atlas", now);
        }
    }
}
