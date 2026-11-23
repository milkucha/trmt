package milkucha.trmt.client.render;

import milkucha.trmt.block.ErodedGrassBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the ErodedGrassBlock block-state models. Quad layers are baked per quad (the tinted
 * overlays are CUTOUT, which vanilla mipmaps on this version), so the layer is the same with the
 * mipmapFix setting on and off. With the current atlas built with the copies (mipmapFix on at
 * the last F3+T, see MipmapFix) the position-aware collectParts swaps each part for a wrapper whose UP overlay
 * quad is moved onto one of MipmapFix.VARIANTS copies of its stage sprite, chosen from a position
 * hash: level 0 of every copy is identical, so up close nothing changes; far away the copies'
 * different mip rounding (SpriteContentsMixin) averages out to the stage's true grass coverage.
 * Off: plain delegation.
 *
 * Snowy variants (eroded_grass_block_s*_snow) have no side overlay element, only the opaque
 * vanilla grass_block_snow side plus the dirt + eroded_top UP pair (same tinted stage sprite),
 * so the same layer and overlay-copy transform applies.
 */
public class ErodedGrassBlockModel extends DelegateBlockStateModel {

    private static final int VARIANTS = MipmapFix.VARIANTS;

    /**
     * [stage][variant] sprites, resolved on first use (a new model instance per resource reload).
     * Copies are null when the atlas was built without them (mipmapFix off at that reload).
     */
    private volatile TextureAtlasSprite[][] variantSprites;

    /**
     * Wrapped parts per original part, index = copy. Keyed by part identity (parts may be records
     * holding big quad lists, so equality-based keys would hash/compare all of them), guarded by
     * its own monitor. Per model instance, so rebuilt on reload.
     */
    private final Map<BlockStateModelPart, BlockStateModelPart[]> copyParts = new IdentityHashMap<>();

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int start = parts.size();
        super.collectParts(level, pos, state, random, parts);
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

    private BlockStateModelPart copyPart(BlockStateModelPart base, int stage, int v) {
        synchronized (copyParts) {
            BlockStateModelPart[] byCopy = copyParts.computeIfAbsent(base, k -> new BlockStateModelPart[VARIANTS]);
            BlockStateModelPart p = byCopy[v];
            if (p == null) {
                p = buildCopyPart(base, stage, v);
                byCopy[v] = p;
            }
            return p;
        }
    }

    private BlockStateModelPart buildCopyPart(BlockStateModelPart base, int stage, int v) {
        TextureAtlasSprite to = variantSprites()[stage][v];
        if (to == null) return base;
        Identifier plain = MipmapFix.spriteId(stage, 0);
        List<BakedQuad> quads = base.getQuads(Direction.UP);
        List<BakedQuad> up = new ArrayList<>(quads.size());
        boolean changed = false;
        for (BakedQuad q : quads) {
            BakedQuad.MaterialInfo mi = q.materialInfo();
            // The tinted top overlay (not the dirt base): pure UV translation from the plain
            // stage sprite onto this copy, so the FACING rotation baked into the UVs is kept.
            if (q.direction() == Direction.UP && mi.isTinted() && mi.sprite().contents().name().equals(plain)) {
                TextureAtlasSprite from = mi.sprite();
                float du = to.getU0() - from.getU0();
                float dv = to.getV0() - from.getV0();
                // Same layer (CUTOUT), tint, shade, emission and AO flag; only the sprite changes.
                BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(to, mi.layer(), mi.itemRenderType(),
                        mi.tintIndex(), mi.shade(), mi.lightEmission(), mi.ambientOcclusion());
                up.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                        shift(q.packedUV0(), du, dv), shift(q.packedUV1(), du, dv),
                        shift(q.packedUV2(), du, dv), shift(q.packedUV3(), du, dv),
                        q.direction(), info, q.bakedNormals(), q.bakedColors()));
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
    private record CopyPart(BlockStateModelPart base, List<BakedQuad> up) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == Direction.UP ? up : base.getQuads(direction);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return base.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return base.materialFlags();
        }

        @Override
        public TriState ambientOcclusion() {
            return base.ambientOcclusion();
        }
    }
}
