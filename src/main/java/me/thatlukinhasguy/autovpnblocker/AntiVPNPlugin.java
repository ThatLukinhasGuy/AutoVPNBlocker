package me.thatlukinhasguy.autovpnblocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


import org.json.JSONObject;

public class AntiVPNPlugin extends JavaPlugin implements Listener, CommandExecutor {
    public static AntiVPNPlugin plugin = null;
    private Set<String> whitelistedPlayers = new HashSet<>();
    private FileConfiguration config = getConfig();

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("antivpn").setExecutor(this);
        String kickMessage = getConfig().getString("kick-message");
    }

    private CompletableFuture<Boolean> isUsingDisallowedServiceAsync(String ip) {
        return isUsingVPNAsync(ip).thenApply(vpnInfo -> {
            return vpnInfo.values().stream().anyMatch(value -> value);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerNickname = event.getPlayer().getName();
        String playerIp = event.getPlayer().getAddress().getAddress().getHostAddress();

        if (config.getBoolean("whitelist." + player.getName())) {
            getLogger().info("[AutoVPNBlocker] O jogador " + playerNickname + " está na whitelist do AntiVPN.");
            return;
        } else if (config.getBoolean("ipwhitelist." + playerIp)) {
            getLogger().info("[AutoVPNBlocker] O IP " + playerIp + " está na whitelist do AntiVPN.");
            return;
        }

        CompletableFuture<Boolean> future = isUsingDisallowedServiceAsync(playerIp);
        future.thenAccept(isUsingDisallowedService -> {
            if (isUsingDisallowedService) {
                Bukkit.getScheduler().runTask(this, () -> {
                    String kickMessage = getConfig().getString("kick-message");
                    player.kickPlayer(kickMessage);
                });
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("antivpn")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso inválido. Para ver os argumentos corretos, digite " + ChatColor.GREEN + "/antivpn help");
                return true;
            }

            if (args[0].equalsIgnoreCase("adduser")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso correto: " + ChatColor.GREEN + "/antivpn adduser <nome>");
                    return true;
                }
                String playerName = args[1];
                plugin.getConfig().set("whitelist." + playerName, true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O jogador " + ChatColor.GREEN + playerName + ChatColor.GRAY + " foi adicionado à whitelist do AntiVPN");
                return true;
            }


            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                getConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O plugin foi " + ChatColor.GREEN + " recarregado " + ChatColor.GRAY + " com sucesso");
                return true;
            }

            if (args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Comandos:");
                sender.sendMessage(ChatColor.GREEN + "/antivpn reload" + ChatColor.GRAY + " - Recarrega a configuração do plugin");
                sender.sendMessage(ChatColor.GREEN + "/antivpn adduser <nome> " + ChatColor.GRAY + " - Adiciona um jogador à whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn removeuser <nome> " + ChatColor.GRAY + " - Remove um jogador da whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn addip <ip> " + ChatColor.GRAY + " - Adiciona um IP à whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn removeip <ip> " + ChatColor.GRAY + " - Remove um IP da whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn help" + ChatColor.GRAY + " - Mostra essa mensagem");
                return true;
            }

            if (args[0].equalsIgnoreCase("removeuser")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso correto: " + ChatColor.GREEN + "/antivpn removeuser <nome>");
                    return true;
                }
                String playerName = args[1];
                if (plugin.getConfig().contains("whitelist." + playerName)) {
                    plugin.getConfig().set("whitelist." + playerName, null);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O jogador " + ChatColor.GREEN + playerName + ChatColor.GRAY + " foi removido da whitelist do AntiVPN");
                } else {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O jogador " + ChatColor.GREEN + playerName + ChatColor.GRAY + " não está na whitelist do AntiVPN");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("addip")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso correto: " + ChatColor.GREEN + "/antivpn addip <ip>");
                    return true;
                }
                String ipAddress = args[1];
                plugin.getConfig().set("ipwhitelist." + ipAddress, true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.GRAY + " foi adicionado à whitelist do AntiVPN");
                return true;
            }

            if (args[0].equalsIgnoreCase("removeip")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso correto: " + ChatColor.GREEN + "/antivpn removeip <ip>");
                    return true;
                }
                String ipAddress = args[1];
                if (plugin.getConfig().contains("ipwhitelist." + ipAddress)) {
                    plugin.getConfig().set("ipwhitelist." + ipAddress, null);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.GRAY + " foi removido da whitelist do AntiVPN");
                } else {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.GRAY + " não está na whitelist do AntiVPN");
                }
                return true;
            }


            sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.GRAY + "Uso inválido. Para ver os argumentos corretos, digite " + ChatColor.GREEN + "/antivpn help");
            return true;
        }
        return false;
    }

    private void set(String whitelistedPlayers, Set<String> whitelistedPlayers1) {
    }

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private static final Cache<String, Map<String, Boolean>> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private CompletableFuture<Map<String, Boolean>> isUsingVPNAsync(String ip) {
        Map<String, Boolean> cachedResult = cache.getIfPresent(ip);
        if (cachedResult != null) {
            return CompletableFuture.completedFuture(cachedResult);
        }

        Request request = new Request.Builder()
                .url("https://api.incolumitas.com/?q=" + ip)
                .addHeader("Accept", "application/json")
                .build();

        return CompletableFuture.supplyAsync(() -> {
            Response response = null;
            try {
                response = httpClient.newCall(request).execute();
                if (!response.isSuccessful()) {
                    getLogger().warning("Parece que a API não está funcionando (código de resposta: " + response.code() + ")");
                    return Collections.emptyMap();
                }

                String responseBody = response.body().string();
                JSONObject obj = new JSONObject(responseBody);
                Map<String, Boolean> vpnInfo = new HashMap<>();
                vpnInfo.put("is_tor", obj.getBoolean("is_tor"));
                vpnInfo.put("is_abuser", obj.getBoolean("is_abuser"));
                vpnInfo.put("is_bogon", obj.getBoolean("is_bogon"));
                vpnInfo.put("is_datacenter", obj.getBoolean("is_datacenter"));
                vpnInfo.put("is_proxy", obj.getBoolean("is_proxy"));
                vpnInfo.put("is_vpn", obj.getBoolean("is_vpn"));

                cache.put(ip, vpnInfo);
                return vpnInfo;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
        });
    }
}
