package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.entity.QuotaUsageLog;
import com.papercritical.entity.TeacherQuota;
import com.papercritical.exception.QuotaInsufficientException;
import com.papercritical.mapper.QuotaUsageLogMapper;
import com.papercritical.mapper.TeacherQuotaMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherQuotaService {

    private final TeacherQuotaMapper teacherQuotaMapper;
    private final QuotaUsageLogMapper quotaUsageLogMapper;

    @Value("${payment.free-trial-quota:50}")
    private Integer freeTrialQuota;

    public TeacherQuotaService(TeacherQuotaMapper teacherQuotaMapper,
                               QuotaUsageLogMapper quotaUsageLogMapper) {
        this.teacherQuotaMapper = teacherQuotaMapper;
        this.quotaUsageLogMapper = quotaUsageLogMapper;
    }

    public QuotaSummaryDTO getQuotaSummary(Long teacherId) {
        return toSummary(getQuotaEntity(teacherId));
    }

    @Transactional
    public ConsumeResult consumeOneQuota(Long teacherId, Long assignmentId, Long studentId, String remark) {
        TeacherQuota quota = getQuotaEntity(teacherId);
        String quotaSource;
        if (teacherQuotaMapper.consumeFreeQuota(teacherId) > 0) {
            quotaSource = "FREE";
        } else if (teacherQuotaMapper.consumePaidQuota(teacherId) > 0) {
            quotaSource = "PAID";
        } else {
            throw new QuotaInsufficientException("批改额度不足，请先购买 9.9 元 500 篇套餐", toSummary(quota));
        }

        TeacherQuota updatedQuota = getQuotaEntity(teacherId);
        quotaUsageLogMapper.insert(buildUsageLog(teacherId, assignmentId, studentId, null,
                "CONSUME", quotaSource, -1, totalRemaining(updatedQuota), remark));
        return new ConsumeResult(quotaSource, toSummary(updatedQuota));
    }

    @Transactional
    public QuotaSummaryDTO refundQuota(Long teacherId, ConsumeResult consumeResult,
                                       Long assignmentId, Long studentId, String remark) {
        if (consumeResult == null) {
            return getQuotaSummary(teacherId);
        }

        if ("FREE".equals(consumeResult.getQuotaSource())) {
            teacherQuotaMapper.refundFreeQuota(teacherId);
        } else {
            teacherQuotaMapper.refundPaidQuota(teacherId);
        }

        TeacherQuota quota = getQuotaEntity(teacherId);
        quotaUsageLogMapper.insert(buildUsageLog(teacherId, assignmentId, studentId, null,
                "REFUND", consumeResult.getQuotaSource(), 1, totalRemaining(quota), remark));
        return toSummary(quota);
    }

    @Transactional
    public QuotaSummaryDTO grantPaidQuota(Long teacherId, String orderNo, Integer quotaAmount, String remark) {
        getQuotaEntity(teacherId);
        teacherQuotaMapper.addPaidQuota(teacherId, quotaAmount);
        TeacherQuota quota = getQuotaEntity(teacherId);
        quotaUsageLogMapper.insert(buildUsageLog(teacherId, null, null, orderNo,
                "PAYMENT_GRANT", "PAID", quotaAmount, totalRemaining(quota), remark));
        return toSummary(quota);
    }

    private TeacherQuota getQuotaEntity(Long teacherId) {
        LambdaQueryWrapper<TeacherQuota> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeacherQuota::getTeacherId, teacherId).last("LIMIT 1");
        TeacherQuota quota = teacherQuotaMapper.selectOne(wrapper);
        if (quota != null) {
            return quota;
        }

        TeacherQuota initialQuota = new TeacherQuota();
        initialQuota.setTeacherId(teacherId);
        initialQuota.setFreeQuotaTotal(freeTrialQuota);
        initialQuota.setFreeQuotaUsed(0);
        initialQuota.setPaidQuotaTotal(0);
        initialQuota.setPaidQuotaUsed(0);
        initialQuota.setStatus("ACTIVE");
        try {
            teacherQuotaMapper.insert(initialQuota);
        } catch (Exception ignored) {
            // Another request may have created the quota row first; read it below.
        }

        quota = teacherQuotaMapper.selectOne(wrapper);
        if (quota != null) {
            return quota;
        }
        throw new IllegalStateException("额度账户初始化失败");
    }

    private QuotaUsageLog buildUsageLog(Long teacherId, Long assignmentId, Long studentId, String orderNo,
                                        String usageType, String quotaSource, Integer changeAmount,
                                        Integer remainingAfter, String remark) {
        QuotaUsageLog log = new QuotaUsageLog();
        log.setTeacherId(teacherId);
        log.setAssignmentId(assignmentId);
        log.setStudentId(studentId);
        log.setOrderNo(orderNo);
        log.setUsageType(usageType);
        log.setQuotaSource(quotaSource);
        log.setChangeAmount(changeAmount);
        log.setRemainingAfter(remainingAfter);
        log.setRemark(remark);
        return log;
    }

    private QuotaSummaryDTO toSummary(TeacherQuota quota) {
        QuotaSummaryDTO summary = new QuotaSummaryDTO();
        int freeTotal = safeValue(quota.getFreeQuotaTotal());
        int freeUsed = safeValue(quota.getFreeQuotaUsed());
        int paidTotal = safeValue(quota.getPaidQuotaTotal());
        int paidUsed = safeValue(quota.getPaidQuotaUsed());
        summary.setFreeTotal(freeTotal);
        summary.setFreeUsed(freeUsed);
        summary.setFreeRemaining(Math.max(freeTotal - freeUsed, 0));
        summary.setPaidTotal(paidTotal);
        summary.setPaidUsed(paidUsed);
        summary.setPaidRemaining(Math.max(paidTotal - paidUsed, 0));
        summary.setRemainingTotal(summary.getFreeRemaining() + summary.getPaidRemaining());
        summary.setStatus(quota.getStatus());
        return summary;
    }

    private int totalRemaining(TeacherQuota quota) {
        return Math.max(safeValue(quota.getFreeQuotaTotal()) - safeValue(quota.getFreeQuotaUsed()), 0)
                + Math.max(safeValue(quota.getPaidQuotaTotal()) - safeValue(quota.getPaidQuotaUsed()), 0);
    }

    private int safeValue(Integer value) {
        return value == null ? 0 : value;
    }

    public void addPaidQuota(Long teacherId, int amount) {
        TeacherQuota quota = teacherQuotaMapper.selectOne(
                new LambdaQueryWrapper<TeacherQuota>().eq(TeacherQuota::getTeacherId, teacherId));
        if (quota == null) {
            quota = new TeacherQuota();
            quota.setTeacherId(teacherId);
            quota.setFreeQuotaTotal(0);
            quota.setFreeQuotaUsed(0);
            quota.setPaidQuotaTotal(amount);
            quota.setPaidQuotaUsed(0);
            quota.setStatus("ACTIVE");
            teacherQuotaMapper.insert(quota);
        } else {
            quota.setPaidQuotaTotal(quota.getPaidQuotaTotal() + amount);
            teacherQuotaMapper.updateById(quota);
        }
    }

    public static class ConsumeResult {
        private final String quotaSource;
        private final QuotaSummaryDTO quotaSummary;

        public ConsumeResult(String quotaSource, QuotaSummaryDTO quotaSummary) {
            this.quotaSource = quotaSource;
            this.quotaSummary = quotaSummary;
        }

        public String getQuotaSource() {
            return quotaSource;
        }

        public QuotaSummaryDTO getQuotaSummary() {
            return quotaSummary;
        }
    }
}
