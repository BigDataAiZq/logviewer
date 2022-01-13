package com.zjh.logviewer;

import org.jflame.commons.config.ConfigKey;
import org.jflame.commons.config.PropertiesConfigHolder;
import org.jflame.commons.util.file.FileHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SysParam {

    static {
        PropertiesConfigHolder.loadProperties("classpath:system.properties");
    }
    public static String SESSION_CURRENT_USER = "current_user_key";

    private static volatile Path TMP_DIR;

    /**
     * 程序监听端口
     * 
     * @return 端口
     */
    public static int getServerPort() {
        int port = 8889;
        String portStr = System.getenv("port");
        if (portStr != null) {
            port = Integer.parseInt(System.getenv("port"));
        } else {
            port = PropertiesConfigHolder.getInt(new ConfigKey<>("server.port", port));
        }
        return port;
    }

    /**
     * 请求应用路径
     * 
     * @return
     */
    public static String getServerContextPath() {
        return PropertiesConfigHolder.getStringOrDefault("server.context", "");
    }

    /**
     * 应用域名
     * 
     * @return
     */
    public static String getServerHost() {
        return PropertiesConfigHolder.getString("server.host");
    }

    public static String getUser() {
        return PropertiesConfigHolder.getString("sys.user");
    }

    public static String getUserpwd() {
        return PropertiesConfigHolder.getString("sys.pwd");
    }

    public static Path tmpDir() throws IOException {
        if (TMP_DIR == null) {
            TMP_DIR = Files.createTempDirectory("logviewer");
            TMP_DIR.toFile()
                    .setWritable(true);
        }
        return TMP_DIR;
    }

    public static void clearTmpDir() {
        if (TMP_DIR != null) {
            try {
                FileHelper.deleteDirectory(TMP_DIR.toFile());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
