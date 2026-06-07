package com.example.snake_back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.snake_back.pojo.entity.PrivateMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {
}
