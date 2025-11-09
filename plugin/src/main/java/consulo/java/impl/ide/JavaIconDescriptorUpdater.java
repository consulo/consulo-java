/*
 * Copyright 2013 Consulo.org
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
package consulo.java.impl.ide;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiClassUtil;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.component.util.Iconable;
import consulo.java.impl.util.JavaProjectRootsUtil;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 0:45/19.07.13
 */
@ExtensionImpl(id = "java")
public class JavaIconDescriptorUpdater implements IconDescriptorUpdater {
  @RequiredReadAction
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags) {
    if (element instanceof PsiClass) {
      if (processedFile(element, iconDescriptor)) {
        return;
      }

      if (element instanceof PsiTypeParameter) {
        iconDescriptor.setMainIcon(AllIcons.Nodes.TypeAlias);
        return;
      }
      final PsiClass psiClass = (PsiClass) element;
      if (psiClass.isEnum()) {
        iconDescriptor.setMainIcon(AllIcons.Nodes.Enum);
      } else if (psiClass.isAnnotationType()) {
        iconDescriptor.setMainIcon(AllIcons.Nodes.Annotationtype);
      } else if (psiClass.isRecord()) {
        iconDescriptor.setMainIcon(PlatformIconGroup.nodesRecord());
      } else if (psiClass.isInterface()) {
        iconDescriptor.setMainIcon(AllIcons.Nodes.Interface);
      } else if (psiClass instanceof PsiAnonymousClass) {
        iconDescriptor.setMainIcon(AllIcons.Nodes.AnonymousClass);
      } else {
        final boolean abst = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
        iconDescriptor.setMainIcon(abst ? AllIcons.Nodes.AbstractClass : AllIcons.Nodes.Class);

        if (!DumbService.getInstance(element.getProject()).isDumb()) {
          final PsiManager manager = psiClass.getManager();
          final PsiClass javaLangThrowable = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, psiClass.getResolveScope());
          final boolean isException = javaLangThrowable != null && InheritanceUtil.isInheritorOrSelf(psiClass, javaLangThrowable, true);
          if (isException) {
            iconDescriptor.setMainIcon(abst ? AllIcons.Nodes.AbstractException : AllIcons.Nodes.ExceptionClass);
          }

          if (PsiClassUtil.isRunnableClass(psiClass, false) && PsiMethodUtil.findMainMethod(psiClass) != null) {
            iconDescriptor.addLayerIcon(AllIcons.Nodes.RunnableMark);
          }
        }
      }

      processModifierList(element, iconDescriptor, flags);
    } else if (element instanceof PsiLambdaExpression) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.Lambda);
    } else if (element instanceof PsiMethodReferenceExpression) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.MethodReference);
    } else if (element instanceof PsiJavaFile) {
      if (processedFile(element, iconDescriptor)) {
        return;
      }

      final PsiClass[] classes = ((PsiJavaFile) element).getClasses();
      if (classes.length == 1) {
        IconDescriptorUpdaters.processExistingDescriptor(iconDescriptor, classes[0], flags);
      }
    } else if (element instanceof PsiMethod) {
      iconDescriptor.setMainIcon(((PsiMethod) element).hasModifierProperty(PsiModifier.ABSTRACT) ? AllIcons.Nodes.AbstractMethod : AllIcons.Nodes.Method);

      processModifierList(element, iconDescriptor, flags);
    } else if (element instanceof PsiField) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.Field);
      processModifierList(element, iconDescriptor, flags);
    } else if (element instanceof PsiLocalVariable) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.Variable);
      processModifierList(element, iconDescriptor, flags);
    } else if (element instanceof PsiParameter) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.Parameter);
      processModifierList(element, iconDescriptor, flags);
    }
  }

  public static void processModifierList(PsiElement element, IconDescriptor iconDescriptor, int flags) {
    PsiModifierListOwner owner = (PsiModifierListOwner) element;

    if (owner.hasModifierProperty(PsiModifier.FINAL)) {
      iconDescriptor.addLayerIcon(AllIcons.Nodes.FinalMark);
    }

    if (owner.hasModifierProperty(PsiModifier.STATIC)) {
      iconDescriptor.addLayerIcon(AllIcons.Nodes.StaticMark);
    }

    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      if (owner.hasModifierProperty(PsiModifier.PUBLIC)) {
        iconDescriptor.setRightIcon(AllIcons.Nodes.C_public);
      } else if (owner.hasModifierProperty(PsiModifier.PRIVATE)) {
        iconDescriptor.setRightIcon(AllIcons.Nodes.C_private);
      } else if (owner.hasModifierProperty(PsiModifier.PROTECTED)) {
        iconDescriptor.setRightIcon(AllIcons.Nodes.C_protected);
      } else if (owner.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        iconDescriptor.setRightIcon(AllIcons.Nodes.C_plocal);
      }
    }
  }

  private static boolean processedFile(PsiElement element, IconDescriptor iconDescriptor) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return false;
    }
    final FileType fileType = containingFile.getFileType();
    if (fileType != JavaFileType.INSTANCE && fileType != JavaClassFileType.INSTANCE) {
      return false;
    }

    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    if (!JavaProjectRootsUtil.isJavaSourceFile(element.getProject(), virtualFile, true)) {
      iconDescriptor.setMainIcon(JavaPsiImplIconGroup.filetypesJavaoutsidesource());
      return true;
    }
    return false;
  }
}
