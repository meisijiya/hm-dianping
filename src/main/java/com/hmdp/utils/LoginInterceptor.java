package com.hmdp.utils;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//手动new出来的类，所以不能直接注入bean，需要手动注册到spring容器中
public class LoginInterceptor implements HandlerInterceptor {
    //通过构造函数创建
    private StringRedisTemplate redisTemplate;
    public LoginInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //TODO 1.获取请求头中的token

        //2.TODO 2.基于Token获取Redis中的用户
        Object user = session.getAttribute("user");
        //3.判断用户是否存在
        if(user != null) {
            //4.不存在就拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        //TODO 5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        UserHolder.saveUser(userDTO);
        //TODO 6.存在，将UserDTO对象存入ThreadLocal中

        //TODO 7.刷新token有效期
        //6.放行
        return true;
    }
}
