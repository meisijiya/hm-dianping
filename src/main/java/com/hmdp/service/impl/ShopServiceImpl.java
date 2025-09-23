package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.util.concurrent.TimeUnit;

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
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String Key=CACHE_SHOP_KEY+id;
        // 1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(Key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在直接返回
            Shop shop= JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        /*  内容：添加判断命中的是否空值
            时间： 2025/9/24 00:18 */
         if(shopJson!=null){
             //返回一个错误信息
             return Result.fail("店铺不存在");
         }
        // 4.不存在，根据id查询数据库
        Shop shop = getById( id);
        // 5.数据库不存在，返回错误
        if(shop==null){
            /*  内容：将空值写入Redis
                时间： 2025/9/24 00:20 */
             stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //6.存在，写入redis
        //设置redis缓存时添加过期时间
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
}
