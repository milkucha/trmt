package milkucha.trmt.path;

/** Implemented (by mixin) on {@code ServerPlayerEntity} so other mixins can reach the player's path speed buff state. */
public interface PathBuffHolder {
    PathSpeedBuff.State trmt$getPathBuff();
}
