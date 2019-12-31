package org.antlr.works.plugin.intellij;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.antlr.works.IDE;
import org.antlr.works.dialog.AWPrefsDialog;
import org.antlr.works.prefs.AWPrefs;
import org.antlr.works.utils.IconManager;
import org.antlr.xjlib.appkit.app.XJApplication;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

/*

[The "BSD licence"]
Copyright (c) 2005-2006 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

public class PIApplicationComponent implements ApplicationComponent, Configurable {

    private final IDE ide;
    private AWPrefsDialog prefsDialog;

    public PIApplicationComponent() {
        this.ide = new IDE();
    }

    public void initComponent() {
        setCustomClassPath();

        XJApplication.setDelegate(ide);
        XJApplication.setPropertiesPath(IDE.PROPERTIES_PATH);

        PIActionNewFile action = new PIActionNewFile();
        ActionManager.getInstance().registerAction("NewGrammarFile", action);
        DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance().getAction("NewGroup");
        group.add(action, new Constraints(Anchor.AFTER, "NewFile"));
    }

    private void setCustomClassPath() {
        PluginId ourPluginId = PluginManager.getPluginByClassName(getClass().getName());
        IdeaPluginDescriptor ourPlugin = PluginManager.getPlugin(ourPluginId);

        if (ourPlugin != null) {
            File libDir = new File(ourPlugin.getPath(), "lib");
            File[] libs = libDir.listFiles();
            if (libs != null) {
                String cp = Arrays.stream(libs)
                        .map(File::getAbsolutePath)
                        .filter(path ->
                                path.contains("antlr-runtime")
                                        || path.contains("stringtemplate")
                                        || path.contains("antlr-2")
                        )
                        .collect(Collectors.joining(File.pathSeparator));
                AWPrefs.getPreferences().setBoolean(AWPrefs.PREF_CLASSPATH_CUSTOM, true);
                AWPrefs.setCustomClassPath(cp);
            }
        }
    }


    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "AWApplicationComponent";
    }

    // ********* Configurable *************

    public String getDisplayName() {
        return "ANTLRWorks";
    }

    public Icon getIcon() {
        return IconManager.shared().getIconApplication32x32();
    }

    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        prefsDialog = new AWPrefsDialog();
        prefsDialog.becomingVisibleForTheFirstTime();
        return prefsDialog.getComponent();
    }

    public boolean isModified() {
        return true;
    }

    public void apply() throws ConfigurationException {
        prefsDialog.apply();
    }

    public void reset() {
    }

    public void disposeUIResources() {
        prefsDialog.close(false);
        prefsDialog = null;
    }
}
