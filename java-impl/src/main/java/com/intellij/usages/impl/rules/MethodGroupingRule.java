/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

import javax.annotation.Nonnull;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import consulo.util.dataholder.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import consulo.ide.IconDescriptorUpdaters;
import consulo.ui.image.Image;

/**
 * @author max
 */
public class MethodGroupingRule implements UsageGroupingRule {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.rules.MethodGroupingRule");

  @Override
  public UsageGroup groupUsage(@Nonnull Usage usage) {
    if (!(usage instanceof PsiElementUsage)) return null;
    PsiElement psiElement = ((PsiElementUsage)usage).getElement();
    PsiFile containingFile = psiElement.getContainingFile();
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(containingFile.getProject());
    PsiFile topLevelFile = manager.getTopLevelFile(containingFile);
    if (topLevelFile instanceof PsiJavaFile) {
      PsiElement containingMethod = topLevelFile == containingFile ? psiElement : manager.getInjectionHost(containingFile);
      if (usage instanceof UsageInfo2UsageAdapter && topLevelFile == containingFile) {
        int offset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
        containingMethod = containingFile.findElementAt(offset);
      }
      do {
        containingMethod = PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class, true);
        if (containingMethod == null) break;
        final PsiClass containingClass = ((PsiMethod)containingMethod).getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() != null) break;
      }
      while (true);

      if (containingMethod != null) {
        return new MethodUsageGroup((PsiMethod)containingMethod);
      }
    }
    return null;
  }

  private static class MethodUsageGroup implements UsageGroup, TypeSafeDataProvider {
    private final SmartPsiElementPointer<PsiMethod> myMethodPointer;
    private final String myName;
    private final Image myIcon;
    private final Project myProject;

    public MethodUsageGroup(PsiMethod psiMethod) {
      myName = PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY,
          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE
        );
      myProject = psiMethod.getProject();
      myMethodPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiMethod);

      myIcon = getIconImpl(psiMethod);
    }

    @Override
    public void update() {
    }

    private static Image getIconImpl(PsiMethod psiMethod) {
      return IconDescriptorUpdaters.getIcon(psiMethod, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    public int hashCode() {
      return myName.hashCode();
    }

    public boolean equals(Object object) {
      if (!(object instanceof MethodUsageGroup)) {
        return false;
      }
      MethodUsageGroup group = (MethodUsageGroup) object;
      return Comparing.equal(myName, ((MethodUsageGroup)object).myName)
             && SmartPointerManager.getInstance(myProject).pointToTheSameElement(myMethodPointer, group.myMethodPointer);
    }

    @Override
    public Image getIcon() {
      return myIcon;
    }

    private PsiMethod getMethod() {
      return myMethodPointer.getElement();
    }

    @Override
    @Nonnull
    public String getText(UsageView view) {
      return myName;
    }

    @Override
    public FileStatus getFileStatus() {
      return isValid() ? NavigationItemFileStatus.get(getMethod()) : null;
    }

    @Override
    public boolean isValid() {
      final PsiMethod method = getMethod();
      return method != null && method.isValid();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getMethod().navigate(focus);
      }
    }

    @Override
    public boolean canNavigate() {
      return isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }

    @Override
    public int compareTo(UsageGroup usageGroup) {
      if (!(usageGroup instanceof MethodUsageGroup)) {
        LOG.error("MethodUsageGroup expected but " + usageGroup.getClass() + " found");
      }
      MethodUsageGroup other = (MethodUsageGroup)usageGroup;
      if (SmartPointerManager.getInstance(myProject).pointToTheSameElement(myMethodPointer, other.myMethodPointer)) {
        return 0;
      }
      if (!UsageViewSettings.getInstance().IS_SORT_MEMBERS_ALPHABETICALLY) {
        Segment segment1 = myMethodPointer.getRange();
        Segment segment2 = other.myMethodPointer.getRange();
        if (segment1 != null && segment2 != null) {
          return segment1.getStartOffset() - segment2.getStartOffset();
        }
      }

      return myName.compareToIgnoreCase(other.myName);
    }

    @Override
    public void calcData(final Key<?> key, final DataSink sink) {
      if (!isValid()) return;
      if (LangDataKeys.PSI_ELEMENT == key) {
        sink.put(LangDataKeys.PSI_ELEMENT, getMethod());
      }
      if (UsageView.USAGE_INFO_KEY == key) {
        PsiMethod method = getMethod();
        if (method != null) {
          sink.put(UsageView.USAGE_INFO_KEY, new UsageInfo(method));
        }
      }
    }
  }
}
