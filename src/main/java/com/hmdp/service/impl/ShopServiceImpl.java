package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    /*  内容：定义互斥锁的上锁和解锁方法
        时间： 2025/9/24 10:53 */
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }
/*  内容：利用互斥锁解决缓存击穿问题
    时间： 2025/9/24 16:45 */
    public Shop queryWithMutex(Long id) {
        String Key=CACHE_SHOP_KEY+id;
        // 1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(Key);
        //2.判断缓存是否命中
        //isNotBlank用于判断字符串是否‌不为空且不全是空白字符‌的方法
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的值是否是空值
        if (shopJson != null) {
            //是空值，则返回一个错误信息
            return null;
        }
        //4.未命中则尝试获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop = null;
        try {
            boolean isLock=tryLock(lockKey);
            //判断是否成功获取
            if(!isLock){
                //失败，则休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //5.成功，根据id查询数据库
            shop=getById(id);
            if(shop==null){
                //不存在，将空值写入Redis
                stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
        //6.写入Redis
        //设置redis缓存时添加过期时间，增加随机值防止缓存雪崩
        Random random = new Random();
        long randomTTL = CACHE_SHOP_TTL + random.nextInt(10);
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),randomTTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }



    /*  内容：解决缓存穿透的封装
        时间： 2025/9/24 10:54 */
     public Shop queryWithPassThrough(Long id) {
         String Key=CACHE_SHOP_KEY+id;
         // 1.从redis查询商铺缓存
         String shopJson=stringRedisTemplate.opsForValue().get(Key);
         //2.判断是否存在
         if(StrUtil.isNotBlank(shopJson)){
             // 3.存在直接返回
             return JSONUtil.toBean(shopJson, Shop.class);
         }
        /*  内容：添加判断命中的是否空值
            时间： 2025/9/24 00:18 */
         if(shopJson!=null){
             //返回一个错误信息
             return null;
         }
         // 4.不存在，根据id查询数据库
         Shop shop = getById( id);
         // 5.数据库不存在，返回错误
         if(shop==null){
            /*  内容：将空值写入Redis
                时间： 2025/9/24 00:20 */
             // 为防止缓存雪崩，给空值也添加随机过期时间
             Random random = new Random();
             Long randomTTL = CACHE_NULL_TTL + random.nextInt(5);
             stringRedisTemplate.opsForValue().set(Key,"",randomTTL,TimeUnit.MINUTES);
             return null;
         }
         //6.存在，写入redis
         //设置redis缓存时添加过期时间，增加随机值防止缓存雪崩
         Random random = new Random();
         Long randomTTL = CACHE_SHOP_TTL + random.nextInt(10);
         stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),randomTTL, TimeUnit.MINUTES);
         return shop;
    }


    @Override
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    /*  内容：利用逻辑过期解决缓存击穿问题
        时间： 2025/9/24 16:55 */
    //缓存预热
    @Override
    public void saveShop2Redis(Long id, Long expireSeconds) {
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire( Long id ) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return shop;
    }

}
