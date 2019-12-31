package org.antlr.works.plugin.intellij;

import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.antlr.works.components.GrammarDocument;
import org.antlr.works.components.GrammarDocumentFactory;
import org.antlr.works.components.GrammarWindow;
import org.antlr.xjlib.appkit.menu.XJMainMenuBar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

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

public class PIEditor implements FileEditor {

    private GrammarWindow window;
    private Project project;
    private VirtualFile virtualFile;
    private Document document;
    private GrammarDocument componentDocument;

    private boolean layout = false;

    private MessageBusConnection messageBusConnection;
    private final List<PropertyChangeListener> listeners = new ArrayList<PropertyChangeListener>();

    private static final List<PIEditor> editors = new ArrayList<PIEditor>();

    public PIEditor(Project project, VirtualFile file) {
        this.project = project;
        this.virtualFile = file;
        this.document = FileDocumentManager.getInstance().getDocument(this.virtualFile);

        try {
            componentDocument = (GrammarDocument) new GrammarDocumentFactory(GrammarWindow.class).createDocument();
        } catch (Exception e) {
            System.err.println("Cannot create the document:");
            e.printStackTrace();
            return;
        }
        componentDocument.awake();

        // Hide unnecessary menus
        XJMainMenuBar mainMenuBar = componentDocument.getWindow().getMainMenuBar();
        mainMenuBar.createMenuBar(XJMainMenuBar.IGNORE_FILEMENU | XJMainMenuBar.IGNORE_HELPMENU | XJMainMenuBar.IGNORE_WINDOWMENU);
        componentDocument.getWindow().setMainMenuBar(mainMenuBar);

        try {
            componentDocument.load(VfsUtil.virtualToIoFile(virtualFile).getPath());
        } catch (Exception e) {
            System.err.println("Cannot load the document:");
            e.printStackTrace();
        }

        window = componentDocument.getWindow();
        registerKeybindings();

        register();
    }

    private void registerKeybindings() {
        // Must register custom action in order to override the default mechanism in IntelliJ 7
        registerKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
                DefaultEditorKit.beginLineAction);
        registerKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, Event.SHIFT_MASK),
                DefaultEditorKit.selectionBeginLineAction);

        registerKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
                DefaultEditorKit.endLineAction);
        registerKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_END, Event.SHIFT_MASK),
                DefaultEditorKit.selectionEndLineAction);
    }

    private void registerKeyBinding(KeyStroke ks, final String action) {
        AnAction a = new AnAction() {
            public void actionPerformed(AnActionEvent event) {
                componentDocument.getWindow().getTextEditor().getTextPane().getActionMap().get(action).actionPerformed(null);
            }
        };

        final String uniqueAction = action+this;
        a.registerCustomShortcutSet(new CustomShortcutSet(ks), componentDocument.getWindow().getTextEditor().getTextPane());
        ActionManager.getInstance().registerAction(uniqueAction, a);
    }

    public void close() {
        save();
        unregister();
    }

    public static void saveAll() {
        synchronized(editors) {
            for(PIEditor e : editors) {
                e.save();
            }
        }
    }

    public void register() {
        window.getContentPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layout();
            }
        });

        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        messageBusConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new MyFileDocumentManagerAdapter());
        synchronized(editors) {
            editors.add(this);
        }
    }

    public void unregister() {
        synchronized(editors) {
            editors.remove(this);
        }
        messageBusConnection.disconnect();
    }

    public void load(String file) {
        try {
            componentDocument.load(file);
        } catch (Exception e) {
            // todo alert?
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void save() {
        window.resetDirty();
        window.saveAll();
        notifyFileModified();
    }

    /**
     * Perform the layout only once, when the component resizes for the first time.
     *
     */
    public void layout() {
        if(!layout) {
            window.becomingVisibleForTheFirstTime();
            layout = true;
        }
    }

    public void pluginDocumentDidChange() {
        if(!document.isWritable()) {
            ReadonlyStatusHandler.OperationStatus op = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(virtualFile);
            if(!document.isWritable()) {
                System.err.println(op.getReadonlyFilesMessage());
                return;
            }
        }

        notifyFileModified();

        /*CommandProcessor.getInstance().runUndoTransparentAction( new Runnable() {
            public void run() {
                CommandProcessor.getInstance().executeCommand( project, new Runnable() {
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction( new Runnable() {
                            public void run() {
                                int offset = document.getTextLength();
                                document.insertString( offset, " " );
                                document.deleteString( offset, offset + 1 );
                            }
                        } );
                    }
                }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION );
            }
        } ); */
    }

    private void notifyFileModified() {
        for (PropertyChangeListener propertyChangeListener : listeners) {
            propertyChangeListener.propertyChange(new PropertyChangeEvent(this, FileEditor.PROP_MODIFIED, null, null));
        }
    }

    @NotNull
    public JComponent getComponent() {
        return window.getRootPane();
    }

    public JComponent getPreferredFocusedComponent() {
        //return window.getComponentContainer().getSelectedEditor().getTextEditor();
        return null;
    }

    @NotNull
    public String getName() {
        return virtualFile.getName();
    }

    @NotNull
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return new FileEditorState() {
            public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
                return false;
            }
        };
    }

    public void setState(@NotNull FileEditorState state) {
    }

    public boolean isModified() {
        return window.getDocument().isDirty();
    }

    public boolean isValid() {
        return true;
    }

    public void selectNotify() {
        window.componentActivated();
    }

    public void deselectNotify() {
        window.windowDeactivated();
        save();
    }

    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        listeners.add(listener);
    }

    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        listeners.remove(listener);
    }

    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    public StructureViewBuilder getStructureViewBuilder() {
        return new PIStructureViewBuilder(window);
    }

    public <T> T getUserData(Key<T> tKey) {
        return null;
    }

    public <T> void putUserData(Key<T> tKey, T t) {

    }

    public void dispose() {

    }

    private class MyFileDocumentManagerAdapter extends FileDocumentManagerAdapter {

        @Override
        public void fileContentReloaded(VirtualFile file, Document document) {
            if(file.equals(PIEditor.this.virtualFile)) {
                load(VfsUtil.virtualToIoFile(virtualFile).getPath());
            }
        }

        @Override
        public void fileContentLoaded(VirtualFile file, Document document) {
            if(file.equals(PIEditor.this.virtualFile)) {
                load(VfsUtil.virtualToIoFile(virtualFile).getPath());
            }
        }

        @Override
        public void beforeDocumentSaving(Document document) {
            Document doc = FileDocumentManager.getInstance().getDocument(PIEditor.this.virtualFile);
            if(doc == document) {
                save();
            }
        }

    }


}
