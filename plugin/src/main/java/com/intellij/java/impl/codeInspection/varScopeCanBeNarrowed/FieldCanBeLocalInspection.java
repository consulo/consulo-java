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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
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
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesClassStructure();
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionLocalize.inspectionFieldCanBeLocalDisplayName();
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
    JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      EXCLUDE_ANNOS,
      InspectionLocalize.specialAnnotationsAnnotationsList().get()
    );

    JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(
    @Nonnull final ProblemsHolder holder,
    boolean isOnTheFly,
    LocalInspectionToolSession session,
    Object state
  ) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@Nonnull PsiJavaFile file) {
        for (PsiClass aClass : file.getClasses()) {
          doCheckClass(aClass, holder, EXCLUDE_ANNOS);
        }
      }
    };
  }

  private static void doCheckClass(PsiClass aClass, ProblemsHolder holder, List<String> excludeAnnos) {
    if (aClass.isInterface()) return;
    PsiField[] fields = aClass.getFields();
    Set<PsiField> candidates = new LinkedHashSet<>();
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

    Set<PsiField> usedFields = new HashSet<>();
    removeReadFields(aClass, candidates, usedFields);

    if (candidates.isEmpty()) return;
    for (PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field)) {
        LocalizeValue message = InspectionLocalize.inspectionFieldCanBeLocalProblemDescriptor();
        holder.registerProblem(field.getNameIdentifier(), message.get(), new ConvertFieldToLocalQuickFix());
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
      @RequiredReadAction
      public void visitMethod(@Nonnull PsiMethod method) {
        super.visitMethod(method);

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields);
        }
      }

      @Override
      @RequiredReadAction
      public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        PsiElement body = expression.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields);
        }
      }

      @RequiredReadAction
      @Override
      public void visitClassInitializer(@Nonnull PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        checkCodeBlock(initializer.getBody(), candidates, usedFields);
      }
    });
  }

  @RequiredReadAction
  private static void checkCodeBlock(PsiElement body, Set<PsiField> candidates, Set<PsiField> usedFields) {
    try {
      ControlFlow controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
      List<PsiVariable> usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
      for (PsiVariable usedVariable : usedVars) {
        if (usedVariable instanceof PsiField usedField) {
          if (!usedFields.add(usedField)) {
            candidates.remove(usedField); //used in more than one code block
          }
        }
      }
      Ref<Collection<PsiVariable>> writtenVariables = new Ref<>();
      List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      for (PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
        PsiElement resolved = readBeforeWrite.resolve();
        if (resolved instanceof PsiField field) {
          if (!isImmutableState(field.getType()) || !PsiUtil.isConstantExpression(field.getInitializer())
            || getWrittenVariables(controlFlow, writtenVariables).contains(field)) {
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
        Comparing.strEqual(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
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
      public void visitMethod(@Nonnull PsiMethod method) {
        //do not go inside method
      }

      @Override
      public void visitClassInitializer(@Nonnull PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override
      @RequiredReadAction
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField) resolved;
          if (aClass.equals(field.getContainingClass())) {
            candidates.remove(field);
          }
        }

        super.visitReferenceExpression(expression);
      }
    });
  }

  private static boolean hasImplicitReadOrWriteUsage(PsiField field) {
    return field.getProject().getExtensionPoint(ImplicitUsageProvider.class)
      .findFirstSafe(provider -> provider.isImplicitRead(field) || provider.isImplicitWrite(field)) != null;
  }

  private static class ConvertFieldToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiField> {

    @Override
    @Nullable
    @RequiredReadAction
    protected PsiField getVariable(@Nonnull ProblemDescriptor descriptor) {
      return PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
    }

    @Override
    protected void beforeDelete(@Nonnull Project project, @Nonnull PsiField variable, @Nonnull PsiElement newDeclaration) {
      PsiDocComment docComment = variable.getDocComment();
      if (docComment != null) moveDocCommentToDeclaration(project, docComment, newDeclaration);
    }

    @Nonnull
    @Override
    protected String suggestLocalName(@Nonnull Project project, @Nonnull PsiField field, @Nonnull PsiCodeBlock scope) {
      JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);

      String propertyName = styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
      String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      return RefactoringUtil.suggestUniqueVariableName(localName, scope, field);
    }

    @RequiredReadAction
    private static void moveDocCommentToDeclaration(
      @Nonnull Project project,
      @Nonnull PsiDocComment docComment,
      @Nonnull PsiElement declaration
    ) {
      StringBuilder buf = new StringBuilder();
      for (PsiElement psiElement : docComment.getDescriptionElements()) {
        buf.append(psiElement.getText());
      }
      if (buf.length() > 0) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        JavaCommenter commenter = new JavaCommenter();
        PsiComment comment = elementFactory.createCommentFromText(commenter.getBlockCommentPrefix() + buf.toString() + commenter.getBlockCommentSuffix(), declaration);
        declaration.getParent().addBefore(comment, declaration);
      }
    }
  }
}