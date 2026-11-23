package milkucha.trmt.client.render;

import milkucha.trmt.block.ErodedGrassBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps the ErodedGrassBlock block-state models. The layer is set per block in TRMTNeoForgeClient
 * (CUTOUT, mipmapped by vanilla on this version), so it is the same with the mipmapFix setting on
 * and off. With the current atlas built with the copies (mipmapFix on at the last F3+T, see MipmapFix) the
 * position-aware collectParts swaps each part for a wrapper whose UP overlay quad is moved onto one
 * of MipmapFix.VARIANTS copies of its stage sprite, chosen from a position hash: level 0 of every
 * copy is identical, so up close nothing changes; far away the copies' different mip rounding
 * (SpriteContentsMixin) averages out to the stage's true grass coverage. Off: plain delegation.
 *
 * Snowy variants (eroded_grass_block_s*_snow) have no side overlay element, only the opaque
 * vanilla grass_block_snow side plus the dirt + eroded_top UP pair (same tinted stage sprite),
 * so the same layer and overlay-copy transform applies.
 */
public class ErodedGrassBlockModel implements BlockStateModel {

    private static final int VARIANTS = MipmapFix.VARIANTS;

    private final BlockStateModel wrapped;

    /**
     * [stage][variant] sprites, resolved on first use (a new model instance per resource reload).
     * Copies are null when the atlas was built without them (mipmapFix off at that reload).
     */
    private volatile TextureAtlasSprite[][] variantSprites;

    /** Wrapped parts per original part, index = copy. Per model instance, so rebuilt on reload. */
    private final ConcurrentHashMap<BlockModelPart, BlockModelPart[]> copyParts = new ConcurrentHashMap<>();

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> list) {
        wrapped.collectParts(random, list);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
        int start = parts.size();
        wrapped.collectParts(level, pos, state, random, parts);
        // Follows the current atlas (built with the copies or not), not the live setting: vanilla mips
        // CUTOUT, so leaving the rebuilt mips on copy 0 would look wrong. Changes apply on F3+T.
        if (!atlasHasCopies()) return;
        int v = MipmapFix.copyIndex(pos.getX(), pos.getY(), pos.getZ());
        if (v == 0) return; // copy 0 is the plain sprite, same layer
        if (!state.hasProperty(ErodedGrassBlock.STAGE)) return;
        int stage = state.getValue(ErodedGrassBlock.STAGE);
        if (stage < 0 || stage >= MipmapFix.STAGES) return;
        for (int i = start; i < parts.size(); i++) {
            parts.set(i, copyPart(parts.get(i), stage, v));
        }
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return wrapped.particleIcon();
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

    private BlockModelPart copyPart(BlockModelPart base, int stage, int v) {
        BlockModelPart[] byCopy = copyParts.computeIfAbsent(base, k -> new BlockModelPart[VARIANTS]);
        BlockModelPart p = byCopy[v];
        if (p == null) {
            p = buildCopyPart(base, stage, v);
            byCopy[v] = p;
        }
        return p;
    }

    private BlockModelPart buildCopyPart(BlockModelPart base, int stage, int v) {
        TextureAtlasSprite to = variantSprites()[stage][v];
        if (to == null) return base;
        Identifier plain = MipmapFix.spriteId(stage, 0);
        List<BakedQuad> quads = base.getQuads(Direction.UP);
        List<BakedQuad> up = new ArrayList<>(quads.size());
        boolean changed = false;
        for (BakedQuad q : quads) {
            // The tinted top overlay (not the dirt base): pure UV translation from the plain
            // stage sprite onto this copy, so the FACING rotation baked into the UVs is kept.
            if (q.direction() == Direction.UP && q.isTinted() && q.sprite().contents().name().equals(plain)) {
                TextureAtlasSprite from = q.sprite();
                float du = to.getU0() - from.getU0();
                float dv = to.getV0() - from.getV0();
                up.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                        shift(q.packedUV0(), du, dv), shift(q.packedUV1(), du, dv),
                        shift(q.packedUV2(), du, dv), shift(q.packedUV3(), du, dv),
                        q.tintIndex(), q.direction(), to, q.shade(), q.lightEmission(),
                        q.bakedNormals(), q.bakedColors(), q.hasAmbientOcclusion()));
                changed = true;
            } else {
                up.add(q);
            }
        }
        return changed ? new CopyPart(base, up) : base;
    }

    private static long shift(long packed, float du, float dv) {
        return UVPair.pack(UVPair.unpackU(packed) + du, UVPair.unpackV(packed) + dv);
    }

    /** The original part, except that its UP quads are the copy-sprite ones. */
    private record CopyPart(BlockModelPart base, List<BakedQuad> up) implements BlockModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == Direction.UP ? up : base.getQuads(direction);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public TextureAtlasSprite particleIcon() {
            return base.particleIcon();
        }

        @Override
        public ChunkSectionLayer getRenderType(BlockState state) {
            return base.getRenderType(state);
        }

        @Override
        public TriState ambientOcclusion() {
            return base.ambientOcclusion();
        }
    }
}
