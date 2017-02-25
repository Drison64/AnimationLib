package me.megamichiel.animationlib.bukkit;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import me.megamichiel.animationlib.config.AbstractConfig;
import me.megamichiel.animationlib.placeholder.Formula;
import me.megamichiel.animationlib.placeholder.IPlaceholder;
import me.megamichiel.animationlib.placeholder.ctx.ParsingContext;
import me.megamichiel.animationlib.util.db.SQLHandler;
import me.megamichiel.animationlib.util.pipeline.Pipeline;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AnimLibPlaceholders extends PlaceholderHook {

    static AnimLibPlaceholders init(AnimLibPlugin plugin) {
        AnimLibPlaceholders res = new AnimLibPlaceholders(plugin);
        PlaceholderAPI.registerPlaceholderHook("animlib", res);
        return res;
    }

    private final AnimLibPlugin plugin;
    private final Map<String, IPlaceholder<String>> formulas = new HashMap<>();
    private final SQLHandler sql;
    private BukkitTask sqlTask;
    private Pipeline<? extends PlayerEvent> joinPipeline, quitPipeline;

    private AnimLibPlaceholders(AnimLibPlugin plugin) {
        this.plugin = plugin;
        sql = new SQLHandler(plugin);
    }

    void load(AbstractConfig config) {
        formulas.clear();
        if (sqlTask != null) {
            sqlTask.cancel();
            joinPipeline.unregister();
            quitPipeline.unregister();
        }
        String locale = config.getString("formula-locale");
        if (locale != null) Formula.setLocale(new Locale(locale));
        if (config.isSection("formulas")) {
            AbstractConfig section = config.getSection("formulas");
            section.forEach((key, value) -> {
                Formula formula;
                if (value instanceof AbstractConfig) {
                    AbstractConfig sec = (AbstractConfig) value;
                    String     val = sec.getString("value"),
                            format = sec.getString("format");
                    if (val == null) return;
                    DecimalFormat nf;
                    try {
                        nf = new DecimalFormat(format, Formula.getSymbols());
                    } catch (IllegalArgumentException ex) {
                        plugin.nag("Invalid formula format: " + format);
                        return;
                    }
                    try {
                        formula = Formula.parse(val, ParsingContext.ofFormat(nf));
                    } catch (IllegalArgumentException ex) {
                        plugin.nag("Failed to parse formula " + val + ": " + ex.getMessage());
                        return;
                    }
                } else if (!(value instanceof Collection)) {
                    try {
                        formula = Formula.parse(value.toString(), null);
                    } catch (IllegalArgumentException ex) {
                        plugin.nag("Failed to parse formula " + value + ": " + ex.getMessage());
                        return;
                    }
                } else return;
                formulas.put(section.getOriginalKey(key), formula);
            });
        }
        sql.load(config.getSection("sql-queries"));
        long delay = sql.getRefreshDelay();
        if (delay > 0) {
            sqlTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, sql, delay * 20, delay * 20);
            (joinPipeline = plugin.newPipeline(PlayerJoinEvent.class)).map(PlayerEvent::getPlayer).forEach(sql::playerJoin);
            (quitPipeline = plugin.newPipeline(PlayerQuitEvent.class)).map(PlayerEvent::getPlayer).forEach(sql::playerQuit);
        }
    }

    @Override
    public String onPlaceholderRequest(Player player, String arg) {
        if (arg.startsWith("formula_")) {
            IPlaceholder<String> formula = formulas.get(arg.substring(8));
            return formula == null ? null : formula.invoke(plugin, player);
        } else if (arg.startsWith("sql_")) {
            return sql.get(arg.substring(4), player);
        } else if (arg.startsWith("sqlrefresh_")) {
            return sql.getAndRefresh(arg.substring(11), player);
        }
        return null;
    }
}
