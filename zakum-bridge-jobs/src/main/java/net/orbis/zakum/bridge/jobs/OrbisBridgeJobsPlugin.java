package net.orbis.zakum.bridge.jobs;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class OrbisBridgeJobsPlugin extends JavaPlugin {

  private ActionBus bus;

  // Scaling
  private long moneyScale;
  private long expScale;
  private boolean emitActions;
  private boolean emitMoney;
  private boolean emitExp;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    reloadConfig();

    if (!getConfig().getBoolean("enabled", true)) {
      getLogger().info("Disabled via config. Disabling OrbisBridgeJobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    if (Bukkit.getPluginManager().getPlugin("Jobs") == null) {
      getLogger().info("Jobs not found. Disabling OrbisBridgeJobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    var rsp = Bukkit.getServicesManager().getRegistration(ActionBus.class);
    if (rsp == null || rsp.getProvider() == null) {
      getLogger().warning("Zakum ActionBus service not found. Disabling OrbisBridgeJobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.bus = rsp.getProvider();

    FileConfiguration cfg = getConfig();
    this.moneyScale = Math.max(1L, cfg.getLong("scale.money", 100L));
    this.expScale = Math.max(1L, cfg.getLong("scale.exp", 100L));
    this.emitActions = cfg.getBoolean("emit.actions", true);
    this.emitMoney = cfg.getBoolean("emit.money", true);
    this.emitExp = cfg.getBoolean("emit.exp", true);

    try {
      hookPrePayment();
      hookExpGain();
      getLogger().info("OrbisBridgeJobs enabled.");
    } catch (Throwable t) {
      getLogger().severe("Failed to hook Jobs events: " + t.getMessage());
      t.printStackTrace();
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    bus = null;
  }

  private void hookPrePayment() throws Throwable {
    Class<?> eventClass = Class.forName("com.gamingmesh.jobs.api.JobsPrePaymentEvent", false, getClassLoader());
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle mhGetPlayer = lookup.findVirtual(eventClass, "getPlayer", MethodType.methodType(OfflinePlayer.class));
    MethodHandle mhGetJob = lookup.findVirtual(eventClass, "getJob", MethodType.methodType(Object.class));
    MethodHandle mhGetAmount = lookup.findVirtual(eventClass, "getAmount", MethodType.methodType(double.class));
    MethodHandle mhGetActionInfo = lookup.findVirtual(eventClass, "getActionInfo", MethodType.methodType(Object.class));

    Listener dummy = new Listener() {};
    EventExecutor exec = (ignoredListener, event) -> {
      try {
        OfflinePlayer op = (OfflinePlayer) mhGetPlayer.invoke(event);
        if (op == null || op.getUniqueId() == null) return;

        Object job = mhGetJob.invoke(event);
        String jobName = resolveName(job);

        Object actionInfo = mhGetActionInfo.invoke(event);
        String actionType = resolveActionTypeName(actionInfo);

        if (emitActions && jobName != null && actionType != null) {
          bus.publish(new ActionEvent(
            "jobs_action",
            op.getUniqueId(),
            1,
            "job_action",
            jobName + ":" + actionType
          ));
        }

        if (emitMoney && jobName != null) {
          double amount = (double) mhGetAmount.invoke(event);
          long scaled = scale(amount, moneyScale);
          if (scaled > 0) {
            bus.publish(new ActionEvent(
              "jobs_money",
              op.getUniqueId(),
              scaled,
              "job",
              jobName
            ));
          }
        }
      } catch (Throwable t) {
        getLogger().warning("JobsPrePayment hook error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    };

    Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, dummy, EventPriority.MONITOR, exec, this, true);
  }

  private void hookExpGain() throws Throwable {
    Class<?> eventClass = Class.forName("com.gamingmesh.jobs.api.JobsExpGainEvent", false, getClassLoader());
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle mhGetPlayer = lookup.findVirtual(eventClass, "getPlayer", MethodType.methodType(OfflinePlayer.class));
    MethodHandle mhGetJob = lookup.findVirtual(eventClass, "getJob", MethodType.methodType(Object.class));
    MethodHandle mhGetExp = lookup.findVirtual(eventClass, "getExp", MethodType.methodType(double.class));
    MethodHandle mhGetActionInfo = lookup.findVirtual(eventClass, "getActionInfo", MethodType.methodType(Object.class));

    Listener dummy = new Listener() {};
    EventExecutor exec = (ignoredListener, event) -> {
      try {
        OfflinePlayer op = (OfflinePlayer) mhGetPlayer.invoke(event);
        if (op == null || op.getUniqueId() == null) return;

        Object job = mhGetJob.invoke(event);
        String jobName = resolveName(job);

        Object actionInfo = mhGetActionInfo.invoke(event);
        String actionType = resolveActionTypeName(actionInfo);

        if (emitActions && jobName != null && actionType != null) {
          bus.publish(new ActionEvent(
            "jobs_action",
            op.getUniqueId(),
            1,
            "job_action",
            jobName + ":" + actionType
          ));
        }

        if (emitExp && jobName != null) {
          double exp = (double) mhGetExp.invoke(event);
          long scaled = scale(exp, expScale);
          if (scaled > 0) {
            bus.publish(new ActionEvent(
              "jobs_exp",
              op.getUniqueId(),
              scaled,
              "job",
              jobName
            ));
          }
        }
      } catch (Throwable t) {
        getLogger().warning("JobsExpGain hook error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    };

    Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, dummy, EventPriority.MONITOR, exec, this, true);
  }

  private static long scale(double value, long scale) {
    if (!Double.isFinite(value) || value <= 0) return 0;
    BigDecimal bd = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(scale));
    bd = bd.setScale(0, RoundingMode.HALF_UP);
    try {
      return bd.longValueExact();
    } catch (ArithmeticException ignored) {
      return Long.MAX_VALUE;
    }
  }

  private static String resolveName(Object job) {
    if (job == null) return null;
    try {
      // Jobs Job class usually has getName().
      Object v = job.getClass().getMethod("getName").invoke(job);
      return Objects.toString(v, null);
    } catch (ReflectiveOperationException ignored) {
      return Objects.toString(job, null);
    }
  }

  private static String resolveActionTypeName(Object actionInfo) {
    if (actionInfo == null) return null;
    try {
      // actionInfo.getType().getName()
      Object type = actionInfo.getClass().getMethod("getType").invoke(actionInfo);
      if (type == null) return null;
      Object name = type.getClass().getMethod("getName").invoke(type);
      return Objects.toString(name, null);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }
}
