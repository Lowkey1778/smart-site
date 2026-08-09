package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.DeviceOfflineRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 设备离线记录 Mapper
 */
@Mapper
public interface DeviceOfflineRecordMapper extends BaseMapper<DeviceOfflineRecord> {

    /** 指定设备在时间段内的状态变更记录（升序） */
    @Select("SELECT * FROM t_device_offline_record " +
            "WHERE device_id = #{deviceId} AND record_time >= #{start} AND record_time <= #{end} " +
            "ORDER BY record_time ASC")
    java.util.List<DeviceOfflineRecord> selectRange(Long deviceId, String start, String end);

    /** 指定设备在时间段内的离线次数 */
    @Select("SELECT COUNT(*) FROM t_device_offline_record " +
            "WHERE device_id = #{deviceId} AND status = 1 AND record_time >= #{start} AND record_time <= #{end}")
    long countOffline(Long deviceId, String start, String end);

    /** 指定设备在时间段内的状态变更总次数 */
    @Select("SELECT COUNT(*) FROM t_device_offline_record " +
            "WHERE device_id = #{deviceId} AND record_time >= #{start} AND record_time <= #{end}")
    long countChange(Long deviceId, String start, String end);
}
