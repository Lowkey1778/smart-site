-- 修正摄像头 HLS 地址：nginx-rtmp hls_path 直接输出 {stream}.m3u8（无子目录）
USE `smart_site`;
UPDATE `t_camera`
SET `stream_url` = CASE
    WHEN `camera_code` = 'CAM-001' THEN 'http://localhost:8068/hls/cam1.m3u8'
    WHEN `camera_code` = 'CAM-002' THEN 'http://localhost:8068/hls/cam2.m3u8'
    ELSE `stream_url`
END
WHERE `camera_code` IN ('CAM-001', 'CAM-002');
