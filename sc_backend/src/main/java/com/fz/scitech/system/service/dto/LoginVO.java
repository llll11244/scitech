package com.fz.scitech.system.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;
    private String username;
    private String realName;
    private String role;
}
