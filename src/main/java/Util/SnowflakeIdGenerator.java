package Util;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    // 基准时间：2025-01-01
    private static final long START_TIMESTAMP = 1735689600000L;
    // 机器ID、数据中心ID 位数
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATA_CENTER_BITS = 5L;
    // 序列号位数
    private static final long SEQUENCE_BITS = 12L;

    // 最大值计算
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_DATA_CENTER_ID = (1L << DATA_CENTER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    // 位移偏移量
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_BITS;

    private final long workerId;
    private final long dataCenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        // 单机部署固定值，集群环境需动态配置
        this.workerId = 1L;
        this.dataCenterId = 1L;
    }

    /** 同步方法，生成唯一ID */
    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();
        // 处理时间回拨
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("系统时间回拨，无法生成ID");
        }
        // 同一毫秒内，序列号自增
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号用尽，等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = currentTimestamp;
        // 拼接ID
        return (currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT
                | (dataCenterId << DATA_CENTER_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}