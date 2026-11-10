package fr.canarddev.headdrop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CanardHeadDrop - a player's head drops when they die.
 * PvP / PvE toggles, world list, drop chance, per-player limit, anti-rename, optional effects.
 */
public final class CanardHeadDrop extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey headKey;
    private Lang lang;
    private File dataFile;
    private YamlConfiguration data;

    // cached config
    private boolean pvp, pve, limitOn, preventRename, lightning, broadcast, soundOn;
    private double chance;
    private int limitMax;
    private final List<String> worlds = new ArrayList<>();
    private String headName, soundName;
    private List<String> headLore;
    private float soundVolume, soundPitch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        headKey = new NamespacedKey(this, "head");
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = getCommand("headdrop");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    private void load() {
        reloadConfig();
        FileConfiguration c = getConfig();
        lang = new Lang(this, c.getString("language", "en"));
        pvp = c.getBoolean("drop.pvp", true);
        pve = c.getBoolean("drop.pve", false);
        worlds.clear();
        for (String w : c.getStringList("drop.worlds")) worlds.add(w.toLowerCase(Locale.ROOT));
        chance = Math.max(0, Math.min(100, c.getDouble("drop.chance", 100)));
        limitOn = c.getBoolean("drop.limit.enabled", false);
        limitMax = c.getInt("drop.limit.max", 5);
        headName = c.getString("head.name", "&e%player% &7- &fHead");
        headLore = c.getStringList("head.lore");
        preventRename = c.getBoolean("head.prevent-rename", true);
        lightning = c.getBoolean("effects.lightning", false);
        broadcast = c.getBoolean("effects.broadcast", false);
        soundOn = c.getBoolean("effects.sound.enabled", false);
        soundName = c.getString("effects.sound.name", "entity.player.levelup");
        soundVolume = (float) c.getDouble("effects.sound.volume", 1.0);
        soundPitch = (float) c.getDouble("effects.sound.pitch", 1.0);
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ head

    /** Builds the head of a player, tagged so it can be recognised later. */
    public ItemStack createHead(OfflinePlayer owner) {
        String name = owner.getName() != null ? owner.getName() : "?";
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            meta.setDisplayName(Lang.color(headName.replace("%player%", name)));
            List<String> lore = new ArrayList<>();
            for (String l : headLore) lore.add(Lang.color(l.replace("%player%", name)));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(headKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            head.setItemMeta(meta);
        }
        return head;
    }

    private boolean isDroppedHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(headKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ drop

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        World world = victim.getWorld();
        if (!worlds.isEmpty() && !worlds.contains(world.getName().toLowerCase(Locale.ROOT))) return;

        Player killer = victim.getKiller();
        boolean byPlayer = killer != null && !killer.equals(victim);
        if (byPlayer ? !pvp : !pve) return;
        if (chance < 100 && ThreadLocalRandom.current().nextDouble(100) >= chance) return;

        if (limitOn) {
            String path = "drops." + victim.getUniqueId();
            int done = data.getInt(path, 0);
            if (done >= limitMax) return;
            data.set(path, done + 1);
            saveData();
        }

        Location loc = victim.getLocation();
        world.dropItemNaturally(loc, createHead(victim));

        if (lightning) world.strikeLightningEffect(loc);
        if (broadcast) Bukkit.broadcastMessage(lang.get("broadcast", "player", victim.getName()));
        if (soundOn) world.playSound(loc, soundName, soundVolume, soundPitch);
    }

    // ------------------------------------------------------------------ anti-rename

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!preventRename) return;
        if (isDroppedHead(e.getInventory().getItem(0))) e.setResult(null);
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("headdrop.admin")) {
            lang.send(sender, "no-permission");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "worlds" -> {
                lang.send(sender, "worlds-header");
                for (World w : Bukkit.getWorlds()) {
                    boolean on = worlds.isEmpty() || worlds.contains(w.getName().toLowerCase(Locale.ROOT));
                    sender.sendMessage(lang.get(on ? "world-on" : "world-off", "world", w.getName()));
                }
            }
            case "reload" -> {
                load();
                lang.send(sender, "reloaded");
            }
            case "reset" -> {
                if (args.length < 2) {
                    lang.send(sender, "usage-reset", "label", label);
                    return true;
                }
                OfflinePlayer p = findCached(args[1]);
                if (p == null) {
                    lang.send(sender, "player-not-found", "player", args[1]);
                    return true;
                }
                data.set("drops." + p.getUniqueId(), null);
                saveData();
                lang.send(sender, "limit-reset", "player", args[1]);
            }
            default -> lang.send(sender, "usage", "label", label);
        }
        return true;
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            lang.send(sender, "usage-give", "label", label);
            return;
        }
        Player target;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        else target = sender instanceof Player p ? p : null;
        if (target == null) {
            lang.send(sender, args.length >= 3 ? "player-not-found" : "console-target", "player", args.length >= 3 ? args[2] : "");
            return;
        }
        String ownerName = args[1];
        OfflinePlayer owner = findCached(ownerName);
        if (owner != null) {
            handOver(sender, target, owner, ownerName);
            return;
        }
        // unknown on this server: look it up without freezing the server
        Player finalTarget = target;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer looked = Bukkit.getOfflinePlayer(ownerName);
            Bukkit.getScheduler().runTask(this, () -> {
                if (!finalTarget.isOnline()) return;
                handOver(sender, finalTarget, looked, ownerName);
            });
        });
    }

    private void handOver(CommandSender sender, Player target, OfflinePlayer owner, String ownerName) {
        ItemStack head = createHead(owner);
        Map<Integer, ItemStack> left = target.getInventory().addItem(head);
        if (!left.isEmpty()) {
            for (ItemStack it : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), it);
            lang.send(target, "inventory-full");
        }
        lang.send(sender, "given", "player", ownerName, "target", target.getName());
    }

    /** Online player, or a player who already joined this server. Never blocks. */
    private OfflinePlayer findCached(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("headdrop.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("give", "worlds", "reset", "reload")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if ((args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset")))
                || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            String start = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(start)) out.add(p.getName());
        }
        return out;
    }

    /** Owner of a dropped head (null if it is not one). Handy for other plugins. */
    public UUID headOwner(ItemStack item) {
        if (!isDroppedHead(item)) return null;
        try {
            return UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(headKey, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }
}
