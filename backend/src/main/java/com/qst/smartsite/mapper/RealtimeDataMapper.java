package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.RealtimeData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RealtimeDataMapper extends BaseMapper<RealtimeData> {

    /**
     * 查询所有设备每个参数的最新一条数据（子查询取 MAX(collect_time)）
     */
    @Select("SELECT rd.id, rd.device_id, rd.point_id, rd.param_code, rd.param_value, rd.unit, rd.collect_time, rd.create_time " +
            "FROM t_realtime_data rd " +
            "JOIN (SELECT device_id, param_code, MAX(collect_time) mt FROM t_realtime_data GROUP BY device_id, param_code) t " +
            "ON rd.device_id = t.device_id AND rd.param_code = t.param_code AND rd.collect_time = t.mt")
    List<RealtimeData> selectLatestByDeviceParam();

    /**
     * 查询指定设备每个参数的最新一条
     */
    @Select("SELECT rd.id, rd.device_id, rd.point_id, rd.param_code, rd.param_value, rd.unit, rd.collect_time, rd.create_time " +
            "FROM t_realtime_data rd " +
            "JOIN (SELECT param_code, MAX(collect_time) mt FROM t_realtime_data WHERE device_id = #{deviceId} GROUP BY param_code) t " +
            "ON rd.param_code = t.param_code AND rd.collect_time = t.mt " +
            "WHERE rd.device_id = #{deviceId}")
    List<RealtimeData> selectLatestByDevice(Long deviceId);
}
