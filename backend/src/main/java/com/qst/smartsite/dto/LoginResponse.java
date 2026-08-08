package com.qst.smartsite.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应
 */
@Data
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色编码列表 */
    private List<String> roles;
}
