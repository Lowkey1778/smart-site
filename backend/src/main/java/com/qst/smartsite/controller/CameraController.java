package com.qst.smartsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartsite.common.BusinessException;
import com.qst.smartsite.common.Result;
import com.qst.smartsite.entity.Camera;
import com.qst.smartsite.mapper.CameraMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 摄像头管理接口
 * 对应《页面功能清单》六、视频监控
 */
@RestController
@RequestMapping("/api/camera")
public class CameraController {

    @Autowired
    private CameraMapper cameraMapper;

    /** 摄像头列表 */
    @GetMapping("/list")
    public Result<List<Camera>> list() {
        return Result.ok(cameraMapper.selectList(
                new LambdaQueryWrapper<Camera>().orderByAsc(Camera::getId)));
    }

    /** 新增摄像头 */
    @PostMapping
    public Result<Void> add(@RequestBody Camera camera) {
        if (camera.getCameraCode() == null || camera.getCameraCode().isBlank()) {
            throw new BusinessException(400, "摄像头编码不能为空");
        }
        Long exists = cameraMapper.selectCount(
                new LambdaQueryWrapper<Camera>().eq(Camera::getCameraCode, camera.getCameraCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "摄像头编码已存在");
        }
        if (camera.getOnlineStatus() == null) camera.setOnlineStatus(1);
        if (camera.getEnableStatus() == null) camera.setEnableStatus(1);
        cameraMapper.insert(camera);
        return Result.ok();
    }

    /** 编辑摄像头 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Camera camera) {
        Camera db = cameraMapper.selectById(id);
        if (db == null) {
            throw new BusinessException(404, "摄像头不存在");
        }
        camera.setId(id);
        cameraMapper.updateById(camera);
        return Result.ok();
    }


    /** AI 截帧目录（application.yml ai.capture-dir） */
    @Value("${ai.capture-dir:}")
    private String captureDir;

    /**
     * 摄像头缩略图（T-29 / RQ-36）：扫描 AI 截帧目录，返回每个摄像头最新一帧截图
     * 用于数据大屏视频监控缩略图区域
     */
    @GetMapping("/thumbs")
    public Result<List<Map<String, Object>>> thumbs() {
        List<Camera> cameras = cameraMapper.selectList(
                new LambdaQueryWrapper<Camera>().orderByAsc(Camera::getId));
        // cameraCode -> 最新截图 URL（文件名按时间戳排序，取最后一张）
        Map<String, String> latestByCamera = new LinkedHashMap<>();
        if (captureDir != null && !captureDir.isBlank()) {
            File dir = new File(captureDir);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    String name = f.getName();
                    int idx = name.indexOf('_');
                    if (idx > 0) {
                        latestByCamera.put(name.substring(0, idx), "/ai-capture/" + name);
                    }
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Camera c : cameras) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cameraId", c.getId());
            item.put("cameraCode", c.getCameraCode());
            item.put("cameraName", c.getCameraName());
            item.put("onlineStatus", c.getOnlineStatus());
            item.put("aiHelmet", c.getAiHelmet());
            item.put("aiVest", c.getAiVest());
            item.put("aiSmoke", c.getAiSmoke());
            item.put("aiFire", c.getAiFire());
            item.put("thumbUrl", latestByCamera.get(c.getCameraCode()));
            result.add(item);
        }
        return Result.ok(result);
    }
    /** 删除摄像头 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cameraMapper.deleteById(id);
        return Result.ok();
    }
}
