package com.papercritical.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("tb_quota_card")
public class QuotaCard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cardKey;
    private Integer quotaAmount;
    private String status;
    private Long usedByTeacherId;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardKey() { return cardKey; }
    public void setCardKey(String cardKey) { this.cardKey = cardKey; }
    public Integer getQuotaAmount() { return quotaAmount; }
    public void setQuotaAmount(Integer quotaAmount) { this.quotaAmount = quotaAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getUsedByTeacherId() { return usedByTeacherId; }
    public void setUsedByTeacherId(Long usedByTeacherId) { this.usedByTeacherId = usedByTeacherId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
