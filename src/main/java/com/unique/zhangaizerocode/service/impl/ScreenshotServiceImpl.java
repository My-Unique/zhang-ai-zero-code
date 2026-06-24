package com.unique.zhangaizerocode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.manager.CosManager;
import com.unique.zhangaizerocode.service.ScreenshotService;
import com.unique.zhangaizerocode.utils.WebScreenshotUtils;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    /**
     * 截图工作线程数量。每个工作线程会通过 ThreadLocal 复用自己的 WebDriver。
     */
    private static final int SCREENSHOT_WORKER_COUNT = 2;

    /**
     * 单个截图任务最大等待时间，避免截图页面卡住导致异步任务长期不结束。
     */
    private static final int SCREENSHOT_TIMEOUT_SECONDS = 90;

    /**
     * 本地截图临时目录保留时间，超过该时间的残留目录会被定时清理。
     */
    private static final long TEMP_FILE_EXPIRE_MILLIS = TimeUnit.HOURS.toMillis(24);

    private static final AtomicInteger SCREENSHOT_THREAD_COUNTER = new AtomicInteger();

    /**
     * 固定截图线程池。不要直接在外层虚拟线程里创建 ThreadLocal WebDriver，
     * 否则每个截图任务都可能创建新的浏览器实例，达不到复用效果。
     */
    private final ExecutorService screenshotExecutor = Executors.newFixedThreadPool(
            SCREENSHOT_WORKER_COUNT,
            runnable -> {
                Thread thread = new Thread(runnable,
                        "screenshot-worker-" + SCREENSHOT_THREAD_COUNTER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
    );

    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "网页 URL 不能为空");
        try {
            // 提交到固定截图线程池执行，使 ThreadLocal WebDriver 绑定到稳定的工作线程。
            return screenshotExecutor.submit(() -> doGenerateAndUploadScreenshot(webUrl))
                    .get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "截图任务被中断");
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "截图任务执行超时");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "截图任务执行失败");
        }
    }

    /**
     * 在线程池工作线程内执行真正的截图上传流程。
     */
    private String doGenerateAndUploadScreenshot(String webUrl) {
        log.info("开始生成网页截图，URL: {}", webUrl);
        // 1. 生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR,
                "本地截图生成失败");
        try {
            // 2. 上传到对象存储
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR,
                    "截图上传对象存储失败");
            log.info("网页截图生成并上传成功: {} -> {}", webUrl, cosUrl);
            return cosUrl;
        } finally {
            // 3. 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 应用关闭时释放截图线程池和所有 WebDriver。
     */
    @PreDestroy
    public void destroy() {
        screenshotExecutor.shutdown();
        try {
            if (!screenshotExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                screenshotExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            screenshotExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        WebScreenshotUtils.destroyAllWebDrivers();
    }

    /**
     * 定时清理过期的本地截图临时目录，防止异常场景下残留文件持续堆积。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredScreenshotTempFiles() {
        File screenshotRootDir = new File(System.getProperty("user.dir"), "tmp/screenshots");
        if (!screenshotRootDir.exists()) {
            return;
        }
        File[] tempDirs = screenshotRootDir.listFiles(File::isDirectory);
        if (tempDirs == null) {
            return;
        }
        long expireBefore = System.currentTimeMillis() - TEMP_FILE_EXPIRE_MILLIS;
        for (File tempDir : tempDirs) {
            if (tempDir.lastModified() < expireBefore) {
                FileUtil.del(tempDir);
                log.info("已清理过期截图临时目录: {}", tempDir.getAbsolutePath());
            }
        }
    }

    /**
     * 上传截图到对象存储。
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问 URL，失败返回 null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键。
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地截图文件所在目录。
     *
     * @param localFilePath 本地截图文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("本地截图文件已清理: {}", localFilePath);
        }
    }
}
