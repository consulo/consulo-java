/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.AddRequiredModuleFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.GoToSymbolFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.MergeModuleStatementsFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.MoveFileFix;
import com.intellij.java.language.impl.psi.impl.light.AutomaticJavaModule;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.java.language.psi.PsiJavaModule.MODULE_INFO_FILE;

public class ModuleHighlightUtil {
    @Nullable
    public static PsiJavaModule getModuleDescriptor(@Nonnull PsiFileSystemItem fsItem) {
        VirtualFile file = fsItem.getVirtualFile();
        if (file == null) {
            return null;
        }

        return JavaPsiFacade.getInstance(fsItem.getProject()).findModule(file);
    }

    @RequiredReadAction
    public static HighlightInfo checkPackageStatement(
        @Nonnull PsiPackageStatement statement,
        @Nonnull PsiFile file,
        @Nullable PsiJavaModule module
    ) {
        if (PsiUtil.isModuleFile(file)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(JavaErrorLocalize.moduleNoPackage())
                .registerFix(factory().createDeleteFix(statement))
                .create();
        }

        if (module != null) {
            String packageName = statement.getPackageName();
            if (packageName != null) {
                PsiJavaModule origin = JavaModuleGraphUtil.findOrigin(module, packageName);
                if (origin != null) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(statement)
                        .descriptionAndTooltip(JavaErrorLocalize.moduleConflictingPackages(packageName, origin.getName()))
                        .create();
                }
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFileName(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        if (!MODULE_INFO_FILE.equals(file.getName())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(range(element))
                .descriptionAndTooltip(JavaErrorLocalize.moduleFileWrongName())
                .registerFix(factory().createRenameFileFix(MODULE_INFO_FILE))
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFileDuplicates(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        Module module = findModule(file);
        if (module != null) {
            Project project = file.getProject();
            Collection<VirtualFile> others =
                FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, GlobalSearchScope.moduleScope(module));
            if (others.size() > 1) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(element))
                    .descriptionAndTooltip(JavaErrorLocalize.moduleFileDuplicate().get())
                    .create();
                others.stream()
                    .map(f -> PsiManager.getInstance(project).findFile(f))
                    .filter(f -> f != file)
                    .findFirst()
                    .ifPresent(duplicate -> QuickFixAction.registerQuickFixAction(
                        info,
                        new GoToSymbolFix(duplicate, JavaErrorLocalize.moduleOpenDuplicateText().get())
                    ));
                return info;
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkDuplicateStatements(@Nonnull PsiJavaModule module) {
        List<HighlightInfo> results = new ArrayList<>();

        checkDuplicateRefs(
            module.getRequires(),
            st -> Optional.ofNullable(st.getReferenceElement()).map(PsiJavaModuleReferenceElement::getReferenceText),
            JavaErrorLocalize::moduleDuplicateRequires,
            results
        );

        checkDuplicateRefs(
            module.getExports(),
            st -> Optional.ofNullable(st.getPackageReference()).map(ModuleHighlightUtil::refText),
            JavaErrorLocalize::moduleDuplicateExports,
            results
        );

        checkDuplicateRefs(
            module.getOpens(),
            st -> Optional.ofNullable(st.getPackageReference()).map(ModuleHighlightUtil::refText),
            JavaErrorLocalize::moduleDuplicateOpens,
            results
        );

        checkDuplicateRefs(
            module.getUses(),
            st -> Optional.ofNullable(st.getClassReference()).map(ModuleHighlightUtil::refText),
            JavaErrorLocalize::moduleDuplicateUses,
            results
        );

        checkDuplicateRefs(
            module.getProvides(),
            st -> Optional.ofNullable(st.getInterfaceReference()).map(ModuleHighlightUtil::refText),
            JavaErrorLocalize::moduleDuplicateProvides,
            results
        );

        return results;
    }

    @RequiredReadAction
    private static <T extends PsiElement> void checkDuplicateRefs(
        Iterable<T> statements,
        @RequiredReadAction Function<T, Optional<String>> ref,
        @Nonnull Function<Object, LocalizeValue> descriptionTemplate,
        List<HighlightInfo> results
    ) {
        Set<String> filter = new HashSet<>();
        for (T statement : statements) {
            String refText = ref.apply(statement).orElse(null);
            if (refText != null && !filter.add(refText)) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(descriptionTemplate.apply(refText))
                    .registerFix(factory().createDeleteFix(statement))
                    .registerFix(MergeModuleStatementsFix.createFix(statement))
                    .create();
                results.add(info);
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkUnusedServices(@Nonnull PsiJavaModule module) {
        List<HighlightInfo> results = new ArrayList<>();

        Set<String> exports = JBIterable.from(module.getExports())
            .map(st -> refText(st.getPackageReference()))
            .filter(Objects::nonNull)
            .toSet();
        Set<String> uses = JBIterable.from(module.getUses())
            .map(st -> refText(st.getClassReference()))
            .filter(Objects::nonNull)
            .toSet();

        Module host = findModule(module);
        for (PsiProvidesStatement statement : module.getProvides()) {
            PsiJavaCodeReferenceElement ref = statement.getInterfaceReference();
            if (ref != null && ref.resolve() instanceof PsiClass psiClass && findModule(psiClass) == host) {
                String className = refText(ref), packageName = StringUtil.getPackageName(className);
                if (!exports.contains(packageName) && !uses.contains(className)) {
                    results.add(
                        HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                            .range(range(ref))
                            .descriptionAndTooltip(JavaErrorLocalize.moduleServiceUnused())
                            .create()
                    );
                }
            }
        }

        return results;
    }

    @RequiredReadAction
    private static String refText(PsiJavaCodeReferenceElement ref) {
        return ref != null ? PsiNameHelper.getQualifiedClassName(ref.getText(), true) : null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFileLocation(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
            VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
            if (root != null && !root.equals(vFile.getParent())) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(range(element))
                    .descriptionAndTooltip(JavaErrorLocalize.moduleFileWrongLocation())
                    .registerFix(new MoveFileFix(vFile, root, JavaQuickFixLocalize.moveFileToSourceRootText().get()))
                    .create();
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkModuleReference(@Nullable PsiJavaModuleReferenceElement refElement, @Nonnull PsiJavaModule container) {
        if (refElement != null) {
            PsiPolyVariantReference ref = refElement.getReference();
            assert ref != null : refElement.getParent();
            PsiElement target = ref.resolve();
            if (!(target instanceof PsiJavaModule)) {
                return moduleResolveError(refElement, ref);
            }
            else if (target == container) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(refElement)
                    .descriptionAndTooltip(JavaErrorLocalize.moduleCyclicDependence(container.getName()))
                    .create();
            }
            else {
                Collection<PsiJavaModule> cycle = JavaModuleGraphUtil.findCycle((PsiJavaModule)target);
                if (cycle != null && cycle.contains(container)) {
                    Stream<String> stream = cycle.stream().map(PsiJavaModule::getName);
                    if (Application.get().isUnitTestMode()) {
                        stream = stream.sorted();
                    }
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(refElement)
                        .descriptionAndTooltip(JavaErrorLocalize.moduleCyclicDependence(stream.collect(Collectors.joining(", "))))
                        .create();
                }
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkHostModuleStrength(@Nonnull PsiPackageAccessibilityStatement statement) {
        if (statement.getRole() == Role.OPENS && statement.getParent() instanceof PsiJavaModule javaModule
            && javaModule.hasModifierProperty(PsiModifier.OPEN)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(JavaErrorLocalize.moduleOpensInWeakModule())
                .registerFix(factory().createModifierFixBuilder(javaModule).remove(PsiModifier.OPEN).create())
                .registerFix(factory().createDeleteFix(statement))
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkPackageReference(@Nonnull PsiPackageAccessibilityStatement statement) {
        PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
        if (refElement != null) {
            PsiElement target = refElement.resolve();
            Module module = findModule(refElement);
            PsiDirectory[] directories = target instanceof PsiPackage psiPackage && module != null
                ? psiPackage.getDirectories(GlobalSearchScope.moduleScope(module, false))
                : null;
            String packageName = refText(refElement);
            HighlightInfoType type = statement.getRole() == Role.OPENS ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
            if (directories == null || directories.length == 0) {
                return HighlightInfo.newHighlightInfo(type)
                    .range(refElement)
                    .descriptionAndTooltip(JavaErrorLocalize.packageNotFound(packageName))
                    .create();
            }
            if (PsiUtil.isPackageEmpty(directories, packageName)) {
                return HighlightInfo.newHighlightInfo(type)
                    .range(refElement)
                    .descriptionAndTooltip(JavaErrorLocalize.packageIsEmpty(packageName))
                    .create();
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkPackageAccessTargets(@Nonnull PsiPackageAccessibilityStatement statement) {
        List<HighlightInfo> results = new ArrayList<>();

        Set<String> targets = new HashSet<>();
        for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
            String refText = refElement.getReferenceText();
            PsiPolyVariantReference ref = refElement.getReference();
            assert ref != null : statement;
            if (!targets.add(refText)) {
                boolean exports = statement.getRole() == Role.EXPORTS;
                LocalizeValue message = exports
                    ? JavaErrorLocalize.moduleDuplicateExportsTarget(refText)
                    : JavaErrorLocalize.moduleDuplicateOpensTarget(refText);
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .registerFix(factory().createDeleteFix(refElement, JavaQuickFixLocalize.deleteReferenceFixText()))
                    .create();
                results.add(info);
            }
            else if (ref.multiResolve(true).length == 0) {
                results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(refElement)
                    .descriptionAndTooltip(JavaErrorLocalize.moduleNotFound(refElement.getReferenceText()))
                    .create());
            }
        }

        return results;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
        if (refElement != null) {
            PsiElement target = refElement.resolve();
            if (target == null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(refElement))
                    .descriptionAndTooltip(JavaErrorLocalize.cannotResolveSymbol(refElement.getReferenceName()))
                    .create();
            }
            else if (target instanceof PsiClass psiClass && psiClass.isEnum()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(refElement))
                    .descriptionAndTooltip(JavaErrorLocalize.moduleServiceEnum(psiClass.getName()))
                    .create();
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkServiceImplementations(@Nonnull PsiProvidesStatement statement) {
        PsiReferenceList implRefList = statement.getImplementationList();
        if (implRefList == null) {
            return Collections.emptyList();
        }

        List<HighlightInfo> results = new ArrayList<>();
        PsiJavaCodeReferenceElement intRef = statement.getInterfaceReference();
        PsiElement intTarget = intRef != null ? intRef.resolve() : null;

        Set<String> filter = new HashSet<>();
        for (PsiJavaCodeReferenceElement implRef : implRefList.getReferenceElements()) {
            String refText = refText(implRef);
            if (!filter.add(refText)) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(implRef)
                    .descriptionAndTooltip(JavaErrorLocalize.moduleDuplicateImpl(refText))
                    .registerFix(factory().createDeleteFix(implRef, JavaQuickFixLocalize.deleteReferenceFixText()))
                    .create();
                results.add(info);
                continue;
            }

            if (!(intTarget instanceof PsiClass)) {
                continue;
            }

            PsiElement implTarget = implRef.resolve();
            if (implTarget instanceof PsiClass implClass) {
                if (findModule(statement) != findModule(implClass)) {
                    results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(range(implRef))
                        .descriptionAndTooltip(JavaErrorLocalize.moduleServiceAlien())
                        .create());
                }

                PsiMethod provider = ContainerUtil.find(
                    implClass.findMethodsByName("provider", false),
                    m -> m.isPublic() && m.isStatic()
                        && m.getParameterList().getParametersCount() == 0
                );
                if (provider != null) {
                    PsiType type = provider.getReturnType();
                    PsiClass typeClass = type instanceof PsiClassType classType ? classType.resolve() : null;
                    if (!InheritanceUtil.isInheritorOrSelf(typeClass, (PsiClass)intTarget, true)) {
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(JavaErrorLocalize.moduleServiceProviderType(implClass.getName()))
                            .create());
                    }
                }
                else if (InheritanceUtil.isInheritorOrSelf(implClass, (PsiClass)intTarget, true)) {
                    if (implClass.isAbstract()) {
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(JavaErrorLocalize.moduleServiceAbstract(implClass.getName()))
                            .create());
                    }
                    else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.isStatic())) {
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(JavaErrorLocalize.moduleServiceInner(implClass.getName()))
                            .create());
                    }
                    else if (!PsiUtil.hasDefaultConstructor(implClass)) {
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(JavaErrorLocalize.moduleServiceNoCtor(implClass.getName()))
                            .create());
                    }
                }
                else {
                    results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(range(implRef))
                        .descriptionAndTooltip(JavaErrorLocalize.moduleServiceImpl())
                        .create());
                }
            }
        }

        return results;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkPackageAccessibility(
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nonnull PsiElement target,
        @Nonnull PsiJavaModule refModule
    ) {
        if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) == null) {
            Module module = findModule(refModule);
            if (module != null) {
                if (target instanceof PsiClass psiClass) {
                    if (psiClass.getParent() instanceof PsiClassOwner targetFile) {
                        PsiJavaModule targetModule = getModuleDescriptor(targetFile);
                        String packageName = targetFile.getPackageName();
                        return checkPackageAccessibility(ref, refModule, targetModule, packageName);
                    }
                }
                else if (target instanceof PsiPackage psiPackage) {
                    PsiElement refImport = ref.getParent();
                    if (refImport instanceof PsiImportStatementBase importStatementBase && importStatementBase.isOnDemand()) {
                        PsiDirectory[] dirs =
                            psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false));
                        if (dirs.length == 1) {
                            PsiJavaModule targetModule = getModuleDescriptor(dirs[0]);
                            String packageName = psiPackage.getQualifiedName();
                            return checkPackageAccessibility(ref, refModule, targetModule, packageName);
                        }
                    }
                }
            }
        }

        return null;
    }

    @RequiredReadAction
    private static HighlightInfo checkPackageAccessibility(
        PsiJavaCodeReferenceElement ref,
        PsiJavaModule refModule,
        PsiJavaModule targetModule,
        String packageName
    ) {
        if (!refModule.equals(targetModule)) {
            if (targetModule == null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(ref)
                    .descriptionAndTooltip(JavaErrorLocalize.modulePackageOnClasspath())
                    .create();
            }

            String refModuleName = refModule.getName();
            String requiredName = targetModule.getName();
            if (!(targetModule instanceof AutomaticJavaModule || JavaModuleGraphUtil.exports(targetModule, packageName, refModule))) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(ref)
                    .descriptionAndTooltip(JavaErrorLocalize.modulePackageNotExported(requiredName, packageName, refModuleName))
                    .create();
            }

            if (!(PsiJavaModule.JAVA_BASE.equals(requiredName) || JavaModuleGraphUtil.reads(refModule, targetModule))) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(ref)
                    .descriptionAndTooltip(JavaErrorLocalize.moduleNotInRequirements(refModuleName, requiredName))
                    .registerFix(new AddRequiredModuleFix(refModule, requiredName))
                    .create();
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkClashingReads(@Nonnull PsiJavaModule module) {
        Trinity<String, PsiJavaModule, PsiJavaModule> conflict = JavaModuleGraphUtil.findConflict(module);
        if (conflict != null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(range(module))
                .descriptionAndTooltip(JavaErrorLocalize.moduleConflictingReads(
                    module.getName(),
                    conflict.first,
                    conflict.second.getName(),
                    conflict.third.getName()
                ))
                .create();
        }

        return null;
    }

    private static Module findModule(PsiElement element) {
        return Optional.ofNullable(element.getContainingFile())
            .map(PsiFile::getVirtualFile)
            .map(f -> ModuleUtilCore.findModuleForFile(f, element.getProject()))
            .orElse(null);
    }

    @RequiredReadAction
    private static HighlightInfo moduleResolveError(PsiJavaModuleReferenceElement refElement, PsiPolyVariantReference ref) {
        if (ref.multiResolve(true).length == 0) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refElement)
                .descriptionAndTooltip(JavaErrorLocalize.moduleNotFound(refElement.getReferenceText()))
                .create();
        }
        else if (ref.multiResolve(false).length > 1) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                .range(refElement)
                .descriptionAndTooltip(JavaErrorLocalize.moduleAmbiguous(refElement.getReferenceText()))
                .create();
        }
        else {
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refElement)
                .descriptionAndTooltip(JavaErrorLocalize.moduleNotOnPath(refElement.getReferenceText()))
                .create();
            factory().registerOrderEntryFixes(ref);
            return info;
        }
    }

    private static QuickFixFactory factory() {
        return QuickFixFactory.getInstance();
    }

    @RequiredReadAction
    private static TextRange range(PsiJavaModule module) {
        PsiKeyword kw = PsiTreeUtil.getChildOfType(module, PsiKeyword.class);
        return new TextRange(
            kw != null ? kw.getTextOffset() : module.getTextOffset(),
            module.getNameIdentifier().getTextRange().getEndOffset()
        );
    }

    private static PsiElement range(PsiJavaCodeReferenceElement refElement) {
        return ObjectUtil.notNull(refElement.getReferenceNameElement(), refElement);
    }
}