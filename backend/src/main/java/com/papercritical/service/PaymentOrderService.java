package com.papercritical.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.dto.CreatePaymentOrderDTO;
import com.papercritical.dto.PaymentOrderDTO;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.entity.PaymentCallbackLog;
import com.papercritical.entity.PaymentOrder;
import com.papercritical.mapper.PaymentCallbackLogMapper;
import com.papercritical.mapper.PaymentOrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentOrderService {

    private static final String PACKAGE_CODE = "QUOTA_500";
    private static final String PACKAGE_NAME = "500篇批改额度包";

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentCallbackLogMapper paymentCallbackLogMapper;
    private final TeacherQuotaService teacherQuotaService;

    @Value("${payment.package-price:9.90}")
    private String packagePriceText;

    @Value("${payment.package-quota:500}")
    private Integer packageQuota;

    @Value("${payment.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${payment.wechat.appid:}")
    private String wechatAppId;

    @Value("${payment.wechat.mchid:}")
    private String wechatMerchantId;

    @Value("${payment.wechat.notify-url:}")
    private String wechatNotifyUrl;

    public PaymentOrderService(PaymentOrderMapper paymentOrderMapper,
                               PaymentCallbackLogMapper paymentCallbackLogMapper,
                               TeacherQuotaService teacherQuotaService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentCallbackLogMapper = paymentCallbackLogMapper;
        this.teacherQuotaService = teacherQuotaService;
    }

    public PaymentOrderDTO createOrder(Long teacherId, CreatePaymentOrderDTO dto) {
        String channel = dto.getChannel() == null ? "" : dto.getChannel().trim().toUpperCase();
        if (!"WECHAT".equals(channel)) {
            throw new IllegalArgumentException("微信小程序当前仅支持微信支付");
        }

        PaymentOrder order = new PaymentOrder();
        order.setTeacherId(teacherId);
        order.setOrderNo(generateOrderNo());
        order.setPackageCode(PACKAGE_CODE);
        order.setPackageName(PACKAGE_NAME);
        order.setPayChannel(channel);
        order.setPaymentMode(mockEnabled ? "MOCK" : "WECHAT_MINI_PROGRAM");
        order.setAmount(new BigDecimal(packagePriceText).setScale(2, RoundingMode.HALF_UP));
        order.setQuotaAmount(packageQuota);
        order.setStatus("PENDING");
        paymentOrderMapper.insert(order);

        PaymentOrderDTO response = toDto(order, teacherQuotaService.getQuotaSummary(teacherId));
        if (mockEnabled) {
            response.setMessage("当前为开发模式，可直接使用模拟支付完成购买");
            Map<String, Object> params = new HashMap<>();
            params.put("mock", true);
            response.setPaymentParams(params);
        } else {
            validateWechatConfig();
            response.setMessage("订单已创建，请使用返回的支付参数调起微信支付");
            response.setPaymentParams(buildWechatPlaceholderParams(order));
        }
        return response;
    }

    @Transactional
    public PaymentOrderDTO mockPayOrder(String orderNo, Long teacherId) {
        if (!mockEnabled) {
            throw new IllegalArgumentException("当前环境未开启模拟支付");
        }

        PaymentOrder order = requireOwnedOrder(orderNo, teacherId);
        if ("PAID".equals(order.getStatus())) {
            return toDto(order, teacherQuotaService.getQuotaSummary(teacherId));
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不可支付");
        }

        String transactionId = "MOCK-" + System.currentTimeMillis();
        int affected = paymentOrderMapper.markPaidIfPending(orderNo, teacherId, transactionId);
        if (affected > 0) {
            recordCallback(orderNo, "WECHAT", "MOCK_PAY",
                    "{\"transactionId\":\"" + transactionId + "\"}",
                    "SUCCESS", "模拟支付成功");
            teacherQuotaService.grantPaidQuota(teacherId, orderNo, order.getQuotaAmount(), "模拟支付完成，增加付费额度");
        }

        PaymentOrder updated = requireOwnedOrder(orderNo, teacherId);
        return toDto(updated, teacherQuotaService.getQuotaSummary(teacherId));
    }

    private PaymentOrder requireOwnedOrder(String orderNo, Long teacherId) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getTeacherId, teacherId)
                .last("LIMIT 1");
        PaymentOrder order = paymentOrderMapper.selectOne(wrapper);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private PaymentOrderDTO toDto(PaymentOrder order, QuotaSummaryDTO quotaSummary) {
        PaymentOrderDTO dto = new PaymentOrderDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setPackageName(order.getPackageName());
        dto.setPayChannel(order.getPayChannel());
        dto.setPaymentMode(order.getPaymentMode());
        dto.setAmount(order.getAmount());
        dto.setQuotaAmount(order.getQuotaAmount());
        dto.setStatus(order.getStatus());
        dto.setQuotaSummary(quotaSummary);
        return dto;
    }

    private void validateWechatConfig() {
        if (isBlank(wechatAppId) || isBlank(wechatMerchantId) || isBlank(wechatNotifyUrl)) {
            throw new IllegalArgumentException("当前未配置完整的微信支付参数，请先使用模拟支付流程");
        }
    }

    private Map<String, Object> buildWechatPlaceholderParams(PaymentOrder order) {
        Map<String, Object> params = new HashMap<>();
        params.put("provider", "wxpay");
        params.put("orderNo", order.getOrderNo());
        params.put("packageName", order.getPackageName());
        params.put("note", "请在后端接入微信统一下单后返回真实的小程序支付参数");
        return params;
    }

    private void recordCallback(String orderNo, String payChannel, String callbackType,
                                String payload, String processStatus, String remark) {
        PaymentCallbackLog log = new PaymentCallbackLog();
        log.setOrderNo(orderNo);
        log.setPayChannel(payChannel);
        log.setCallbackType(callbackType);
        log.setPayload(payload);
        log.setProcessStatus(processStatus);
        log.setRemark(remark);
        paymentCallbackLogMapper.insert(log);
    }

    private String generateOrderNo() {
        return "PC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + RandomUtil.randomNumbers(6);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
