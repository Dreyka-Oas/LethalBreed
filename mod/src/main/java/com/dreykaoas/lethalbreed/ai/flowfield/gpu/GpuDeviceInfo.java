package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;

import java.nio.charset.StandardCharsets;

import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.clGetDeviceInfo;

/**
 * OpenCL device-info queries used by {@link GpuContext} to name and size-check a device. Each query degrades
 * to a safe fallback rather than throwing, so a driver missing an optional param never aborts device pick.
 */
final class GpuDeviceInfo {
    /** AMD extension: the commercial board name (e.g. "AMD Radeon RX 9060 XT") vs CL_DEVICE_NAME ("gfx1200"). */
    private static final int CL_DEVICE_BOARD_NAME_AMD = 0x4038;

    private GpuDeviceInfo() {}

    /** Best human-readable name: the AMD marketing board name when present, else the short arch name. */
    static String name(cl_device_id device) {
        String board = queryString(device, CL_DEVICE_BOARD_NAME_AMD); // prefer the marketing name
        if (board != null && !board.isEmpty()) {
            return board;
        }
        String name = queryString(device, CL_DEVICE_NAME); // fallback: short arch name (gfx1200)
        return name == null ? "GPU" : name;
    }

    /** Query CL_DEVICE_MAX_WORK_GROUP_SIZE (a size_t). Falls back to a conservative 256 if the query fails
     *  or reports a nonsense value, so the solver still has a safe upper bound to validate against. */
    static long maxWorkGroupSize(cl_device_id device) {
        try {
            long[] out = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(out), null);
            return out[0] > 0 ? out[0] : 256L;
        } catch (Throwable t) {
            return 256L;
        }
    }

    /** Query a string device-info param; returns null if the param is unsupported by the driver. */
    private static String queryString(cl_device_id device, int param) {
        try {
            long[] size = new long[1];
            clGetDeviceInfo(device, param, 0, null, size);
            if (size[0] <= 0) {
                return null;
            }
            byte[] buffer = new byte[(int) size[0]];
            clGetDeviceInfo(device, param, buffer.length, Pointer.to(buffer), null);
            return new String(buffer, 0, Math.max(0, buffer.length - 1), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null; // extension not supported on this device/driver
        }
    }
}
