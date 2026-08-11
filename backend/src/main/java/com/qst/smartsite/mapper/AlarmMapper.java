package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {

    /** 按告警级别统计 */
    @Select("SELECT alarm_level AS level, COUNT(*) AS cnt FROM t_alarm GROUP BY alarm_level")
    List<Map<String, Object>> countByLevel();

    /** 按处置状态统计 */
    @Select("SELECT handle_status AS status, COUNT(*) AS cnt FROM t_alarm GROUP BY handle_status")
    List<Map<String, Object>> countByHandleStatus();

    /** 按告警来源统计 */
    @Select("SELECT alarm_source AS source, COUNT(*) AS cnt FROM t_alarm GROUP BY alarm_source")
    List<Map<String, Object>> countBySource();

    /** 近 N 天每日告警趋势 */
    @Select("SELECT DATE_FORMAT(alarm_time, '%Y-%m-%d') AS day, COUNT(*) AS cnt " +
            "FROM t_alarm WHERE alarm_time >= #{startDate} GROUP BY day ORDER BY day")
    List<Map<String, Object>> countByDay(String startDate);

    /** 按设备类型统计告警分布（告警来源关联设备 → 设备类型） */
    @Select("SELECT IFNULL(dt.type_name, '未关联设备') AS typeName, COUNT(*) AS cnt " +
            "FROM t_alarm a " +
            "LEFT JOIN t_device d ON a.device_id = d.id " +
            "LEFT JOIN t_device_type dt ON d.type_id = dt.id " +
            "GROUP BY dt.type_name ORDER BY cnt DESC")
    List<Map<String, Object>> countByDeviceType();

    /** 处置及时率：已处置告警中处置耗时 ≤ 24 小时的比例；avg_minutes 为平均处置时长(分钟) */
    @Select("SELECT COUNT(*) AS total_handled, " +
            "COALESCE(SUM(CASE WHEN TIMESTAMPDIFF(HOUR, alarm_time, handle_time) <= 24 THEN 1 ELSE 0 END), 0) AS timely_cnt, " +
            "IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, alarm_time, handle_time)), 1), 0) AS avg_minutes " +
            "FROM t_alarm WHERE handle_status = 2")
    Map<String, Object> handleTimeliness();

    /** 按告警类型（内容关键词分类）统计占比 */
    @Select("SELECT CASE " +
            "WHEN alarm_content LIKE '%安全帽%' THEN '安全帽未佩戴' " +
            "WHEN alarm_content LIKE '%安全服%' THEN '安全服未穿' " +
            "WHEN alarm_content LIKE '%吸烟%' THEN '现场吸烟' " +
            "WHEN alarm_content LIKE '%明火%' THEN '明火' " +
            "WHEN alarm_content LIKE '%力矩%' THEN '塔吊力矩' " +
            "WHEN alarm_content LIKE '%吊重%' THEN '塔吊吊重' " +
            "WHEN alarm_content LIKE '%载重%' THEN '升降机载重' " +
            "WHEN alarm_content LIKE '%超员%' THEN '升降机超员' " +
            "WHEN alarm_content LIKE '%门%' THEN '门锁异常' " +
            "WHEN alarm_content LIKE '%风速%' THEN '风速超标' " +
            "WHEN alarm_content LIKE '%PM2.5%' THEN 'PM2.5' " +
            "WHEN alarm_content LIKE '%PM10%' THEN 'PM10' " +
            "WHEN alarm_content LIKE '%噪声%' THEN '噪声' " +
            "WHEN alarm_content LIKE '%温度%' THEN '温度' " +
            "WHEN alarm_content LIKE '%湿度%' THEN '湿度' " +
            "ELSE '其他' END AS typeName, COUNT(*) AS cnt " +
            "FROM t_alarm GROUP BY typeName ORDER BY cnt DESC")
    List<Map<String, Object>> countByType();
}
