package milkucha.trmt.path;

import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TestLog;
import milkucha.trmt.path.PathDetector.PathSample;
import milkucha.trmt.path.PathDetector.PathWeights;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Path speed buff: a mild, tiered movement-speed bonus for a player (or the mount they ride) walking on a
 * path of TRMT-eroded blocks. Evaluated once per new ground block (called from {@code ServerPlayerEntityMixin});
 * the bonus is a transient attribute modifier, never saved, so it cannot outlive the mod.
 */
public final class PathSpeedBuff {

    /** Fixed id of the modifier (the client debug HUD reads it too). */
    public static final UUID MODIFIER_ID = UUID.fromString("39251b3b-b47c-4be5-b962-1fe3efaf4136");
    public static final String MODIFIER_NAME = "trmt:path_speed_buff";

    private static volatile PathWeights weights = new PathWeights(new TRMTConfig.PathBlockWeights());

    private PathSpeedBuff() {}

    /** Per-player evaluation state, owned by {@code ServerPlayerEntityMixin}. */
    public static final class State {
        /** 0 = no buff. */
        int tier;
        /** Consecutive evaluations that failed {@code gateKeep}. */
        int misses;
        /** The entity (the player or the mount they control) currently carrying the modifier; null when none. */
        LivingEntity target;
    }

    /** Rebuilds the weight lookup from the active config (config load and {@code /trmt reloadconfig}). */
    public static void rebuildWeights(TRMTConfig.PathSpeedBuffConfig cfg) {
        weights = new PathWeights(cfg.weights);
    }

    /**
     * Per-tick safety net behind the dismount hook ({@code EntityMixin}): if the entity carrying the buff is
     * no longer the one the player stands on, is in another world, or the player is dead, take the modifier
     * off it. A few reference compares per tick, no block reads.
     */
    public static void tickCheck(ServerPlayerEntity player, State state) {
        LivingEntity target = state.target;
        if (target != null && (!player.isAlive()
                || (target != player && target != player.getVehicle())
                || target.getWorld() != player.getWorld())) {
            release(state);
        }
    }

    /** Takes the buff off whatever carries it and resets the state (dismount, target change). */
    public static void release(State state) {
        LivingEntity target = state.target;
        if (target != null) reconcile(target, 0);
        state.target = null;
        state.tier = 0;
        state.misses = 0;
    }

    /** Dismount hook: release if {@code mount} is the entity this player's buff sits on. */
    public static void onDismount(State state, Entity mount) {
        if (mount != null && state.target == mount) release(state);
    }

    /**
     * Evaluates the spot the player (or the mount they control) just stepped onto, and applies or removes the
     * modifier. Independent of the erosion toggles, sneaking and Lightness.
     */
    public static void onNewGroundBlock(ServerPlayerEntity player, Entity vehicle, State state) {
        TRMTConfig.PathSpeedBuffConfig cfg = TRMTConfig.get().pathSpeedBuff;

        // Who carries the buff: the player on foot, the living mount they control, nothing otherwise
        // (boats, minecarts, a second rider of a camel: the first rider owns the mount's modifier).
        LivingEntity target = null;
        if (vehicle == null) target = player;
        else if (vehicle instanceof LivingEntity mount && mount.getControllingPassenger() == player) target = mount;

        if (state.target != null && state.target != target) release(state);

        int oldTier = state.tier;
        PathSample sample = null;
        if (!cfg.enabled || target == null) {
            state.tier = 0;
            state.misses = 0;
        } else {
            sample = PathDetector.sample(player.getWorld(), BlockPos.ofFloored(target.getPos()), weights);
            float density = sample.density();
            if (state.tier == 0) {
                if (density >= cfg.gateEnter) {
                    state.tier = PathDetector.nextTier(0, sample.avgDepth(), cfg);
                    state.misses = 0;
                }
            } else if (density >= cfg.gateKeep) {
                state.misses = 0;
                state.tier = PathDetector.nextTier(state.tier, sample.avgDepth(), cfg);
            } else if (++state.misses >= cfg.graceBlocks) {
                state.tier = 0;
                state.misses = 0;
            }
        }

        // Keep the player's own modifier off while they ride (also covers a stale one copied on respawn).
        if (target != player) reconcile(player, 0);
        if (target != null) reconcile(target, percentToValue(PathDetector.tierPercent(state.tier, cfg)));
        state.target = state.tier > 0 ? target : null;

        if (state.tier != oldTier && TestLog.enabled()) {
            final int tier = state.tier;
            final PathSample s = sample;
            final LivingEntity t = target;
            TestLog.line(() -> "path-buff player=" + player.getName().getString()
                    + " target=" + (t == null ? "none" : t == player ? "player" : "mount:" + Registries.ENTITY_TYPE.getId(t.getType()))
                    + " tier=" + tier + " percent=" + PathDetector.tierPercent(tier, cfg)
                    + " count=" + (s == null ? 0 : s.count()) + " depth=" + (s == null ? 0f : s.avgDepth())
                    + " misses=" + state.misses);
        }
    }

    /** Tier percent to the modifier value, rounded to 0.01 % so e.g. 4.1f does not become 0.040999999. */
    private static double percentToValue(float percent) {
        return Math.round(percent * 100f) / 10000.0;
    }

    /**
     * Makes the movement-speed attribute of {@code e} carry exactly the modifier value {@code desired}
     * (0 = none). Compares with the real attribute rather than a cached field, so it is idempotent and
     * also repairs a stale modifier. Transient only, never persistent.
     */
    private static void reconcile(LivingEntity e, double desired) {
        EntityAttributeInstance inst = e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;
        EntityAttributeModifier cur = inst.getModifier(MODIFIER_ID);
        if (desired == 0.0) {
            if (cur != null) inst.removeModifier(MODIFIER_ID);
            return;
        }
        if (cur == null || cur.getValue() != desired) {
            inst.removeModifier(MODIFIER_ID);
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    MODIFIER_ID, MODIFIER_NAME, desired, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
