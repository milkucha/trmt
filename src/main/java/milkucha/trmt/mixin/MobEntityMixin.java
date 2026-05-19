package milkucha.trmt.mixin;

import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTEffects;
import milkucha.trmt.erosion.ErodingEntity;
import milkucha.trmt.erosion.ErosionLogic;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MobEntity.class)
public class MobEntityMixin implements ErodingEntity {

    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Override
    public void trmt$erodeGround() {
        MobEntity mob = (MobEntity) (Object) this;

        if (!mob.isOnGround()) {
            trmt$lastGroundPos = null;
            return;
        }

        if (!mob.isLeashed()) return;

        BlockPos groundPos = ErosionLogic.getGroundPos(mob);
        if (groundPos.equals(trmt$lastGroundPos)) return;
        trmt$lastGroundPos = groundPos.toImmutable();

        if (mob.hasStatusEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        float mult = TRMTConfig.get().erosionMultipliers.player
                * TRMTConfig.get().erosionMultipliers.leash;

        World world = mob.getWorld();
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ErosionLogic.stepGround(world, manager, groundPos, 1.0f * mult, world.getTime());
    }
}
