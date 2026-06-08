package com.papercritical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.papercritical.entity.Essay;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EssayMapper extends BaseMapper<Essay> {
}
