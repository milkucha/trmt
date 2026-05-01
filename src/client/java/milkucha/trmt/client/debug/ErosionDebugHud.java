package milkucha.trmt.client.debug;

/**
 * Debug HUD stubbed out during the 1.21.11 update.
 * The original implementation depended on the pre-1.21.4 BakedModel API and the
 * MatrixStack-based DrawContext, both of which were removed by Mojang.
 * The HUD is a developer tool with no gameplay impact; restoring it would require
 * reimplementing against the new model + Matrix3x2f rendering APIs.
 */
public final class ErosionDebugHud {
    private ErosionDebugHud() {}
    public static void register() {}
}
