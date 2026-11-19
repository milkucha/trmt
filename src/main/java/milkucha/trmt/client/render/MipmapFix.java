package milkucha.trmt.client.render;

import milkucha.trmt.TRMTNeoForge;
import milkucha.trmt.client.TRMTClientConfig;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The optional worn-grass mipmapping fix ({@code mipmapFix} in trmt-client.json, default off).
 *
 * <p>On: the five eroded-grass top overlay sprites each get {@link #VARIANTS} - 1 extra copies in
 * the block atlas, SpriteContentsMixin rebuilds the mips of all of them (coverage-preserving,
 * stratified far levels), and ErodedGrassBlockModel renders the overlay on cutoutMipped with a
 * per-block copy. Off: none of that happens; the atlas, the mips and the two-pass cutoutMipped/cutout
 * split are exactly as without the fix.
 *
 * <p>When each half switches:
 * <ul>
 *   <li>Atlas copies and mip rebuilding: decided when the block atlas is built (game start and
 *       every resource reload, F3+T), from the config re-read at that moment.</li>
 *   <li>Render layer and per-block copy: decided per chunk rebuild from the live setting (re-read
 *       on F3+A) AND whether the current atlas holds the copies, so "on" never renders vanilla mips
 *       on cutoutMipped. Turning the fix off therefore works with F3+A or F3+T; turning it on needs
 *       F3+T (which also rebuilds all chunks).</li>
 * </ul>
 */
public final class MipmapFix {

    /** Copies per stage sprite (copy 0 = the plain sprite). Must match SpriteContentsMixin. */
    public static final int VARIANTS = 8;
    public static final int STAGES = 5;

    private static final ResourceLocation BLOCKS_ATLAS_INFO = ResourceLocation.withDefaultNamespace("blocks");

    /** Whether the block atlas built last was built with the fix (copies added, mips rebuilt). */
    private static volatile boolean inAtlas = false;

    private MipmapFix() {}

    public static ResourceLocation spriteId(int stage, int variant) {
        return ResourceLocation.fromNamespaceAndPath(TRMTNeoForge.MOD_ID, "block/eroded_grass_block_top_" + stage + (variant == 0 ? "" : "_d" + variant));
    }

    /** Copy index 0..VARIANTS-1 for a block position (home ErodedGrassBlockModel.overlayRemap). */
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

    /** The live setting (chunk meshes use the fix only if the current atlas also holds the copies). */
    public static boolean enabled() {
        return TRMTClientConfig.get().mipmapFix;
    }

    /**
     * SpriteSourceList.load RETURN (resource reload worker thread): for the block atlas, re-read the
     * setting and, when on, add the overlay copies as "single" sources reading the same PNGs.
     */
    public static void onAtlasSources(ResourceLocation atlasInfo, List<SpriteSource> sources) {
        if (!BLOCKS_ATLAS_INFO.equals(atlasInfo)) return;
        boolean on = TRMTClientConfig.refreshMipmapFix();
        inAtlas = on;
        if (on) {
            for (int stage = 0; stage < STAGES; stage++) {
                ResourceLocation resource = spriteId(stage, 0);
                for (int v = 1; v < VARIANTS; v++) {
                    sources.add(new SingleFile(resource, Optional.of(spriteId(stage, v))));
                }
            }
        }
        TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix={}: block atlas built {}", on,
                on ? "with mipmapped, dithered worn-grass overlay" : "without changes (overlay unmipped)");
    }

    /** LevelRenderer.allChanged HEAD (F3+A, also the end of every resource reload): re-read the setting. */
    public static void onChunkReload() {
        boolean before = TRMTClientConfig.get().mipmapFix;
        boolean now = TRMTClientConfig.refreshMipmapFix();
        if (now == before) return;
        if (now && !inAtlas) {
            TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix=true: press F3+T (reload resources) to apply it");
        } else {
            TRMTNeoForge.LOGGER.info("[TRMT] mipmapFix={}: applied to rebuilt chunks", now);
        }
    }
}
