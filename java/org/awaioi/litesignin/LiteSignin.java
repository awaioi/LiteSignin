package org.awaioi.litesignin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.World;
import org.bukkit.inventory.meta.FireworkMeta;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class LiteSignin extends JavaPlugin implements Listener {

    private File configFile;
    private FileConfiguration config;
    private Economy economy = null;
    private boolean vaultEnabled = false;
    private DatabaseManager databaseManager;
    private Random random = new Random();

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("LiteSignin 插件正在启动...");
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 加载配置文件
        loadConfig();
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);
        
        // 设置 Vault 经济插件
        setupEconomy();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 注册GUI监听器
        getServer().getPluginManager().registerEvents(new SigninGUI(this), this);
        
        getLogger().info("LiteSignin 插件启动完成!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("LiteSignin 插件正在关闭...");
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        
        getLogger().info("LiteSignin 插件关闭完成!");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("signin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家可以使用此命令！");
                return true;
            }
            
            Player player = (Player) sender;
            
            // 检查是否有gui参数
            if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
                // 打开GUI
                SigninGUI.openSigninGUI(player, this);
                return true;
            }
            
            // 检查玩家是否已经签到
            if (hasSignedToday(player)) {
                player.sendMessage("§a您今天已完成签到！");
                return true;
            }
            
            // 执行签到
            performSignIn(player);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("sigui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家可以使用此命令！");
                return true;
            }
            
            Player player = (Player) sender;
            SigninGUI.openSigninGUI(player, this);
            return true;
        }
        return false;
    }
    
    public boolean hasSignedToday(Player player) {
        return databaseManager.hasPlayerSignedToday(player.getUniqueId());
    }
    
    public boolean hasPlayerSignedOnDate(UUID playerId, LocalDate date) {
        return databaseManager.hasPlayerSignedOnDate(playerId, date);
    }
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("未找到 Vault 插件，经济奖励功能将不可用");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("未找到经济插件服务，经济奖励功能将不可用");
            return false;
        }
        
        economy = rsp.getProvider();
        vaultEnabled = true;
        getLogger().info("成功连接到 Vault 经济插件");
        return true;
    }
    
    private void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public void performSignIn(Player player) {
        UUID playerId = player.getUniqueId();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 标记玩家今日已签到
        databaseManager.setPlayerLastSigninDate(playerId, today);
        
        // 发放奖励
        giveRewards(player);
        
        // 播放烟花效果
        spawnFirework(player);
        
        getLogger().info(player.getName() + " 已成功签到");
    }
    
    private void giveRewards(Player player) {
        StringBuilder rewardMessage = new StringBuilder("§a§l恭喜完成今日签到！\n§7您获得了：");
        boolean hasRewards = false;
        
        // 检查是否启用物品奖励
        if (getConfig().getBoolean("rewards.items.enabled", true) && getConfig().contains("rewards.items.list")) {
            List<Map<?, ?>> items = getConfig().getMapList("rewards.items.list");
            for (Map<?, ?> itemData : items) {
                try {
                    String materialName = (String) itemData.get("material");
                    int amount = getRandomAmount(itemData);
                    
                    Material material = Material.getMaterial(materialName.toUpperCase());
                    if (material != null) {
                        ItemStack item = new ItemStack(material, amount);
                        player.getInventory().addItem(item);
                        
                        // 获取中文物品名称
                        String chineseName = getChineseMaterialName(material);
                        
                        if (hasRewards) {
                            rewardMessage.append(" §8| ");
                        }
                        rewardMessage.append("§b").append(chineseName).append(" §7x §6").append(amount);
                        hasRewards = true;
                        
                        if (getConfig().getBoolean("advanced.debug_logging", false)) {
                            getLogger().info("给予玩家 " + player.getName() + " 物品: " + chineseName + " x" + amount);
                        }
                    } else {
                        getLogger().warning("无效的物品材料: " + materialName);
                    }
                } catch (Exception e) {
                    getLogger().warning("物品奖励配置错误: " + e.getMessage());
                }
            }
        }
        
        // 检查是否启用经济奖励
        if (getConfig().getBoolean("rewards.money.enabled", true) && getConfig().contains("rewards.money.amount")) {
            double amount = getRandomMoneyAmount();
            String currency = getConfig().getString("rewards.money.currency", "金币");
            
            // 使用 Vault 经济插件发放货币
            if (vaultEnabled && economy != null) {
                economy.depositPlayer(player, amount);
                
                if (hasRewards) {
                    rewardMessage.append(" §8| ");
                }
                rewardMessage.append("§e").append(currency).append(" §7x §6").append(String.format("%.2f", amount));
                hasRewards = true;
                
                if (getConfig().getBoolean("advanced.debug_logging", false)) {
                    getLogger().info("通过 Vault 给予玩家 " + player.getName() + " 经济奖励: " + amount + " " + currency);
                }
            } else {
                // Vault 不可用时的备用方案
                if (hasRewards) {
                    rewardMessage.append(" §8| ");
                }
                rewardMessage.append("§e").append(currency).append(" §7x §6").append(String.format("%.2f", amount));
                hasRewards = true;
                
                if (getConfig().getBoolean("advanced.debug_logging", false)) {
                    getLogger().info("Vault 不可用，仅通知玩家获得经济奖励: " + amount + " " + currency);
                }
            }
        }
        
        // 发送奖励消息
        if (hasRewards) {
            player.sendMessage(rewardMessage.toString());
        } else {
            // 如果没有任何奖励，发送默认消息
            player.sendMessage("§a§l恭喜完成今日签到！");
        }
    }
    
    private int getRandomAmount(Map<?, ?> itemData) {
        // 检查是否配置了范围
        if (itemData.containsKey("amount_range")) {
            Map<String, Object> range = (Map<String, Object>) itemData.get("amount_range");
            if (range.containsKey("min") && range.containsKey("max")) {
                int min = (Integer) range.get("min");
                int max = (Integer) range.get("max");
                // 生成随机数并记录调试信息
                int result = random.nextInt(max - min + 1) + min;
                if (getConfig().getBoolean("advanced.debug_logging", false)) {
                    getLogger().info("物品随机数量生成: 范围 " + min + "-" + max + ", 结果 " + result);
                }
                return result;
            }
        }
        
        // 检查是否配置了固定数量
        if (itemData.containsKey("amount")) {
            return (Integer) itemData.get("amount");
        }
        
        // 默认返回1
        return 1;
    }
    
    private double getRandomMoneyAmount() {
        // 检查是否配置了范围
        if (getConfig().contains("rewards.money.amount_range")) {
            double min = getConfig().getDouble("rewards.money.amount_range.min");
            double max = getConfig().getDouble("rewards.money.amount_range.max");
            // 生成随机数并记录调试信息
            double result = min + (max - min) * random.nextDouble();
            if (getConfig().getBoolean("advanced.debug_logging", false)) {
                getLogger().info("货币随机金额生成: 范围 " + min + "-" + max + ", 结果 " + String.format("%.2f", result));
            }
            return result;
        }
        
        // 返回固定金额
        return getConfig().getDouble("rewards.money.amount", 100.0);
    }
    
    private String getChineseMaterialName(Material material) {
        // 这里可以添加更多物品的中文名称映射
        switch (material) {
            case DIAMOND: return "钻石";
            case GOLD_INGOT: return "金锭";
            case IRON_INGOT: return "铁锭";
            case COAL: return "煤炭";
            case EMERALD: return "绿宝石";
            case LAPIS_LAZULI: return "青金石";
            case REDSTONE: return "红石";
            case QUARTZ: return "下界石英";
            default: return material.name().toLowerCase().replace("_", " ");
        }
    }
    
    private void spawnFirework(Player player) {
        // 检查是否启用烟花效果
        if (!getConfig().getBoolean("advanced.enable_firework", true)) {
            return;
        }
        
        World world = player.getWorld();
        Firework fw = world.spawn(player.getLocation(), Firework.class);
        
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.GREEN)
                .with(FireworkEffect.Type.BALL) // 改为较小的球形效果
                .trail(false) // 移除轨迹
                .flicker(false) // 移除闪烁
                .build();
        
        meta.addEffect(effect);
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 检查玩家是否已经签到
        if (hasSignedToday(player)) {
            player.sendMessage("§a您今天已完成签到！");
        } else {
            // 发送可点击的消息
            TextComponent message = new TextComponent("§7您今天还未签到，点击 ");
            TextComponent clickMe = new TextComponent("§2§l[✔]");
            clickMe.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/signin"));
            TextComponent end = new TextComponent(" §7进行签到");
            
            player.spigot().sendMessage(new ComponentBuilder().append(message).append(clickMe).append(end).create());
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 移除玩家临时数据的操作（如果有的话）
    }
    
    // 提供公共方法供GUI使用
    public FileConfiguration getPluginConfig() {
        return getConfig();
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public boolean isVaultEnabled() {
        return vaultEnabled;
    }
    
    // 提供随机数生成器供测试使用
    public Random getRandom() {
        return random;
    }
}