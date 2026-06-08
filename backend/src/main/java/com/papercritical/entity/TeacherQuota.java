package com.papercritical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_teacher_quota")
public class TeacherQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private Integer freeQuotaTotal;
    private Integer freeQuotaUsed;
    private Integer paidQuotaTotal;
    private Integer paidQuotaUsed;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Integer getFreeQuotaTotal() {
        return freeQuotaTotal;
    }

    public void setFreeQuotaTotal(Integer freeQuotaTotal) {
        this.freeQuotaTotal = freeQuotaTotal;
    }

    public Integer getFreeQuotaUsed() {
        return freeQuotaUsed;
    }

    public void setFreeQuotaUsed(Integer freeQuotaUsed) {
        this.freeQuotaUsed = freeQuotaUsed;
    }

    public Integer getPaidQuotaTotal() {
        return paidQuotaTotal;
    }

    public void setPaidQuotaTotal(Integer paidQuotaTotal) {
        this.paidQuotaTotal = paidQuotaTotal;
    }

    public Integer getPaidQuotaUsed() {
        return paidQuotaUsed;
    }

    public void setPaidQuotaUsed(Integer paidQuotaUsed) {
        this.paidQuotaUsed = paidQuotaUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
