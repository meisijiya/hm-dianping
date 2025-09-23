package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = "cache:shop_type_list";

        // 1. 先尝试从Redis中获取
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            // Redis中存在数据，直接返回
            List<ShopType> typeList = JSONUtil.toList(json, ShopType.class);
            return Result.ok(typeList);
        }
        // 2. Redis中不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 3. 将数据保存在Redis中
        if (!typeList.isEmpty()) {
            String jsonStr = JSONUtil.toJsonStr(typeList);
            stringRedisTemplate.opsForValue().set(key, jsonStr);
            // 可以设置过期时间，例如30分钟
             stringRedisTemplate.opsForValue().set(key, jsonStr, 30, TimeUnit.MINUTES);
        }
        // 4. 将数据返回给前端
        return Result.ok(typeList);
    }
}
