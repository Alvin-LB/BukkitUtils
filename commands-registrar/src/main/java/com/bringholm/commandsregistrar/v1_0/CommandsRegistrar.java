package com.bringholm.commandsregistrar.v1_0;

import com.google.common.collect.Sets;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class to register all Commands in a certain package.
 *
 * To find the classes, the jar file containing the
 * plugin will be searched for the specified package,
 * and if it is found, any CommandExecutors found inside
 * will be registered, using the lowercase version of
 * the class name. Because of this, this only works with
 * CommandExecutors in the same jar as the plugin.
 *
 * This class also allows for setting the description, alias,
 * permission, permission message and usage of the command. This
 * is done by making the CommandExecutor implement CommandsRegistrar.Executor
 * and implementing the various methods. If you only wish to use
 * some of the options, null is accepted as a value, and will just
 * cause the option to not be set.
 *
 * @author AlvinB
 */
public class CommandsRegistrar {

    /**
     * Registers the commands in the specified package to the specified plugin.
     *
     * The package name must be separated by dots (.) and not slashes (/).
     *
     * @param packageName the package name to search for CommandExecutors in
     * @param plugin the plugin instance to register the commands to
     */
    public static void registerCommands(String packageName, Plugin plugin) {
        CommandMap commandMap;
        try {
            commandMap = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }
        for (Class<?> clazz : getCommandExecutorsInPackage(packageName, plugin)) {
            try {
                Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
                PluginCommand command = constructor.newInstance(clazz.getSimpleName().toLowerCase(), plugin);
                CommandExecutor commandExecutor = (CommandExecutor) clazz.getConstructor().newInstance();
                command.setExecutor(commandExecutor);
                if (commandExecutor instanceof Executor) {
                    Executor executor = (Executor) commandExecutor;
                    if (executor.getDescription() != null) {
                        command.setDescription(executor.getDescription());
                    }
                    if (executor.getAliases() != null) {
                        command.setAliases(executor.getAliases());
                    }
                    if (executor.getPermission() != null) {
                        command.setPermission(executor.getPermission());
                    }
                    if (executor.getPermissionMessage() != null) {
                        command.setPermissionMessage(executor.getPermissionMessage());
                    }
                    if (executor.getUsage() != null) {
                        command.setUsage(executor.getUsage());
                    }
                }
                commandMap.register(plugin.getName(), command);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private static Set<Class<?>> getCommandExecutorsInPackage(String packageName, Plugin plugin) {
        Set<Class<?>> classes = Sets.newHashSet();
        JarFile jarFile;
        try {
            jarFile = new JarFile(URLDecoder.decode(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().getFile(), "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return classes;
        }
        for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
            String className = jarEntry.getName().replace("/", ".");
            if (className.startsWith(packageName) && className.endsWith(".class")) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(className.substring(0, className.length() - 6));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    continue;
                }
                if (CommandExecutor.class.isAssignableFrom(clazz)) {
                    classes.add(clazz);
                }
            }
        }
        return classes;
    }

    /**
     * An interface extending CommandExecutor allowing for various
     * options in the command to be set.
     *
     * The option names are the same as in the plugin.yml. Additional
     * information about them can be viewed here:
     * https://bukkit.gamepedia.com/Plugin_YAML
     */
    public interface Executor extends CommandExecutor {
        String getDescription();

        List<String> getAliases();

        String getPermission();

        String getPermissionMessage();

        String getUsage();
    }
}
