/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2003-2004  The Chemistry Development Kit (CDK) project
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307  USA.
 */
package org.openscience.cdk.applications.plugin;

import org.openscience.cdk.applications.APIVersionTester;
import org.openscience.cdk.event.ChemObjectChangeEvent;
import org.openscience.cdk.tools.LoggingTool;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

/**
 * Manager that loads and maintains CDK plugins. In addition, it provides a JMenu 
 * to allow access to the plugin's functionality.
 *
 * <p>Plugins can be loaded by name, but by default it will download the plugins
 * in the <code>pluginDirName</code> directory passed as argument to the
 * constructor. The jars in this directory are browsed, and the first class
 * it encounters which name ends in Plugin will be loaded as plugin.
 *
 * @cdk.module applications
 * @cdk.require swing
 */
public class CDKPluginManager {

    private LoggingTool logger;
    private Hashtable cdkPlugins;
    
    private String pluginConfigDirName;
    private CDKEditBus editBus;
    
    /**
     * Instantiate a CDKPluginManager.
     *
     * @param pluginDirName       directory where the plugin jars can be found
     * @param pluginConfigDirName directory where the plugin config files can be found
     * @param editBus             object implementing the CDKEditBus interface
     *
     * @see   org.openscience.cdk.applications.plugin.CDKEditBus
     */
    public CDKPluginManager(String pluginDirName, String pluginConfigDirName,
                            CDKEditBus editBus) {
        this(pluginConfigDirName, editBus);
        loadPlugins(pluginDirName);
    }
    public CDKPluginManager(String pluginConfigDirName, CDKEditBus editBus) {
        this.logger = new LoggingTool(this);
        this.editBus = editBus;
        this.pluginConfigDirName = pluginConfigDirName;
        this.cdkPlugins = new Hashtable();
    }
    
    /**
     * Returns a JMenu with submenus for each loaded plugin.
     */
    public JMenu getMenu() {
        JMenu menu = new JMenu("Plugins");
        Enumeration pluginsEnum = cdkPlugins.elements();
        while (pluginsEnum.hasMoreElements()) {
            CDKPluginInterface plugin = (CDKPluginInterface)pluginsEnum.nextElement();
            JMenu pluginMenu = new JMenu(plugin.getName());
            
            // add default items
            boolean hasOneOrMoreDefaultMenuItems = false;
            JPanel pluginPanel = plugin.getPluginPanel();
            if (pluginPanel != null) {
                // add action that fires up a window
                JMenuItem windowMenu = new JMenuItem("Plugin Window");
                windowMenu.addActionListener(
                    new PluginDialogAction(plugin)
                );
                pluginMenu.add(windowMenu);
                hasOneOrMoreDefaultMenuItems = true;
            }
            JPanel configPanel = plugin.getPluginConfigPanel();
            if (configPanel != null) {
                // add action that fires up a window
                pluginMenu.add(new JMenuItem("Config Window"));
                hasOneOrMoreDefaultMenuItems = true;
            }
            
            // try to plugin's private menu
            JMenu customPluginMenu = plugin.getMenu();
            if (customPluginMenu != null) {
                if (hasOneOrMoreDefaultMenuItems) {
                    pluginMenu.addSeparator();
                };
                if (customPluginMenu.getText().length() == 0) {
                    customPluginMenu.setText("Plugin's menu");
                }
                pluginMenu.add(customPluginMenu);
            }
            menu.add(pluginMenu);
        }
        return menu;
    }
    
    /** 
     * Load a plugin based on it's class name.
     *
     * @param className Class name of the class constituting the plugin main class.
     */
    public void loadPlugin(String className) {
        loadPlugin(this.getClass().getClassLoader(), className);
    }
     
    public void loadPlugin(ClassLoader classLoader, String className) {
        try {
            Class c = classLoader.loadClass(className);
            Object plugin = c.newInstance();
            if (plugin instanceof CDKPluginInterface) {
                CDKPluginInterface cdkPlugin = (CDKPluginInterface)plugin;
                if (APIVersionTester.isSmaller("1.4", cdkPlugin.getAPIVersion())) {
                    // ignore old plugins
                    logger.warn("Will not load plugins with old API: ", className);
                    logger.debug("  the plugin has API version: ", cdkPlugin.getAPIVersion());
                } else {
                    boolean loadPlugin = false;
                    if (cdkPlugins.containsKey(className)) {
                        // deal with already loaded plugins
                        CDKPluginInterface alreadyLoadedPlugin = (CDKPluginInterface)cdkPlugins.get(className);
                        /* ok, here's the deal: the plugin with the latest version stays */
                        if (Double.parseDouble(alreadyLoadedPlugin.getPluginVersion()) <=
                            Double.parseDouble(cdkPlugin.getPluginVersion())) {
                            loadPlugin = true;
                        } // already loaded plugin is of same version, or better
                    } else {
                        loadPlugin = true;
                    }
                    if (loadPlugin) {
                        // this plugin is not loaded yet, or there is a newer version installed
                        if (APIVersionTester.isBiggerOrEqual("1.5", cdkPlugin.getAPIVersion())) {
                            logger.debug("Setting prop dir in plugin...");
                            cdkPlugin.setPropertyDirectory(pluginConfigDirName);
                        } else {
                            logger.warn("Plugin is too old to set property directory");
                            logger.debug("  the plugin has API version: ", cdkPlugin.getAPIVersion());
                        }
                        cdkPlugin.setEditBus(editBus);
                        cdkPlugins.put(className, cdkPlugin);
                    }
                }
            } else {
                logger.info("Class is not type CDKPluginInterface");
            }
        } catch (ClassNotFoundException exception) {
            logger.error("Could not find class");
            logger.debug(exception);
        } catch (IllegalAccessException exception) {
            logger.error("Don't have access to class");
            logger.debug(exception);
        } catch (InstantiationException exception) {
            logger.error("Could not instantiate object");
            logger.debug(exception);
        } catch (NoSuchMethodError error) {
            logger.warn("Plugin is too old. Download a more recent version.");
            logger.debug(error);
        }
    }
    
