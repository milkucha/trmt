package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Clears a block's erosion entry when a player breaks it.
 *
 * NeoForge 26.1.2.21-beta renamed {@code BlockEvent.BreakEvent} to
 * {@code event.level.block.BreakBlockEvent} (neoforged/NeoForge#3064). To run on every NeoForge 26.1.x
 * from one jar, neither class is linked directly: the one present is looked up by name and registered
 * with a handler typed as their common base {@link BlockEvent}. The new event also fires on the
 * client, hence the {@code isClientSide()} guard.
 */
final class TRMTBlockBreakListener {

    private static final String BREAK_BLOCK_EVENT = "net.neoforged.neoforge.event.level.block.BreakBlockEvent";
    private static final String LEGACY_BREAK_EVENT = "net.neoforged.neoforge.event.level.BlockEvent$BreakEvent";

    private TRMTBlockBreakListener() {}

    /** Called once from the mod constructor (runs on both the client and the dedicated server). */
    static void register() {
        Class<BlockEvent> type = find(BREAK_BLOCK_EVENT);
        if (type == null) type = find(LEGACY_BREAK_EVENT);
        if (type == null) {
            TRMTNeoForge.LOGGER.error("[TRMT] No block-break event found; erosion will not be cleared on block break");
            return;
        }
        NeoForge.EVENT_BUS.addListener(type, TRMTBlockBreakListener::onBlockBreak);
    }

    @SuppressWarnings("unchecked")
    private static Class<BlockEvent> find(String name) {
        try {
            return (Class<BlockEvent>) Class.forName(name).asSubclass(BlockEvent.class);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void onBlockBreak(BlockEvent event) {
        // Server side only; per-dimension manager from the event's level (B1).
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            ErosionMapManager.get(level).removeEntry(event.getPos());
        }
    }
}
