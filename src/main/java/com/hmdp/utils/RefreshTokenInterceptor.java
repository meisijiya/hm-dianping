package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

//手动new出来的类，所以不能直接注入bean，需要手动注册到spring容器中
public class RefreshTokenInterceptor implements HandlerInterceptor {
    final private StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1.获取请求头中的token    authorization请求头
        String token=request.getHeader("authorization");
        //2.基于Token获取Redis中的用户
        String key =LOGIN_USER_KEY+token;
        Map<Object,Object> userMap=stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if(userMap.isEmpty()) {
            //为空也直接放行交给下一个拦截器处理
            return true;
        }
        // 5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO =BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在，将UserDTO对象存入ThreadLocal中 */
        UserHolder.saveUser(userDTO);
        // 7.刷新token有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }
}
