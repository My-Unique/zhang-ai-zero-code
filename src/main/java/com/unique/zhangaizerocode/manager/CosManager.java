package com.unique.zhangaizerocode.manager;

import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.unique.zhangaizerocode.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 腾讯云 COS 对象存储管理器。
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象。
     *
     * @param key  对象键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS，并返回访问 URL。
     *
     * @param key  COS 对象键
     * @param file 要上传的文件
     * @return 文件访问 URL，失败返回 null
     */
    public String uploadFile(String key, File file) {
        PutObjectResult result = putObject(key, file);
        if (result == null) {
            log.error("文件上传 COS 失败，返回结果为空");
            return null;
        }
        String url = String.format("%s%s", cosClientConfig.getHost(), key);
        log.info("文件上传 COS 成功: {} -> {}", file.getName(), url);
        return url;
    }

    /**
     * 根据完整访问 URL 删除 COS 文件。
     *
     * @param fileUrl 完整访问 URL
     */
    public void deleteFileByUrl(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return;
        }
        String host = cosClientConfig.getHost();
        if (StrUtil.isBlank(host) || !fileUrl.startsWith(host)) {
            log.warn("文件 URL 不属于当前 COS host，跳过删除: {}", fileUrl);
            return;
        }
        String key = fileUrl.substring(host.length());
        if (StrUtil.isBlank(key)) {
            return;
        }
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
        log.info("文件已从 COS 删除: {}", fileUrl);
    }
}
