package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class RedisIdWorker {
    //起始时间戳（2025年10月13日 08:58:58）
    private static final long BEGIN_TIMESTAMP =1760317138000L;
    //序列号位数(32位，决定每秒最大生产量)
    private static final long COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    //构造注入
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp= nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        // 判空处理，防止拆箱空指针
        if (count == null) {
            count = 0L;
        }
        //拼接时间戳和序列号
        return timestamp<<COUNT_BITS|count;
    }
}
