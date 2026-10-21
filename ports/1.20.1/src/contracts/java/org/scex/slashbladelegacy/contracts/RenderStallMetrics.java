package org.scex.slashbladelegacy.contracts;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

/** Collect primitive counters on the probe's client thread. */
public final class RenderStallMetrics {
    public static final String[] FIELD_NAMES={
            "model_gets","model_cache_loads","obj_parses","obj_parse_ns",
            "texture_uploads","texture_upload_pixels","texture_upload_ns",
            "filter_calls","filter_same","filter_changed","filter_wall_ns","filter_cpu_ns",
            "tessellate_only_calls","tessellate_only_ns","faces","vertices",
            "blade_capability_initializations","runtime_components_not_applicable","layer_calls","layer_ns",
            "peek_calls","peek_ns","compound_tag_copies"};
    public static final int MODEL_GETS=0,MODEL_LOADS=1,OBJ_PARSES=2,OBJ_NS=3,
            UPLOADS=4,UPLOAD_PIXELS=5,UPLOAD_NS=6,FILTER_CALLS=7,FILTER_SAME=8,FILTER_CHANGED=9,
            FILTER_NS=10,FILTER_CPU_NS=11,TESSELLATE_CALLS=12,TESSELLATE_NS=13,FACES=14,VERTICES=15,
            STATE_INIT=16,RUNTIME_INIT=17,LAYER_CALLS=18,LAYER_NS=19,PEEK_CALLS=20,PEEK_NS=21,COPY_TAG=22;
    private static final long[] FRAME=new long[FIELD_NAMES.length];
    private static Thread owner;
    private static ThreadMXBean cpu;
    private static long timelineCalls,timelineNanos;
    private RenderStallMetrics(){}

    public static boolean configureCpuTimer() {
        owner=Thread.currentThread();
        var bean=ManagementFactory.getThreadMXBean();
        if(bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled())cpu=bean;
        return cpu!=null;
    }
    public static boolean active() {
        return Thread.currentThread()==owner && RenderStallProbe.enabled() && RenderStallProbe.measuring();
    }
    public static boolean stageActive() {
        return Thread.currentThread()==owner && RenderStallProbe.enabled() && RenderStallProbe.stageActive();
    }
    public static void resetFrame() {owner=Thread.currentThread();Arrays.fill(FRAME,0);FRAME[RUNTIME_INIT]=-1;}
    public static void copyFrame(long[] destination,int offset) {System.arraycopy(FRAME,0,destination,offset,FRAME.length);}
    public static void add(int field,long value) {FRAME[field]+=value;}
    public static long cpuTime() {return cpu==null?-1:cpu.getCurrentThreadCpuTime();}
    public static void timeline(long nanos) {timelineCalls++;timelineNanos+=nanos;}
    public static long timelineCalls() {return timelineCalls;}
    public static long timelineNanos() {return timelineNanos;}
}
