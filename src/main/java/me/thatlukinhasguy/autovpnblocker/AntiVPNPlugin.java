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
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.WHITE + "Uso inválido. Para ver os argumentos corretos, digite " + ChatColor.GREEN + "/antivpn help");
                return true;
            }

            if (args[0].equalsIgnoreCase("adduser")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Uso correto: " + ChatColor.GREEN + "/antivpn adduser <nome>");
                    return true;
                }
                String playerName = args[1];
                plugin.getConfig().set("whitelist." + playerName, true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O jogador " + ChatColor.GREEN + playerName + ChatColor.WHITE + " foi adicionado à whitelist do AntiVPN");
                return true;
            }


            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                getConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O plugin foi " + ChatColor.GREEN + " recarregado " + ChatColor.WHITE + " com sucesso");
                return true;
            }

            if (args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Comandos:");
                sender.sendMessage(ChatColor.GREEN + "/antivpn reload" + ChatColor.WHITE + " - Recarrega a configuração do plugin");
                sender.sendMessage(ChatColor.GREEN + "/antivpn adduser <nome> " + ChatColor.WHITE + " - Adiciona um jogador à whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn removeuser <nome> " + ChatColor.WHITE + " - Remove um jogador da whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn addip <ip> " + ChatColor.WHITE + " - Adiciona um IP à whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn removeip <ip> " + ChatColor.WHITE + " - Remove um IP da whitelist do AntiVPN");
                sender.sendMessage(ChatColor.GREEN + "/antivpn help" + ChatColor.WHITE + " - Mostra essa mensagem");
                return true;
            }

            if (args[0].equalsIgnoreCase("removeuser")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Uso correto: " + ChatColor.GREEN + "/antivpn removeuser <nome>");
                    return true;
                }
                String playerName = args[1];
                if (plugin.getConfig().contains("whitelist." + playerName)) {
                    plugin.getConfig().set("whitelist." + playerName, null);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O jogador " + ChatColor.GREEN + playerName + ChatColor.WHITE + " foi removido da whitelist do AntiVPN");
                } else {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O jogador " + ChatColor.GREEN + playerName + ChatColor.WHITE + " não está na whitelist do AntiVPN");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("addip")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Uso correto: " + ChatColor.GREEN + "/antivpn addip <ip>");
                    return true;
                }
                String ipAddress = args[1];
                plugin.getConfig().set("ipwhitelist." + ipAddress, true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.WHITE + " foi adicionado à whitelist do AntiVPN");
                return true;
            }

            if (args[0].equalsIgnoreCase("removeip")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "Uso correto: " + ChatColor.GREEN + "/antivpn removeip <ip>");
                    return true;
                }
                String ipAddress = args[1];
                if (plugin.getConfig().contains("ipwhitelist." + ipAddress)) {
                    plugin.getConfig().set("ipwhitelist." + ipAddress, null);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.WHITE + " foi removido da whitelist do AntiVPN");
                } else {
                    sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + "O IP " + ChatColor.GREEN + ipAddress + ChatColor.WHITE + " não está na whitelist do AntiVPN");
                }
                return true;
            }


            sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.AQUA + "AutoVPNBlocker" + ChatColor.WHITE + "] " + ChatColor.WHITE + "Uso inválido. Para ver os argumentos corretos, digite " + ChatColor.GREEN + "/antivpn help");
            return true;
            }
        return false;
        }

    private void set(String whitelistedPlayers, Set<String> whitelistedPlayers1) {
    }

    private CompletableFuture<Map<String, Boolean>> isUsingVPNAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL apiUrl = new URL("https://api.incolumitas.com/?q=" + ip);
                HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    getLogger().warning("Parece que a API não está funcionando (código de resposta: " + responseCode + ")");
                    return Collections.emptyMap();
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject obj = new JSONObject(response.toString());
                    Map<String, Boolean> vpnInfo = new HashMap<>();
                    vpnInfo.put("is_tor", obj.getBoolean("is_tor"));
                    vpnInfo.put("is_abuser", obj.getBoolean("is_abuser"));
                    vpnInfo.put("is_bogon", obj.getBoolean("is_bogon"));
                    vpnInfo.put("is_datacenter", obj.getBoolean("is_datacenter"));
                    vpnInfo.put("is_proxy", obj.getBoolean("is_proxy"));
                    vpnInfo.put("is_vpn", obj.getBoolean("is_vpn"));

                    return vpnInfo;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }


}
