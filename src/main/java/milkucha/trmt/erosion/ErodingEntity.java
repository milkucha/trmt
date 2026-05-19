package milkucha.trmt.erosion;

public interface ErodingEntity {

    default void trmt$erodeGround() {
    }

    default float trmt$passengerErosionMultiplier() {
        return 0.0f;
    }
}
