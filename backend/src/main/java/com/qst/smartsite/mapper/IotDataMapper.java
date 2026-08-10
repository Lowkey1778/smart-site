package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.IotData;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 上报数据 Mapper
 */
@Mapper
public interface IotDataMapper extends BaseMapper<IotData> {
}
