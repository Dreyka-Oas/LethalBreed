package com.dreykaoas.lethalbreed.gpu;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import com.dreykaoas.lethalbreed.ai.flowfield.CpuFlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import java.nio.charset.StandardCharsets;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.setExceptionsEnabled;

/**
 * OpenCL (JOCL) compute backend for the flow field — Phase 6, <b>benchmark-gated</b>. Initialized
 * lazily only when {@code useGpu} is enabled. Detects an AMD GPU (any model), builds the
 * {@code bellman_ford.cl} kernel, and solves a flow-field snapshot on the GPU. Every failure path
 * degrades to the CPU solver, so enabling the GPU can never break the game — at worst it is no win.
 *
 * <p>CPU stays the master path; the GPU is only used when a benchmark proves it helps.
 */
public final class GpuComputeManager {
    private static final GpuComputeManager INSTANCE = new GpuComputeManager();

    public static GpuComputeManager get() {
        return INSTANCE;
    }

    private boolean initialized = false;
    private boolean available = false;
    private String deviceName = "none";
    private boolean fallbackLogged = false;

    private cl_context context;
    private cl_command_queue queue;
    private cl_kernel kernel;
    private cl_program program;

    private GpuComputeManager() {}

    public synchronized boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    public String deviceName() {
        return deviceName;
    }

    private void init() {
        initialized = true;
        try {
            setExceptionsEnabled(true);

            int[] numPlatforms = new int[1];
            clGetPlatformIDs(0, null, numPlatforms);
            if (numPlatforms[0] == 0) {
                throw new IllegalStateException("no OpenCL platforms");
            }
            cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
            clGetPlatformIDs(platforms.length, platforms, null);

            cl_platform_id chosenPlatform = null;
            cl_device_id chosenDevice = null;
            String chosenName = null;
            cl_platform_id firstPlatform = null;
            cl_device_id firstDevice = null;
            String firstName = null;

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
                    String name = queryDeviceName(device);
                    if (firstDevice == null) {
                        firstPlatform = platform;
                        firstDevice = device;
                        firstName = name;
                    }
                    String upper = name.toUpperCase();
                    if (upper.contains("AMD") || upper.contains("RADEON")) {
                        chosenPlatform = platform;
                        chosenDevice = device;
                        chosenName = name;
                        break;
                    }
                }
                if (chosenDevice != null) {
                    break;
                }
            }

            if (chosenDevice == null) {
                chosenPlatform = firstPlatform;
                chosenDevice = firstDevice;
                chosenName = firstName;
            }
            if (chosenDevice == null) {
                throw new IllegalStateException("no OpenCL GPU device");
            }

            cl_context_properties props = new cl_context_properties();
            props.addProperty(CL_CONTEXT_PLATFORM, chosenPlatform);
            context = clCreateContext(props, 1, new cl_device_id[]{chosenDevice}, null, null, null);
            queue = clCreateCommandQueueWithProperties(context, chosenDevice, new cl_queue_properties(), null);

            String source = loadKernelSource();
            program = clCreateProgramWithSource(context, 1, new String[]{source}, null, null);
            clBuildProgram(program, 0, null, null, null, null);
            kernel = clCreateKernel(program, "relax_step", null);

