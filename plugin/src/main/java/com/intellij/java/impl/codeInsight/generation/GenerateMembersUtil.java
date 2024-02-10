/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.impl.generate.GenerationUtil;
import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.impl.generate.template.TemplatesManager;
import com.intellij.java.impl.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.impl.psi.impl.light.LightTypeElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.matcher.NameUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.component.util.text.UniqueNameGenerator;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.impl.psi.LightElement;
import consulo.language.impl.psi.PsiWhiteSpaceImpl;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import org.jetbrains.annotations.Contract;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.*;

public class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance(GenerateMembersUtil.class);

  private GenerateMembersUtil() {
  }

  @Nonnull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(PsiFile file,
                                                                         int offset,
                                                                         @Nonnull List<T> memberPrototypes) throws IncorrectOperationException {
    if (memberPrototypes.isEmpty()) {
      return memberPrototypes;
    }
    final PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) {
      return Collections.emptyList();
    }

    PsiClass aClass = findClassAtOffset(file, leaf);
    if (aClass == null) {
      return Collections.emptyList();
    }
    PsiElement anchor = memberPrototypes.get(0).findInsertionAnchor(aClass, leaf);

    if (anchor instanceof PsiWhiteSpace) {
      final ASTNode spaceNode = anchor.getNode();
      anchor = anchor.getNextSibling();

      assert spaceNode != null;
      if (spaceNode.getStartOffset() <= offset && spaceNode.getStartOffset() + spaceNode.getTextLength() >= offset) {
        String whiteSpace = spaceNode.getText().substring(0, offset - spaceNode.getStartOffset());
        if (!StringUtil.containsLineBreak(whiteSpace)) {
          // There is a possible case that the caret is located at the end of the line that already contains expression, say, we
          // want to override particular method while caret is located after the field.
          // Example - consider that we want to override toString() method at the class below:
          //     class Test {
          //         int i;<caret>
          //     }
          // We want to add line feed then in order to avoid situation like below:
          //     class Test {
          //         int i;@Override String toString() {
          //             super.toString();
          //         }
          //     }
          whiteSpace += "\n";
        }
        final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(file.getProject());
        final ASTNode singleNewLineWhitespace = parserFacade.createWhiteSpaceFromText(whiteSpace).getNode();
        if (singleNewLineWhitespace != null) {
          spaceNode.getTreeParent().replaceChild(spaceNode, singleNewLineWhitespace); // See http://jetbrains.net/jira/browse/IDEADEV-12837
        }
      }
    }

    // Q: shouldn't it be somewhere in PSI?
    PsiElement element = anchor;
    while (true) {
      if (element == null) {
        break;
      }
      if (element instanceof PsiField || element instanceof PsiMethod || element instanceof PsiClassInitializer) {
        break;
      }
      element = element.getNextSibling();
    }
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null && !field.equals(typeElement.getParent())) {
        field.normalizeDeclaration();
        anchor = field;
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  @Nonnull
  public static <T extends GenerationInfo> List<T> insertMembersBeforeAnchor(PsiClass aClass,
                                                                             @Nullable PsiElement anchor,
                                                                             @Nonnull List<T> memberPrototypes) throws IncorrectOperationException {
    boolean before = true;
    for (T memberPrototype : memberPrototypes) {
      memberPrototype.insert(aClass, anchor, before);
      before = false;
      anchor = memberPrototype.getPsiMember();
    }
    return memberPrototypes;
  }

  /**
   * @see GenerationInfo#positionCaret(Editor, boolean)
   */
  public static void positionCaret(@Nonnull Editor editor, @Nonnull PsiElement firstMember, boolean toEditMethodBody) {
    LOG.assertTrue(firstMember.isValid());
    Project project = firstMember.getProject();

    if (toEditMethodBody) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        PsiElement firstBodyElement = body.getFirstBodyElement();
        PsiElement l = firstBodyElement;
        while (l instanceof PsiWhiteSpace) {
          l = l.getNextSibling();
        }
        if (l == null) {
          l = body;
        }
        PsiElement lastBodyElement = body.getLastBodyElement();
        PsiElement r = lastBodyElement;
        while (r instanceof PsiWhiteSpace) {
          r = r.getPrevSibling();
        }
        if (r == null) {
          r = body;
        }

        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();

        boolean adjustLineIndent = false;

        // body is whitespace
        if (start > end &&
          firstBodyElement == lastBodyElement &&
          firstBodyElement instanceof PsiWhiteSpaceImpl) {
          CharSequence chars = ((PsiWhiteSpaceImpl)firstBodyElement).getChars();
          if (chars.length() > 1 && chars.charAt(0) == '\n' && chars.charAt(1) == '\n') {
            start = end = firstBodyElement.getTextRange().getStartOffset() + 1;
            adjustLineIndent = true;
          }
        }

        editor.getCaretModel().moveToOffset(Math.min(start, end));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (start < end) {
          //Not an empty body
          editor.getSelectionModel().setSelection(start, end);
        }
        else if (adjustLineIndent) {
          Document document = editor.getDocument();
          RangeMarker marker = document.createRangeMarker(start, start);
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          if (marker.isValid()) {
            CodeStyleManager.getInstance(project).adjustLineIndent(document, marker.getStartOffset());
          }
        }
        return;
      }
    }

    int offset;
    if (firstMember instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        offset = method.getTextRange().getStartOffset();
      }
      else {
        PsiJavaToken lBrace = body.getLBrace();
        assert lBrace != null : firstMember.getText();
        offset = lBrace.getTextRange().getEndOffset();
      }
    }
    else {
      offset = firstMember.getTextRange().getStartOffset();
    }

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  public static PsiElement insert(@Nonnull PsiClass aClass,
                                  @Nonnull PsiMember member,
                                  @Nullable PsiElement anchor,
                                  boolean before) throws IncorrectOperationException {
    if (member instanceof PsiMethod) {
      if (!aClass.isInterface()) {
        final PsiParameter[] parameters = ((PsiMethod)member).getParameterList().getParameters();
        final boolean generateFinals = CodeStyleSettingsManager.getSettings(aClass.getProject()).GENERATE_FINAL_PARAMETERS;
        for (final PsiParameter parameter : parameters) {
          final PsiModifierList modifierList = parameter.getModifierList();
          assert modifierList != null;
          modifierList.setModifierProperty(PsiModifier.FINAL, generateFinals);
        }
      }
    }

    if (anchor != null) {
      return before ? aClass.addBefore(member, anchor) : aClass.addAfter(member, anchor);
    }
    else {
      return aClass.add(member);
    }
  }

  @Nullable
  private static PsiClass findClassAtOffset(PsiFile file, PsiElement leaf) {
    PsiElement element = leaf;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        final PsiClass psiClass = (PsiClass)element;
        if (psiClass.isEnum()) {
          PsiElement lastChild = null;
          for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiJavaToken && ";".equals(child.getText())) {
              lastChild = child;
              break;
            }
            else if (child instanceof PsiJavaToken && ",".equals(child.getText()) || child instanceof PsiEnumConstant) {
              lastChild = child;
            }
          }
          if (lastChild != null) {
            int adjustedOffset = lastChild.getTextRange().getEndOffset();
            if (leaf.getTextRange().getEndOffset() <= adjustedOffset) {
              return findClassAtOffset(file, file.findElementAt(adjustedOffset));
            }
          }
        }
        return psiClass;
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiMethod substituteGenericMethod(PsiMethod method, final PsiSubstitutor substitutor) {
    return substituteGenericMethod(method, substitutor, null);
  }

  public static PsiMethod substituteGenericMethod(@Nonnull PsiMethod sourceMethod,
                                                  @Nonnull PsiSubstitutor substitutor,
                                                  @Nullable PsiElement target) {
    final Project project = sourceMethod.getProject();
    final JVMElementFactory factory = getFactory(sourceMethod.getProject(), target);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    try {
      final PsiMethod resultMethod = createMethod(factory, sourceMethod, target);
      copyModifiers(sourceMethod.getModifierList(), resultMethod.getModifierList());
      final PsiSubstitutor collisionResolvedSubstitutor =
        substituteTypeParameters(factory, target, sourceMethod.getTypeParameterList(), resultMethod.getTypeParameterList(), substitutor,
                                 sourceMethod);
      substituteReturnType(PsiManager.getInstance(project), resultMethod, sourceMethod.getReturnType(), collisionResolvedSubstitutor);
      substituteParameters(factory,
                           codeStyleManager,
                           sourceMethod.getParameterList(),
                           resultMethod.getParameterList(),
                           collisionResolvedSubstitutor,
                           target);
      copyDocComment(sourceMethod, resultMethod, factory);
      GlobalSearchScope scope = sourceMethod.getResolveScope();
      final List<PsiClassType> thrownTypes =
        ExceptionUtil.collectSubstituted(collisionResolvedSubstitutor, sourceMethod.getThrowsList().getReferencedTypes(), scope);
      if (target instanceof PsiClass) {
        final PsiMethod[] methods = ((PsiClass)target).findMethodsBySignature(sourceMethod, true);
        for (PsiMethod psiMethod : methods) {
          if (psiMethod != null && psiMethod != sourceMethod) {
            PsiClass aSuper = psiMethod.getContainingClass();
            if (aSuper != null && aSuper != target) {
              PsiSubstitutor superClassSubstitutor =
                TypeConversionUtil.getSuperClassSubstitutor(aSuper, (PsiClass)target, PsiSubstitutor.EMPTY);
              ExceptionUtil.retainExceptions(thrownTypes,
                                             ExceptionUtil.collectSubstituted(superClassSubstitutor,
                                                                              psiMethod.getThrowsList().getReferencedTypes(),
                                                                              scope));
            }
          }
        }
      }
      substituteThrows(factory, resultMethod.getThrowsList(), collisionResolvedSubstitutor, sourceMethod, thrownTypes);
      return resultMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return sourceMethod;
    }
  }

  private static void copyModifiers(@Nonnull PsiModifierList sourceModifierList, @Nonnull PsiModifierList targetModifierList) {
    VisibilityUtil.setVisibility(targetModifierList, VisibilityUtil.getVisibilityModifier(sourceModifierList));
  }

  @Nonnull
  private static PsiSubstitutor substituteTypeParameters(@Nonnull JVMElementFactory factory,
                                                         @Nullable PsiElement target,
                                                         @Nullable PsiTypeParameterList sourceTypeParameterList,
                                                         @Nullable PsiTypeParameterList targetTypeParameterList,
                                                         @Nonnull PsiSubstitutor substitutor,
                                                         @Nonnull PsiMethod sourceMethod) {
    if (sourceTypeParameterList == null || targetTypeParameterList == null || PsiUtil.isRawSubstitutor(sourceMethod, substitutor)) {
      return substitutor;
    }

    final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<>(substitutor.getSubstitutionMap());
    for (PsiTypeParameter typeParam : sourceTypeParameterList.getTypeParameters()) {
      final PsiTypeParameter substitutedTypeParam = substituteTypeParameter(factory, typeParam, substitutor, sourceMethod);

      final PsiTypeParameter resolvedTypeParam = resolveTypeParametersCollision(factory, sourceTypeParameterList, target,
                                                                                substitutedTypeParam, substitutor);
      targetTypeParameterList.add(resolvedTypeParam);
      if (substitutedTypeParam != resolvedTypeParam) {
        substitutionMap.put(typeParam, factory.createType(resolvedTypeParam));
      }
    }
    return substitutionMap.isEmpty() ? substitutor : factory.createSubstitutor(substitutionMap);
  }

  @Nonnull
  @RequiredReadAction
  private static PsiTypeParameter resolveTypeParametersCollision(@Nonnull JVMElementFactory factory,
                                                                 @Nonnull PsiTypeParameterList sourceTypeParameterList,
                                                                 @Nullable PsiElement target,
                                                                 @Nonnull PsiTypeParameter typeParam,
                                                                 @Nonnull PsiSubstitutor substitutor) {
    String typeParamName = typeParam.getName();
    for (PsiType type : substitutor.getSubstitutionMap().values()) {
      if (type != null && Objects.equals(type.getCanonicalText(), typeParamName)) {
        final String newName = suggestUniqueTypeParameterName(typeParamName,
                                                              sourceTypeParameterList,
                                                              PsiTreeUtil.getParentOfType(target, PsiClass.class, false));
        final PsiTypeParameter newTypeParameter = factory.createTypeParameter(newName, typeParam.getSuperTypes());
        substitutor.put(typeParam, factory.createType(newTypeParameter));
        return newTypeParameter;
      }
    }
    return factory.createTypeParameter(typeParamName, typeParam.getSuperTypes());
  }

  @Nonnull
  private static String suggestUniqueTypeParameterName(String baseName,
                                                       @Nonnull PsiTypeParameterList typeParameterList,
                                                       @Nullable PsiClass targetClass) {
    int i = 0;
    while (true) {
      final String newName = baseName + ++i;
      if (checkUniqueTypeParameterName(newName, typeParameterList) && (targetClass == null || checkUniqueTypeParameterName(newName,
                                                                                                                           targetClass.getTypeParameterList()))) {
        return newName;
      }
    }
  }


  private static boolean checkUniqueTypeParameterName(@Nonnull String baseName, @Nullable PsiTypeParameterList typeParameterList) {
    if (typeParameterList == null) {
      return true;
    }

    for (PsiTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
      if (Comparing.equal(typeParameter.getName(), baseName)) {
        return false;
      }
    }
    return true;
  }


  @Nonnull
  private static PsiTypeParameter substituteTypeParameter(final @Nonnull JVMElementFactory factory,
                                                          @Nonnull PsiTypeParameter typeParameter,
                                                          final @Nonnull PsiSubstitutor substitutor,
                                                          @Nonnull final PsiMethod sourceMethod) {
    if (typeParameter instanceof LightElement) {
      List<PsiClassType> substitutedSupers =
        ContainerUtil.map(typeParameter.getSuperTypes(), t -> ObjectUtil.notNull(toClassType(substitutor.substitute(t)), t));
      return factory.createTypeParameter(Objects.requireNonNull(typeParameter.getName()),
                                         substitutedSupers.toArray(PsiClassType.EMPTY_ARRAY));
    }
    final PsiElement copy =
      ObjectUtil.notNull(typeParameter instanceof PsiCompiledElement ? ((PsiCompiledElement)typeParameter).getMirror() : typeParameter,
                         typeParameter).copy();
    LOG.assertTrue(copy != null, typeParameter);
    final Map<PsiElement, PsiElement> replacementMap = new HashMap<>();
    copy.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiTypeParameter) {
          final PsiType type = factory.createType((PsiTypeParameter)resolve);
          replacementMap.put(reference,
                             factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, type, sourceMethod, null)));
        }
      }
    });
    return (PsiTypeParameter)RefactoringUtil.replaceElementsWithMap(copy, replacementMap);
  }

  private static PsiClassType toClassType(PsiType type) {
    if (type instanceof PsiClassType) {
      return (PsiClassType)type;
    }
    if (type instanceof PsiCapturedWildcardType) {
      return toClassType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    if (type instanceof PsiWildcardType) {
      return toClassType(((PsiWildcardType)type).getBound());
    }
    return null;
  }
  
  private static void substituteParameters(@Nonnull JVMElementFactory factory,
                                           @Nonnull JavaCodeStyleManager codeStyleManager,
                                           @Nonnull PsiParameterList sourceParameterList,
                                           @Nonnull PsiParameterList targetParameterList,
                                           @Nonnull PsiSubstitutor substitutor,
                                           PsiElement target) {
    final PsiParameter[] parameters = sourceParameterList.getParameters();
    final PsiParameter[] newParameters = overriddenParameters(parameters, factory, codeStyleManager, substitutor, target);
    for (int i = 0; i < newParameters.length; i++) {
      final PsiParameter newParameter = newParameters[i];
      copyOrReplaceModifierList(parameters[i], newParameter);
      targetParameterList.add(newParameter);
    }
  }

  public static PsiParameter[] overriddenParameters(PsiParameter[] parameters,
                                                    @Nonnull JVMElementFactory factory,
                                                    @Nonnull JavaCodeStyleManager codeStyleManager,
                                                    @Nonnull PsiSubstitutor substitutor,
                                                    PsiElement target) {
    PsiParameter[] result = new PsiParameter[parameters.length];
    UniqueNameGenerator generator = new UniqueNameGenerator();

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      PsiElement declarationScope = parameter.getDeclarationScope();
      PsiType substituted = declarationScope instanceof PsiTypeParameterListOwner ? substituteType(substitutor,
                                                                                                   parameterType,
                                                                                                   (PsiTypeParameterListOwner)declarationScope,
                                                                                                   parameter.getModifierList())
        : parameterType;
      String paramName = parameter.getName();
      boolean isBaseNameGenerated = true;
      final boolean isSubstituted = substituted.equals(parameterType);
      if (!isSubstituted && isBaseNameGenerated(codeStyleManager, TypeConversionUtil.erasure(parameterType), paramName)) {
        isBaseNameGenerated = false;
      }

      if (paramName == null ||
        isBaseNameGenerated && !isSubstituted && isBaseNameGenerated(codeStyleManager, parameterType, paramName) ||
        !factory.isValidParameterName(paramName)) {
        String[] names = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, substituted).names;
        if (names.length > 0) {
          paramName = generator.generateUniqueName(names[0]);
        }
        else {
          paramName = generator.generateUniqueName("p");
        }
      }
      else if (!generator.test(paramName)) {
        paramName = generator.generateUniqueName(paramName);
      }
      generator.addExistingName(paramName);
      result[i] = factory.createParameter(paramName, substituted, target);
    }
    return result;
  }

  private static void substituteThrows(@Nonnull JVMElementFactory factory,
                                       @Nonnull PsiReferenceList targetThrowsList,
                                       @Nonnull PsiSubstitutor substitutor,
                                       @Nonnull PsiMethod sourceMethod,
                                       List<PsiClassType> thrownTypes) {
    for (PsiClassType thrownType : thrownTypes) {
      targetThrowsList.add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType, sourceMethod, null)));
    }
  }

  @RequiredReadAction
  private static void copyDocComment(PsiMethod source, PsiMethod target, JVMElementFactory factory) {
    final PsiElement navigationElement = source.getNavigationElement();
    if (navigationElement instanceof PsiDocCommentOwner) {
      final PsiDocComment docComment = ((PsiDocCommentOwner)navigationElement).getDocComment();
      if (docComment != null) {
        target.addAfter(factory.createDocCommentFromText(docComment.getText()), null);
      }
    }
    final PsiParameter[] sourceParameters = source.getParameterList().getParameters();
    final PsiParameterList targetParameterList = target.getParameterList();
    RefactoringUtil.fixJavadocsForParams(target, new HashSet<>(Arrays.asList(targetParameterList.getParameters())), new Condition<>() {
      @Override
      public boolean value(Pair<PsiParameter, String> pair) {
        final int parameterIndex = targetParameterList.getParameterIndex(pair.first);
        if (parameterIndex >= 0 && parameterIndex < sourceParameters.length) {
          return Comparing.strEqual(pair.second, sourceParameters[parameterIndex].getName());
        }
        return false;
      }
    });
  }

  @Nonnull
  private static PsiMethod createMethod(@Nonnull JVMElementFactory factory, @Nonnull PsiMethod method, PsiElement target) {
    if (method.isConstructor()) {
      return factory.createConstructor(method.getName(), target);
    }
    return factory.createMethod(method.getName(), PsiType.VOID, target);
  }

  private static void substituteReturnType(@Nonnull PsiManager manager,
                                           @Nonnull PsiMethod method,
                                           @Nullable PsiType returnType,
                                           @Nonnull PsiSubstitutor substitutor) {
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement == null || returnType == null) {
      return;
    }
    final PsiType substitutedReturnType = substituteType(substitutor, returnType, method, method.getModifierList());

    returnTypeElement.replace(new LightTypeElement(manager,
                                                   substitutedReturnType instanceof PsiWildcardType ? TypeConversionUtil.erasure(
                                                     substitutedReturnType) : substitutedReturnType));
  }

  @Nonnull
  private static JVMElementFactory getFactory(@Nonnull Project p, @Nullable PsiElement target) {
    return target == null ? JavaPsiFacade.getInstance(p).getElementFactory() : JVMElementFactories.requireFactory(target.getLanguage(), p);
  }

  private static boolean isBaseNameGenerated(JavaCodeStyleManager csManager, PsiType parameterType, String paramName) {
    if (Arrays.asList(csManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameterType).names).contains(paramName)) {
      return true;
    }
    final String typeName = JavaCodeStyleManagerImpl.getTypeName(parameterType);
    return typeName != null && NameUtil.getSuggestionsByName(typeName, "", "", false, false, parameterType instanceof PsiArrayType)
                                       .contains(paramName);
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor,
                                        final PsiType type,
                                        @Nonnull PsiTypeParameterListOwner owner,
                                        PsiModifierList modifierList) {
    PsiType substitutedType = PsiUtil.isRawSubstitutor(owner, substitutor)
      ? TypeConversionUtil.erasure(type)
      : GenericsUtil.eliminateWildcards(substitutor.substitute(type), false, true);
    return substitutedType != null ? AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(modifierList, substitutedType) : null;
  }

  public static boolean isChildInRange(PsiElement child, PsiElement first, PsiElement last) {
    if (child.equals(first)) {
      return true;
    }
    while (true) {
      if (child.equals(first)) {
        return false; // before first
      }
      if (child.equals(last)) {
        return true;
      }
      child = child.getNextSibling();
      if (child == null) {
        return false;
      }
    }
  }

  public static void setupGeneratedMethod(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    PsiClass base = containingClass == null ? null : containingClass.getSuperClass();
    PsiMethod overridden = base == null ? null : base.findMethodBySignature(method, true);

    boolean emptyTemplate = true;
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiJavaToken lBrace = body.getLBrace();
      int left = lBrace != null ? lBrace.getStartOffsetInParent() + 1 : 0;
      PsiJavaToken rBrace = body.getRBrace();
      int right = rBrace != null ? rBrace.getStartOffsetInParent() : body.getTextLength();
      emptyTemplate = StringUtil.isEmptyOrSpaces(body.getText().substring(left, right));
    }

    if (overridden == null) {
      if (emptyTemplate) {
        CreateFromUsageUtils.setupMethodBody(method, containingClass);
      }
      return;
    }

    if (emptyTemplate) {
      OverrideImplementUtil.setupMethodBody(method, overridden, containingClass);
    }
    OverrideImplementUtil.annotateOnOverrideImplement(method, base, overridden);
  }

  public static void copyOrReplaceModifierList(@Nonnull PsiModifierListOwner sourceParam, @Nonnull PsiModifierListOwner targetParam) {
    PsiModifierList sourceModifierList = sourceParam.getModifierList();
    PsiModifierList targetModifierList = targetParam.getModifierList();

    if (sourceModifierList != null && targetModifierList != null) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(targetModifierList);
      final GlobalSearchScope moduleScope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : null;
      final Project project = targetModifierList.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      JVMElementFactory factory = JVMElementFactories.requireFactory(targetParam.getLanguage(), targetParam.getProject());
      for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(sourceParam, false, null, false)) {
        final String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null && (moduleScope == null || facade.findClass(qualifiedName, moduleScope) != null) &&
          !AnnotationTargetUtil.isTypeAnnotation(annotation)) {
          targetModifierList.add(factory.createAnnotationFromText(annotation.getText(), sourceParam));
        }
      }
      for (@PsiModifier.ModifierConstant String m : PsiModifier.MODIFIERS) {
        targetModifierList.setModifierProperty(m, sourceParam.hasModifierProperty(m));
      }

      filterAnnotations(sourceModifierList.getProject(), targetModifierList, targetModifierList.getResolveScope());
    }
  }

  private static void filterAnnotations(Project project, PsiModifierList modifierList, GlobalSearchScope moduleScope) {
    Set<String> toRemove = new HashSet<>();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName != null) {
        for (OverrideImplementsAnnotationsHandler handler : OverrideImplementsAnnotationsHandler.EP_NAME.getExtensionList()) {
          String[] annotations2Remove = handler.annotationsToRemove(project, qualifiedName);
          Collections.addAll(toRemove, annotations2Remove);
          if (moduleScope != null && psiFacade.findClass(qualifiedName, moduleScope) == null) {
            toRemove.add(qualifiedName);
          }
        }
      }
    }
    for (String fqn : toRemove) {
      PsiAnnotation psiAnnotation = modifierList.findAnnotation(fqn);
      if (psiAnnotation != null) {
        psiAnnotation.delete();
      }
    }
  }

  //java bean getters/setters
  public static PsiMethod generateSimpleGetterPrototype(@Nonnull PsiField field) {
    return generatePrototype(field, PropertyUtil.generateGetterPrototype(field));
  }

  public static PsiMethod generateSimpleSetterPrototype(@Nonnull PsiField field) {
    return generatePrototype(field, PropertyUtil.generateSetterPrototype(field));
  }

  public static PsiMethod generateSimpleSetterPrototype(PsiField field, PsiClass targetClass) {
    return generatePrototype(field, PropertyUtil.generateSetterPrototype(field, targetClass));
  }

  //custom getters/setters
  public static String suggestGetterName(PsiField field) {
    final PsiMethod prototype = generateGetterPrototype(field);
    return prototype != null ? prototype.getName() : PropertyUtil.suggestGetterName(field);
  }

  public static String suggestGetterName(String name, PsiType type, Project project) {
    return suggestGetterName(JavaPsiFacade.getElementFactory(project)
                                          .createField(name,
                                                       type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type));
  }

  public static String suggestSetterName(PsiField field) {
    final PsiMethod prototype = generateSetterPrototype(field);
    return prototype != null ? prototype.getName() : PropertyUtil.suggestSetterName(field);
  }

  public static String suggestSetterName(String name, PsiType type, Project project) {
    return suggestSetterName(JavaPsiFacade.getElementFactory(project)
                                          .createField(name,
                                                       type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type));
  }

  public static PsiMethod generateGetterPrototype(@Nonnull PsiField field) {
    return generateGetterPrototype(field, true);
  }

  public static PsiMethod generateSetterPrototype(@Nonnull PsiField field) {
    return generateSetterPrototype(field, true);
  }

  public static PsiMethod generateSetterPrototype(@Nonnull PsiField field, PsiClass aClass) {
    return generatePrototype(field, aClass, true, SetterTemplatesManager.getInstance());
  }

  static PsiMethod generateGetterPrototype(@Nonnull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, GetterTemplatesManager.getInstance());
  }

  static PsiMethod generateSetterPrototype(@Nonnull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, SetterTemplatesManager.getInstance());
  }

  private static PsiMethod generatePrototype(@Nonnull PsiField field,
                                             PsiClass psiClass,
                                             boolean ignoreInvalidTemplate,
                                             TemplatesManager templatesManager) {
    Project project = field.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String template = templatesManager.getDefaultTemplate().getTemplate();
    String methodText =
      GenerationUtil.velocityGenerateCode(psiClass, Collections.singletonList(field), new HashMap<>(), template, 0, false);

    boolean isGetter = templatesManager instanceof GetterTemplatesManager;
    PsiMethod result;
    try {
      result = factory.createMethodFromText(methodText, psiClass);
    }
    catch (IncorrectOperationException e) {
      if (ignoreInvalidTemplate) {
        LOG.info(e);
        result = isGetter ? PropertyUtil.generateGetterPrototype(field) : PropertyUtil.generateSetterPrototype(field);
        assert result != null : field.getText();
      }
      else {
        throw new GenerateCodeException(e);
      }
    }
    result = (PsiMethod)CodeStyleManager.getInstance(project).reformat(result);

    PsiModifierListOwner annotationTarget;
    if (isGetter) {
      annotationTarget = result;
    }
    else {
      final PsiParameter[] parameters = result.getParameterList().getParameters();
      annotationTarget = parameters.length == 1 ? parameters[0] : null;
    }
    if (annotationTarget != null) {
      NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, annotationTarget);
    }

    return generatePrototype(field, result);
  }

  @Nullable
  private static PsiMethod generatePrototype(@Nonnull PsiField field, PsiMethod result) {
    return setVisibility(field, annotateOnOverrideImplement(field.getContainingClass(), result));
  }

  @Contract("_, null -> null")
  public static PsiMethod setVisibility(PsiMember member, PsiMethod prototype) {
    if (prototype == null) {
      return null;
    }

    String visibility = CodeStyleSettingsManager.getSettings(member.getProject()).VISIBILITY;

    @PsiModifier.ModifierConstant String newVisibility;
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      PsiClass aClass = member instanceof PsiClass ? (PsiClass)member : member.getContainingClass();
      newVisibility = PsiUtil.getMaximumModifierForMember(aClass, false);
    }
    else {
      //noinspection MagicConstant
      newVisibility = visibility;
    }
    VisibilityUtil.setVisibility(prototype.getModifierList(), newVisibility);

    return prototype;
  }

  @Nullable
  public static PsiMethod annotateOnOverrideImplement(@Nullable PsiClass targetClass, @Nullable PsiMethod generated) {
    if (generated == null || targetClass == null) {
      return generated;
    }

    if (CodeStyleSettingsManager.getSettings(targetClass.getProject()).INSERT_OVERRIDE_ANNOTATION) {
      PsiMethod superMethod = targetClass.findMethodBySignature(generated, true);
      if (superMethod != null && superMethod.getContainingClass() != targetClass) {
        OverrideImplementUtil.annotateOnOverrideImplement(generated, targetClass, superMethod, true);
      }
    }
    return generated;
  }
}
