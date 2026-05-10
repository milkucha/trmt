package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SinglePlayerErosionTracker {

    private final Map<UUID, PlayerTrace> traces = new HashMap<>();

    public void tick(MinecraftServer server, ErosionMapManager manager) {
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                UUID id = player.getUUID();
                seen.add(id);
                tickPlayer(level, player, manager, traces.computeIfAbsent(id, key -> new PlayerTrace()));
            }
        }
        traces.keySet().removeIf(id -> !seen.contains(id));
    }

    public void clear() {
        traces.clear();
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, ErosionMapManager manager, PlayerTrace trace) {
        Entity mover = player.getVehicle() != null ? player.getVehicle() : player;
        if (player.isSpectator() || !mover.onGround()) {
            trace.reset(mover.getX(), mover.getZ());
            return;
        }

        double x = mover.getX();
        double z = mover.getZ();
        double dx = x - trace.lastX;
        double dz = z - trace.lastZ;
        double distance = trace.initialized ? Math.sqrt(dx * dx + dz * dz) : 0.0;
        trace.reset(x, z);

        if (distance < 0.003) return;

        BlockPos groundPos = mover.blockPosition().below();
        BlockState aboveGround = level.getBlockState(groundPos.above());
        if (aboveGround.is(TRMTBlocks.ERODED_SAND) || aboveGround.is(Blocks.SAND)) {
            groundPos = groundPos.above();
        }

        float amount = (float) distance
                * TRMTConfig.get().erosionMultipliers.player
                * TRMTConfig.get().erosionMultipliers.singlePlayerDistance;
        if (player.getVehicle() != null) {
            amount *= TRMTConfig.get().erosionMultipliers.mounted;
        }

        long gameTime = level.getGameTime();
        manager.applyWear(level, groundPos, amount, gameTime);

        BlockPos forward = groundPos.relative(player.getDirection());
        manager.applyWear(level, forward, amount * 0.25f, gameTime);
    }

    private static final class PlayerTrace {
        private boolean initialized;
        private double lastX;
        private double lastZ;

        private void reset(double x, double z) {
            initialized = true;
            lastX = x;
            lastZ = z;
        }
    }
}
