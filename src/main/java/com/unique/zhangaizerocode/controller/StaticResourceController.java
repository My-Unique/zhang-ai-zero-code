package com.unique.zhangaizerocode.controller;

import com.unique.zhangaizerocode.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.file.Path;

@RestController
@RequestMapping("/static")
public class StaticResourceController {

    /**
     * 应用部署根目录
     * 例如：tmp/code_deploy
     */
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    /**
     * 提供部署后静态资源访问
     * 访问格式：
     * http://localhost:8123/api/static/{deployKey}
     * http://localhost:8123/api/static/{deployKey}/index.html
     * http://localhost:8123/api/static/{deployKey}/style.css
     * http://localhost:8123/api/static/{deployKey}/script.js
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(@PathVariable String deployKey,
                                                        HttpServletRequest request) {
        try {
            // 获取当前请求路径，例如 /static/2nQb6O/index.html
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            // 去掉 /static/{deployKey}
            String prefix = "/static/" + deployKey;
            resourcePath = resourcePath.substring(prefix.length());

            // 访问 /static/{deployKey} 时，重定向到 /static/{deployKey}/
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            // 访问 /static/{deployKey}/ 时，默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }

            // 部署目录：tmp/code_deploy/{deployKey}
            File deployDir = new File(DEPLOY_ROOT_DIR, deployKey);

            // 目标文件：tmp/code_deploy/{deployKey}/index.html
            File targetFile = new File(deployDir, resourcePath);

            // 防止路径穿越攻击，例如 ../
            Path deployPath = deployDir.toPath().toAbsolutePath().normalize();
            Path targetPath = targetFile.toPath().toAbsolutePath().normalize();

            if (!targetPath.startsWith(deployPath)) {
                return ResponseEntity.badRequest().build();
            }

            if (!targetFile.exists() || !targetFile.isFile()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(targetFile);

            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(targetFile.getName()))
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回 Content-Type
     */
    private String getContentTypeWithCharset(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
