package milkucha.trmt.client.mixin;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
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

    @Final @Shadow private DisconnectionInfo info;
    @Unique private ButtonWidget trmt$backButton;
    @Unique private ButtonWidget trmt$downloadButton;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    // Creates the button and stores a reference to the Back button.
    // Position may be pre-layout at this point; initTabNavigation() corrects it.
    @Inject(method = "init()V", at = @At("TAIL"))
    private void trmt$addUpdateButton(CallbackInfo ci) {
        trmt$backButton = null;
        trmt$downloadButton = null;
        if (this.info == null || !this.info.reason().getString().startsWith("The Roads More Travelled")) return;
        for (Element child : this.children()) {
            if (!(child instanceof ButtonWidget backBtn)) continue;
            trmt$backButton = backBtn;
            trmt$downloadButton = this.addDrawableChild(
                ButtonWidget.builder(
                    Text.literal("Download Mod Update"),
                    btn -> Util.getOperatingSystem().open(URI.create(TRMTPackets.MODRINTH_URL))
                ).dimensions(backBtn.getX(), backBtn.getY() + 25, backBtn.getWidth(), 20).build()
            );
            return;
        }
    }

    // refreshWidgetPositions() (formerly initTabNavigation in pre-1.21.11) is called after init()
    // and after every resize (via clearAndInit). By this point the layout has set final positions,
    // so we correct our button here.
    @Inject(method = "refreshWidgetPositions()V", at = @At("TAIL"))
    private void trmt$repositionUpdateButton(CallbackInfo ci) {
        if (trmt$downloadButton == null || trmt$backButton == null) return;
        trmt$downloadButton.setPosition(trmt$backButton.getX(), trmt$backButton.getY() + 25);
    }

}
