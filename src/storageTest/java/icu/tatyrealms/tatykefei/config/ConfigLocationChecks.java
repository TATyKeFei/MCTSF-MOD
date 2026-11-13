package icu.tatyrealms.tatykefei.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 检查配置目录调整时保留已有设置，避免连接凭据丢失。 */
public final class ConfigLocationChecks {
    public static void run() throws Exception {
        Path root = Files.createTempDirectory("tsf-config-location-");
        Path directory = root.resolve("tsf");
        Path config = directory.resolve("tsf-server.yml");
        Path previous = root.resolve("tsf-server.yml");
        try {
            String existing = "# 保留原有注释\nconfig-version: 1\nstorage:\n  sql:\n    password: '原有密码'\n";
            Files.writeString(previous, existing);
            var moved = TSFServerConfig.load(directory);
            require(moved.storage().sqlPassword.equals("原有密码"), "移动配置后保留密码");
            require(Files.readString(config).equals(existing), "移动配置后保留原文与注释");
            require(!Files.exists(previous), "旧 YAML 移入 TSF 目录");

            Files.writeString(previous, "config-version: 1\ngameplay:\n  max-skin-layers: 2\n");
            require(TSFServerConfig.load(directory).maxSkinLayers == 16, "优先读取新目录，不覆盖已有配置");
            require(Files.exists(previous), "新配置存在时保留旧文件");

            Files.delete(config);
            Files.writeString(previous, "config-version: 999\n");
            try {
                TSFServerConfig.load(directory);
                throw new AssertionError("无效 YAML 不应移动");
            } catch (IOException expected) {
                require(Files.exists(previous) && !Files.exists(config), "解析失败时保持配置原位");
            }
            Files.delete(previous);

            TSFServerConfig.load(directory);
            try (var stream = TSFServerConfig.class.getResourceAsStream("/tsf-server.yml")) {
                require(stream != null && java.util.Arrays.equals(stream.readAllBytes(), Files.readAllBytes(config)),
                        "首次启动在新目录原样生成内置模板");
            }
        } finally {
            Files.deleteIfExists(previous);
            Files.deleteIfExists(config);
            Files.deleteIfExists(directory);
            Files.deleteIfExists(root);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