    /**
     * Returns an Enumeration of CDK plugins.
     */
    public Enumeration getPlugins() {
        return cdkPlugins.elements();
    }
    
    public void closePlugins() {
        Enumeration plugins = getPlugins();
        if (plugins.hasMoreElements()) {
            while (plugins.hasMoreElements()) {
                CDKPluginInterface plugin = (CDKPluginInterface)plugins.nextElement();
                plugin.stop();
            }
        }
    }

    /**
     * Loads the plugins from a certain directory.
     */
    public void loadPlugins(String pluginDirName) {
        File pluginDir = new File(pluginDirName);
        logger.info("User plugin dir: ", pluginDir);
        logger.debug("       exists: ", pluginDir.exists());
        logger.debug("  isDirectory: ", pluginDir.isDirectory());
        if (pluginDir.exists() && pluginDir.isDirectory()) {
            File[] plugins = pluginDir.listFiles();
            for (int i=0; i<plugins.length; i++) {
                // loop over these files and load them
                String pluginJarName = plugins[i].getName();
                if (pluginJarName.endsWith("jar")) {
                    logger.debug("Possible plugin found: ", pluginJarName);
                    try {
                        JarFile jarfile = new JarFile(plugins[i]);
                        Enumeration entries = jarfile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = (JarEntry)entries.nextElement();
                            if (entry.getName().endsWith("Plugin.class")) {
                                StringBuffer buffer = new StringBuffer(entry.getName());
                                for (int charIndex=0; charIndex<buffer.length(); charIndex++) {
                                    if (buffer.charAt(charIndex) == '/') {
                                        buffer.setCharAt(charIndex, '.');
                                    }
                                }
                                String pluginName = buffer.toString().substring(0, buffer.toString().indexOf(".class"));
                                logger.info("Plugin class found: ", pluginName);

                                // FIXME: use a classloader that loads the whole jar
                                logger.debug("Plugin URL: ", plugins[i].toURL());
                                URL u = new URL("jar", "", plugins[i].toURL() + "!/");
                                ClassLoader loader = new PluginClassLoader(u);
                                loadPlugin(loader, pluginName);
                                logger.info("  loaded.");
                                break;
                            }
                        }
                    } catch (IOException exception) {
                        logger.error("Could not load plugin jar file: ");
                        logger.debug(exception);
                    }
                }
            }
        }
    }
    
    /**
     * Signal the plugins that the ChemModel/ChemFile has changed.
     */
	public void stateChanged(ChemObjectChangeEvent sourceEvent) {
        // send event to plugins
        Enumeration plugins = getPlugins();
        if (plugins.hasMoreElements()) {
            while (plugins.hasMoreElements()) {
                CDKPluginInterface plugin = (CDKPluginInterface)plugins.nextElement();
                if (Double.parseDouble(plugin.getAPIVersion()) >= 1.6) {
                    plugin.stateChanged(sourceEvent);
                } else {
                    logger.debug("Plugin API is not bigger than 1.6. Not send it the change event. : ");
                }
            }
        }
	}

    /**
     * Action that creates a dialog with the content defined by the plugin for
     * which the dialog is created.
     */
    class PluginDialogAction extends AbstractAction {
        
        private CDKPluginInterface plugin;
        
        public PluginDialogAction(CDKPluginInterface plugin) {
            super("PluginDialog");
            this.plugin = plugin;
        }
        
        public void actionPerformed(ActionEvent e) {
            JPanel pluginPanel = plugin.getPluginPanel();
            plugin.start();
            if (pluginPanel != null) {
                JDialog pluginWindow = new JDialog();
                pluginWindow.setTitle(plugin.getName());
                pluginWindow.getContentPane().add(pluginPanel);
                pluginWindow.pack();
                pluginWindow.show();
            }
        }
    }
    
}