            this.deviceName = chosenName;
            this.available = true;
            this.solveDevice = chosenDevice;
            LethalBreedMod.LOGGER.info("[LethalBreed] GPU: {} — OpenCL OK", chosenName);
        } catch (Throwable t) {
            available = false;
            LethalBreedMod.LOGGER.warn("[LethalBreed] GPU: unavailable — CPU fallback activated ({})", t.toString());
        }
    }

    private cl_device_id solveDevice;

    private static String queryDeviceName(cl_device_id device) {
        long[] size = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, size);
        byte[] buffer = new byte[(int) size[0]];
        clGetDeviceInfo(device, CL_DEVICE_NAME, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, Math.max(0, buffer.length - 1), StandardCharsets.UTF_8).trim();
    }

    private static String loadKernelSource() throws Exception {
        try (var in = GpuComputeManager.class.getResourceAsStream("/kernels/bellman_ford.cl")) {
            if (in == null) {
                throw new IllegalStateException("kernel resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Solve a snapshot on the GPU. Serialized (single shared queue). Returns a {@link FlowField} or
     * throws — callers fall back to CPU on any throwable.
     */
    public synchronized FlowField solve(CpuFlowField.Snapshot s) {
        int width = s.width();
        int depth = s.depth();
        int n = width * depth;

        short[] cost = new short[n];
        java.util.Arrays.fill(cost, FlowField.IMPASSABLE);
        byte[] blockType = new byte[n];
        boolean[] walk = s.walk();
        for (int i = 0; i < n; i++) {
            blockType[i] = (byte) (walk[i] ? 0 : 1);
        }
        for (int seed : s.seedCells()) {
            cost[seed] = 0;
        }
        byte[] dirX = new byte[n];
        byte[] dirZ = new byte[n];
        byte[] flags = new byte[n];
        int[] changed = new int[1];

        cl_mem costMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_short * n, Pointer.to(cost), null);
        cl_mem btMem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * n, Pointer.to(blockType), null);
        cl_mem dirXMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * n, Pointer.to(dirX), null);
        cl_mem dirZMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * n, Pointer.to(dirZ), null);
        cl_mem flagsMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * n, Pointer.to(flags), null);
        cl_mem changedMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(changed), null);
        try {
            org.jocl.CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(costMem));
            org.jocl.CL.clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(btMem));
            org.jocl.CL.clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(dirXMem));
            org.jocl.CL.clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(dirZMem));
            org.jocl.CL.clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(flagsMem));
            org.jocl.CL.clSetKernelArg(kernel, 5, Sizeof.cl_int, Pointer.to(new int[]{width}));
            org.jocl.CL.clSetKernelArg(kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{depth}));
            org.jocl.CL.clSetKernelArg(kernel, 7, Sizeof.cl_mem, Pointer.to(changedMem));

            long[] global = new long[]{n};
            int maxIter = width + depth + 2;
            for (int iter = 0; iter < maxIter; iter++) {
                changed[0] = 0;
                clEnqueueWriteBuffer(queue, changedMem, true, 0, Sizeof.cl_int, Pointer.to(changed), 0, null, null);
                clEnqueueNDRangeKernel(queue, kernel, 1, null, global, null, 0, null, null);
                clEnqueueReadBuffer(queue, changedMem, true, 0, Sizeof.cl_int, Pointer.to(changed), 0, null, null);
                if (changed[0] == 0) {
                    break;
                }
            }

            clEnqueueReadBuffer(queue, costMem, true, 0, (long) Sizeof.cl_short * n, Pointer.to(cost), 0, null, null);
            clEnqueueReadBuffer(queue, dirXMem, true, 0, (long) Sizeof.cl_char * n, Pointer.to(dirX), 0, null, null);
            clEnqueueReadBuffer(queue, dirZMem, true, 0, (long) Sizeof.cl_char * n, Pointer.to(dirZ), 0, null, null);
            clFinish(queue);
        } finally {
            clReleaseMemObject(costMem);
            clReleaseMemObject(btMem);
            clReleaseMemObject(dirXMem);
            clReleaseMemObject(dirZMem);
            clReleaseMemObject(flagsMem);
            clReleaseMemObject(changedMem);
        }

        // Flags come from the snapshot's block classification (break/build), independent of the GPU solve.
        return new FlowField(s.originX(), s.originZ(), width, depth, s.focusY(), cost, dirX, dirZ, s.flags());
    }

    void logFallbackOnce(Throwable t) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            LethalBreedMod.LOGGER.warn("[LethalBreed] GPU solve failed once, using CPU from now on: {}", t.toString());
        }
    }
}
