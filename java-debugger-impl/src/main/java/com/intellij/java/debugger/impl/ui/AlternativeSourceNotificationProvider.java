/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.JavaStackFrame;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XStackFrame;
import consulo.fileEditor.*;
import consulo.internal.com.sun.jdi.Location;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author egor
 */
@ExtensionImpl
public class AlternativeSourceNotificationProvider implements EditorNotificationProvider {
  private static final Key<Boolean> FILE_PROCESSED_KEY = Key.create("AlternativeSourceCheckDone");
  private final Project myProject;

  @Inject
  public AlternativeSourceNotificationProvider(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public String getId() {
    return "java-debugger-alternative-source";
  }

  @RequiredReadAction
  @Nullable
  @Override
  public EditorNotificationBuilder buildNotification(@Nonnull VirtualFile file, @Nonnull FileEditor fileEditor, @Nonnull Supplier<EditorNotificationBuilder> builderFactory) {
    if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
      return null;
    }
    XDebugSession session = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (session == null) {
      FILE_PROCESSED_KEY.set(file, null);
      return null;
    }

    XSourcePosition position = session.getCurrentPosition();
    if (position == null || !file.equals(position.getFile())) {
      FILE_PROCESSED_KEY.set(file, null);
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }

    PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
    if (classes.length == 0) {
      return null;
    }

    PsiClass baseClass = classes[0];
    String name = baseClass.getQualifiedName();

    if (name == null) {
      return null;
    }

    if (DumbService.getInstance(myProject).isDumb()) {
      return null;
    }

    ArrayList<PsiClass> alts = ContainerUtil.newArrayList(JavaPsiFacade.getInstance(myProject).findClasses(name, GlobalSearchScope.allScope(myProject)));
    ContainerUtil.removeDuplicates(alts);

    FILE_PROCESSED_KEY.set(file, true);

    if (alts.size() > 1) {
      for (PsiClass cls : alts) {
        if (cls.equals(baseClass) || cls.getNavigationElement().equals(baseClass)) {
          alts.remove(cls);
          break;
        }
      }
      alts.add(0, baseClass);

      String locationDeclName = null;
      XStackFrame frame = session.getCurrentStackFrame();
      if (frame instanceof JavaStackFrame) {
        Location location = ((JavaStackFrame) frame).getDescriptor().getLocation();
        if (location != null) {
          locationDeclName = location.declaringType().name();
        }
      }

      EditorNotificationBuilder builder = builderFactory.get();
      build(alts, baseClass.getQualifiedName(), myProject, file, locationDeclName, builder);
      return builder;
    }
    return null;
  }

  public static boolean fileProcessed(VirtualFile file) {
    return FILE_PROCESSED_KEY.get(file) != null;
  }

  public static void build(List<PsiClass> alternatives, final String aClass, final Project project, final VirtualFile file, String locationDeclName, EditorNotificationBuilder builder) {
    builder.withText(LocalizeValue.localizeTODO(DebuggerBundle.message("editor.notification.alternative.source", aClass)));

    builder.withAction(LocalizeValue.localizeTODO("Select Source..."), (e) -> {
      BaseListPopupStep<PsiClass> classes = new BaseListPopupStep<PsiClass>("Choose class module", alternatives) {
        @Override
        public Image getIconFor(PsiClass value) {
          Module module = ModuleUtilCore.findModuleForPsiElement(value);
          return ModuleManager.getInstance(project).getModuleIcon(module);
        }

        @Nonnull
        @Override
        public String getTextFor(PsiClass value) {
          Module module = ModuleUtilCore.findModuleForPsiElement(value);
          return module == null ? "<unknown>" : module.getName();
        }

        @Override
        public PopupStep onChosen(PsiClass item, boolean finalChoice) {
          if (finalChoice) {
            final DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
            final DebuggerSession session = context.getDebuggerSession();
            final VirtualFile vFile = item.getContainingFile().getVirtualFile();
            if (session != null && vFile != null) {
              session.getProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
                @Override
                protected void action() throws Exception {
                  if (!StringUtil.isEmpty(locationDeclName)) {
                    DebuggerUtilsEx.setAlternativeSourceUrl(locationDeclName, vFile.getUrl(), project);
                  }
                  Application.get().invokeLater(() ->
                  {
                    FileEditorManager.getInstance(project).closeFile(file);
                    session.refresh(true);
                  });
                }
              });
            } else {
              FileEditorManager.getInstance(project).closeFile(file);
              item.navigate(true);
            }
          }
          return super.onChosen(item, finalChoice);
        }
      };

      JBPopupFactory.getInstance().createListPopup(classes).showBy(e);
    });

    builder.withAction(LocalizeValue.localizeTODO(DebuggerBundle.message("action.disable.text")), uiEvent -> {
      DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE = false;
      FILE_PROCESSED_KEY.set(file, null);

      EditorNotifications.getInstance(project).updateNotifications(file);
    });
  }
}
