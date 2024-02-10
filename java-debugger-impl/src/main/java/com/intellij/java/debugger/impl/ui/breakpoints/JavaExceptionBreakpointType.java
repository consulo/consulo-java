/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.HelpID;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaExceptionBreakpointProperties;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 26, 2005
 */
@ExtensionImpl
public class JavaExceptionBreakpointType extends JavaBreakpointTypeBase<JavaExceptionBreakpointProperties> implements
  JavaBreakpointType<JavaExceptionBreakpointProperties> {
  public JavaExceptionBreakpointType() {
    super("java-exception", DebuggerBundle.message("exception.breakpoints.tab.title"));
  }

  @Nonnull
  @Override
  public Image getEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @Nonnull
  @Override
  public Image getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.EXCEPTION_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("exception.breakpoints.tab.title");
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    String name = breakpoint.getProperties().myQualifiedName;
    if (name != null) {
      return DebuggerBundle.message("breakpoint.exception.breakpoint.display.name", name);
    }
    else {
      return DebuggerBundle.message("breakpoint.any.exception.display.name");
    }
  }

  @Nullable
  @Override
  public JavaExceptionBreakpointProperties createProperties() {
    return new JavaExceptionBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<JavaExceptionBreakpointProperties>> createCustomPropertiesPanel() {
    return new ExceptionBreakpointPropertiesPanel();
  }

  @Nullable
  @Override
  public XBreakpoint<JavaExceptionBreakpointProperties> createDefaultBreakpoint(@Nonnull XBreakpointCreator<JavaExceptionBreakpointProperties>
                                                                                  creator) {
    return creator.createBreakpoint(new JavaExceptionBreakpointProperties());
  }

  //public Key<ExceptionBreakpoint> getBreakpointCategory() {
  //  return ExceptionBreakpoint.CATEGORY;
  //}

  @Nullable
  @Override
  public XBreakpoint<JavaExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final PsiClass throwableClass =
      JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", GlobalSearchScope.allScope(project));
    TreeClassChooser chooser =
      TreeClassChooserFactory.getInstance(project).createInheritanceClassChooser(DebuggerBundle.message("add.exception" +
                                                                                                          ".breakpoint.classchooser.title"),
                                                                                 GlobalSearchScope.allScope(project),
                                                                                 throwableClass,
                                                                                 true,
                                                                                 true,
                                                                                 null);
    chooser.showDialog();
    final PsiClass selectedClass = chooser.getSelected();
    final String qName = selectedClass == null ? null : JVMNameUtil.getNonAnonymousClassName(selectedClass);

    if (qName != null && qName.length() > 0) {
      return ApplicationManager.getApplication().runWriteAction(new Computable<XBreakpoint<JavaExceptionBreakpointProperties>>() {
        @Override
        public XBreakpoint<JavaExceptionBreakpointProperties> compute() {
          return XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(JavaExceptionBreakpointType.this,
                                                                                            new JavaExceptionBreakpointProperties(qName,
                                                                                                                                  ((PsiClassOwner)selectedClass
                                                                                                                                    .getContainingFile())
                                                                                                                                    .getPackageName()));
        }
      });
    }
    return null;
  }

  @Override
  public Breakpoint createJavaBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    if (!XDebuggerManager.getInstance(project).getBreakpointManager().isDefaultBreakpoint(breakpoint)) {
      return new ExceptionBreakpoint(project, breakpoint);
    }
    else {
      return new AnyExceptionBreakpoint(project, breakpoint);
    }
  }
}
