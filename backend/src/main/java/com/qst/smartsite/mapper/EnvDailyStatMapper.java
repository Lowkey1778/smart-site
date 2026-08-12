package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartsite.entity.EnvDailyStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 环境日统计数据访问（T-23 / RQ-31）
 */
@Mapper
public interface EnvDailyStatMapper extends BaseMapper<EnvDailyStat> {

    /** 按天聚合环境数据：最大值/最小值/平均值/条数 */
    @Select("SELECT DATE(collect_time) AS stat_date, " +
            "MAX(index_value) AS max_val, MIN(index_value) AS min_val, " +
            "ROUND(AVG(index_value), 2) AS avg_val, COUNT(*) AS data_count " +
            "FROM t_env_data " +
            "WHERE point_id = #{pointId} AND collect_time >= #{start} " +
            "GROUP BY DATE(collect_time) ORDER BY stat_date ASC")
    List<Map<String, Object>> aggregateDaily(@Param("pointId") Long pointId, @Param("start") LocalDateTime start);
}