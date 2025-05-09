/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.PsiVariableEx;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.content.bundle.Sdk;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;

public class JavaSuppressionUtil {
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  public static boolean alreadyHas14Suppressions(@Nonnull PsiDocCommentOwner commentOwner) {
    final PsiDocComment docComment = commentOwner.getDocComment();
    return docComment != null && docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME) != null;
  }

  @Nullable
  public static String getInspectionIdSuppressedInAnnotationAttribute(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression) element).getValue();
      if (value instanceof String) {
        return (String) value;
      }
    } else if (element instanceof PsiReferenceExpression) {
      final PsiElement psiElement = ((PsiReferenceExpression) element).resolve();
      if (psiElement instanceof PsiVariableEx) {
        final Object val = ((PsiVariableEx) psiElement).computeConstantValue(new HashSet<PsiVariable>());
        if (val instanceof String) {
          return (String) val;
        }
      }
    }
    return null;
  }

  @Nonnull
  public static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    final PsiModifierListOwner owner = (PsiModifierListOwner) modifierList.getParent();
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    final PsiAnnotationMemberValue attributeValue = attributes[0].getValue();
    Collection<String> result = new ArrayList<String>();
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) attributeValue).getInitializers();
      for (PsiAnnotationMemberValue annotationMemberValue : initializers) {
        final String id = getInspectionIdSuppressedInAnnotationAttribute(annotationMemberValue);
        if (id != null) {
          result.add(id);
        }
      }
    } else {
      final String id = getInspectionIdSuppressedInAnnotationAttribute(attributeValue);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  public static PsiElement getElementMemberSuppressedIn(@Nonnull PsiDocCommentOwner owner, String inspectionToolID) {
    PsiElement element = getDocCommentToolSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    element = getAnnotationMemberSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(owner, PsiDocCommentOwner.class);
    while (classContainer != null) {
      element = getDocCommentToolSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      element = getAnnotationMemberSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      classContainer = PsiTreeUtil.getParentOfType(classContainer, PsiDocCommentOwner.class);
    }
    return null;
  }

  public static PsiElement getAnnotationMemberSuppressedIn(@Nonnull PsiModifierListOwner owner, String inspectionToolID) {
    final PsiAnnotation generatedAnnotation = AnnotationUtil.findAnnotation(owner, CommonClassNames.JAVAX_ANNOTATION_GENERATED);
    if (generatedAnnotation != null) return generatedAnnotation;
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (SuppressionUtil.isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList != null ? AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
      }
    }
    return null;
  }

  public static PsiElement getDocCommentToolSuppressedIn(@Nonnull PsiDocCommentOwner owner, String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment == null && owner.getParent() instanceof PsiDeclarationStatement) {
      final PsiElement el = PsiTreeUtil.skipSiblingsBackward(owner.getParent(), PsiWhiteSpace.class);
      if (el instanceof PsiDocComment) {
        docComment = (PsiDocComment) el;
      }
    }
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {
        final PsiElement[] dataElements = inspectionTag.getDataElements();
        for (PsiElement dataElement : dataElements) {
          String valueText = dataElement.getText();
          if (SuppressionUtil.isInspectionToolIdMentioned(valueText, inspectionToolID)) {
            return docComment;
          }
        }
      }
    }
    return null;
  }

  public static Collection<String> getInspectionIdsSuppressedInAnnotation(@Nonnull PsiModifierListOwner owner) {
    if (!PsiUtil.isLanguageLevel5OrHigher(owner)) return Collections.emptyList();
    PsiModifierList modifierList = owner.getModifierList();
    return getInspectionIdsSuppressedInAnnotation(modifierList);
  }

  public static String getSuppressedInspectionIdsIn(@Nonnull PsiElement element) {
    if (element instanceof PsiComment) {
      String text = element.getText();
      Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches()) {
        return matcher.group(1).trim();
      }
    }
    if (element instanceof PsiDocCommentOwner) {
      PsiDocComment docComment = ((PsiDocCommentOwner) element).getDocComment();
      if (docComment != null) {
        PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (inspectionTag != null) {
          String valueText = "";
          for (PsiElement dataElement : inspectionTag.getDataElements()) {
            valueText += dataElement.getText();
          }
          return valueText;
        }
      }
    }
    if (element instanceof PsiModifierListOwner) {
      Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation((PsiModifierListOwner) element);
      return suppressedIds.isEmpty() ? null : StringUtil.join(suppressedIds, ",");
    }
    return null;
  }

  public static PsiElement getElementToolSuppressedIn(@Nonnull final PsiElement place, final String toolId) {
    if (place instanceof PsiFile) return null;
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Override
      @Nullable
      public PsiElement compute() {
        final PsiElement statement = SuppressionUtil.getStatementToolSuppressedIn(place, toolId, PsiStatement.class);
        if (statement != null) {
          return statement;
        }

        PsiVariable local = PsiTreeUtil.getParentOfType(place, PsiVariable.class);
        if (local != null && getAnnotationMemberSuppressedIn(local, toolId) != null) {
          PsiModifierList modifierList = local.getModifierList();
          return modifierList != null ? modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
        }

        PsiDocCommentOwner container = PsiTreeUtil.getNonStrictParentOfType(place, PsiDocCommentOwner.class);
        while (true) {
          if (!(container instanceof PsiTypeParameter)) break;
          container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
        }

        if (container != null) {
          PsiElement element = getElementMemberSuppressedIn(container, toolId);
          if (element != null) return element;
        }
        PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class, true);
        if (classContainer != null) {
          PsiElement element = getElementMemberSuppressedIn(classContainer, toolId);
          if (element != null) return element;
        }

        return null;
      }
    });
  }

  public static void addSuppressAnnotation(@Nonnull Project project,
                                           final PsiElement container,
                                           final PsiModifierListOwner modifierOwner,
                                           @Nonnull String id) throws IncorrectOperationException {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierOwner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    final PsiAnnotation newAnnotation = createNewAnnotation(project, container, annotation, id);
    if (newAnnotation != null) {
      if (annotation != null && annotation.isPhysical()) {
        annotation.replace(newAnnotation);
      } else {
        final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        new AddAnnotationPsiFix(SUPPRESS_INSPECTIONS_ANNOTATION_NAME, modifierOwner, attributes).applyFix();
      }
    }
  }

  private static PsiAnnotation createNewAnnotation(@Nonnull Project project,
                                                   PsiElement container,
                                                   PsiAnnotation annotation,
                                                   @Nonnull String id) throws IncorrectOperationException {
    if (annotation == null) {
      return JavaPsiFacade.getInstance(project).getElementFactory()
          .createAnnotationFromText("@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" + id + "\")", container);
    }
    final String currentSuppressedId = "\"" + id + "\"";
    if (!annotation.getText().contains("{")) {
      final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      if (attributes.length == 1) {
        final String suppressedWarnings = attributes[0].getText();
        if (suppressedWarnings.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
            "@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "({" + suppressedWarnings + ", " + currentSuppressedId + "})", container);

      }
    } else {
      final int curlyBraceIndex = annotation.getText().lastIndexOf("}");
      if (curlyBraceIndex > 0) {
        final String oldSuppressWarning = annotation.getText().substring(0, curlyBraceIndex);
        if (oldSuppressWarning.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
            oldSuppressWarning + ", " + currentSuppressedId + "})", container);
      } else {
        throw new IncorrectOperationException(annotation.getText());
      }
    }
    return null;
  }

  public static boolean canHave15Suppressions(@Nonnull PsiElement file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return false;
    final Sdk jdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    if (jdk == null) return false;
    JavaSdkVersion version = getVersion(jdk);
    if (version == null) return false;
    final boolean is_1_5 = version.isAtLeast(JavaSdkVersion.JDK_1_5);
    return DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings() && is_1_5 && PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Nullable
  private static JavaSdkVersion getVersion(@Nonnull Sdk sdk) {
    String version = sdk.getVersionString();
    if (version == null) return null;
    return JavaSdkVersion.fromVersionString(version);
  }

  @Nullable
  public static PsiElement getElementToAnnotate(PsiElement element, PsiElement container) {
    if (container instanceof PsiDeclarationStatement && canHave15Suppressions(element)) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) container;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          final PsiModifierList modifierList = ((PsiLocalVariable) declaredElement).getModifierList();
          if (modifierList != null) {
            return declaredElement;
          }
        }
      }
    }
    return null;
  }
}
