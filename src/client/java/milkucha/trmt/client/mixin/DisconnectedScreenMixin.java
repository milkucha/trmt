package milkucha.trmt.client.mixin;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.awt.Desktop;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Shadow protected Component message;
    @Unique private Button trmt$downloadButton;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void trmt$addUpdateButton(CallbackInfo ci) {
        trmt$downloadButton = null;
        if (this.message == null || !this.message.getString().startsWith("The Roads More Travelled")) return;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button backBtn)) continue;
            trmt$downloadButton = this.addRenderableWidget(
                Button.builder(
                    Component.literal("Download Mod Update"),
                    btn -> trmt$openModrinth()
                ).bounds(backBtn.getX(), backBtn.getY() + 25, backBtn.getWidth(), 20).build()
            );
            return;
        }
    }

    @Unique
    private static void trmt$openModrinth() {
        try {
            Desktop.getDesktop().browse(URI.create(TRMTPackets.MODRINTH_URL));
        } catch (Exception ignored) {
        }
    }
}
