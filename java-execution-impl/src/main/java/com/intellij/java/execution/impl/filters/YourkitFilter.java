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
package com.intellij.java.execution.impl.filters;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.dataContext.DataManager;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.HyperlinkInfo;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.EditSourceUtil;
import consulo.logging.Logger;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.popup.AWTPopupChooserBuilder;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YourkitFilter implements Filter {
  private static final Logger LOG = Logger.getInstance(YourkitFilter.class);

  private final Project myProject;


  private static final Pattern PATTERN = Pattern.compile("\\s*(\\w*)\\(\\):(-?\\d*), (\\w*\\.java)\\n");

  public YourkitFilter(@Nonnull final Project project) {
    myProject = project;
  }

  public Result applyFilter(final String line, final int entireLength) {
    if (!line.endsWith(".java\n")) {
      return null;
    }

    try {
      final Matcher matcher = PATTERN.matcher(line);
      if (matcher.matches()) {
        final String method = matcher.group(1);
        final int lineNumber = Integer.parseInt(matcher.group(2));
        final String fileName = matcher.group(3);

        final int textStartOffset = entireLength - line.length();

        final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myProject);
        final PsiFile[] psiFiles = cache.getFilesByName(fileName);

        if (psiFiles.length == 0) return null;


        final HyperlinkInfo info = psiFiles.length == 1
          ? new OpenFileHyperlinkInfo(myProject, psiFiles[0].getVirtualFile(), lineNumber - 1)
          : new MyHyperlinkInfo(psiFiles);

        return new Result(textStartOffset + matcher.start(2), textStartOffset + matcher.end(3), info);
      }
    } catch (NumberFormatException e) {
      LOG.debug(e);
    }

    return null;
  }

  private static class MyHyperlinkInfo implements HyperlinkInfo {
    private final PsiFile[] myPsiFiles;

    public MyHyperlinkInfo(final PsiFile[] psiFiles) {
      myPsiFiles = psiFiles;
    }

    @RequiredUIAccess
    public void navigate(final Project project) {
      DefaultPsiElementListCellRenderer renderer = new DefaultPsiElementListCellRenderer();

      final JList<PsiFile> list = new JBList<>(myPsiFiles);
      list.setCellRenderer(renderer);

      final AWTPopupChooserBuilder builder = ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list);
      renderer.installSpeedSearch(builder);

      final Runnable runnable = () -> {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        Object[] selectedElements = list.getSelectedValues();
        for (Object element : selectedElements) {
          Navigatable descriptor = EditSourceUtil.getDescriptor((PsiElement) element);
          if (descriptor != null && descriptor.canNavigate()) {
            descriptor.navigate(true);
          }
        }
      };

      final Editor editor = DataManager.getInstance().getDataContext().getData(Editor.KEY);

      JBPopup popup = builder.setItemChoosenCallback(runnable).
          setTitle("Choose file").
          createPopup();

      EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
    }
  }

  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer<PsiFile> {
    @RequiredReadAction
    public String getElementText(final PsiFile element) {
      return element.getContainingFile().getName();
    }

    @Nullable
    protected String getContainerText(final PsiFile element, final String name) {
      final PsiDirectory parent = element.getParent();
      if (parent == null) return null;
      final PsiJavaPackage psiPackage = JavaDirectoryService.getInstance().getPackage(parent);
      if (psiPackage == null) return null;
      return "(" + psiPackage.getQualifiedName() + ")";
    }

    protected int getIconFlags() {
      return 0;
    }
  }
}
