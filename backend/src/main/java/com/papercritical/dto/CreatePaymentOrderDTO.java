package com.papercritical.dto;

import javax.validation.constraints.NotBlank;

public class CreatePaymentOrderDTO {
    @NotBlank(message = "支付渠道不能为空")
    private String channel;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
