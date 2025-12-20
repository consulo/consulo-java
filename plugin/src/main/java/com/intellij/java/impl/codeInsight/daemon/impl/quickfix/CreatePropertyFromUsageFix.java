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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.template.*;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class CreatePropertyFromUsageFix extends CreateFromUsageBaseFix implements HighPriorityAction, SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreatePropertyFromUsageFix.class);
  @NonNls private static final String FIELD_VARIABLE = "FIELD_NAME_VARIABLE";
  @NonNls private static final String TYPE_VARIABLE = "FIELD_TYPE_VARIABLE";
  @NonNls private static final String GET_PREFIX = "get";
  @NonNls private static final String IS_PREFIX = "is";
  @NonNls private static final String SET_PREFIX = "set";

  public CreatePropertyFromUsageFix(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
    setText(JavaQuickFixLocalize.createPropertyFromUsageFamily());
  }

  protected final PsiMethodCallExpression myMethodCall;

  @Override
  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (CreateMethodFromUsageFix.hasErrorsInArgumentList(myMethodCall)) return false;
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    String methodName = myMethodCall.getMethodExpression().getReferenceName();
    LOG.assertTrue(methodName != null);
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName == null || propertyName.length() == 0) return false;

    LocalizeValue getterOrSetter = LocalizeValue.of();
    if (methodName.startsWith(GET_PREFIX) || methodName.startsWith(IS_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 0) return false;
      getterOrSetter = JavaQuickFixLocalize.createGetter();
    }
    else if (methodName.startsWith(SET_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 1) return false;
      getterOrSetter = JavaQuickFixLocalize.createSetter();
    }
    else {
      LOG.error("Internal error in create property intention");
    }

    List<PsiClass> classes = getTargetClasses(myMethodCall);
    if (classes.isEmpty()) return false;

    if (!checkTargetClasses(classes, methodName)) return false;
    
    for (PsiClass aClass : classes) {
      if (!aClass.isInterface()) {
        setText(getterOrSetter);
        return true;
      }
    }

    return false;
  }

  protected boolean checkTargetClasses(List<PsiClass> classes, String methodName) {
    return true;
  }

  static class FieldExpression extends Expression {
    private final String myDefaultFieldName;
    private final PsiField myField;
    private final PsiClass myClass;
    private final PsiType[] myExpectedTypes;

    public FieldExpression(PsiField field, PsiClass aClass, PsiType[] expectedTypes) {
      myField = field;
      myClass = aClass;
      myExpectedTypes = expectedTypes;
      myDefaultFieldName = field.getName();
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      set.add(JavaLookupElementBuilder.forField(myField).withTypeText(myField.getType().getPresentableText()));
      PsiField[] fields = myClass.getFields();
      for (PsiField otherField : fields) {
        if (!myDefaultFieldName.equals(otherField.getName())) {
          PsiType otherType = otherField.getType();
          for (PsiType type : myExpectedTypes) {
            if (type.equals(otherType)) {
              set.add(JavaLookupElementBuilder.forField(otherField).withTypeText(otherType.getPresentableText()));
            }
          }
        }
      }

      if (set.size() < 2) return null;
      return set.toArray(new LookupElement[set.size()]);
    }
  }

  @Override
  @Nonnull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> all = super.getTargetClasses(element);
    if (all.isEmpty()) return all;

    List<PsiClass> nonInterfaces = new ArrayList<PsiClass>();
    for (PsiClass aClass : all) {
      if (!aClass.isInterface()) nonInterfaces.add(aClass);
    }
    return nonInterfaces;
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    PsiManager manager = myMethodCall.getManager();
    final Project project = manager.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    boolean isStatic = false;
    PsiExpression qualifierExpression = myMethodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression != null) {
      PsiReference reference = qualifierExpression.getReference();
      if (reference != null) {
        isStatic = reference.resolve() instanceof PsiClass;
      }
    }
    else {
      PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
      if (method != null) {
        isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      }
    }
    String fieldName = getVariableName(myMethodCall, isStatic);
    LOG.assertTrue(fieldName != null);
    String callText = myMethodCall.getMethodExpression().getReferenceName();
    LOG.assertTrue(callText != null, myMethodCall.getMethodExpression());
    PsiType[] expectedTypes;
    PsiType type;
    PsiField field = targetClass.findFieldByName(fieldName, true);
    if (callText.startsWith(GET_PREFIX)) {
      expectedTypes = field != null ? new PsiType[]{field.getType()} : CreateFromUsageUtils.guessType(myMethodCall, false);
      type = expectedTypes[0];
    }
    else if (callText.startsWith(IS_PREFIX)) {
      type = PsiType.BOOLEAN;
      expectedTypes = new PsiType[]{type};
    }
    else {
      type = myMethodCall.getArgumentList().getExpressions()[0].getType();
      if (type == null || PsiType.NULL.equals(type)) type = PsiType.getJavaLangObject(manager, myMethodCall.getResolveScope());
      expectedTypes = new PsiType[]{type};
    }

    positionCursor(project, targetClass.getContainingFile(), targetClass);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    if (field == null) {
      field = factory.createField(fieldName, type);
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, isStatic);
    }
    PsiMethod accessor;
    PsiElement fieldReference;
    PsiElement typeReference;
    PsiCodeBlock body;
    if (callText.startsWith(GET_PREFIX) || callText.startsWith(IS_PREFIX)) {
      accessor = (PsiMethod)targetClass.add(PropertyUtil.generateGetterPrototype(field));
      body = accessor.getBody();
      LOG.assertTrue(body != null, accessor.getText());
      fieldReference = ((PsiReturnStatement)body.getStatements()[0]).getReturnValue();
      typeReference = accessor.getReturnTypeElement();
    }
    else {
      accessor = (PsiMethod)targetClass.add(PropertyUtil.generateSetterPrototype(field, targetClass));
      body = accessor.getBody();
      LOG.assertTrue(body != null, accessor.getText());
      PsiAssignmentExpression expr = (PsiAssignmentExpression)((PsiExpressionStatement)body.getStatements()[0]).getExpression();
      fieldReference = ((PsiReferenceExpression)expr.getLExpression()).getReferenceNameElement();
      typeReference = accessor.getParameterList().getParameters()[0].getTypeElement();
    }
    accessor.setName(callText);
    PsiUtil.setModifierProperty(accessor, PsiModifier.STATIC, isStatic);

    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(accessor);
    builder.replaceElement(typeReference, TYPE_VARIABLE, new TypeExpression(project, expectedTypes), true);
    builder.replaceElement(fieldReference, FIELD_VARIABLE, new FieldExpression(field, targetClass, expectedTypes), true);
    builder.setEndVariableAfter(body.getLBrace());

    accessor = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(accessor);
    LOG.assertTrue(accessor != null);
    targetClass = accessor.getContainingClass();
    LOG.assertTrue(targetClass != null);
    Template template = builder.buildTemplate();
    TextRange textRange = accessor.getTextRange();
    final PsiFile file = targetClass.getContainingFile();
    final Editor editor = positionCursor(project, targetClass.getContainingFile(), accessor);
    if (editor == null) return;
    editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
    editor.getCaretModel().moveToOffset(textRange.getStartOffset());

    final boolean isStatic1 = isStatic;
    startTemplate(editor, template, project, new TemplateEditingAdapter() {
      @Override
      public void beforeTemplateFinished(final TemplateState state, Template template) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            String fieldName = state.getVariableValue(FIELD_VARIABLE).getText();
            if (!PsiNameHelper.getInstance(project).isIdentifier(fieldName)) return;
            String fieldType = state.getVariableValue(TYPE_VARIABLE).getText();

            PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
            PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (aClass == null) return;
            PsiField field = aClass.findFieldByName(fieldName, true);
            if (field != null){
              CreatePropertyFromUsageFix.this.beforeTemplateFinished(aClass, field);
              return;
            }
            PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
            try {
              PsiType type = factory.createTypeFromText(fieldType, aClass);
              try {
                field = factory.createField(fieldName, type);
                field = (PsiField)aClass.add(field);
                PsiUtil.setModifierProperty(field, PsiModifier.STATIC, isStatic1);
                CreatePropertyFromUsageFix.this.beforeTemplateFinished(aClass, field);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
            catch (IncorrectOperationException e) {
            }
          }
        });
      }

      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        int offset = editor.getCaretModel().getOffset();
        final PsiMethod generatedMethod = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);
        if (generatedMethod != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CodeStyleManager.getInstance(project).reformat(generatedMethod);
            }
          });
        }
      }
    });
  }

  protected void beforeTemplateFinished(PsiClass aClass, PsiField field) {
    if (myMethodCall.isValid()) {
      positionCursor(myMethodCall.getProject(), myMethodCall.getContainingFile(), myMethodCall);
    }
  }

  private static String getVariableName(PsiMethodCallExpression methodCall, boolean isStatic) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(methodCall.getProject());
    String methodName = methodCall.getMethodExpression().getReferenceName();
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName != null && propertyName.length() > 0) {
      VariableKind kind = isStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
      return codeStyleManager.propertyNameToVariableName(propertyName, kind);
    }

    return null;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
    return methodCall.getMethodExpression().resolve() != null;
  }
}
