package org.economyplugin.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarTask extends BukkitRunnable {

    private final Money money;

    public ActionBarTask(Money money) { this.money = money; }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int bal = money.getBalance(p);
            p.sendActionBar(
                    Component.text("잔액: ")
                            .color(NamedTextColor.GOLD)
                            .append(Component.text(bal + "원").color(NamedTextColor.GOLD))
            );

        }
    }
}
