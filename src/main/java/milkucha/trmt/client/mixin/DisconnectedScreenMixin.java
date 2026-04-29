package milkucha.trmt.client.mixin;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Final @Shadow private DisconnectionDetails details;
    @Unique private Button trmt$backButton;
    @Unique private Button trmt$downloadButton;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void trmt$addUpdateButton(CallbackInfo ci) {
        trmt$backButton = null;
        trmt$downloadButton = null;
        if (this.details == null || !this.details.reason().getString().startsWith("The Roads More Travelled")) return;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button backBtn)) continue;
            trmt$backButton = backBtn;
            trmt$downloadButton = this.addRenderableWidget(
                Button.builder(
                    Component.literal("Download Mod Update"),
                    btn -> Util.getPlatform().openUri(URI.create(TRMTPackets.MODRINTH_URL))
                ).bounds(backBtn.getX(), backBtn.getY() + 25, backBtn.getWidth(), 20).build()
            );
            return;
        }
    }

    // repositionElements() is called after init() and after every resize (via rebuildWidgets).
    // By this point the layout has set final positions, so we correct our button here.
    @Inject(method = "repositionElements()V", at = @At("TAIL"))
    private void trmt$repositionUpdateButton(CallbackInfo ci) {
        if (trmt$downloadButton == null || trmt$backButton == null) return;
        trmt$downloadButton.setPosition(trmt$backButton.getX(), trmt$backButton.getY() + 25);
    }

}
