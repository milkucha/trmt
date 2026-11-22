package milkucha.trmt.client.render;

import milkucha.trmt.block.ErodedGrassBlock;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Supplier;

/**
 * FabricBakedModel wrapper for ErodedGrassBlock block-state models.
 * Applies a cutout material to every non-DOWN quad so that transparent pixels in the
 * eroded-top overlay and the grass_block_side_overlay are discarded correctly.
 *
 * This per-quad material overrides the block-level BlockRenderLayerMap registration on
 * the quads it is stamped on (Indigo resolves a non-DEFAULT BlendMode directly to its
 * own RenderLayer), so whichever mode is set here is what actually renders. It is chosen
 * per chunk rebuild by MipmapFix (setting mipmapFix in trmt-client.json):
 * - off (default): CUTOUT, which samples the atlas unmipped; the overlay shimmers at
 *   distance while the camera moves, as it always has.
 * - on: CUTOUT_MIPPED with the overlay mips rebuilt by SpriteContentsMixin (vanilla mip
 *   generation darkens and dilates the sparse high erosion stages: per-2x2 colour sums are
 *   divided by 4 regardless of how many texels are visible, and gamma-space alpha averaging
 *   keeps any block with one visible texel above the 0.5 cutout threshold). Each block's top
 *   overlay also uses one of MipmapFix.VARIANTS copies of its stage sprite (dithered far mip
 *   levels, see SpriteContentsMixin): level 0 of every copy is identical, so up close nothing
 *   changes; far away the copies' different 2x2/1x1 rounding averages out to the stage's true
 *   grass coverage.
 *
 * Stage texture selection and FACING Y-rotation are already baked into the wrapped
 * model by the block-state system; the copy choice is the only per-position work.
 */
public class ErodedGrassBlockModel implements BakedModel {

    private final BakedModel wrapped;
    private static RenderMaterial cutoutMaterial;
    private static RenderMaterial cutoutMippedMaterial;

    private static RenderMaterial cutoutMaterial() {
        if (cutoutMaterial == null) {
            cutoutMaterial = RendererAccess.INSTANCE.getRenderer()
                    .materialFinder()
                    .blendMode(BlendMode.CUTOUT)
                    .find();
        }
        return cutoutMaterial;
    }

    private static RenderMaterial cutoutMippedMaterial() {
        if (cutoutMippedMaterial == null) {
            cutoutMippedMaterial = RendererAccess.INSTANCE.getRenderer()
                    .materialFinder()
                    .blendMode(BlendMode.CUTOUT_MIPPED)
                    .find();
        }
        return cutoutMippedMaterial;
    }

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    private static final int VARIANTS = MipmapFix.VARIANTS;
    /**
     * [stage][variant] sprites, resolved on first use (a new model instance per resource reload).
     * Copies are null when the atlas was built without them (mipmapFix off at that reload).
     */
    private volatile Sprite[][] variantSprites;

    private Sprite[][] variantSprites() {
        Sprite[][] s = variantSprites;
        if (s == null) {
            SpriteAtlasTexture atlas = MinecraftClient.getInstance().getBakedModelManager()
                    .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            s = new Sprite[MipmapFix.STAGES][VARIANTS];
            for (int st = 0; st < MipmapFix.STAGES; st++) {
                for (int v = 0; v < VARIANTS; v++) {
                    Identifier id = MipmapFix.spriteId(st, v);
                    Sprite sp = atlas.getSprite(id);
                    // Missing copy (atlas built with mipmapFix off) -> no remap.
                    s[st][v] = sp.getContents().getId().equals(id) ? sp : null;
                }
            }
            variantSprites = s;
        }
        return s;
    }

    /** Whether the current atlas holds the overlay copies (it was built with mipmapFix on). */
    private boolean atlasHasCopies() {
        return variantSprites()[0][1] != null;
    }

