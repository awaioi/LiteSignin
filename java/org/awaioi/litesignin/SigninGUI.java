package org.awaioi.litesignin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SigninGUI implements Listener {
    private static final String GUI_TITLE = "每日签到 - %s";
    private static LiteSignin plugin;
    // 添加点击冷却时间限制（防止重复点击）
    private static Map<UUID, Long> clickCooldown = new HashMap<>();
    private static final long COOLDOWN_TIME = 1000; // 1秒冷却时间
    
    public SigninGUI(LiteSignin plugin) {
        SigninGUI.plugin = plugin;
    }
    
    public static void openSigninGUI(Player player, LiteSignin plugin) {
        openSigninGUI(player, plugin, YearMonth.now());
    }
    
    public static void openSigninGUI(Player player, LiteSignin plugin, YearMonth yearMonth) {
        SigninGUI.plugin = plugin;
        
        // 创建一个6行9列的GUI (54格)
        String title = String.format(GUI_TITLE, yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        
        // 设置外围灰色玻璃板边框（第一行和最后一行）
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        // 填充第一行和最后一行
        for (int i = 0; i < 9; i++) { // 第一行(0-8)
            gui.setItem(i, border.clone());
        }
        for (int i = 45; i < 54; i++) { // 最后一行(45-53)
            gui.setItem(i, border.clone());
        }
        
        // 填充日期 (从第二行第一列开始，位置9-35，共27个格子)
        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();
        int daysInMonth = yearMonth.lengthOfMonth();
        
        for (int day = 1; day <= daysInMonth && day <= 27; day++) {
            LocalDate date = yearMonth.atDay(day);
            // 从第二行第一列开始(位置9)，依次排列
            int slot = 8 + day; // 位置从9开始(8+1)
            
            // 创建日期物品
            ItemStack dateItem;
            ItemMeta dateMeta = Bukkit.getItemFactory().getItemMeta(Material.WHITE_WOOL);
            
            // 判断日期状态
            if (date.isEqual(today)) {
                // 今天 - 检查是否已签到
                if (plugin.hasPlayerSignedOnDate(playerId, date)) {
                    // 今天已签到 - 绿色羊毛
                    dateItem = new ItemStack(Material.GREEN_WOOL);
                    dateMeta.setDisplayName("§a" + day + "日");
                } else {
                    // 今天未签到 - 黄色羊毛
                    dateItem = new ItemStack(Material.YELLOW_WOOL);
                    dateMeta.setDisplayName("§e§l" + day + "日");
                }
            } else if (plugin.hasPlayerSignedOnDate(playerId, date)) {
                // 已签到 - 绿色羊毛
                dateItem = new ItemStack(Material.GREEN_WOOL);
                dateMeta.setDisplayName("§a" + day + "日");
            } else if (date.isBefore(today)) {
                // 过去的未签到日期 - 红色羊毛
                dateItem = new ItemStack(Material.RED_WOOL);
                dateMeta.setDisplayName("§c" + day + "日");
            } else {
                // 未来的日期 - 灰色羊毛
                dateItem = new ItemStack(Material.GRAY_WOOL);
                dateMeta.setDisplayName("§7" + day + "日");
            }
            
            // 添加描述信息
            List<String> lore = new ArrayList<>();
            lore.add("§7日期: " + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            if (date.isEqual(today) && !plugin.hasPlayerSignedOnDate(playerId, date)) {
                lore.add("§e今天 - 点击签到");
            } else if (plugin.hasPlayerSignedOnDate(playerId, date)) {
                lore.add("§a已签到");
            } else if (date.isBefore(today)) {
                lore.add("§c未签到");
            } else {
                lore.add("§7未来日期");
            }
            dateMeta.setLore(lore);
            
            dateItem.setItemMeta(dateMeta);
            gui.setItem(slot, dateItem);
        }
        
        // 添加导航按钮 (最后一行)
        // 上个月按钮
        ItemStack prevMonth = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevMonth.getItemMeta();
        prevMeta.setDisplayName("§b上个月");
        List<String> prevLore = new ArrayList<>();
        prevLore.add("§7点击切换到上个月");
        YearMonth prevYearMonth = yearMonth.minusMonths(1);
        prevLore.add("§7" + prevYearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        prevMeta.setLore(prevLore);
        prevMonth.setItemMeta(prevMeta);
        gui.setItem(46, prevMonth); // 最后一行第二个格子
        
        // 下个月按钮
        ItemStack nextMonth = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextMonth.getItemMeta();
        nextMeta.setDisplayName("§b下个月");
        List<String> nextLore = new ArrayList<>();
        nextLore.add("§7点击切换到下个月");
        YearMonth nextYearMonth = yearMonth.plusMonths(1);
        nextLore.add("§7" + nextYearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        nextMeta.setLore(nextLore);
        nextMonth.setItemMeta(nextMeta);
        gui.setItem(52, nextMonth); // 最后一行倒数第二个格子
        
        // 当前月份信息
        ItemStack currentMonth = new ItemStack(Material.CLOCK);
        ItemMeta currentMeta = currentMonth.getItemMeta();
        currentMeta.setDisplayName("§e" + yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        List<String> currentLore = new ArrayList<>();
        currentLore.add("§7今天: " + today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        currentMeta.setLore(currentLore);
        currentMonth.setItemMeta(currentMeta);
        gui.setItem(49, currentMonth); // 最后一行中间格子
        
        // 打开GUI
        player.openInventory(gui);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("每日签到")) {
            event.setCancelled(true); // 防止玩家拿走物品
            
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            
            if (clickedItem != null) {
                // 检查点击冷却时间
                UUID playerId = player.getUniqueId();
                long currentTime = System.currentTimeMillis();
                if (clickCooldown.containsKey(playerId)) {
                    long lastClickTime = clickCooldown.get(playerId);
                    if (currentTime - lastClickTime < COOLDOWN_TIME) {
                        // 在冷却时间内，忽略点击
                        return;
                    }
                }
                // 更新点击时间
                clickCooldown.put(playerId, currentTime);
                
                String title = event.getView().getTitle();
                // 从标题中提取当前年月
                String[] parts = title.split(" - ");
                if (parts.length == 2) {
                    try {
                        String dateStr = parts[1].replace("年", "-").replace("月", "");
                        YearMonth currentYearMonth = YearMonth.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM"));
                        
                        // 检查是否点击了今天的日期（黄色羊毛）
                        if (clickedItem.getType() == Material.YELLOW_WOOL) {
                            // 检查玩家是否已经签到
                            if (!plugin.hasSignedToday(player)) {
                                // 执行签到
                                plugin.performSignIn(player);
                                
                                // 重新打开界面以更新颜色
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    openSigninGUI(player, plugin, currentYearMonth);
                                }, 1L); // 延迟1tick执行，避免鼠标位置问题
                                return;
                            } else {
                                player.sendMessage("§c您今天已经签到过了！");
                            }
                        }
                        
                        // 检查是否点击了上个月按钮
                        if (clickedItem.getType() == Material.ARROW && 
                            clickedItem.hasItemMeta() && 
                            clickedItem.getItemMeta().getDisplayName().contains("上个月")) {
                            YearMonth prevYearMonth = currentYearMonth.minusMonths(1);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                openSigninGUI(player, plugin, prevYearMonth);
                            }, 1L); // 延迟1tick执行，避免鼠标位置问题
                            return;
                        }
                        
                        // 检查是否点击了下个月按钮
                        if (clickedItem.getType() == Material.ARROW && 
                            clickedItem.hasItemMeta() && 
                            clickedItem.getItemMeta().getDisplayName().contains("下个月")) {
                            YearMonth nextYearMonth = currentYearMonth.plusMonths(1);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                openSigninGUI(player, plugin, nextYearMonth);
                            }, 1L); // 延迟1tick执行，避免鼠标位置问题
                            return;
                        }
                    } catch (Exception e) {
                        player.sendMessage("§c界面解析错误");
                    }
                }
            }
        }
    }
}