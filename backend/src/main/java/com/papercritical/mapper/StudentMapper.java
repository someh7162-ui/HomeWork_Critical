package com.papercritical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.papercritical.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
