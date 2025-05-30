// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.inlay;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.codeEditor.event.EditorMouseEvent;
import consulo.language.editor.inlay.InlayActionHandler;
import consulo.language.editor.inlay.InlayActionPayload;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaFqnDeclarativeInlayActionHandler implements InlayActionHandler {
    public static final String HANDLER_NAME = "java.fqn.class";

    @Nonnull
    @Override
    public String getHandlerId() {
        return HANDLER_NAME;
    }

    @Override
    public void handleClick(EditorMouseEvent e, InlayActionPayload payload) {
        Project project = e.getEditor().getProject();
        if (project == null) return;

        InlayActionPayload.StringInlayActionPayload actionPayload = (InlayActionPayload.StringInlayActionPayload) payload;
        String fqn = actionPayload.getText();

        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        AppExecutorUtil.getAppExecutorService().submit(() ->
            ApplicationManager.getApplication().runReadAction(() -> {
                PsiClass aClass = facade.findClass(fqn, GlobalSearchScope.allScope(project));
                if (aClass != null) {
                    ApplicationManager.getApplication().invokeLater(() -> aClass.navigate(true));
                }
            })
        );
    }
}
