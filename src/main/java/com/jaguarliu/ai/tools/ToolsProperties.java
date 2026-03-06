package com.jaguarliu.ai.tools;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tools")
public class ToolsProperties {

    /**
     * 工作空间目录（工具只能访问此目录内的文件）
     */
    private String workspace = "./workspace";

    /**
     * 最大文件大小（字节）
     */
    private long maxFileSize = 20971520; // 20MB

    /**
     * 文件上传存放目录（相对于工作空间）
     */
    private String uploadDir = "uploads";

    /**
     * 内置运行时配置（Node/Python）
     */
    private RuntimeProperties runtime = new RuntimeProperties();

    @Data
    public static class RuntimeProperties {

        /**
         * 是否启用内置 runtime 注入
         */
        private boolean enabled = false;

        /**
         * 内置 runtime 根目录（绝对/相对路径）
         */
        private String home = "";

        /**
         * runtime 可执行目录列表（相对于 home 或绝对路径）
         */
        private List<String> binPaths = new ArrayList<>();

        /**
         * agent-browser 可执行文件路径（绝对路径或相对于 runtime home）
         */
        private String agentBrowserExecutablePath = "";

        /**
         * Chromium 可执行文件路径（绝对路径或相对于 runtime home）
         */
        private String chromiumExecutablePath = "";

        /**
         * Chromium 根目录（绝对路径或相对于 runtime home）
         */
        private String chromiumHome = "";
    }
}
