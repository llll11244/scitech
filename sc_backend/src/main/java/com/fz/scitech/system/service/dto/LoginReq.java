package com.fz.scitech.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {

    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
