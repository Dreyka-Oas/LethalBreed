package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.setExceptionsEnabled;

/**
 * OpenCL (JOCL) device pick + context/queue/kernel build. Prefers an AMD/Radeon GPU, falls back to
 * the first GPU device found, then builds the {@code bellman_ford.cl} kernel. Construction throws on
 * any failure so the caller can degrade to the CPU solver — the GPU is never load-bearing.
 */
final class GpuContext {
    /** AMD extension: the commercial board name (e.g. "AMD Radeon RX 9060 XT") vs CL_DEVICE_NAME ("gfx1200"). */
    private static final int CL_DEVICE_BOARD_NAME_AMD = 0x4038;

    final cl_context context;
    final cl_command_queue queue;
    final cl_kernel kernel;
    final cl_program program;
    final String deviceName;
    /** CL_DEVICE_MAX_WORK_GROUP_SIZE of the chosen device — the largest legal local work-group size.
     *  Used by the solver to reject an over-large/illegal gpuWorkgroupSize and let the driver pick instead. */
    final long maxWorkGroupSize;

    GpuContext() {
        setExceptionsEnabled(true);

        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        if (numPlatforms[0] == 0) {
            throw new IllegalStateException("no OpenCL platforms");
        }
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        // Enumerate every GPU device across all platforms into one flat, index-stable list.
        List<cl_platform_id> platList = new ArrayList<>();
        List<cl_device_id> devList = new ArrayList<>();
        List<String> nameList = new ArrayList<>();
        for (cl_platform_id platform : platforms) {
            int[] numDevices = new int[1];
            try {
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, numDevices);
            } catch (Exception ignored) {
                continue;
            }
            if (numDevices[0] == 0) {
                continue;
            }
            cl_device_id[] devices = new cl_device_id[numDevices[0]];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices.length, devices, null);
            for (cl_device_id device : devices) {
                platList.add(platform);
                devList.add(device);
                nameList.add(queryDeviceName(device));
            }
        }
        if (devList.isEmpty()) {
            throw new IllegalStateException("no OpenCL GPU device");
        }
        for (int i = 0; i < nameList.size(); i++) {
            LethalBreed.LOGGER.info("[LethalBreed] GPU[{}] = {}", i, nameList.get(i));
        }

        // Pick: an explicit, in-range gpuDeviceIndex wins; otherwise auto — prefer AMD/Radeon, else device 0.
        int want = FlowConfig.gpuDeviceIndex;
        int chosen;
        if (want >= 0 && want < devList.size()) {
            chosen = want;
        } else {
            chosen = 0;
            for (int i = 0; i < nameList.size(); i++) {
                String upper = nameList.get(i).toUpperCase();
                if (upper.contains("AMD") || upper.contains("RADEON")) {
                    chosen = i;
                    break;
                }
            }
        }
        cl_platform_id chosenPlatform = platList.get(chosen);
        cl_device_id chosenDevice = devList.get(chosen);
        String chosenName = nameList.get(chosen);

        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, chosenPlatform);
        this.context = clCreateContext(props, 1, new cl_device_id[]{chosenDevice}, null, null, null);
        this.queue = clCreateCommandQueueWithProperties(context, chosenDevice, new cl_queue_properties(), null);

        String source = loadKernelSource();
        this.program = clCreateProgramWithSource(context, 1, new String[]{source}, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        this.kernel = clCreateKernel(program, "relax_step", null);
        this.deviceName = chosenName;
        this.maxWorkGroupSize = queryMaxWorkGroupSize(chosenDevice);
    }

    /** Query CL_DEVICE_MAX_WORK_GROUP_SIZE (a size_t). Falls back to a conservative 256 if the query fails
     *  or reports a nonsense value, so the solver still has a safe upper bound to validate against. */
    private static long queryMaxWorkGroupSize(cl_device_id device) {
        try {
            long[] out = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(out), null);
            return out[0] > 0 ? out[0] : 256L;
        } catch (Throwable t) {
            return 256L;
        }
    }

    private static String queryDeviceName(cl_device_id device) {
        String board = queryStringInfo(device, CL_DEVICE_BOARD_NAME_AMD); // prefer the marketing name
        if (board != null && !board.isEmpty()) {
            return board;
        }
        String name = queryStringInfo(device, CL_DEVICE_NAME); // fallback: short arch name (gfx1200)
        return name == null ? "GPU" : name;
    }

    /** Query a string device-info param; returns null if the param is unsupported by the driver. */
    private static String queryStringInfo(cl_device_id device, int param) {
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

    private static String loadKernelSource() {
        try (var in = GpuContext.class.getResourceAsStream("/kernels/bellman_ford.cl")) {
            if (in == null) {
                throw new IllegalStateException("kernel resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("kernel load failed", e);
        }
    }
}
