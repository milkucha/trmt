package milkucha.trmt.mixin;

import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.util.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@OnlyIn(Dist.CLIENT)
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
        if (this.details == null) return;
        ComponentContents contents = this.details.reason().getContents();
        if (!(contents instanceof TranslatableContents tc)) return;
        if (!tc.getKey().equals("trmt.disconnect.outdated")) return;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button backBtn)) continue;
            trmt$backButton = backBtn;
            trmt$downloadButton = this.addRenderableWidget(
                Button.builder(
                    Component.translatable("trmt.button.download_update"),
                    btn -> Util.getPlatform().openUri(URI.create(TRMTNetwork.MODRINTH_URL))
                ).pos(backBtn.getX(), backBtn.getY() + 25).width(backBtn.getWidth()).build()
            );
            return;
        }
    }

    @Inject(method = "repositionElements()V", at = @At("TAIL"))
    private void trmt$repositionUpdateButton(CallbackInfo ci) {
        if (trmt$downloadButton == null || trmt$backButton == null) return;
        trmt$downloadButton.setPosition(trmt$backButton.getX(), trmt$backButton.getY() + 25);
    }
}
