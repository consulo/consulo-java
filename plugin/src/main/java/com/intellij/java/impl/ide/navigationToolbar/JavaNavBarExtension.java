// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.navigationToolbar;

import com.intellij.java.impl.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.java.impl.ide.structureView.impl.java.JavaLambdaNodeProvider;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ui.UISettings;
import consulo.fileEditor.structureView.tree.NodeProvider;
import consulo.ide.navigationToolbar.StructureAwareNavBarModelExtension;
import consulo.java.localize.JavaLocalize;
import consulo.language.Language;
import consulo.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.usage.UsageViewShortNameLocation;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.intellij.java.language.psi.util.PsiFormatUtilBase.*;

/**
 * @author anna
 */
@ExtensionImpl
public class JavaNavBarExtension extends StructureAwareNavBarModelExtension {
  private final List<NodeProvider<?>> myNodeProviders = List.of(new JavaLambdaNodeProvider(), new JavaAnonymousClassesNodeProvider());

  @Nullable
  @Override
  @RequiredReadAction
  public String getPresentableText(Object object) {
    return getPresentableText(object, false);
  }

  @Override
  @RequiredReadAction
  public String getPresentableText(Object object, boolean forPopup) {
    if (object instanceof PsiMember member) {
      if (forPopup && object instanceof PsiMethod method) {
        return PsiFormatUtil.formatMethod(
            method,
            PsiSubstitutor.EMPTY,
            SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS,
            SHOW_TYPE
        );
      }
      return ElementDescriptionUtil.getElementDescription(member, UsageViewShortNameLocation.INSTANCE).getNullIfEmpty();
    } else if (object instanceof PsiJavaPackage javaPackage) {
      return LocalizeValue.ofNullable(javaPackage.getName()).orIfEmpty(JavaLocalize.dependenciesTreeNodeDefaultPackageAbbreviation()).get();
    } else if (object instanceof PsiDirectory directory && JrtFileSystem.isRoot(directory.getVirtualFile())) {
      return JavaLocalize.jrtNodeShort().get();
    } else if (object instanceof PsiLambdaExpression) {
      return "Lambda";
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public PsiElement getParent(PsiElement psiElement) {
    if (psiElement instanceof PsiPackage psiPackage) {
      PsiPackage parentPackage = psiPackage.getParentPackage();
      if (parentPackage != null && StringUtil.isNotEmpty(parentPackage.getQualifiedName())) {
        return parentPackage;
      }
    }
    return super.getParent(psiElement);
  }

  @Nullable
  @Override
  public PsiElement adjustElement(PsiElement psiElement) {
    ProjectFileIndex index = ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) {
      VirtualFile file = containingFile.getVirtualFile();
      if (file != null && (index.isInSourceContent(file) || index.isInLibraryClasses(file) || index.isInLibrary(file))) {
        if (psiElement instanceof PsiJavaFile) {
          PsiJavaFile psiJavaFile = (PsiJavaFile) psiElement;
          if (psiJavaFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE) {
            PsiClass[] psiClasses = psiJavaFile.getClasses();
            if (psiClasses.length == 1) {
              return psiClasses[0];
            }
          }
        }
        if (!UISettings.getInstance().getShowMembersInNavigationBar() && psiElement instanceof PsiClass) {
          return psiElement;
        }
      }
      if (!UISettings.getInstance().getShowMembersInNavigationBar()) {
        return containingFile;
      }
    }
    return psiElement;
  }

  @Override
  protected Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  protected List<NodeProvider<?>> getApplicableNodeProviders() {
    return myNodeProviders;
  }

  @Override
  protected boolean acceptParentFromModel(@Nullable PsiElement psiElement) {
    return !(psiElement instanceof PsiJavaFile javaFile) || javaFile.getClasses().length > 1;
  }
}
