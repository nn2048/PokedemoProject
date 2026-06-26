package win.pokedemo;

public record SpeciesMovementProfile(
        boolean canWalk,
        boolean canFly,
        boolean canSwimInWater,
        boolean canBreatheUnderwater,
        boolean canWalkOnWater,
        boolean canSwimInLava,
        boolean canWalkOnLava,
        boolean avoidsWater,
        boolean avoidsLand,
        double walkSpeed,
        double swimSpeed,
        double flySpeed,
        double hoverHeight,
        boolean canSleep,
        boolean sleepAnyTime
) {
    public static final SpeciesMovementProfile DEFAULT = new SpeciesMovementProfile(
            true, false, true, false, false, false, false, false, false,
            0.23D, 0.30D, 0.30D, 1.15D, false, false
    );

    public double clampedWalkSpeed() { return clamp(walkSpeed, 0.08D, 0.60D); }
    public double clampedSwimSpeed() { return clamp(swimSpeed, 0.08D, 0.80D); }
    public double clampedFlySpeed() { return clamp(flySpeed, 0.08D, 1.10D); }
    public double clampedHoverHeight() { return clamp(hoverHeight, 0.6D, 3.0D); }

    public double mappedWalkAttribute() {
        double v = clampedWalkSpeed();
        return clamp(0.12D + (v * 0.45D), 0.16D, 0.34D);
    }

    public double mappedFlyMotion() {
        double v = clampedFlySpeed();
        return clamp(0.05D + (v * 0.28D), 0.09D, 0.26D);
    }

    public double mappedSwimMotion() {
        double v = clampedSwimSpeed();
        return clamp(0.04D + (v * 0.22D), 0.08D, 0.20D);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
