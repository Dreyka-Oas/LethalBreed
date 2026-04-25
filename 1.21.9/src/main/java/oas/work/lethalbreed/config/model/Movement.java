package oas.work.lethalbreed.config.model;

public class Movement {
    public String _comment = "Climbing and building speeds";
    public double climbVerticalSpeed = 0.25;
    public double climbHorizontalSpeed = 0.15;
    public int buildGlobalCooldownTicks = 4;
    public TemporaryBlocks temporaryBlocks = new TemporaryBlocks();

    public static class TemporaryBlocks {
        public String _comment = "Blocks placed by zombies will disappear after a certain time";
        public boolean enabled = true;
        public int decayTicks = 600;
    }
}






