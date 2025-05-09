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
package com.intellij.java.impl.psi.impl.source.codeStyle;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.ReferenceAdjuster;
import com.intellij.java.impl.psi.statistics.JavaStatisticsManager;
import com.intellij.java.indexing.impl.search.MethodDeepestSuperSearcher;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.matcher.NameUtil;
import consulo.application.util.matcher.NameUtilCore;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.beans.Introspector;
import java.util.*;

/**
 * @author max
 */
@Singleton
@ServiceImpl
public class JavaCodeStyleManagerImpl extends JavaCodeStyleManager {
    private static final Logger LOG = Logger.getInstance(JavaCodeStyleManagerImpl.class);
    private static final String IMPL_SUFFIX = "Impl";
    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    private static final String FIND_PREFIX = "find";
    private static final String CREATE_PREFIX = "create";
    private static final String SET_PREFIX = "set";
    private static final String AS_PREFIX = "as";
    private static final String TO_PREFIX = "to";

    private static final String[] ourPrepositions = {
        "as", "at", "by", "down", "for", "from", "in", "into", "of", "on", "onto", "out", "over",
        "per", "to", "up", "upon", "via", "with"};
    private static final String[] ourCommonTypeSuffixes = {"Entity"};

    private final Project myProject;

    @Inject
    public JavaCodeStyleManagerImpl(Project project) {
        myProject = project;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement shortenClassReferences(@Nonnull PsiElement element) throws IncorrectOperationException {
        return shortenClassReferences(element, 0);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement shortenClassReferences(@Nonnull PsiElement element, int flags) throws IncorrectOperationException {
        CheckUtil.checkWritable(element);
        if (!SourceTreeToPsiMap.hasTreeElement(element)) {
            return element;
        }

        boolean addImports = (flags & DO_NOT_ADD_IMPORTS) == 0;
        boolean incompleteCode = (flags & INCOMPLETE_CODE) != 0;

        ReferenceAdjuster adjuster = ReferenceAdjuster.forLanguage(element.getLanguage());
        if (adjuster != null) {
            ASTNode reference = adjuster.process(element.getNode(), addImports, incompleteCode, myProject);
            return SourceTreeToPsiMap.treeToPsiNotNull(reference);
        }
        else {
            return element;
        }
    }

    @Override
    @RequiredReadAction
    public void shortenClassReferences(@Nonnull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
        CheckUtil.checkWritable(element);
        if (SourceTreeToPsiMap.hasTreeElement(element)) {
            ReferenceAdjuster adjuster = ReferenceAdjuster.forLanguage(element.getLanguage());
            if (adjuster != null) {
                adjuster.processRange(element.getNode(), startOffset, endOffset, myProject);
            }
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement qualifyClassReferences(@Nonnull PsiElement element) {
        ReferenceAdjuster adjuster = ReferenceAdjuster.forLanguage(element.getLanguage());
        if (adjuster != null) {
            ASTNode reference = adjuster.process(element.getNode(), false, false, true, true);
            return SourceTreeToPsiMap.treeToPsiNotNull(reference);
        }
        return element;
    }

    @Override
    @RequiredReadAction
    public void optimizeImports(@Nonnull PsiFile file) throws IncorrectOperationException {
        CheckUtil.checkWritable(file);
        if (file instanceof PsiJavaFile javaFile) {
            PsiImportList newList = prepareOptimizeImportsResult(javaFile);
            if (newList != null) {
                PsiImportList importList = javaFile.getImportList();
                if (importList != null) {
                    importList.replace(newList);
                }
            }
        }
    }

    @Override
    public boolean hasConflictingOnDemandImport(@Nonnull PsiJavaFile file, @Nonnull PsiClass psiClass, @Nonnull String referenceName) {
        return ImportHelper.hasConflictingOnDemandImport(file, psiClass, referenceName);
    }

    @Override
    public PsiImportList prepareOptimizeImportsResult(@Nonnull PsiJavaFile file) {
        return new ImportHelper(JavaCodeStyleSettings.getInstance(file)).prepareOptimizeImportsResult(file);
    }

    @Override
    public boolean addImport(@Nonnull PsiJavaFile file, @Nonnull PsiClass refClass) {
        return new ImportHelper(JavaCodeStyleSettings.getInstance(file)).addImport(file, refClass);
    }

    @Override
    @RequiredReadAction
    public void removeRedundantImports(@Nonnull PsiJavaFile file) throws IncorrectOperationException {
        Collection<PsiImportStatementBase> redundant = findRedundantImports(file);
        if (redundant == null) {
            return;
        }

        for (PsiImportStatementBase importStatement : redundant) {
            PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
            //Do not remove non-resolving refs
            if (ref == null || ref.resolve() == null) {
                continue;
            }

            importStatement.delete();
        }
    }

    @Nullable
    @Override
    @RequiredReadAction
    public Collection<PsiImportStatementBase> findRedundantImports(@Nonnull final PsiJavaFile file) {
        PsiImportList importList = file.getImportList();
        if (importList == null) {
            return null;
        }
        PsiImportStatementBase[] imports = importList.getAllImportStatements();
        if (imports.length == 0) {
            return null;
        }

        Set<PsiImportStatementBase> allImports = new HashSet<>(Arrays.asList(imports));
        final Collection<PsiImportStatementBase> redundant;
        /* if(FileTypeUtils.isInServerPageFile(file))
        {
                // remove only duplicate imports
                redundant = ContainerUtil.newIdentityTroveSet();
                ContainerUtil.addAll(redundant, imports);
                redundant.removeAll(allImports);
                for(PsiImportStatementBase importStatement : imports)
                {
                    if(importStatement instanceof JspxImportStatement && importStatement.isForeignFileImport())
                    {
                        redundant.remove(importStatement);
                    }
                }
            }
            else */
        {
            redundant = allImports;
            List<PsiFile> roots = file.getViewProvider().getAllFiles();
            for (PsiElement root : roots) {
                root.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
                        if (!reference.isQualified()) {
                            JavaResolveResult resolveResult = reference.advancedResolve(false);
                            if (!inTheSamePackage(file, resolveResult.getElement())
                                && resolveResult.getCurrentFileResolveScope() instanceof PsiImportStatementBase importStatementBase) {
                                redundant.remove(importStatementBase);
                            }
                        }
                        super.visitReferenceElement(reference);
                    }

                    private boolean inTheSamePackage(PsiJavaFile file, PsiElement element) {
                        if (element instanceof PsiClass psiClass && psiClass.getContainingClass() == null) {
                            PsiFile containingFile = element.getContainingFile();
                            if (containingFile instanceof PsiJavaFile javaFile) {
                                return Comparing.strEqual(file.getPackageName(), javaFile.getPackageName());
                            }
                        }
                        return false;
                    }
                });
            }
        }
        return redundant;
    }

    @Override
    public int findEntryIndex(@Nonnull PsiImportStatementBase statement) {
        return new ImportHelper(JavaCodeStyleSettings.getInstance(statement.getContainingFile())).findEntryIndex(statement);
    }

    @Override
    @Nullable
    public String suggestCompiledParameterName(@Nonnull PsiType type) {
        // avoid hang due to nice name evaluation that uses indices for resolve (IDEA-116803)
        Collection<String> result = doSuggestParameterNamesByTypeWithoutIndex(type);
        return ContainerUtil.getFirstItem(getSuggestionsByNames(result, VariableKind.PARAMETER, true));
    }

    @Nonnull
    private Collection<String> doSuggestParameterNamesByTypeWithoutIndex(@Nonnull PsiType type) {
        String fromTypeMap = suggestNameFromTypeMap(type, VariableKind.PARAMETER, type.getCanonicalText());
        if (fromTypeMap != null) {
            return Collections.singletonList(fromTypeMap);
        }

        return suggestNamesFromTypeName(type, VariableKind.PARAMETER, getTypeNameWithoutIndex(type.getDeepComponentType()));
    }

    @Nullable
    private static String getTypeNameWithoutIndex(@Nonnull PsiType type) {
        type = type.getDeepComponentType();
        return type instanceof PsiClassType ? ((PsiClassType)type).getClassName() :
            type instanceof PsiPrimitiveType ? type.getPresentableText() :
                null;
    }

    @Nonnull
    private static List<String> suggestNamesFromTypeName(
        @Nonnull PsiType type,
        @Nonnull VariableKind variableKind,
        @Nullable String typeName
    ) {
        if (typeName == null) {
            return Collections.emptyList();
        }

        typeName = normalizeTypeName(typeName);
        String result = type instanceof PsiArrayType ? StringUtil.pluralize(typeName) : typeName;
        if (variableKind == VariableKind.PARAMETER && type instanceof PsiClassType && typeName.endsWith("Exception")) {
            return Arrays.asList("e", result);
        }
        for (String suffix : ourCommonTypeSuffixes) {
            if (result.length() > suffix.length() && result.endsWith(suffix)) {
                return Arrays.asList(result, result.substring(0, result.length() - suffix.length()));
            }
        }
        return Collections.singletonList(result);
    }

    @Nonnull
    private Collection<String> getSuggestionsByNames(@Nonnull Iterable<String> names, @Nonnull VariableKind kind, boolean correctKeywords) {
        Collection<String> suggestions = new LinkedHashSet<>();
        for (String name : names) {
            suggestions.addAll(getSuggestionsByName(name, kind, correctKeywords));
        }
        return suggestions;
    }

    @Nonnull
    private Collection<String> getSuggestionsByName(@Nonnull String name, @Nonnull VariableKind variableKind, boolean correctKeywords) {
        if (!StringUtil.isJavaIdentifier(name)) {
            return List.of();
        }
        boolean upperCaseStyle = variableKind == VariableKind.STATIC_FINAL_FIELD;
        boolean preferLongerNames = getJavaSettings().PREFER_LONGER_NAMES;
        String prefix = getPrefixByVariableKind(variableKind);
        String suffix = getSuffixByVariableKind(variableKind);

        List<String> answer = new ArrayList<>();
        for (String suggestion : NameUtil.getSuggestionsByName(name, prefix, suffix, upperCaseStyle, preferLongerNames, false)) {
            answer.add(correctKeywords ? changeIfNotIdentifier(suggestion) : suggestion);
        }

        String wordByPreposition = getWordByPreposition(name, prefix, suffix, upperCaseStyle);
        if (wordByPreposition != null && (!correctKeywords || isIdentifier(wordByPreposition))) {
            answer.add(wordByPreposition);
        }
        if (name.equals("hashCode")) {
            answer.add("hash");
        }
        return answer;
    }

    private static String getWordByPreposition(@Nonnull String name, String prefix, String suffix, boolean upperCaseStyle) {
        String[] words = NameUtil.splitNameIntoWords(name);
        for (int i = 1; i < words.length; i++) {
            for (String preposition : ourPrepositions) {
                if (preposition.equalsIgnoreCase(words[i])) {
                    String mainWord = words[i - 1];
                    if (upperCaseStyle) {
                        mainWord = StringUtil.toUpperCase(mainWord);
                    }
                    else {
                        if (prefix.isEmpty() || StringUtil.endsWithChar(prefix, '_')) {
                            mainWord = StringUtil.toLowerCase(mainWord);
                        }
                        else {
                            mainWord = StringUtil.capitalize(mainWord);
                        }
                    }
                    return prefix + mainWord + suffix;
                }
            }
        }
        return null;
    }

    @Nullable
    private String suggestNameFromTypeMap(@Nonnull PsiType type, @Nonnull VariableKind variableKind, @Nullable String longTypeName) {
        if (longTypeName != null) {
            if (type.equals(PsiTypes.nullType())) {
                longTypeName = CommonClassNames.JAVA_LANG_OBJECT;
            }
            String name = nameByType(longTypeName, variableKind);
            if (name != null && isIdentifier(name)) {
                return type instanceof PsiArrayType ? StringUtil.pluralize(name) : name;
            }
        }
        return null;
    }

    @Nullable
    private static String nameByType(@Nonnull String longTypeName, @Nonnull VariableKind kind) {
        if (kind == VariableKind.PARAMETER) {
            return switch (longTypeName) {
                case "int", "boolean", "byte", "char", "long" -> longTypeName.substring(0, 1);
                case "double", "float" -> "v";
                case "short" -> "i";
                case CommonClassNames.JAVA_LANG_OBJECT -> "o";
                case CommonClassNames.JAVA_LANG_STRING -> "s";
                case CommonClassNames.JAVA_LANG_VOID -> "unused";
                default -> null;
            };
        }
        if (kind == VariableKind.LOCAL_VARIABLE) {
            return switch (longTypeName) {
                case "int", "boolean", "byte", "char", "long" -> longTypeName.substring(0, 1);
                case "double", "float", CommonClassNames.JAVA_LANG_DOUBLE, CommonClassNames.JAVA_LANG_FLOAT -> "v";
                case "short", CommonClassNames.JAVA_LANG_SHORT, CommonClassNames.JAVA_LANG_INTEGER -> "i";
                case CommonClassNames.JAVA_LANG_LONG -> "l";
                case CommonClassNames.JAVA_LANG_BOOLEAN, CommonClassNames.JAVA_LANG_BYTE -> "b";
                case CommonClassNames.JAVA_LANG_CHARACTER -> "c";
                case CommonClassNames.JAVA_LANG_OBJECT -> "o";
                case CommonClassNames.JAVA_LANG_STRING -> "s";
                case CommonClassNames.JAVA_LANG_VOID -> "unused";
                default -> null;
            };
        }
        return null;
    }


    @Nonnull
    @Override
    @RequiredReadAction
    public SuggestedNameInfo suggestVariableName(
        @Nonnull final VariableKind kind,
        @Nullable String propertyName,
        @Nullable PsiExpression expr,
        @Nullable PsiType type,
        boolean correctKeywords
    ) {
        if (expr != null && type == null) {
            type = expr.getType();
        }

        Set<String> names = new LinkedHashSet<>();
        if (propertyName != null) {
            String[] namesByName = ArrayUtil.toStringArray(getSuggestionsByName(propertyName, kind, correctKeywords));
            sortVariableNameSuggestions(namesByName, kind, propertyName, null);
            ContainerUtil.addAll(names, namesByName);
        }

        NamesByExprInfo namesByExpr;
        if (expr != null) {
            namesByExpr = suggestVariableNameByExpression(expr, kind);
            String[] suggestions = ArrayUtil.toStringArray(getSuggestionsByNames(namesByExpr.names, kind, correctKeywords));
            if (namesByExpr.propertyName != null) {
                sortVariableNameSuggestions(suggestions, kind, namesByExpr.propertyName, null);
            }
            ContainerUtil.addAll(names, suggestions);
        }
        else {
            namesByExpr = null;
        }

        if (type != null) {
            String[] namesByType = suggestVariableNameByType(type, kind, correctKeywords);
            sortVariableNameSuggestions(namesByType, kind, null, type);
            ContainerUtil.addAll(names, namesByType);
        }

        final String _propertyName;
        if (propertyName != null) {
            _propertyName = propertyName;
        }
        else {
            _propertyName = namesByExpr != null ? namesByExpr.propertyName : null;
        }

        filterOutBadNames(names);
        addNamesFromStatistics(names, kind, _propertyName, type);

        String[] namesArray = ArrayUtil.toStringArray(names);
        sortVariableNameSuggestions(namesArray, kind, _propertyName, type);

        final String _type = type == null ? null : type.getCanonicalText();
        return new SuggestedNameInfo(namesArray) {
            @Override
            public void nameChosen(String name) {
                if (_propertyName != null || _type != null) {
                    JavaStatisticsManager.incVariableNameUseCount(name, kind, _propertyName, _type);
                }
            }
        };
    }

    private static void filterOutBadNames(Set<String> names) {
        names.remove("of");
        names.remove("to");
    }

    private static void addNamesFromStatistics(
        @Nonnull Set<String> names,
        @Nonnull VariableKind variableKind,
        @Nullable String propertyName,
        @Nullable PsiType type
    ) {
        String[] allNames = JavaStatisticsManager.getAllVariableNamesUsed(variableKind, propertyName, type);

        int maxFrequency = 0;
        for (String name : allNames) {
            int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
            maxFrequency = Math.max(maxFrequency, count);
        }

        int frequencyLimit = Math.max(5, maxFrequency / 2);

        for (String name : allNames) {
            if (names.contains(name)) {
                continue;
            }
            int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
            if (LOG.isDebugEnabled()) {
                LOG.debug("new name:" + name + " count:" + count);
                LOG.debug("frequencyLimit:" + frequencyLimit);
            }
            if (count >= frequencyLimit) {
                names.add(name);
            }
        }

        if (propertyName != null && type != null) {
            addNamesFromStatistics(names, variableKind, propertyName, null);
            addNamesFromStatistics(names, variableKind, null, type);
        }
    }

    @Nonnull
    private String[] suggestVariableNameByType(@Nonnull PsiType type, @Nonnull VariableKind variableKind, boolean correctKeywords) {
        Collection<String> byTypeNames = doSuggestNamesByType(type, variableKind);
        return ArrayUtil.toStringArray(getSuggestionsByNames(byTypeNames, variableKind, correctKeywords));
    }

    private static void suggestNamesFromGenericParameters(@Nonnull PsiClassType type, @Nonnull Collection<? super String> suggestions) {
        PsiType[] parameters = type.getParameters();
        if (parameters.length == 0) {
            return;
        }

        StringBuilder fullNameBuilder = new StringBuilder();
        for (PsiType parameter : parameters) {
            if (parameter instanceof PsiClassType) {
                String typeName = normalizeTypeName(getTypeName(parameter));
                if (typeName != null) {
                    fullNameBuilder.append(typeName);
                }
            }
        }
        String baseName = normalizeTypeName(getTypeName(type));
        if (baseName != null) {
            fullNameBuilder.append(baseName);
            suggestions.add(fullNameBuilder.toString());
        }
    }

    private static void suggestNamesForCollectionInheritors(@Nonnull PsiClassType type, @Nonnull Collection<? super String> suggestions) {
        PsiType componentType = PsiUtil.extractIterableTypeParameter(type, false);
        if (componentType == null || componentType.equals(type)) {
            return;
        }
        String typeName = normalizeTypeName(getTypeName(componentType));
        if (typeName != null) {
            suggestions.add(StringUtil.pluralize(typeName));
        }
    }

    private static String normalizeTypeName(@Nullable String typeName) {
        if (typeName == null) {
            return null;
        }
        if (typeName.endsWith(IMPL_SUFFIX) && typeName.length() > IMPL_SUFFIX.length()) {
            return typeName.substring(0, typeName.length() - IMPL_SUFFIX.length());
        }
        return typeName;
    }

    @Nullable
    public static String getTypeName(@Nonnull PsiType type) {
        return getTypeName(type, true);
    }

    @Nullable
    private static String getTypeName(@Nonnull PsiType type, boolean withIndices) {
        type = type.getDeepComponentType();
        if (type instanceof PsiClassType classType) {
            String className = classType.getClassName();
            if (className != null || !withIndices) {
                return className;
            }
            PsiClass aClass = classType.resolve();
            return aClass instanceof PsiAnonymousClass anonymousClass ? anonymousClass.getBaseClassType().getClassName() : null;
        }
        else if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        else if (type instanceof PsiWildcardType wildcardType) {
            return getTypeName(wildcardType.getExtendsBound(), withIndices);
        }
        else if (type instanceof PsiIntersectionType intersectionType) {
            return getTypeName(intersectionType.getRepresentative(), withIndices);
        }
        else if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
            return getTypeName(capturedWildcardType.getWildcard(), withIndices);
        }
        else if (type instanceof PsiDisjunctionType disjunctionType) {
            return getTypeName(disjunctionType.getLeastUpperBound(), withIndices);
        }
        else {
            return null;
        }
    }

