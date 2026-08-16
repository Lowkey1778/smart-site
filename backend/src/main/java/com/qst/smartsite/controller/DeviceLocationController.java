package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Camera;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.entity.DeviceLocation;
import com.qst.smartsite.mapper.CameraMapper;
import com.qst.smartsite.mapper.DeviceLocationMapper;
import com.qst.smartsite.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备位置管理接口（T-07，RQ-07）
 * 工地区域/位置层级树，增删改
 */
@RestController
@RequestMapping("/api/device-location")
public class DeviceLocationController {

    @Autowired
    private DeviceLocationMapper deviceLocationMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private CameraMapper cameraMapper;

    /** 位置树（多级嵌套） */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        List<DeviceLocation> list = deviceLocationMapper.selectList(
                new LambdaQueryWrapper<DeviceLocation>().orderByAsc(DeviceLocation::getSort));
        return Result.ok(buildTree(list, 0L));
    }

    /** 新增位置 */
    @PostMapping
    public Result<Void> add(@RequestBody DeviceLocation loc) {
        if (loc.getLocationName() == null || loc.getLocationName().isBlank()) {
            throw new BusinessException(400, "位置名称不能为空");
        }
        if (loc.getParentId() == null) loc.setParentId(0L);
        if (loc.getSort() == null) loc.setSort(0);
        deviceLocationMapper.insert(loc);
        return Result.ok();
    }

    /** 编辑位置 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DeviceLocation loc) {
        DeviceLocation db = deviceLocationMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "位置不存在");
        }
        if (id.equals(loc.getParentId())) {
            throw new BusinessException(400, "上级位置不能选择自身");
        }
        loc.setId(id);
        deviceLocationMapper.updateById(loc);
        return Result.ok();
    }

    /** 删除位置（存在子节点或已关联设备/摄像头则拒绝） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Long child = deviceLocationMapper.selectCount(
                new LambdaQueryWrapper<DeviceLocation>().eq(DeviceLocation::getParentId, id));
        if (child != null && child > 0) {
            throw new BusinessException(400, "请先删除该位置下的子位置");
        }
        Long dev = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getLocationId, id));
        if (dev != null && dev > 0) {
            throw new BusinessException(400, "该位置下已关联 " + dev + " 台设备，无法删除");
        }
        Long cam = cameraMapper.selectCount(
                new LambdaQueryWrapper<Camera>().eq(Camera::getLocationId, id));
        if (cam != null && cam > 0) {
            throw new BusinessException(400, "该位置下已关联 " + cam + " 个摄像头，无法删除");
        }
        deviceLocationMapper.deleteById(id);
        return Result.ok();
    }

    private List<Map<String, Object>> buildTree(List<DeviceLocation> list, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (DeviceLocation l : list) {
            if (!parentId.equals(l.getParentId())) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", l.getId());
            node.put("parentId", l.getParentId());
            node.put("locationName", l.getLocationName());
            node.put("sort", l.getSort());
            List<Map<String, Object>> children = buildTree(list, l.getId());
            if (!children.isEmpty()) {
                node.put("children", children);
            }
            nodes.add(node);
        }
        return nodes;
    }
}
