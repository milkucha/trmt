package milkucha.trmt.client.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractWidget.class)
public interface ClickableWidgetPositionAccessor {
    @Accessor("x") void trmt$setX(int x);
    @Accessor("y") void trmt$setY(int y);
}
