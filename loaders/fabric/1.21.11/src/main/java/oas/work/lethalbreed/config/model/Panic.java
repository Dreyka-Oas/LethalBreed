/**
 * Project: Lethal Breed
 * Responsibility: Zombie Panic and Fleeing Behavior
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.model;

public class Panic {
    public String _comment = "Fleeing and pack behavior when low health";
    public float healthThreshold = 0.25f;
    public float continueHealthThreshold = 0.5f;
    public int screamIntervalTicks = 40;
    public double allyAlertRange = 12.0;
    public int stopPackSize = 5;
    public int cooldownTicks = 600;
    public double fleeExplosionRange = 8.0;
}
