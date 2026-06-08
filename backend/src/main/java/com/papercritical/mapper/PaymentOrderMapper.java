package com.papercritical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.papercritical.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Update("UPDATE tb_payment_order SET status = 'PAID', transaction_id = #{transactionId}, paid_at = NOW(), updated_at = NOW() WHERE order_no = #{orderNo} AND teacher_id = #{teacherId} AND status = 'PENDING'")
    int markPaidIfPending(@Param("orderNo") String orderNo,
                          @Param("teacherId") Long teacherId,
                          @Param("transactionId") String transactionId);
}
