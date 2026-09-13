package oas.dreyka.lethalbreed.ai.flowfield.gpu;

import java.util.List;
import java.util.Locale;

/**
 * Which enumerated OpenCL device the solver runs on.
 *
 * <p>Its own class, taking names and widths rather than {@code cl_device_id}, because the rule is worth a
 * test and an OpenCL handle is not something a test can hold. {@link GpuContext} does the querying.
 *
 * <p>The rule it replaces was "the first device whose name says AMD". On a machine with an integrated
 * Radeon beside a discrete one that is not a choice, it is whatever order the driver enumerated, and it
 * decides where every flow field in the game is solved.
 */
final class GpuDevicePick {
    private GpuDevicePick() {}

    /**
     * @param names       device names, in enumeration order
     * @param computeUnits CL_DEVICE_MAX_COMPUTE_UNITS per device, same order. 0 where the query failed,
     *                     which simply makes that device the least attractive rather than unusable
     * @param wantIndex   {@code gpuDeviceIndex}: taken as-is when it points at a real device, so an operator
     *                    can always overrule the rule below on a machine it guesses wrong about
     * @return the index to open
     */
    static int choose(List<String> names, int[] computeUnits, int wantIndex) {
        if (wantIndex >= 0 && wantIndex < names.size()) {
            return wantIndex;
        }
        // Vendor first, then width. Preferring AMD is this project's own hardware talking, and it stays
        // ahead of the width so a wide integrated device from another vendor cannot take the discrete card's
        // place; among equals, the widest device is the discrete one on every machine seen so far.
        int best = 0;
        for (int i = 1; i < names.size(); i++) {
            if (better(names, computeUnits, i, best)) {
                best = i;
            }
        }
        return best;
    }

    private static boolean better(List<String> names, int[] units, int candidate, int incumbent) {
        boolean candidateAmd = isAmd(names.get(candidate));
        boolean incumbentAmd = isAmd(names.get(incumbent));
        if (candidateAmd != incumbentAmd) {
            return candidateAmd;
        }
        return width(units, candidate) > width(units, incumbent);
    }

    private static int width(int[] units, int i) {
        return i < units.length ? units[i] : 0;
    }

    private static boolean isAmd(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("AMD") || upper.contains("RADEON");
    }
}
