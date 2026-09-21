package milkucha.trmt.client.mixin;

import milkucha.trmt.network.TRMTPackets;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    // 26.3: DisconnectedScreen no longer stores a plain "reason" Component field; the disconnect
    // reason is now wrapped in a DisconnectionDetails record, accessed via details.reason().
    @Shadow private DisconnectionDetails details;
    @Unique private Button trmt$downloadButton;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void trmt$addUpdateButton(CallbackInfo ci) {
        trmt$downloadButton = null;
        if (this.details == null
                || !(this.details.reason().getContents() instanceof TranslatableContents tc)
                || !tc.getKey().startsWith("trmt.disconnect")) return;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button backBtn)) continue;
            trmt$downloadButton = this.addRenderableWidget(
                Button.builder(
                    Component.translatable("trmt.button.download_update"),
                    // 26.3: Util.getPlatform().openUri(URI) was removed; use Blaze3D.openUri(URI) instead.
                    btn -> Blaze3D.openUri(URI.create(TRMTPackets.MODRINTH_URL))
                ).bounds(backBtn.getX(), backBtn.getY() + 25, backBtn.getWidth(), 20).build()
            );
            return;
        }
    }
}