    @Nullable
    private static String getLongTypeName(@Nonnull PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass == null) {
                return null;
            }
            else if (aClass instanceof PsiAnonymousClass) {
                PsiClass baseClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
                return baseClass != null ? baseClass.getQualifiedName() : null;
            }
            else {
                return aClass.getQualifiedName();
            }
        }
        else if (type instanceof PsiArrayType) {
            return getLongTypeName(((PsiArrayType)type).getComponentType()) + "[]";
        }
        else if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        else if (type instanceof PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
        }
        else if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
            PsiType bound = capturedWildcardType.getWildcard().getBound();
            return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
        }
        else if (type instanceof PsiIntersectionType intersectionType) {
            return getLongTypeName(intersectionType.getRepresentative());
        }
        else if (type instanceof PsiDisjunctionType disjunctionType) {
            return getLongTypeName(disjunctionType.getLeastUpperBound());
        }
        else {
            return null;
        }
    }

    private static final class NamesByExprInfo {
        static final NamesByExprInfo EMPTY = new NamesByExprInfo(null, Collections.emptyList());

        private final String propertyName;
        private final Collection<String> names;

        private NamesByExprInfo(@Nullable String propertyName, @Nonnull Collection<String> names) {
            this.propertyName = propertyName;
            this.names = names;
        }

        private NamesByExprInfo(@Nonnull String propertyName) {
            this(propertyName, Collections.singletonList(propertyName));
        }

        private NamesByExprInfo(@Nullable String propertyName, @Nonnull String... names) {
            this(
                propertyName,
                propertyName == null ? Arrays.asList(names) : ContainerUtil.prepend(Arrays.asList(names), propertyName)
            );
        }
    }

    @Nonnull
    @RequiredReadAction
    private NamesByExprInfo suggestVariableNameByExpression(@Nonnull PsiExpression expr, @Nullable VariableKind variableKind) {
        List<String> fromLiteral = ExpressionUtils.nonStructuralChildren(expr)
            .map(e -> e instanceof PsiLiteralExpression literal && literal.getValue() instanceof String str ? str : null)
            .filter(Objects::nonNull)
            .flatMap(literal -> LiteralNameSuggester.literalNames(literal).stream())
            .distinct()
            .toList();
        LinkedHashSet<String> names = new LinkedHashSet<>(fromLiteral);
        ContainerUtil.addIfNotNull(names, suggestVariableNameFromConstant(expr, variableKind));
        ContainerUtil.addIfNotNull(names, suggestVariableNameFromLiterals(expr));

        NamesByExprInfo byExpr = suggestVariableNameByExpressionOnly(expr, variableKind, false);
        NamesByExprInfo byExprPlace = suggestVariableNameByExpressionPlace(expr, variableKind);
        NamesByExprInfo byExprAllMethods = suggestVariableNameByExpressionOnly(expr, variableKind, true);

        names.addAll(byExpr.names);
        names.addAll(byExprPlace.names);

        PsiType type = expr.getType();
        if (type != null && variableKind != null) {
            names.addAll(doSuggestNamesByType(type, variableKind));
        }
        names.addAll(byExprAllMethods.names);

        String propertyName = byExpr.propertyName != null ? byExpr.propertyName : byExprPlace.propertyName;
        return new NamesByExprInfo(propertyName, names);
    }

    @Nullable
    @RequiredReadAction
    private static String suggestVariableNameFromConstant(@Nonnull PsiExpression expr, @Nullable VariableKind kind) {
        if (kind == null || kind == VariableKind.LOCAL_VARIABLE) {
            return null;
        }
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown(expr);
        if (expression instanceof PsiReferenceExpression referenceExpression &&
            referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
            return normalizeTypeName(getTypeName(enumConstant.getType()));
        }
        return null;
    }

    @Nonnull
    private Collection<String> doSuggestNamesByType(@Nonnull PsiType type, @Nonnull VariableKind variableKind) {
        String fromTypeMap = suggestNameFromTypeMap(type, variableKind, getLongTypeName(type));
        if (fromTypeMap != null && type instanceof PsiPrimitiveType) {
            return Collections.singletonList(fromTypeMap);
        }
        Collection<String> suggestions = new LinkedHashSet<>();
        if (fromTypeMap != null) {
            suggestions.add(fromTypeMap);
        }

        List<String> fromTypeName = suggestNamesFromTypeName(type, variableKind, getTypeName(type));
        if (!(type instanceof PsiClassType classType)) {
            suggestions.addAll(fromTypeName);
            return suggestions;
        }

        suggestNamesForCollectionInheritors(classType, suggestions);
        suggestFromOptionalContent(variableKind, classType, suggestions);
        suggestNamesFromGenericParameters(classType, suggestions);
        suggestions.addAll(fromTypeName);
        suggestNamesFromHierarchy(classType, suggestions);
        return suggestions;
    }

    private static void suggestNamesFromHierarchy(@Nonnull PsiClassType type, @Nonnull Collection<? super String> suggestions) {
        PsiClass resolved = type.resolve();
        if (resolved == null || resolved.getContainingClass() == null) {
            return;
        }

        InheritanceUtil.processSupers(
            resolved,
            false,
            superClass -> {
                if (PsiTreeUtil.isAncestor(superClass, resolved, true)) {
                    suggestions.add(superClass.getName());
                }
                return false;
            }
        );
    }

    private void suggestFromOptionalContent(
        @Nonnull VariableKind variableKind,
        @Nonnull PsiClassType classType,
        @Nonnull Collection<? super String> suggestions
    ) {
        PsiType optionalContent = extractOptionalContent(classType);
        if (optionalContent == null) {
            return;
        }

        Collection<String> contentSuggestions = doSuggestNamesByType(optionalContent, variableKind);
        suggestions.addAll(contentSuggestions);
        for (String s : contentSuggestions) {
            suggestions.add("optional" + StringUtil.capitalize(s));
        }
    }

    @Nullable
    private static PsiType extractOptionalContent(@Nonnull PsiClassType classType) {
        PsiClass resolved = classType.resolve();
        if (resolved != null && CommonClassNames.JAVA_UTIL_OPTIONAL.equals(resolved.getQualifiedName())) {
            if (classType.getParameterCount() == 1) {
                return classType.getParameters()[0];
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static String suggestVariableNameFromLiterals(@Nonnull PsiExpression expr) {
        String text = findLiteralText(expr);
        if (text == null) {
            return null;
        }
        return expr.getType() instanceof PsiArrayType ? StringUtil.pluralize(text) : text;
    }

    private static boolean isNameSupplier(String text) {
        if (!StringUtil.isQuotedString(text)) {
            return false;
        }
        String stringPresentation = StringUtil.unquoteString(text);
        String[] words = stringPresentation.split(" ");
        //noinspection SimplifiableIfStatement
        if (words.length > 5) {
            return false;
        }
        return ContainerUtil.and(words, StringUtil::isJavaIdentifier);
    }

    @Nullable
    @RequiredReadAction
    private static String findLiteralText(@Nonnull PsiExpression expr) {
        List<PsiLiteralExpression> literals = SyntaxTraverser.psiTraverser(expr)
            .filter(PsiLiteralExpression.class)
            .filter(lit -> isNameSupplier(lit.getText()))
            .filter(lit -> {
                PsiElement exprList = lit.getParent();
                if (!(exprList instanceof PsiExpressionList)) {
                    return false;
                }
                PsiElement call = exprList.getParent();
                //TODO: exclude or not getA().getB("name").getC(); or getA(getB("name").getC()); It works fine for now in the most cases
                return call instanceof PsiNewExpression || call instanceof PsiMethodCallExpression;
            })
            .toList();

        if (literals.size() == 1) {
            return StringUtil.unquoteString(literals.get(0).getText()).replaceAll(" ", "_");
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    private NamesByExprInfo suggestVariableNameByExpressionOnly(
        @Nonnull PsiExpression expr,
        @Nullable VariableKind variableKind,
        boolean useAllMethodNames
    ) {
        if (expr instanceof PsiMethodCallExpression) {
            PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
            String methodName = methodExpr.getReferenceName();
            if (methodName != null) {
                if ("of".equals(methodName) || "ofNullable".equals(methodName)) {
                    if (isJavaUtilMethodCall((PsiMethodCallExpression)expr)) {
                        PsiExpression[] expressions = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
                        if (expressions.length > 0) {
                            return suggestVariableNameByExpressionOnly(expressions[0], variableKind, useAllMethodNames);
                        }
                    }
                }
                if ("map".equals(methodName) || "flatMap".equals(methodName) || "filter".equals(methodName)) {
                    if (isJavaUtilMethodCall((PsiMethodCallExpression)expr)) {
                        return NamesByExprInfo.EMPTY;
                    }
                }

                String[] words = NameUtilCore.nameToWords(methodName);
                if (words.length > 0) {
                    String firstWord = words[0];
                    if (GET_PREFIX.equals(firstWord)
                        || IS_PREFIX.equals(firstWord)
                        || FIND_PREFIX.equals(firstWord)
                        || CREATE_PREFIX.equals(firstWord)
                        || AS_PREFIX.equals(firstWord)
                        || TO_PREFIX.equals(firstWord)) {
                        if (words.length > 1) {
                            String propertyName = methodName.substring(firstWord.length());
                            if (methodExpr.getQualifierExpression() instanceof PsiReferenceExpression qRefExpr
                                && qRefExpr.resolve() instanceof PsiVariable) {
                                String name = qRefExpr.getReferenceName() + StringUtil.capitalize(propertyName);
                                return new NamesByExprInfo(propertyName, name);
                            }
                            return new NamesByExprInfo(propertyName);
                        }
                    }
                    else if (words.length == 1 || useAllMethodNames) {
                        if (!"equals".equals(firstWord) && !"valueOf".equals(methodName)) {
                            words[0] = PastParticiple.pastParticiple(firstWord);
                            return new NamesByExprInfo(methodName, words[0], String.join("", words));
                        }
                        else {
                            return new NamesByExprInfo(methodName);
                        }
                    }
                }
            }
        }
        else if (expr instanceof PsiReferenceExpression refExpr) {
            String propertyName = getPropertyName(refExpr, true);
            if (propertyName != null) {
                return new NamesByExprInfo(propertyName);
            }
        }
        else if (expr instanceof PsiArrayAccessExpression) {
            NamesByExprInfo info =
                suggestVariableNameByExpressionOnly(((PsiArrayAccessExpression)expr).getArrayExpression(), variableKind, useAllMethodNames);

            String singular = info.propertyName == null ? null : StringUtil.unpluralize(info.propertyName);
            if (singular != null) {
                return new NamesByExprInfo(singular, ContainerUtil.mapNotNull(info.names, StringUtil::unpluralize));
            }
        }
        else if (expr instanceof PsiLiteralExpression literalExpression && variableKind == VariableKind.STATIC_FINAL_FIELD) {
            Object value = literalExpression.getValue();
            if (value instanceof String stringValue) {
                String[] names = getSuggestionsByValue(stringValue);
                if (names.length > 0) {
                    return new NamesByExprInfo(null, constantValueToConstantName(names));
                }
            }
        }
        else if (expr instanceof PsiParenthesizedExpression parenthesized) {
            PsiExpression expression = parenthesized.getExpression();
            if (expression != null) {
                return suggestVariableNameByExpressionOnly(expression, variableKind, useAllMethodNames);
            }
        }
        else if (expr instanceof PsiTypeCastExpression typeCast) {
            PsiExpression operand = typeCast.getOperand();
            if (operand != null) {
                return suggestVariableNameByExpressionOnly(operand, variableKind, useAllMethodNames);
            }
        }
        else if (expr instanceof PsiLiteralExpression) {
            String text = StringUtil.unquoteString(expr.getText());
            if (isIdentifier(text)) {
                return new NamesByExprInfo(text);
            }
        }
        else if (expr instanceof PsiFunctionalExpression functionalExpr && variableKind != null) {
            PsiType functionalInterfaceType = functionalExpr.getFunctionalInterfaceType();
            if (functionalInterfaceType != null) {
                return new NamesByExprInfo(null, doSuggestNamesByType(functionalInterfaceType, variableKind));
            }
        }

        return NamesByExprInfo.EMPTY;
    }

    @Nullable
    @RequiredReadAction
    private String getPropertyName(@Nonnull PsiReferenceExpression expression) {
        return getPropertyName(expression, false);
    }

    @Nullable
    @RequiredReadAction
    private String getPropertyName(@Nonnull PsiReferenceExpression expression, boolean skipUnresolved) {
        String propertyName = expression.getReferenceName();
        if (propertyName == null) {
            return null;
        }

        PsiElement refElement = expression.resolve();
        if (refElement instanceof PsiVariable variable) {
            VariableKind refVariableKind = getVariableKind(variable);
            return variableNameToPropertyName(propertyName, refVariableKind);
        }
        else if (refElement == null && skipUnresolved) {
            return null;
        }
        else {
            return propertyName;
        }
    }

    private static boolean isJavaUtilMethodCall(@Nonnull PsiMethodCallExpression expr) {
        PsiMethod method = expr.resolveMethod();
        //noinspection SimplifiableIfStatement
        if (method == null) {
            return false;
        }

        return isJavaUtilMethod(method)
            || !MethodDeepestSuperSearcher.processDeepestSuperMethods(method, method1 -> !isJavaUtilMethod(method1));
    }

    private static boolean isJavaUtilMethod(@Nonnull PsiMethod method) {
        String name = PsiUtil.getMemberQualifiedName(method);
        return name != null && name.startsWith("java.util.");
    }

    @Nonnull
    private static String constantValueToConstantName(@Nonnull String[] names) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                result.append("_");
            }
            result.append(names[i]);
        }
        return result.toString();
    }

    @Nonnull
    private static String[] getSuggestionsByValue(@Nonnull String stringValue) {
        List<String> result = new ArrayList<>();
        StringBuffer currentWord = new StringBuffer();

        boolean prevIsUpperCase = false;

        for (int i = 0; i < stringValue.length(); i++) {
            char c = stringValue.charAt(i);
            if (Character.isUpperCase(c)) {
                if (currentWord.length() > 0 && !prevIsUpperCase) {
                    result.add(currentWord.toString());
                    currentWord = new StringBuffer();
                }
                currentWord.append(c);
            }
            else if (Character.isLowerCase(c)) {
                currentWord.append(Character.toUpperCase(c));
            }
            else if (Character.isJavaIdentifierPart(c) && c != '_') {
                if (Character.isJavaIdentifierStart(c) || currentWord.length() > 0 || !result.isEmpty()) {
                    currentWord.append(c);
                }
            }
            else {
                if (currentWord.length() > 0) {
                    result.add(currentWord.toString());
                    currentWord = new StringBuffer();
                }
            }

            prevIsUpperCase = Character.isUpperCase(c);
        }

        if (currentWord.length() > 0) {
            result.add(currentWord.toString());
        }
        return ArrayUtil.toStringArray(result);
    }

    @Nonnull
    @RequiredReadAction
    private NamesByExprInfo suggestVariableNameByExpressionPlace(@Nonnull PsiExpression expr, @Nullable VariableKind variableKind) {
        if (expr.getParent() instanceof PsiExpressionList list) {
            PsiElement listParent = list.getParent();
            PsiSubstitutor subst = PsiSubstitutor.EMPTY;
            PsiMethod method = null;
            if (listParent instanceof PsiMethodCallExpression call) {
                JavaResolveResult resolveResult = call.getMethodExpression().advancedResolve(false);
                method = (PsiMethod)resolveResult.getElement();
                subst = resolveResult.getSubstitutor();
            }
            else {
                if (listParent instanceof PsiAnonymousClass) {
                    listParent = listParent.getParent();
                }
                if (listParent instanceof PsiNewExpression newExpression) {
                    method = newExpression.resolveConstructor();
                }
            }

            if (method != null) {
                PsiParameter parameter = MethodCallUtils.getParameterForArgument(expr);
                if (parameter != null) {
                    String name = parameter.getName();
                    if (TypeConversionUtil.areTypesAssignmentCompatible(subst.substitute(parameter.getType()), expr)) {
                        name = variableNameToPropertyName(name, VariableKind.PARAMETER);
                        if (list.getExpressionCount() == 1) {
                            String methodName = method.getName();
                            String[] words = NameUtilCore.nameToWords(methodName);
                            if (words.length > 0) {
                                String firstWord = words[0];
                                if (SET_PREFIX.equals(firstWord)) {
                                    String propertyName = methodName.substring(firstWord.length());
                                    return new NamesByExprInfo(name, propertyName);
                                }
                            }
                        }
                        return new NamesByExprInfo(name);
                    }
                }
            }
        }
        else if (expr.getParent() instanceof PsiAssignmentExpression assignmentExpression) {
            if (expr == assignmentExpression.getRExpression()) {
                PsiExpression leftExpression = assignmentExpression.getLExpression();
                if (leftExpression instanceof PsiReferenceExpression ref) {
                    String name = getPropertyName(ref);
                    if (name != null) {
                        return new NamesByExprInfo(name);
                    }
                }
            }
        }
        //skip places where name for this local variable is calculated, otherwise grab the name
        else if (expr.getParent() instanceof PsiLocalVariable variable && variableKind != VariableKind.LOCAL_VARIABLE) {
            String variableName = variable.getName();
            String propertyName = variableNameToPropertyName(variableName, getVariableKind(variable));
            return new NamesByExprInfo(propertyName);
        }

        return NamesByExprInfo.EMPTY;
    }

    @Nonnull
    @Override
    public String variableNameToPropertyName(@Nonnull String name, @Nonnull VariableKind variableKind) {
        if (variableKind == VariableKind.STATIC_FINAL_FIELD || variableKind == VariableKind.STATIC_FIELD && name.contains("_")) {
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c != '_') {
                    if (Character.isLowerCase(c)) {
                        return variableNameToPropertyNameInner(name, variableKind);
                    }

                    buffer.append(Character.toLowerCase(c));
                    continue;
                }
                //noinspection AssignmentToForLoopParameter
                i++;
                if (i < name.length()) {
                    c = name.charAt(i);
                    buffer.append(c);
                }
            }
            return buffer.toString();
        }

        return variableNameToPropertyNameInner(name, variableKind);
    }

    @Nonnull
    private String variableNameToPropertyNameInner(@Nonnull String name, @Nonnull VariableKind variableKind) {
        String prefix = getPrefixByVariableKind(variableKind);
        String suffix = getSuffixByVariableKind(variableKind);
        boolean doDecapitalize = false;

        int pLength = prefix.length();
        if (pLength > 0 && name.startsWith(prefix) && name.length() > pLength &&
            // check it's not just a long camel word that happens to begin with the specified prefix
            (!Character.isLetter(prefix.charAt(pLength - 1)) || Character.isUpperCase(name.charAt(pLength)))) {
            name = name.substring(pLength);
            doDecapitalize = true;
        }

        if (name.endsWith(suffix) && name.length() > suffix.length()) {
            name = name.substring(0, name.length() - suffix.length());
            doDecapitalize = true;
        }

        if (doDecapitalize) {
            name = Introspector.decapitalize(name);
        }

        return name;
    }

    @Nonnull
    @Override
    public String propertyNameToVariableName(@Nonnull String propertyName, @Nonnull VariableKind variableKind) {
        if (variableKind == VariableKind.STATIC_FINAL_FIELD) {
            String[] words = NameUtil.nameToWords(propertyName);
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                if (i > 0) {
                    buffer.append("_");
                }
                buffer.append(StringUtil.toUpperCase(word));
            }
            return buffer.toString();
        }

        String prefix = getPrefixByVariableKind(variableKind);
        String name = propertyName;
        if (!name.isEmpty() && !prefix.isEmpty() && !StringUtil.endsWithChar(prefix, '_')) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        name = prefix + name + getSuffixByVariableKind(variableKind);
        name = changeIfNotIdentifier(name);
        return name;
    }

    @Nonnull
    private String[] getSuggestionsByName(
        @Nonnull String name,
        @Nonnull VariableKind variableKind,
        boolean isArray,
        boolean correctKeywords
    ) {
        boolean upperCaseStyle = variableKind == VariableKind.STATIC_FINAL_FIELD;
        boolean preferLongerNames = getSettings().PREFER_LONGER_NAMES;
        String prefix = getPrefixByVariableKind(variableKind);
        String suffix = getSuffixByVariableKind(variableKind);

        List<String> answer = new ArrayList<>();
        for (String suggestion : NameUtil.getSuggestionsByName(name, prefix, suffix, upperCaseStyle, preferLongerNames, isArray)) {
            answer.add(correctKeywords ? changeIfNotIdentifier(suggestion) : suggestion);
        }

        return ArrayUtil.toStringArray(answer);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String suggestUniqueVariableName(@Nonnull String baseName, PsiElement place, boolean lookForward) {
        return suggestUniqueVariableName(baseName, place, lookForward, false);
    }

    @Nonnull
    @RequiredReadAction
    private static String suggestUniqueVariableName(
        @Nonnull String baseName,
        PsiElement place,
        boolean lookForward,
        boolean allowShadowing
    ) {
        PsiElement scope = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class, PsiCodeBlock.class, PsiMethod.class);
        for (int index = 0; ; index++) {
            String name = index > 0 ? baseName + index : baseName;
            if (hasConflictingVariable(place, name, allowShadowing) || lookForward && hasConflictingVariableAfterwards(scope, name)) {
                continue;
            }
            return name;
        }
    }

    @RequiredReadAction
    private static boolean hasConflictingVariableAfterwards(@Nullable PsiElement scope, @Nonnull final String name) {
        PsiElement run = scope;
        while (run != null) {
            class CancelException extends RuntimeException {
            }
            try {
                run.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitClass(@Nonnull PsiClass aClass) {
                    }

                    @Override
                    @RequiredReadAction
                    public void visitVariable(@Nonnull PsiVariable variable) {
                        if (name.equals(variable.getName())) {
                            throw new CancelException();
                        }
                    }
                });
            }
            catch (CancelException e) {
                return true;
            }
            run = run.getNextSibling();
            if (scope instanceof PsiMethod || scope instanceof PsiForeachStatement) {//do not check next member for param name conflict
                break;
            }
        }
        return false;
    }

    private static boolean hasConflictingVariable(@Nullable PsiElement place, @Nonnull String name, boolean allowShadowing) {
        if (place == null) {
            return false;
        }
        PsiResolveHelper helper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
        PsiVariable existingVariable = helper.resolveAccessibleReferencedVariable(name, place);
        //noinspection SimplifiableIfStatement
        if (existingVariable == null) {
            return false;
        }

        return !allowShadowing
            || !(existingVariable instanceof PsiField)
            || PsiTreeUtil.getNonStrictParentOfType(place, PsiMethod.class) == null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public SuggestedNameInfo suggestUniqueVariableName(
        @Nonnull final SuggestedNameInfo baseNameInfo,
        PsiElement place,
        boolean ignorePlaceName,
        boolean lookForward
    ) {
        String[] names = baseNameInfo.names;
        final LinkedHashSet<String> uniqueNames = new LinkedHashSet<>(names.length);
        for (String name : names) {
            if (ignorePlaceName && place instanceof PsiNamedElement namedElem) {
                String placeName = namedElem.getName();
                if (Comparing.strEqual(placeName, name)) {
                    uniqueNames.add(name);
                    continue;
                }
            }
            String unique = suggestUniqueVariableName(name, place, lookForward);
            if (!unique.equals(name)) {
                String withShadowing = suggestUniqueVariableName(name, place, lookForward, true);
                if (withShadowing.equals(name)) {
                    uniqueNames.add(name);
                }
            }
            uniqueNames.add(unique);
        }

        return new SuggestedNameInfo(ArrayUtil.toStringArray(uniqueNames)) {
            @Override
            public void nameChosen(String name) {
                baseNameInfo.nameChosen(name);
            }
        };
    }

    private static void sortVariableNameSuggestions(
        @Nonnull String[] names,
        @Nonnull VariableKind variableKind,
        @Nullable String propertyName,
        @Nullable PsiType type
    ) {
        if (names.length <= 1) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("sorting names:" + variableKind);
            if (propertyName != null) {
                LOG.debug("propertyName:" + propertyName);
            }
            if (type != null) {
                LOG.debug("type:" + type);
            }
            for (String name : names) {
                int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
                LOG.debug(name + " : " + count);
            }
        }

        Comparator<String> comparator = (s1, s2) -> {
            int count1 = JavaStatisticsManager.getVariableNameUseCount(s1, variableKind, propertyName, type);
            int count2 = JavaStatisticsManager.getVariableNameUseCount(s2, variableKind, propertyName, type);
            return count2 - count1;
        };
        Arrays.sort(names, comparator);
    }

    @Override
    @Nonnull
    public String getPrefixByVariableKind(@Nonnull VariableKind variableKind) {
        String prefix = null;
        switch (variableKind) {
            case FIELD:
                prefix = getJavaSettings().FIELD_NAME_PREFIX;
                break;
            case STATIC_FIELD:
                prefix = getJavaSettings().STATIC_FIELD_NAME_PREFIX;
                break;
            case PARAMETER:
                prefix = getJavaSettings().PARAMETER_NAME_PREFIX;
                break;
            case LOCAL_VARIABLE:
                prefix = getJavaSettings().LOCAL_VARIABLE_NAME_PREFIX;
                break;
            case STATIC_FINAL_FIELD:
                break;
            default:
                LOG.assertTrue(false);
                break;
        }
        return prefix == null ? "" : prefix;
    }

    @Override
    @Nonnull
    public String getSuffixByVariableKind(@Nonnull VariableKind variableKind) {
        String suffix = null;
        switch (variableKind) {
            case FIELD:
                suffix = getJavaSettings().FIELD_NAME_SUFFIX;
                break;
            case STATIC_FIELD:
                suffix = getJavaSettings().STATIC_FIELD_NAME_SUFFIX;
                break;
            case PARAMETER:
                suffix = getJavaSettings().PARAMETER_NAME_SUFFIX;
                break;
            case LOCAL_VARIABLE:
                suffix = getJavaSettings().LOCAL_VARIABLE_NAME_SUFFIX;
                break;
            case STATIC_FINAL_FIELD:
                break;
            default:
                LOG.assertTrue(false);
                break;
        }
        return suffix == null ? "" : suffix;
    }

    @Nonnull
    private String changeIfNotIdentifier(@Nonnull String name) {
        if (!isIdentifier(name)) {
            return StringUtil.fixVariableNameDerivedFromPropertyName(name);
        }
        return name;
    }

    private boolean isIdentifier(@Nonnull String name) {
        return PsiNameHelper.getInstance(myProject).isIdentifier(name, LanguageLevel.HIGHEST);
    }

    @Nonnull
    private JavaCodeStyleSettings getJavaSettings() {
        return CodeStyle.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class);
    }

    @Nonnull
    private CodeStyleSettings getSettings() {
        return CodeStyleSettingsManager.getSettings(myProject);
    }

    @RequiredReadAction
    private static boolean isStringPsiLiteral(@Nonnull PsiElement element) {
        if (element instanceof PsiLiteralExpression) {
            String text = element.getText();
            return StringUtil.isQuotedString(text);
        }
        return false;
    }
}
