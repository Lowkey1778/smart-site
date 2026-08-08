package com.qst.smartsite.dto;

import lombok.Data;

/**
 * 当前用户修改/重置密码请求
 */
@Data
public class ChangePasswordRequest {

    /** 原密码（校验用） */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;
}
