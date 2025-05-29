package com.pp.netty.channel;


/**
 * @Author: PP-jessica
 * @Description:channelId默认的实现类
 * 兄弟们，这个类中调用了很多操作系统方法，我就不引进这个了，引入这个类就需要引入更多不必要的类。所以我直接用最简单的
 * 方式代替吧，就搞一个时间戳作为channelId
 */
public class DefaultChannelId implements ChannelId{

    private String longValue;

    private static final long serialVersionUID = 3884076183504074063L;


    public static DefaultChannelId newInstance() {
        return new DefaultChannelId();
    }

    private DefaultChannelId() {
        long currentTimeMillis = System.currentTimeMillis();
        this.longValue = String.valueOf(currentTimeMillis);
    }

    @Override
    public String asShortText() {
        return null;
    }

    @Override
    public String asLongText() {
        String longValue = this.longValue;
        if (longValue == null) {
            this.longValue = longValue =String.valueOf(System.currentTimeMillis());
        }
        return longValue;
    }

//    /**
//     * @Author: PP-jessica
//     * @Description:channelId是由多个int类型的整数拼接而成，进程id+时间戳+机器MAC地址+随机整数+递增整数
//     */
//    private static final byte[] MACHINE_ID;
//    private static final int PROCESS_ID_LEN = 4;
//    private static final int PROCESS_ID;
//    private static final int SEQUENCE_LEN = 4;
//    private static final int TIMESTAMP_LEN = 8;
//    private static final int RANDOM_LEN = 4;
//
//    /**
//     * @Author: PP-jessica
//     * @Description:生成递增整数的原子类
//     */
//    private static final AtomicInteger nextSequence = new AtomicInteger();



//    static {
//        int processId = -1;
//        String customProcessId = SystemPropertyUtil.get("io.netty.processId");
//        if (customProcessId != null) {
//            try {
//                processId = Integer.parseInt(customProcessId);
//            } catch (NumberFormatException e) {
//                // Malformed input.
//            }
//
//            if (processId < 0) {
//                processId = -1;
//            }
//        }
//
//        if (processId < 0) {
//            processId = defaultProcessId();
//        }
//
//        PROCESS_ID = processId;
//
//        byte[] machineId = null;
//        String customMachineId = SystemPropertyUtil.get("io.netty.machineId");
//        if (customMachineId != null) {
//            try {
//                machineId = parseMAC(customMachineId);
//            } catch (Exception e) {
//                logger.warn("-Dio.netty.machineId: {} (malformed)", customMachineId, e);
//            }
//            if (machineId != null) {
//                logger.debug("-Dio.netty.machineId: {} (user-set)", customMachineId);
//            }
//        }
//
//        if (machineId == null) {
//            machineId = defaultMachineId();
//            if (logger.isDebugEnabled()) {
//                logger.debug("-Dio.netty.machineId: {} (auto-detected)", MacAddressUtil.formatAddress(machineId));
//            }
//        }
//
//        MACHINE_ID = machineId;
//    }
//
//
//    private static int defaultProcessId() {
//        ClassLoader loader = null;
//        String value;
//        try {
//            loader = PlatformDependent.getClassLoader(DefaultChannelId.class);
//            // Invoke java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
//            Class<?> mgmtFactoryType = Class.forName("java.lang.management.ManagementFactory", true, loader);
//            Class<?> runtimeMxBeanType = Class.forName("java.lang.management.RuntimeMXBean", true, loader);
//
//            Method getRuntimeMXBean = mgmtFactoryType.getMethod("getRuntimeMXBean", EmptyArrays.EMPTY_CLASSES);
//            Object bean = getRuntimeMXBean.invoke(null, EmptyArrays.EMPTY_OBJECTS);
//            Method getName = runtimeMxBeanType.getMethod("getName", EmptyArrays.EMPTY_CLASSES);
//            value = (String) getName.invoke(bean, EmptyArrays.EMPTY_OBJECTS);
//        } catch (Throwable t) {
//            throw new RuntimeException(t);
//        }
//
//        int atIndex = value.indexOf('@');
//        if (atIndex >= 0) {
//            value = value.substring(0, atIndex);
//        }
//
//        int pid;
//        try {
//            pid = Integer.parseInt(value);
//        } catch (NumberFormatException e) {
//            // value did not contain an integer.
//            pid = -1;
//        }
//
//        if (pid < 0) {
//            pid = PlatformDependent.threadLocalRandom().nextInt();
//        }
//
//        return pid;
//    }
//
//    private final byte[] data;
//    private final int hashCode;
//
//    private transient String shortValue;
//    private transient String longValue;
//
//    private DefaultChannelId() {
//        data = new byte[MACHINE_ID.length + PROCESS_ID_LEN + SEQUENCE_LEN + TIMESTAMP_LEN + RANDOM_LEN];
//        int i = 0;
//
//        // machineId
//        System.arraycopy(MACHINE_ID, 0, data, i, MACHINE_ID.length);
//        i += MACHINE_ID.length;
//
//        // processId
//        i = writeInt(i, PROCESS_ID);
//
//        // sequence
//        i = writeInt(i, nextSequence.getAndIncrement());
//
//        // timestamp (kind of)
//        i = writeLong(i, Long.reverse(System.nanoTime()) ^ System.currentTimeMillis());
//
//        // random
//        int random = PlatformDependent.threadLocalRandom().nextInt();
//        i = writeInt(i, random);
//        assert i == data.length;
//
//        hashCode = Arrays.hashCode(data);
//    }
//
//    private int writeInt(int i, int value) {
//        data[i ++] = (byte) (value >>> 24);
//        data[i ++] = (byte) (value >>> 16);
//        data[i ++] = (byte) (value >>> 8);
//        data[i ++] = (byte) value;
//        return i;
//    }
//
//    private int writeLong(int i, long value) {
//        data[i ++] = (byte) (value >>> 56);
//        data[i ++] = (byte) (value >>> 48);
//        data[i ++] = (byte) (value >>> 40);
//        data[i ++] = (byte) (value >>> 32);
//        data[i ++] = (byte) (value >>> 24);
//        data[i ++] = (byte) (value >>> 16);
//        data[i ++] = (byte) (value >>> 8);
//        data[i ++] = (byte) value;
//        return i;
//    }

//    @Override
//    public String asShortText() {
//        String shortValue = this.shortValue;
//        if (shortValue == null) {
//            this.shortValue = shortValue = ByteBufUtil.hexDump(data, data.length - RANDOM_LEN, RANDOM_LEN);
//        }
//        return shortValue;
//    }


//    private String newLongValue() {
//        StringBuilder buf = new StringBuilder(2 * data.length + 5);
//        int i = 0;
//        i = appendHexDumpField(buf, i, MACHINE_ID.length);
//        i = appendHexDumpField(buf, i, PROCESS_ID_LEN);
//        i = appendHexDumpField(buf, i, SEQUENCE_LEN);
//        i = appendHexDumpField(buf, i, TIMESTAMP_LEN);
//        i = appendHexDumpField(buf, i, RANDOM_LEN);
//        assert i == data.length;
//        return buf.substring(0, buf.length() - 1);
//    }
//
//    private int appendHexDumpField(StringBuilder buf, int i, int length) {
//        buf.append(ByteBufUtil.hexDump(data, i, length));
//        buf.append('-');
//        i += length;
//        return i;
//    }
//
//    @Override
//    public int hashCode() {
//        return hashCode;
//    }
//
//    @Override
//    public int compareTo(final ChannelId o) {
//        if (this == o) {
//            // short circuit
//            return 0;
//        }
//        if (o instanceof DefaultChannelId) {
//            // lexicographic comparison
//            final byte[] otherData = ((DefaultChannelId) o).data;
//            int len1 = data.length;
//            int len2 = otherData.length;
//            int len = Math.min(len1, len2);
//
//            for (int k = 0; k < len; k++) {
//                byte x = data[k];
//                byte y = otherData[k];
//                if (x != y) {
//                    // treat these as unsigned bytes for comparison
//                    return (x & 0xff) - (y & 0xff);
//                }
//            }
//            return len1 - len2;
//        }
//
//        return asLongText().compareTo(o.asLongText());
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) {
//            return true;
//        }
//        if (!(obj instanceof DefaultChannelId)) {
//            return false;
//        }
//        DefaultChannelId other = (DefaultChannelId) obj;
//        return hashCode == other.hashCode && Arrays.equals(data, other.data);
//    }

    @Override
    public String toString() {
        return asShortText();
    }

    @Override
    public int compareTo(ChannelId o) {
        return 0;
    }
}
