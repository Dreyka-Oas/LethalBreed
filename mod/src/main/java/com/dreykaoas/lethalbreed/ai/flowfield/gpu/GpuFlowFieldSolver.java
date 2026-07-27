package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.Snapshot;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_mem;

import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;

/**
 * Per-call buffer marshalling for one flow-field snapshot on the GPU: builds host arrays from the
 * {@link Snapshot}, uploads them, runs the iterative Bellman-Ford relax kernel until it
 * converges, reads results back, and releases the device buffers. Stateless — bound to a
 * {@link GpuContext}'s context/queue/kernel.
 */
final class GpuFlowFieldSolver {
    private GpuFlowFieldSolver() {}

    /** Solve a snapshot on the GPU. Caller serializes access to the shared {@code ctx} queue. */
    static FlowField solve(GpuContext ctx, Snapshot s) {
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
        int[] extra = s.extraCost(); // per-cell enter cost (break/build), same array the CPU solver reads
        for (int seed : s.seedCells()) {
            cost[seed] = 0;
        }
        byte[] dirX = new byte[n];
        byte[] dirZ = new byte[n];
        int[] changed = new int[1];
        int orthoCost = Math.max(1, FlowConfig.flowOrthoCost);
        int diagCost = Math.max(orthoCost, FlowConfig.flowDiagonalCost);

        // Declared null before the try so EVERY buffer — including one whose own clCreateBuffer throws — is
        // covered by the finally. With setExceptionsEnabled(true), an allocation failure at buffer k used to
        // leak the k-1 already created, because the creates sat outside the protected block (audit #8).
        cl_mem costMem = null, btMem = null, extraMem = null, dirXMem = null, dirZMem = null, changedMem = null;
        try {
            costMem = clCreateBuffer(ctx.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_short * n, Pointer.to(cost), null);
            btMem = clCreateBuffer(ctx.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_char * n, Pointer.to(blockType), null);
            extraMem = clCreateBuffer(ctx.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_int * n, Pointer.to(extra), null);
            dirXMem = clCreateBuffer(ctx.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_char * n, Pointer.to(dirX), null);
            dirZMem = clCreateBuffer(ctx.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_char * n, Pointer.to(dirZ), null);
            changedMem = clCreateBuffer(ctx.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                    Sizeof.cl_int, Pointer.to(changed), null);

            clSetKernelArg(ctx.kernel, 0, Sizeof.cl_mem, Pointer.to(costMem));
            clSetKernelArg(ctx.kernel, 1, Sizeof.cl_mem, Pointer.to(btMem));
            clSetKernelArg(ctx.kernel, 2, Sizeof.cl_mem, Pointer.to(extraMem));
            clSetKernelArg(ctx.kernel, 3, Sizeof.cl_mem, Pointer.to(dirXMem));
            clSetKernelArg(ctx.kernel, 4, Sizeof.cl_mem, Pointer.to(dirZMem));
            clSetKernelArg(ctx.kernel, 5, Sizeof.cl_int, Pointer.to(new int[]{width}));
            clSetKernelArg(ctx.kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{depth}));
            clSetKernelArg(ctx.kernel, 7, Sizeof.cl_int, Pointer.to(new int[]{orthoCost}));
            clSetKernelArg(ctx.kernel, 8, Sizeof.cl_int, Pointer.to(new int[]{diagCost}));
            clSetKernelArg(ctx.kernel, 9, Sizeof.cl_mem, Pointer.to(changedMem));

            // Optional explicit work-group size: round the global range up to a multiple (the kernel
            // bounds-checks idx >= W*H, so the padding work-items are no-ops). 0 = let the driver choose.
            int wg = FlowConfig.gpuWorkgroupSize;
            long[] local = null;
            long[] global = new long[]{n};
            // Apply an explicit local size ONLY when it is actually legal for this device: positive, a power
            // of two, and within CL_DEVICE_MAX_WORK_GROUP_SIZE. An illegal value would otherwise make
            // clEnqueueNDRangeKernel throw and force a permanent CPU fallback + per-call alloc churn; instead
            // we silently fall back to local=null (driver picks) and keep using the GPU.
            if (wg > 0 && Integer.bitCount(wg) == 1 && wg <= ctx.maxWorkGroupSize) {
                long g = ((long) (n + wg - 1) / wg) * wg;
                global = new long[]{g};
                local = new long[]{wg};
            }
            // Convergence is checked once per BATCH of kernel passes instead of every pass. The blocking
            // changed-flag round-trip (write 0 + read back) dominated GPU latency at O(width+depth) passes;
            // batching cuts those round-trips ~BATCH×. The in-order command queue runs the queued passes
            // sequentially (each sees the previous pass's relaxations), and extra passes after convergence
            // are cheap no-ops, so at most one extra batch runs. Correctness is unchanged.
            final int BATCH = 16;
            int maxIter = width + depth + 2;
            for (int done = 0; done < maxIter; done += BATCH) {
                changed[0] = 0;
                clEnqueueWriteBuffer(ctx.queue, changedMem, true, 0, Sizeof.cl_int, Pointer.to(changed), 0, null, null);
                int passes = Math.min(BATCH, maxIter - done);
                for (int k = 0; k < passes; k++) {
                    clEnqueueNDRangeKernel(ctx.queue, ctx.kernel, 1, null, global, local, 0, null, null);
                }
                clEnqueueReadBuffer(ctx.queue, changedMem, true, 0, Sizeof.cl_int, Pointer.to(changed), 0, null, null);
                if (changed[0] == 0) {
                    break;
                }
            }

            clEnqueueReadBuffer(ctx.queue, costMem, true, 0, (long) Sizeof.cl_short * n, Pointer.to(cost), 0, null, null);
            clEnqueueReadBuffer(ctx.queue, dirXMem, true, 0, (long) Sizeof.cl_char * n, Pointer.to(dirX), 0, null, null);
            clEnqueueReadBuffer(ctx.queue, dirZMem, true, 0, (long) Sizeof.cl_char * n, Pointer.to(dirZ), 0, null, null);
            clFinish(ctx.queue);
        } finally {
            // Each release guarded on its own: a bare sequence skipped every buffer after the first that
            // threw, leaking the rest (audit #8). null-safe so a partial allocation still frees what it got.
            releaseQuietly(costMem);
            releaseQuietly(btMem);
            releaseQuietly(extraMem);
            releaseQuietly(dirXMem);
            releaseQuietly(dirZMem);
            releaseQuietly(changedMem);
        }

        return new FlowField(s.originX(), s.originZ(), width, depth, cost, dirX, dirZ);
    }

    private static void releaseQuietly(cl_mem mem) {
        if (mem == null) {
            return;
        }
        try {
            clReleaseMemObject(mem);
        } catch (Throwable ignored) {
            // A failed release must not stop the others; the device buffer is unrecoverable either way.
        }
    }
}
