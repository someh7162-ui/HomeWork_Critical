package com.papercritical.dto;

import javax.validation.constraints.NotBlank;

public class WechatLoginDTO {

    @NotBlank(message = "code不能为空")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
