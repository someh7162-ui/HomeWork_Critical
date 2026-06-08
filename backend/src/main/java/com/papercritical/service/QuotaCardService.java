package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.entity.QuotaCard;
import com.papercritical.entity.Teacher;
import com.papercritical.mapper.QuotaCardMapper;
import com.papercritical.mapper.TeacherMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuotaCardService {

    private final QuotaCardMapper cardMapper;
    private final TeacherMapper teacherMapper;
    private final TeacherQuotaService quotaService;

    public QuotaCardService(QuotaCardMapper cardMapper, TeacherMapper teacherMapper,
                            TeacherQuotaService quotaService) {
        this.cardMapper = cardMapper;
        this.teacherMapper = teacherMapper;
        this.quotaService = quotaService;
    }

    public Map<String, Object> generateCards(int amount, int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = generateKey();
            QuotaCard card = new QuotaCard();
            card.setCardKey(key);
            card.setQuotaAmount(amount);
            card.setStatus("UNUSED");
            cardMapper.insert(card);
            keys.add(key);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("amount", amount);
        result.put("count", count);
        result.put("keys", keys);
        return result;
    }

    public List<QuotaCard> listCards(String status) {
        LambdaQueryWrapper<QuotaCard> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            w.eq(QuotaCard::getStatus, status);
        }
        w.orderByDesc(QuotaCard::getCreatedAt);
        return cardMapper.selectList(w);
    }

    public Map<String, Object> redeem(String key, Long teacherId) {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        LambdaQueryWrapper<QuotaCard> w = new LambdaQueryWrapper<>();
        w.eq(QuotaCard::getCardKey, key.trim());
        QuotaCard card = cardMapper.selectOne(w);
        if (card == null) {
            throw new IllegalArgumentException("卡密无效");
        }
        if (!"UNUSED".equals(card.getStatus())) {
            throw new IllegalArgumentException("该卡密已被使用");
        }
        card.setStatus("USED");
        card.setUsedByTeacherId(teacherId);
        card.setUsedAt(LocalDateTime.now());
        cardMapper.updateById(card);

        quotaService.addPaidQuota(teacherId, card.getQuotaAmount());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("addedQuota", card.getQuotaAmount());
        result.put("cardKey", card.getCardKey());
        return result;
    }

    private String generateKey() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
            if (i > 0 && i % 4 == 3 && i < 15) sb.append('-');
        }
        return sb.toString();
    }
}
