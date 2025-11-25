package org.economyplugin.economy;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class EconomyLogger {

    private static File logFile;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 멀티스레드 안전을 위한 락
    private static final Object LOCK = new Object();

    private EconomyLogger() { }

    public static void init(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        logFile = new File(dataFolder, "transaction.log");
        // 파일 존재 보장
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void log(String message) {
        if (logFile == null) {
            // init 호출 누락 방어
            System.err.println("[EconomyLogger] logFile is null. Call EconomyLogger.init(plugin) first.");
            return;
        }

        String line = "[" + LocalDateTime.now().format(FORMATTER) + "] " + message;

        synchronized (LOCK) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                bw.write(line);
                bw.newLine();
                // try-with-resources가 자동 flush/close 처리
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
