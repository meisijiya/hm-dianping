package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
/**
 * UserHolder 类用于在当前线程中存储和获取用户信息。
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
