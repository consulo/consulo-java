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
package com.intellij.java.impl.codeInspection.varScopeCanBeNarrowed;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.analysis.impl.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.lang.java.JavaCommenter;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.controlFlow.AnalysisCanceledException;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlow;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowFactory;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author ven
 */
@ExtensionImpl
public class FieldCanBeLocalInspection extends BaseLocalInspectionTool {
  @NonNls
  public static final String SHORT_NAME = "FieldCanBeLocal";
  public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.CLASS_LAYOUT_GROUP_NAME;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.field.can.be.local.display.name");
  }

  @Override
  @Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    if (!EXCLUDE_ANNOS.isEmpty()) {
      super.writeSettings(node);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel listPanel = SpecialAnnotationsUtil
        .createSpecialAnnotationsListControl(EXCLUDE_ANNOS, InspectionsBundle.message("special.annotations.annotations.list"));

    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            final boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        for (PsiClass aClass : file.getClasses()) {
          doCheckClass(aClass, holder, EXCLUDE_ANNOS);
        }
      }
    };
  }

  private static void doCheckClass(final PsiClass aClass, ProblemsHolder holder, final List<String> excludeAnnos) {
    if (aClass.isInterface()) return;
    final PsiField[] fields = aClass.getFields();
    final Set<PsiField> candidates = new LinkedHashSet<>();
    for (PsiField field : fields) {
      if (!field.isPhysical() || AnnotationUtil.isAnnotated(field, excludeAnnos, 0)) {
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.VOLATILE)) {
        // Assume that fields marked as volatile can be modified concurrently
        // (e.g. if the only method where they are changed is called from several threads)
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.PRIVATE) && !(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(
        PsiModifier.FINAL))) {
        candidates.add(field);
      }
    }

    removeFieldsReferencedFromInitializers(aClass, candidates);
    if (candidates.isEmpty()) return;

    final Set<PsiField> usedFields = new HashSet<>();
    removeReadFields(aClass, candidates, usedFields);

    if (candidates.isEmpty()) return;
    for (PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field)) {
        final String message = InspectionsBundle.message("inspection.field.can.be.local.problem.descriptor");
        holder.registerProblem(field.getNameIdentifier(), message, new ConvertFieldToLocalQuickFix());
      }
    }
  }

  private static void removeReadFields(PsiClass aClass, final Set<PsiField> candidates, final Set<PsiField> usedFields) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!candidates.isEmpty()) super.visitElement(element);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields);
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiElement body = expression.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        checkCodeBlock(initializer.getBody(), candidates, usedFields);
      }
    });
  }

  private static void checkCodeBlock(final PsiElement body, final Set<PsiField> candidates, Set<PsiField> usedFields) {
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
      final List<PsiVariable> usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
      for (PsiVariable usedVariable : usedVars) {
        if (usedVariable instanceof PsiField) {
          final PsiField usedField = (PsiField) usedVariable;
          if (!usedFields.add(usedField)) {
            candidates.remove(usedField); //used in more than one code block
          }
        }
      }
      final Ref<Collection<PsiVariable>> writtenVariables = new Ref<>();
      final List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      for (final PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
        final PsiElement resolved = readBeforeWrite.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField) resolved;
          if (!isImmutableState(field.getType()) || !PsiUtil.isConstantExpression(field.getInitializer()) || getWrittenVariables(controlFlow, writtenVariables).contains(field)) {
            PsiElement parent = body.getParent();
            if (!(parent instanceof PsiMethod) ||
                !((PsiMethod) parent).isConstructor() ||
                field.getInitializer() == null ||
                field.hasModifierProperty(PsiModifier.STATIC) ||
                !PsiTreeUtil.isAncestor(((PsiMethod) parent).getContainingClass(), field, true)) {
              candidates.remove(field);
            }
          }
        }
      }
    } catch (AnalysisCanceledException e) {
      candidates.clear();
    }
  }

  private static boolean isImmutableState(PsiType type) {
    return type instanceof PsiPrimitiveType ||
        PsiPrimitiveType.getUnboxedType(type) != null ||
        Comparing.strEqual(JavaClassNames.JAVA_LANG_STRING, type.getCanonicalText());
  }

  private static Collection<PsiVariable> getWrittenVariables(ControlFlow controlFlow, Ref<Collection<PsiVariable>> writtenVariables) {
    if (writtenVariables.get() == null) {
      writtenVariables.set(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    }
    return writtenVariables.get();
  }

  private static void removeFieldsReferencedFromInitializers(final PsiClass aClass, final Set<PsiField> candidates) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        //do not go inside method
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField) resolved;
          if (aClass.equals(field.getContainingClass())) {
            candidates.remove(field);
          }
        }

        super.visitReferenceExpression(expression);
      }
    });
  }

  private static boolean hasImplicitReadOrWriteUsage(final PsiField field) {
    return field.getProject().getExtensionPoint(ImplicitUsageProvider.class).findFirstSafe(provider -> {
      return provider.isImplicitRead(field) || provider.isImplicitWrite(field);
    }) != null;
  }

  private static class ConvertFieldToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiField> {

    @Override
    @Nullable
    protected PsiField getVariable(@Nonnull ProblemDescriptor descriptor) {
      return PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
    }

    @Override
    protected void beforeDelete(@Nonnull Project project, @Nonnull PsiField variable, @Nonnull PsiElement newDeclaration) {
      final PsiDocComment docComment = variable.getDocComment();
      if (docComment != null) moveDocCommentToDeclaration(project, docComment, newDeclaration);
    }

    @Nonnull
    @Override
    protected String suggestLocalName(@Nonnull Project project, @Nonnull PsiField field, @Nonnull PsiCodeBlock scope) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);

      final String propertyName = styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
      final String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      return RefactoringUtil.suggestUniqueVariableName(localName, scope, field);
    }

    private static void moveDocCommentToDeclaration(@Nonnull Project project, @Nonnull PsiDocComment docComment, @Nonnull PsiElement declaration) {
      final StringBuilder buf = new StringBuilder();
      for (PsiElement psiElement : docComment.getDescriptionElements()) {
        buf.append(psiElement.getText());
      }
      if (buf.length() > 0) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        final JavaCommenter commenter = new JavaCommenter();
        final PsiComment comment = elementFactory.createCommentFromText(commenter.getBlockCommentPrefix() + buf.toString() + commenter.getBlockCommentSuffix(), declaration);
        declaration.getParent().addBefore(comment, declaration);
      }
    }
  }
}