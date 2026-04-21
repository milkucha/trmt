package milkucha.trmt.client.mixin;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Shadow private Text reason;
    @Unique private ButtonWidget trmt$downloadButton;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void trmt$addUpdateButton(CallbackInfo ci) {
        trmt$downloadButton = null;
        if (this.reason == null || !this.reason.getString().startsWith("[TRMT]")) return;
        for (Element child : this.children()) {
            if (!(child instanceof ButtonWidget backBtn)) continue;
            trmt$downloadButton = this.addDrawableChild(
                ButtonWidget.builder(
                    Text.literal("Download TRMT Update"),
                    btn -> Util.getOperatingSystem().open(URI.create(TRMTPackets.CURSEFORGE_URL))
                ).dimensions(backBtn.getX(), backBtn.getY() + 25, backBtn.getWidth(), 20).build()
            );
            return;
        }
    }

    // Sync position to the back button every frame so any resize is corrected immediately.
    @Inject(method = "render", at = @At("HEAD"))
    private void trmt$syncButtonPosition(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (trmt$downloadButton == null) return;
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget backBtn && backBtn != trmt$downloadButton) {
                ((ClickableWidgetPositionAccessor) trmt$downloadButton).trmt$setX(backBtn.getX());
                ((ClickableWidgetPositionAccessor) trmt$downloadButton).trmt$setY(backBtn.getY() + 25);
                return;
            }
        }
    }
}
