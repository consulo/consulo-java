/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.magicConstant;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.impl.openapi.projectRoots.impl.DefaultJavaSdkTypeImpl;
import com.intellij.java.impl.slicer.DuplicateMap;
import com.intellij.java.impl.slicer.SliceAnalysisParams;
import com.intellij.java.impl.slicer.SliceRootNode;
import com.intellij.java.impl.slicer.SliceUsage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.function.Processor;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.*;

@ExtensionImpl
public class MagicConstantInspection extends BaseJavaLocalInspectionTool {
  public static final Key<Boolean> NO_ANNOTATIONS_FOUND = Key.create("REPORTED_NO_ANNOTATIONS_FOUND");

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesProbableBugs().get();
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Magic Constant";
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "MagicConstant";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder, boolean isOnTheFly, @Nonnull LocalInspectionToolSession session, Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@Nonnull PsiJavaFile file) {
        checkAnnotationsJarAttached(file, holder);
      }

      @Override
      @RequiredReadAction
      public void visitCallExpression(@Nonnull PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      @RequiredReadAction
      public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r == null) {
          return;
        }
        PsiExpression l = expression.getLExpression();
        if (!(l instanceof PsiReferenceExpression)) {
          return;
        }
        PsiElement resolved = ((PsiReferenceExpression) l).resolve();
        if (!(resolved instanceof PsiModifierListOwner)) {
          return;
        }
        PsiModifierListOwner owner = (PsiModifierListOwner) resolved;
        PsiType type = expression.getType();
        checkExpression(r, owner, type, holder);
      }

      @Override
      @RequiredReadAction
      public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) {
          return;
        }
        PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
        PsiMethod method = element instanceof PsiMethod ? (PsiMethod) element : LambdaUtil.getFunctionalInterfaceMethod(element);
        if (method == null) {
          return;
        }
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      @RequiredReadAction
      public void visitNameValuePair(@Nonnull PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression)) {
          return;
        }
        PsiReference ref = pair.getReference();
        if (ref == null) {
          return;
        }
        PsiMethod method = (PsiMethod) ref.resolve();
        if (method == null) {
          return;
        }
        checkExpression((PsiExpression) value, method, method.getReturnType(), holder);
      }

      @Override
      @RequiredReadAction
      public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) {
          return;
        }
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) {
          return;
        }
        checkBinary(l, r);
        checkBinary(r, l);
      }

      @RequiredReadAction
      private void checkBinary(PsiExpression l, PsiExpression r) {
        if (l instanceof PsiReference lRef) {
          PsiElement resolved = lRef.resolve();
          if (resolved instanceof PsiModifierListOwner modifierListOwner) {
            checkExpression(r, modifierListOwner, getType(modifierListOwner), holder);
          }
        } else if (l instanceof PsiMethodCallExpression lMethodCall) {
          PsiMethod method = lMethodCall.resolveMethod();
          if (method != null) {
            checkExpression(r, method, method.getReturnType(), holder);
          }
        }
      }
    };
  }

  @Override
  public void cleanup(Project project) {
    super.cleanup(project);
    project.putUserData(NO_ANNOTATIONS_FOUND, null);
  }

  private static void checkAnnotationsJarAttached(@Nonnull PsiFile file, @Nonnull ProblemsHolder holder) {
    final Project project = file.getProject();
    if (!holder.isOnTheFly()) {
      final Boolean found = project.getUserData(NO_ANNOTATIONS_FOUND);
      if (found != null) {
        return;
      }
    }

    PsiClass event = JavaPsiFacade.getInstance(project).findClass("java.awt.event.InputEvent", GlobalSearchScope.allScope(project));
    if (event == null) {
      return; // no jdk to attach
    }
    PsiMethod[] methods = event.findMethodsByName("getModifiers", false);
    if (methods.length != 1) {
      return; // no jdk to attach
    }
    PsiMethod getModifiers = methods[0];
    PsiAnnotation annotation = ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(getModifiers, MagicConstant.class.getName());
    if (annotation != null) {
      return;
    }
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(getModifiers);
    if (virtualFile == null) {
      return; // no jdk to attach
    }
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
    Sdk jdk = null;
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry) {
        Sdk temp = ((ModuleExtensionWithSdkOrderEntry) orderEntry).getSdk();
        if (temp != null && temp.getSdkType() instanceof JavaSdkType) {
          jdk = temp;
          break;
        }
      }
    }
    if (jdk == null) {
      return; // no jdk to attach
    }

    if (!holder.isOnTheFly()) {
      project.putUserData(NO_ANNOTATIONS_FOUND, Boolean.TRUE);
    }

    final Sdk finalJdk = jdk;

    String path = finalJdk.getHomePath();
    String text = "No external annotations attached to the JDK " + finalJdk.getName() +
      (path == null ? "" : " (" + FileUtil.toSystemDependentName(path) + ")") + ", some issues will not be found";
    holder.registerProblem(file, text, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new LocalQuickFix() {
      @Nonnull
      @Override
      public String getName() {
        return "Attach annotations";
      }

      @Nonnull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      @RequiredUIAccess
      public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        project.getApplication().runWriteAction(() -> {
          SdkModificator modificator = finalJdk.getSdkModificator();
          DefaultJavaSdkTypeImpl.attachJdkAnnotations(modificator);
          modificator.commitChanges();
        });
      }
    });
  }

  @RequiredReadAction
  private static void checkExpression(PsiExpression expression, PsiModifierListOwner owner, PsiType type, ProblemsHolder holder) {
    AllowedValues allowed = getAllowedValues(owner, type, null);
    if (allowed == null) {
      return;
    }
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) {
      scope = expression;
    }
    if (!isAllowed(scope, expression, allowed, expression.getManager(), null)) {
      registerProblem(expression, allowed, holder);
    }
  }

  @RequiredReadAction
  private static void checkCall(@Nonnull PsiCallExpression methodCall, @Nonnull ProblemsHolder holder) {
    PsiMethod method = methodCall.resolveMethod();
    if (method == null) {
      return;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      AllowedValues values = getAllowedValues(parameter, parameter.getType(), null);
      if (values == null) {
        continue;
      }
      if (i >= arguments.length) {
        break;
      }
      PsiExpression argument = arguments[i];
      argument = PsiUtil.deparenthesizeExpression(argument);
      if (argument == null) {
        continue;
      }

      checkMagicParameterArgument(parameter, argument, values, holder);
    }
  }

  static class AllowedValues {
    final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;

    private AllowedValues(@Nonnull PsiAnnotationMemberValue[] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
    }

    @Override
    @RequiredReadAction
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AllowedValues a2 = (AllowedValues) o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new HashSet<>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new HashSet<>(Arrays.asList(a2.values));
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    @RequiredReadAction
    public boolean isSubsetOf(@Nonnull AllowedValues other, @Nonnull PsiManager manager) {
      for (PsiAnnotationMemberValue value : values) {
        boolean found = false;
        for (PsiAnnotationMemberValue otherValue : other.values) {
          if (same(value, otherValue, manager)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    }
  }

  private static AllowedValues getAllowedValuesFromMagic(
    @Nonnull PsiModifierListOwner element,
    @Nonnull PsiType type,
    @Nonnull PsiAnnotation magic,
    @Nonnull PsiManager manager
  ) {
    PsiAnnotationMemberValue[] allowedValues;
    final boolean canBeOred;
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue("intValues");
      allowedValues = intValues instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue
        ? arrayInitializerMemberValue.getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
      if (allowedValues.length == 0) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue("flags");
        allowedValues = orValue instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue
          ? arrayInitializerMemberValue.getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
        canBeOred = true;
      } else {
        canBeOred = false;
      }
    } else if (type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())))) {
      PsiAnnotationMemberValue strValuesAttr = magic.findAttributeValue("stringValues");
      allowedValues = strValuesAttr instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue
        ? arrayInitializerMemberValue.getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
      canBeOred = false;
    } else {
      return null; //other types not supported
    }

    if (allowedValues.length != 0) {
      return new AllowedValues(allowedValues, canBeOred);
    }

    // last resort: try valuesFromClass
    PsiAnnotationMemberValue[] values = readFromClass("valuesFromClass", magic, type, manager);
    boolean ored = false;
    if (values == null) {
      values = readFromClass("flagsFromClass", magic, type, manager);
      ored = true;
    }
    if (values == null) {
      return null;
    }
    return new AllowedValues(values, ored);
  }

  private static PsiAnnotationMemberValue[] readFromClass(
    @NonNls @Nonnull String attributeName,
    @Nonnull PsiAnnotation magic,
    @Nonnull PsiType type,
    @Nonnull PsiManager manager
  ) {
    PsiAnnotationMemberValue fromClassAttr = magic.findAttributeValue(attributeName);
    PsiType fromClassType = fromClassAttr instanceof PsiClassObjectAccessExpression classObjectAccessExpression
      ? classObjectAccessExpression.getOperand().getType() : null;
    PsiClass fromClass = fromClassType instanceof PsiClassType classType ? classType.resolve() : null;
    if (fromClass == null) {
      return null;
    }
    String fqn = fromClass.getQualifiedName();
    if (fqn == null) {
      return null;
    }
    List<PsiAnnotationMemberValue> constants = new ArrayList<>();
    for (PsiField field : fromClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.PUBLIC)
        || !field.hasModifierProperty(PsiModifier.STATIC)
        || !field.hasModifierProperty(PsiModifier.FINAL)) {
        continue;
      }
      PsiType fieldType = field.getType();
      if (!Comparing.equal(fieldType, type)) {
        continue;
      }
      PsiAssignmentExpression e = (PsiAssignmentExpression) JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText("x=" + fqn + "." + field.getName(), field);
      PsiReferenceExpression refToField = (PsiReferenceExpression) e.getRExpression();
      constants.add(refToField);
    }
    if (constants.isEmpty()) {
      return null;
    }

    return constants.toArray(new PsiAnnotationMemberValue[constants.size()]);
  }

  @RequiredReadAction
  static AllowedValues getAllowedValues(@Nonnull PsiModifierListOwner element, PsiType type, Set<PsiClass> visited) {
    PsiAnnotation[] annotations = getAllAnnotations(element);
    PsiManager manager = element.getManager();
    for (PsiAnnotation annotation : annotations) {
      AllowedValues values;
      if (type != null && MagicConstant.class.getName().equals(annotation.getQualifiedName())) {
        //PsiAnnotation magic = AnnotationUtil.findAnnotationInHierarchy(element, Collections.singleton(MagicConstant.class.getName()));
        values = getAllowedValuesFromMagic(element, type, annotation, manager);
        if (values != null) {
          return values;
        }
      }

      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      PsiElement resolved = ref == null ? null : ref.resolve();
      if (!(resolved instanceof PsiClass psiClass && psiClass.isAnnotationType())) {
        continue;
      }
      PsiClass aClass = (PsiClass) resolved;
      if (visited == null) {
        visited = new HashSet<>();
      }
      if (!visited.add(aClass)) {
        continue;
      }
      values = getAllowedValues(aClass, type, visited);
      if (values != null) {
        return values;
      }
    }

    return parseBeanInfo(element, manager);
  }

  private static PsiAnnotation[] getAllAnnotations(final PsiModifierListOwner element) {
    return LanguageCachedValueUtil.getCachedValue(element, new CachedValueProvider<PsiAnnotation[]>() {
      @Nullable
      @Override
      public Result<PsiAnnotation[]> compute() {
        return Result.create(
          AnnotationUtil.getAllAnnotations(element, true, null),
          PsiModificationTracker.MODIFICATION_COUNT
        );
      }
    });
  }

  @RequiredReadAction
  private static AllowedValues parseBeanInfo(@Nonnull PsiModifierListOwner owner, @Nonnull PsiManager manager) {
    PsiMethod method = null;
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter) owner;
      PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) {
        return null;
      }
      PsiElement nav = scope.getNavigationElement();
      if (!(nav instanceof PsiMethod)) {
        return null;
      }
      method = (PsiMethod) nav;
      if (method.isConstructor()) {
        // not a property, try the @ConstructorProperties({"prop"})
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "java.beans.ConstructorProperties");
        if (annotation == null) {
          return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (!(value instanceof PsiArrayInitializerMemberValue)) {
          return null;
        }
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
        PsiElement parent = parameter.getParent();
        if (!(parent instanceof PsiParameterList)) {
          return null;
        }
        int index = ((PsiParameterList) parent).getParameterIndex(parameter);
        if (index >= initializers.length) {
          return null;
        }
        PsiAnnotationMemberValue initializer = initializers[index];
        if (!(initializer instanceof PsiLiteralExpression)) {
          return null;
        }
        Object val = ((PsiLiteralExpression) initializer).getValue();
        if (!(val instanceof String)) {
          return null;
        }
        PsiMethod setter = PropertyUtil.findPropertySetter(method.getContainingClass(), (String) val, false, false);
        if (setter == null) {
          return null;
        }
        // try the @beaninfo of the corresponding setter
        PsiElement navigationElement = setter.getNavigationElement();
        if (!(navigationElement instanceof PsiMethod)) {
          return null;
        }
        method = (PsiMethod) navigationElement;
      }
    } else if (owner instanceof PsiMethod) {
      PsiElement nav = owner.getNavigationElement();
      if (!(nav instanceof PsiMethod)) {
        return null;
      }
      method = (PsiMethod) nav;
    }
    if (method == null) {
      return null;
    }

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return null;
    }
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      List<PsiMethod> setters = PropertyUtil.getSetters(aClass, PropertyUtil.getPropertyNameByGetter(method));
      if (setters.size() != 1) {
        return null;
      }
      method = setters.get(0);
    }
    if (!PropertyUtil.isSimplePropertySetter(method)) {
      return null;
    }
    PsiDocComment doc = method.getDocComment();
    if (doc == null) {
      return null;
    }
    PsiDocTag beaninfo = doc.findTagByName("beaninfo");
    if (beaninfo == null) {
      return null;
    }
    String data = StringUtil.join(beaninfo.getDataElements(), element -> element.getText(), "\n");
    int enumIndex = StringUtil.indexOfSubstringEnd(data, "enum:");
    if (enumIndex == -1) {
      return null;
    }
    data = data.substring(enumIndex);
    int colon = data.indexOf(":");
    int last = colon == -1 ? data.length() : data.substring(0, colon).lastIndexOf("\n");
    data = data.substring(0, last);

    List<PsiAnnotationMemberValue> values = new ArrayList<>();
    for (String line : StringUtil.splitByLines(data)) {
      List<String> words = StringUtil.split(line, " ", true, true);
      if (words.size() != 2) {
        continue;
      }
      String ref = words.get(1);
      PsiExpression constRef = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(ref, aClass);
      if (!(constRef instanceof PsiReferenceExpression)) {
        continue;
      }
      PsiReferenceExpression expr = (PsiReferenceExpression) constRef;
      values.add(expr);
    }
    if (values.isEmpty()) {
      return null;
    }
    PsiAnnotationMemberValue[] array = values.toArray(new PsiAnnotationMemberValue[values.size()]);
    return new AllowedValues(array, false);
  }

  private static PsiType getType(@Nonnull PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable) element).getType() : element instanceof PsiMethod ? ((PsiMethod) element).getReturnType() : null;
  }

  @RequiredReadAction
  private static void checkMagicParameterArgument(
    @Nonnull PsiParameter parameter,
    @Nonnull PsiExpression argument,
    @Nonnull AllowedValues allowedValues,
    @Nonnull ProblemsHolder holder
  ) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() && !isAllowed(parameter.getDeclarationScope(), argument, allowedValues, manager, null)) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  @RequiredReadAction
  private static void registerProblem(@Nonnull PsiExpression argument, @Nonnull AllowedValues allowedValues, @Nonnull ProblemsHolder holder) {
    String values = StringUtil.join(
      allowedValues.values,
      value -> {
        if (value instanceof PsiReferenceExpression referenceExpression) {
          PsiElement resolved = referenceExpression.resolve();
          if (resolved instanceof PsiVariable variable) {
            return PsiFormatUtil.formatVariable(
              variable,
              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
              PsiSubstitutor.EMPTY
            );
          }
        }
        return value.getText();
      },
      ", "
    );
    holder.registerProblem(argument, "Must be one of: " + values);
  }

  @RequiredReadAction
  private static boolean isAllowed(
    @Nonnull final PsiElement scope,
    @Nonnull final PsiExpression argument,
    @Nonnull final AllowedValues allowedValues,
    @Nonnull final PsiManager manager,
    final Set<PsiExpression> visited
  ) {
    if (isGoodExpression(argument, allowedValues, scope, manager, visited)) {
      return true;
    }

    return processValuesFlownTo(argument, scope, manager,
      expression -> isGoodExpression(expression, allowedValues, scope, manager, visited)
    );
  }

  @RequiredReadAction
  private static boolean isGoodExpression(
    @Nonnull PsiExpression e,
    @Nonnull AllowedValues allowedValues,
    @Nonnull PsiElement scope,
    @Nonnull PsiManager manager,
    @Nullable Set<PsiExpression> visited
  ) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) {
      return true;
    }
    if (visited == null) {
      visited = new HashSet<>();
    }
    if (!visited.add(expression)) {
      return true;
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression) expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited);
      if (!thenAllowed) {
        return false;
      }
      PsiExpression elseExpression = ((PsiConditionalExpression) expression).getElseExpression();
      return elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited);
    }

    if (isOneOf(expression, allowedValues, manager)) {
      return true;
    }

    if (allowedValues.canBeOred) {
      PsiExpression zero = getLiteralExpression(expression, manager, "0");
      if (same(expression, zero, manager)) {
        return true;
      }
      PsiExpression mOne = getLiteralExpression(expression, manager, "-1");
      if (same(expression, mOne, manager)) {
        return true;
      }
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression) expression).getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : ((PsiPolyadicExpression) expression).getOperands()) {
            if (!isAllowed(scope, operand, allowedValues, manager, visited)) {
              return false;
            }
          }
          return true;
        }
      }
      if (expression instanceof PsiPrefixExpression && JavaTokenType.TILDE.equals(((PsiPrefixExpression) expression).getOperationTokenType())) {
        PsiExpression operand = ((PsiPrefixExpression) expression).getOperand();
        return operand == null || isAllowed(scope, operand, allowedValues, manager, visited);
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference reference) {
      resolved = reference.resolve();
    } else if (expression instanceof PsiCallExpression callExpression) {
      resolved = callExpression.resolveMethod();
    }

    AllowedValues allowedForRef;
    if (resolved instanceof PsiModifierListOwner modifierListOwner
      && (allowedForRef = getAllowedValues(modifierListOwner, getType(modifierListOwner), null)) != null
      && allowedForRef.isSubsetOf(allowedValues, manager)) {
      return true;
    }

    return PsiType.NULL.equals(expression.getType());
  }

  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("LITERAL_EXPRESSION_CACHE");

  private static PsiExpression getLiteralExpression(@Nonnull PsiExpression context, @Nonnull PsiManager manager, @Nonnull String text) {
    Map<String, PsiExpression> cache = LITERAL_EXPRESSION_CACHE.get(manager);
    if (cache == null) {
      cache = ContainerUtil.createConcurrentSoftValueMap();
      cache = manager.putUserDataIfAbsent(LITERAL_EXPRESSION_CACHE, cache);
    }
    PsiExpression expression = cache.get(text);
    if (expression == null) {
      expression = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(text, context);
      cache.put(text, expression);
    }
    return expression;
  }

  @RequiredReadAction
  private static boolean isOneOf(@Nonnull PsiExpression expression, @Nonnull AllowedValues allowedValues, @Nonnull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.values) {
      if (same(allowedValue, expression, manager)) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  private static boolean same(PsiElement e1, PsiElement e2, @Nonnull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression literal1 && e2 instanceof PsiLiteralExpression literal2) {
      return Comparing.equal(literal1.getValue(), literal2.getValue());
    }
    if (e1 instanceof PsiPrefixExpression prefix1 && e2 instanceof PsiPrefixExpression prefix2
      && prefix1.getOperationTokenType() == prefix2.getOperationTokenType()) {
      return same(prefix1.getOperand(), prefix2.getOperand(), manager);
    }
    if (e1 instanceof PsiReference ref1 && e2 instanceof PsiReference ref2) {
      e1 = ref1.resolve();
      e2 = ref2.resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  @RequiredReadAction
  private static boolean processValuesFlownTo(
    @Nonnull final PsiExpression argument,
    @Nonnull PsiElement scope,
    @Nonnull PsiManager manager,
    @Nonnull final Processor<PsiExpression> processor
  ) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(), SliceUsage.createRootUsage(argument, params));

    Collection<? extends AbstractTreeNode> children = rootNode.getChildren().iterator().next().getChildren();
    for (AbstractTreeNode child : children) {
      SliceUsage usage = (SliceUsage) child.getValue();
      PsiElement element = usage.getElement();
      if (element instanceof PsiExpression && !processor.process((PsiExpression) element)) {
        return false;
      }
    }

    return !children.isEmpty();
  }
}
