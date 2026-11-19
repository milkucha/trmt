package milkucha.trmt.client.render;

import milkucha.trmt.block.ErodedGrassBlock;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

/**
 * Wraps the ErodedGrassBlock block-state models. Every non-DOWN quad is put on the CUTOUT layer
 * (vanilla mipmaps CUTOUT on this version, so the layer is the same with the mipmapFix setting on
 * and off). With the current atlas built with the copies (mipmapFix on at the last F3+T, see MipmapFix) each
 * block's tinted top overlay is also moved onto one of MipmapFix.VARIANTS copies of its stage
 * sprite, chosen from a position hash: level 0 of every copy is identical, so up close nothing
 * changes; far away the copies' different mip rounding (SpriteContentsMixin) averages out to the
 * stage's true grass coverage. Off: exactly the pre-fix code path.
 */
public class ErodedGrassBlockModel extends WrapperBlockStateModel {

    private static final int VARIANTS = MipmapFix.VARIANTS;

    /**
     * [stage][variant] sprites, resolved on first use (a new model instance per resource reload).
     * Copies are null when the atlas was built without them (mipmapFix off at that reload).
     */
    private volatile TextureAtlasSprite[][] variantSprites;

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    private TextureAtlasSprite[][] variantSprites() {
        TextureAtlasSprite[][] s = variantSprites;
        if (s == null) {
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            s = new TextureAtlasSprite[MipmapFix.STAGES][VARIANTS];
            for (int st = 0; st < MipmapFix.STAGES; st++) {
                for (int v = 0; v < VARIANTS; v++) {
                    Identifier id = MipmapFix.spriteId(st, v);
                    TextureAtlasSprite sp = atlas.getSprite(id);
                    // Missing copy (atlas built with mipmapFix off) -> no remap.
                    s[st][v] = sp.contents().name().equals(id) ? sp : null;
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
    private TextureAtlasSprite[] overlayRemap(BlockState state, BlockPos pos) {
        if (!state.hasProperty(ErodedGrassBlock.STAGE)) return null;
        int stage = state.getValue(ErodedGrassBlock.STAGE);
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
        TextureAtlasSprite[][] s = variantSprites();
        TextureAtlasSprite from = s[stage][0], to = s[stage][variant];
        return from == null || to == null ? null : new TextureAtlasSprite[]{from, to};
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        // Follows the current atlas (built with the copies or not), not the live setting: vanilla mips
        // CUTOUT, so leaving the rebuilt mips on copy 0 would look wrong. Changes apply on F3+T.
        boolean fix = atlasHasCopies();
        TextureAtlasSprite[] remap = fix ? overlayRemap(state, pos) : null;
        emitter.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.renderLayer(ChunkSectionLayer.CUTOUT);
            }
            // Move the tinted top overlay (not the dirt base, not the sides) onto this
            // block's copy of its stage sprite (same level-0 pixels, different far-mip
            // rounding; see SpriteContentsMixin). The quad is identified by its UV centre
            // lying inside the plain stage sprite. Pure UV translation, so the FACING
            // rotation baked into the UVs is kept.
            if (remap != null && quad.nominalFace() == Direction.UP && quad.tintIndex() >= 0) {
                TextureAtlasSprite from = remap[0], to = remap[1];
                float u0 = (quad.u(0) + quad.u(1) + quad.u(2) + quad.u(3)) * 0.25f;
                float v0 = (quad.v(0) + quad.v(1) + quad.v(2) + quad.v(3)) * 0.25f;
                if (u0 > from.getU0() && u0 < from.getU1() && v0 > from.getV0() && v0 < from.getV1()) {
                    float du = to.getU0() - from.getU0();
                    float dv = to.getV0() - from.getV0();
                    for (int i = 0; i < 4; i++) quad.uv(i, quad.u(i) + du, quad.v(i) + dv);
                }
            }
            return true;
        });
        super.emitQuads(emitter, world, pos, state, random, cullTest);
        emitter.popTransform();
    }
}
