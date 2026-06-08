package com.papercritical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.papercritical.entity.TeacherQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TeacherQuotaMapper extends BaseMapper<TeacherQuota> {

    @Update("UPDATE tb_teacher_quota SET free_quota_used = free_quota_used + 1, updated_at = NOW() WHERE teacher_id = #{teacherId} AND status = 'ACTIVE' AND free_quota_total > free_quota_used")
    int consumeFreeQuota(@Param("teacherId") Long teacherId);

    @Update("UPDATE tb_teacher_quota SET paid_quota_used = paid_quota_used + 1, updated_at = NOW() WHERE teacher_id = #{teacherId} AND status = 'ACTIVE' AND paid_quota_total > paid_quota_used")
    int consumePaidQuota(@Param("teacherId") Long teacherId);

    @Update("UPDATE tb_teacher_quota SET free_quota_used = CASE WHEN free_quota_used > 0 THEN free_quota_used - 1 ELSE 0 END, updated_at = NOW() WHERE teacher_id = #{teacherId}")
    int refundFreeQuota(@Param("teacherId") Long teacherId);

    @Update("UPDATE tb_teacher_quota SET paid_quota_used = CASE WHEN paid_quota_used > 0 THEN paid_quota_used - 1 ELSE 0 END, updated_at = NOW() WHERE teacher_id = #{teacherId}")
    int refundPaidQuota(@Param("teacherId") Long teacherId);

    @Update("UPDATE tb_teacher_quota SET paid_quota_total = paid_quota_total + #{quotaAmount}, updated_at = NOW() WHERE teacher_id = #{teacherId} AND status = 'ACTIVE'")
    int addPaidQuota(@Param("teacherId") Long teacherId, @Param("quotaAmount") Integer quotaAmount);
}
