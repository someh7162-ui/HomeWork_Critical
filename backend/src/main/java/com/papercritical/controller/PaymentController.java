package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.CreatePaymentOrderDTO;
import com.papercritical.dto.PaymentOrderDTO;
import com.papercritical.service.PaymentOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/pay")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    public PaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @PostMapping("/orders")
    public Result<PaymentOrderDTO> createOrder(@Valid @RequestBody CreatePaymentOrderDTO dto,
                                               HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(paymentOrderService.createOrder(teacherId, dto));
    }

    @PostMapping("/orders/{orderNo}/mock-pay")
    public Result<PaymentOrderDTO> mockPay(@PathVariable String orderNo, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(paymentOrderService.mockPayOrder(orderNo, teacherId));
    }
}
