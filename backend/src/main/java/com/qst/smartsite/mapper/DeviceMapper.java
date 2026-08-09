package com.qst.smartsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qst.smartsite.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备台账 Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 设备分页查询（关键字 + 类型 + 位置 + 状态筛选），回填类型/位置名称
     */
    @Select("<script>" +
            "SELECT d.*, dt.type_name AS typeName, dl.location_name AS locationName " +
            "FROM t_device d " +
            "LEFT JOIN t_device_type dt ON d.type_id = dt.id " +
            "LEFT JOIN t_device_location dl ON d.location_id = dl.id " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (d.device_code LIKE CONCAT('%', #{keyword}, '%') " +
            " OR d.device_name LIKE CONCAT('%', #{keyword}, '%') " +
            " OR d.brand LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='typeId != null'> AND d.type_id = #{typeId}</if>" +
            "<if test='locationId != null'> AND d.location_id = #{locationId}</if>" +
            "<if test='status != null'> AND d.status = #{status}</if>" +
            "</where> ORDER BY d.id DESC" +
            "</script>")
    IPage<Device> selectDevicePage(IPage<?> page, @Param("keyword") String keyword,
                                   @Param("typeId") Long typeId, @Param("locationId") Long locationId,
                                   @Param("status") Integer status);

    /**
     * 设备详情（含类型/位置名称）
     */
    @Select("SELECT d.*, dt.type_name AS typeName, dl.location_name AS locationName " +
            "FROM t_device d " +
            "LEFT JOIN t_device_type dt ON d.type_id = dt.id " +
            "LEFT JOIN t_device_location dl ON d.location_id = dl.id " +
            "WHERE d.id = #{id}")
    Device selectDetail(Long id);
}
