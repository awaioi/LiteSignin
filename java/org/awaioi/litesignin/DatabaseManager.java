package org.awaioi.litesignin;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;
    private final File dbFile;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "signin_data.db");
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // 确保数据文件夹存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // 连接到SQLite数据库
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            
            // 创建玩家签到表
            try (Statement stmt = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS player_signins (" +
                             "uuid TEXT," +
                             "sign_date TEXT NOT NULL," +
                             "PRIMARY KEY (uuid, sign_date))";
                stmt.execute(sql);
            }
            
            plugin.getLogger().info("数据库初始化成功");
        } catch (Exception e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
        }
    }

    public String getPlayerLastSigninDate(UUID playerUUID) {
        try {
            String sql = "SELECT sign_date FROM player_signins WHERE uuid = ? ORDER BY sign_date DESC LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("sign_date");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家签到数据失败: " + e.getMessage());
        }
        return "";
    }

    public void setPlayerLastSigninDate(UUID playerUUID, String date) {
        try {
            String sql = "INSERT OR REPLACE INTO player_signins (uuid, sign_date) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, date);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("设置玩家签到数据失败: " + e.getMessage());
        }
    }

    public boolean hasPlayerSignedToday(UUID playerUUID) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return hasPlayerSignedOnDate(playerUUID, LocalDate.now());
    }
    
    public boolean hasPlayerSignedOnDate(UUID playerUUID, LocalDate date) {
        String dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        try {
            String sql = "SELECT COUNT(*) FROM player_signins WHERE uuid = ? AND sign_date = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, dateString);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查玩家签到状态失败: " + e.getMessage());
        }
        return false;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }
}