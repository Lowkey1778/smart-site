package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Device;
import com.qst.smartsite.entity.DeviceType;
import com.qst.smartsite.mapper.DeviceMapper;
import com.qst.smartsite.mapper.DeviceTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备类型管理接口（T-06，RQ-06）
 * 树形结构多级嵌套，增删改
 */
@RestController
@RequestMapping("/api/device-type")
public class DeviceTypeController {

    @Autowired
    private DeviceTypeMapper deviceTypeMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    /** 类型树（多级嵌套） */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        List<DeviceType> list = deviceTypeMapper.selectList(
                new LambdaQueryWrapper<DeviceType>().orderByAsc(DeviceType::getSort));
        return Result.ok(buildTree(list, 0L));
    }

    /** 新增类型 */
    @PostMapping
    public Result<Void> add(@RequestBody DeviceType type) {
        if (type.getTypeName() == null || type.getTypeName().isBlank()) {
            throw new BusinessException(400, "类型名称不能为空");
        }
        if (type.getParentId() == null) type.setParentId(0L);
        if (type.getSort() == null) type.setSort(0);
        deviceTypeMapper.insert(type);
        return Result.ok();
    }

    /** 编辑类型 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DeviceType type) {
        DeviceType db = deviceTypeMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "类型不存在");
        }
        if (id.equals(type.getParentId())) {
            throw new BusinessException(400, "上级类型不能选择自身");
        }
        type.setId(id);
        deviceTypeMapper.updateById(type);
        return Result.ok();
    }

    /** 删除类型（存在子节点或已关联设备则拒绝） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Long child = deviceTypeMapper.selectCount(
                new LambdaQueryWrapper<DeviceType>().eq(DeviceType::getParentId, id));
        if (child != null && child > 0) {
            throw new BusinessException(400, "请先删除该类型下的子类型");
        }
        Long dev = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getTypeId, id));
        if (dev != null && dev > 0) {
            throw new BusinessException(400, "该类型下已关联 " + dev + " 台设备，无法删除");
        }
        deviceTypeMapper.deleteById(id);
        return Result.ok();
    }

    private List<Map<String, Object>> buildTree(List<DeviceType> list, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (DeviceType t : list) {
            if (!parentId.equals(t.getParentId())) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", t.getId());
            node.put("parentId", t.getParentId());
            node.put("typeName", t.getTypeName());
            node.put("sort", t.getSort());
            List<Map<String, Object>> children = buildTree(list, t.getId());
            if (!children.isEmpty()) {
                node.put("children", children);
            }
            nodes.add(node);
        }
        return nodes;
    }
}
