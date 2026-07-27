package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
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
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.setExceptionsEnabled;

/**
 * OpenCL (JOCL) device pick + context/queue/kernel build. Prefers an AMD/Radeon GPU, falls back to
 * the first GPU device found, then builds the {@code bellman_ford.cl} kernel. Construction throws on
 * any failure so the caller can degrade to the CPU solver — the GPU is never load-bearing.
 *
 * <p><b>Native handle lifetime (audit #17):</b> the four handles below — {@code context}, {@code queue},
 * {@code program}, {@code kernel} — are deliberately owned for the whole process. {@link GpuComputeManager}
 * is a JVM singleton that builds exactly one {@code GpuContext} per launch, so this is a bounded, one-time
 * allocation, reclaimed by the OpenCL driver at process exit. It is intentionally NOT freed on
 * {@code SERVER_STOPPED}: the flow-field pool threads acquire {@code isAvailable()} and {@code solve()} as
 * two separate steps, so releasing the context on the server thread while a pool thread sits between them
 * would hand a freed {@code cl_context} to {@code clCreateBuffer} — a driver SIGSEGV, not a catchable Java
 * exception. Releasing on stop would also re-pay device enumeration + {@code clBuildProgram} on every world
 * open (see the warm-up in {@code LifecycleInit}), which is exactly the cost audit #9 is about. If a
 * device-loss recovery path is ever wanted, it must go through a monitor-guarded {@code shutdown()} plus a
 * {@code ctx == null} re-check in {@code solve()}, wired to a JVM shutdown hook — never to the server tick.
 */
final class GpuContext {
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
                nameList.add(GpuDeviceInfo.name(device));
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
        this.maxWorkGroupSize = GpuDeviceInfo.maxWorkGroupSize(chosenDevice);
    }

    // The kernel ships as plaintext OpenCL source under /kernels/bellman_ford.clx (the build copies
    // the .cl to that name). It is read verbatim here just before clCreateProgramWithSource.
    private static String loadKernelSource() {
        try (var in = GpuContext.class.getResourceAsStream("/kernels/bellman_ford.clx")) {
            if (in == null) {
                throw new IllegalStateException("kernel resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("kernel load failed", e);
        }
    }
}