    /** {plain stage sprite, this block's copy}, or null when no remap applies. */
    private Sprite[] overlayRemap(BlockState state, BlockPos pos) {
        if (!state.contains(ErodedGrassBlock.STAGE)) return null;
        int stage = state.get(ErodedGrassBlock.STAGE);
        if (stage < 0 || stage >= MipmapFix.STAGES) return null;
        // Copy index from a position hash: the coordinates are mixed with large odd
        // constants and run through an integer avalanche finalizer (xor-shift/multiply),
        // so neighbouring blocks get uncorrelated copies (white noise, no visible grid or
        // stripes), and the choice is stable across re-meshes. The top 3 bits pick one of
        // the 8 copies; copy 0 is the plain stage sprite and needs no UV change.
        int h = pos.getX() * 0x9E3779B1 ^ pos.getZ() * 0x85EBCA77 ^ pos.getY() * 0xC2B2AE3D;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        int variant = (h >>> 29) & (VARIANTS - 1);
        if (variant == 0) return null;
        Sprite[][] s = variantSprites();
        Sprite from = s[stage][0], to = s[stage][variant];
        return from == null || to == null ? null : new Sprite[]{from, to};
    }

    @Override
    public boolean isVanillaAdapter() { return false; }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        // Setting on AND the current atlas built with the copies and rebuilt mips (else vanilla
        // mips would be sampled on CUTOUT_MIPPED); off renders exactly as without the fix.
        boolean fix = MipmapFix.enabled() && atlasHasCopies();
        RenderMaterial material = fix ? cutoutMippedMaterial() : cutoutMaterial();
        Sprite[] remap = fix ? overlayRemap(state, pos) : null;
        context.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.material(material);
            }
            // Move the tinted top overlay (not the dirt base, not the sides) onto this
            // block's copy of its stage sprite (same level-0 pixels, different far-mip
            // rounding; see SpriteContentsMixin). The quad is identified by its UV centre
            // lying inside the plain stage sprite. Pure UV translation, so the FACING
            // rotation baked into the UVs is kept.
            if (remap != null && quad.nominalFace() == Direction.UP && quad.colorIndex() >= 0) {
                Sprite from = remap[0], to = remap[1];
                float u0 = (quad.u(0) + quad.u(1) + quad.u(2) + quad.u(3)) * 0.25f;
                float v0 = (quad.v(0) + quad.v(1) + quad.v(2) + quad.v(3)) * 0.25f;
                if (u0 > from.getMinU() && u0 < from.getMaxU() && v0 > from.getMinV() && v0 < from.getMaxV()) {
                    float du = to.getMinU() - from.getMinU();
                    float dv = to.getMinV() - from.getMinV();
                    for (int i = 0; i < 4; i++) quad.uv(i, quad.u(i) + du, quad.v(i) + dv);
                }
            }
            return true;
        });
        wrapped.emitBlockQuads(world, state, pos, randomSupplier, context);
        context.popTransform();
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        wrapped.emitItemQuads(stack, randomSupplier, context);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face,
                                    net.minecraft.util.math.random.Random random) {
        List<BakedQuad> quads = wrapped.getQuads(state, face, random);
        // The UP face has two coplanar quads (dirt base + eroded_top overlay).
        // The break-animation renderer uses getQuads() and bypasses FRAPI/CUTOUT,
        // causing the two coincident faces to Z-fight and produce white pixels.
        // Return only the first (dirt base) quad so the damage overlay renders cleanly.
        if (face == Direction.UP && quads.size() > 1) {
            return quads.subList(0, 1);
        }
        return quads;
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean hasDepth()             { return wrapped.hasDepth(); }
    @Override public boolean isSideLit()            { return wrapped.isSideLit(); }
    @Override public boolean isBuiltin()            { return wrapped.isBuiltin(); }
    @Override public Sprite getParticleSprite()     { return wrapped.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return wrapped.getTransformation(); }
    @Override public ModelOverrideList getOverrides()        { return wrapped.getOverrides(); }
}
