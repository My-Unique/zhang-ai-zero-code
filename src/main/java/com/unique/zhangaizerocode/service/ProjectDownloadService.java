package com.unique.zhangaizerocode.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public interface ProjectDownloadService {

/**
 * 将指定项目路径下的内容打包为ZIP文件并下载
 *
 * @param projectPath 需要打包的项目路径
 * @param downloadFileName 下载时显示的文件名
 * @param response HTTP响应对象，用于输出ZIP文件流
 */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);

}
