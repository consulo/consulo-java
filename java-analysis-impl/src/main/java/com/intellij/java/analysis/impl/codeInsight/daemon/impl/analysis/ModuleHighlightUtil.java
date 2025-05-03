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
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.impl.light.AutomaticJavaModule;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.PropertyKey;

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

    public static HighlightInfo checkPackageStatement(
        @Nonnull PsiPackageStatement statement,
        @Nonnull PsiFile file,
        @Nullable PsiJavaModule module
    ) {
        if (PsiUtil.isModuleFile(file)) {
            String message = JavaErrorBundle.message("module.no.package");
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(message)
                .create();
            QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
            return info;
        }

        if (module != null) {
            String packageName = statement.getPackageName();
            if (packageName != null) {
                PsiJavaModule origin = JavaModuleGraphUtil.findOrigin(module, packageName);
                if (origin != null) {
                    String message = JavaErrorBundle.message("module.conflicting.packages", packageName, origin.getName());
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(statement)
                        .descriptionAndTooltip(message)
                        .create();
                }
            }
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkFileName(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        if (!MODULE_INFO_FILE.equals(file.getName())) {
            String message = JavaErrorBundle.message("module.file.wrong.name");
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(range(element))
                .descriptionAndTooltip(message)
                .create();
            QuickFixAction.registerQuickFixAction(info, factory().createRenameFileFix(MODULE_INFO_FILE));
            return info;
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkFileDuplicates(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        Module module = findModule(file);
        if (module != null) {
            Project project = file.getProject();
            Collection<VirtualFile> others =
                FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, GlobalSearchScope.moduleScope(module));
            if (others.size() > 1) {
                String message = JavaErrorBundle.message("module.file.duplicate");
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(element))
                    .descriptionAndTooltip(message)
                    .create();
                others.stream()
                    .map(f -> PsiManager.getInstance(project).findFile(f))
                    .filter(f -> f != file)
                    .findFirst()
                    .ifPresent(duplicate -> QuickFixAction.registerQuickFixAction(
                        info,
                        new GoToSymbolFix(duplicate, JavaErrorBundle.message("module.open.duplicate.text"))
                    ));
                return info;
            }
        }

        return null;
    }

    @Nonnull
    public static List<HighlightInfo> checkDuplicateStatements(@Nonnull PsiJavaModule module) {
        List<HighlightInfo> results = new ArrayList<>();

        checkDuplicateRefs(
            module.getRequires(),
            st -> Optional.ofNullable(st.getReferenceElement()).map(PsiJavaModuleReferenceElement::getReferenceText),
            "module.duplicate.requires",
            results
        );

        checkDuplicateRefs(
            module.getExports(),
            st -> Optional.ofNullable(st.getPackageReference()).map(ModuleHighlightUtil::refText),
            "module.duplicate.exports",
            results
        );

        checkDuplicateRefs(
            module.getOpens(),
            st -> Optional.ofNullable(st.getPackageReference()).map(ModuleHighlightUtil::refText),
            "module.duplicate.opens",
            results
        );

        checkDuplicateRefs(
            module.getUses(),
            st -> Optional.ofNullable(st.getClassReference()).map(ModuleHighlightUtil::refText),
            "module.duplicate.uses",
            results
        );

        checkDuplicateRefs(
            module.getProvides(),
            st -> Optional.ofNullable(st.getInterfaceReference()).map(ModuleHighlightUtil::refText),
            "module.duplicate.provides",
            results
        );

        return results;
    }

    private static <T extends PsiElement> void checkDuplicateRefs(
        Iterable<T> statements,
        Function<T, Optional<String>> ref,
        @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key,
        List<HighlightInfo> results
    ) {
        Set<String> filter = new HashSet<>();
        for (T statement : statements) {
            String refText = ref.apply(statement).orElse(null);
            if (refText != null && !filter.add(refText)) {
                String message = JavaErrorBundle.message(key, refText);
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(statement)
                    .descriptionAndTooltip(message)
                    .create();
                QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
                QuickFixAction.registerQuickFixAction(info, MergeModuleStatementsFix.createFix(statement));
                results.add(info);
            }
        }
    }

    @Nonnull
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
            if (ref != null) {
                PsiElement target = ref.resolve();
                if (target instanceof PsiClass && findModule(target) == host) {
                    String className = refText(ref), packageName = StringUtil.getPackageName(className);
                    if (!exports.contains(packageName) && !uses.contains(className)) {
                        String message = JavaErrorBundle.message("module.service.unused");
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                            .range(range(ref))
                            .descriptionAndTooltip(message)
                            .create());
                    }
                }
            }
        }

        return results;
    }

    private static String refText(PsiJavaCodeReferenceElement ref) {
        return ref != null ? PsiNameHelper.getQualifiedClassName(ref.getText(), true) : null;
    }

    @Nullable
    public static HighlightInfo checkFileLocation(@Nonnull PsiJavaModule element, @Nonnull PsiFile file) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
            VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
            if (root != null && !root.equals(vFile.getParent())) {
                String message = JavaErrorBundle.message("module.file.wrong.location");
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(range(element))
                    .descriptionAndTooltip(message)
                    .create();
                QuickFixAction.registerQuickFixAction(
                    info,
                    new MoveFileFix(vFile, root, JavaQuickFixBundle.message("move.file.to.source.root.text"))
                );
                return info;
            }
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkModuleReference(@Nullable PsiJavaModuleReferenceElement refElement, @Nonnull PsiJavaModule container) {
        if (refElement != null) {
            PsiPolyVariantReference ref = refElement.getReference();
            assert ref != null : refElement.getParent();
            PsiElement target = ref.resolve();
            if (!(target instanceof PsiJavaModule)) {
                return moduleResolveError(refElement, ref);
            }
            else if (target == container) {
                String message = JavaErrorBundle.message("module.cyclic.dependence", container.getName());
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .create();
            }
            else {
                Collection<PsiJavaModule> cycle = JavaModuleGraphUtil.findCycle((PsiJavaModule)target);
                if (cycle != null && cycle.contains(container)) {
                    Stream<String> stream = cycle.stream().map(PsiJavaModule::getName);
                    if (ApplicationManager.getApplication().isUnitTestMode()) {
                        stream = stream.sorted();
                    }
                    String message = JavaErrorBundle.message("module.cyclic.dependence", stream.collect(Collectors.joining(", ")));
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(refElement)
                        .descriptionAndTooltip(message)
                        .create();
                }
            }
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkHostModuleStrength(@Nonnull PsiPackageAccessibilityStatement statement) {
        PsiElement parent;
        if (statement.getRole() == Role.OPENS && (parent =
            statement.getParent()) instanceof PsiJavaModule && ((PsiJavaModule)parent).hasModifierProperty(PsiModifier.OPEN)) {
            String message = JavaErrorBundle.message("module.opens.in.weak.module");
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(statement)
                .descriptionAndTooltip(message)
                .create();
            QuickFixAction.registerQuickFixAction(
                info,
                factory().createModifierListFix((PsiModifierListOwner)parent, PsiModifier.OPEN, false, false)
            );
            QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
            return info;
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkPackageReference(@Nonnull PsiPackageAccessibilityStatement statement) {
        PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
        if (refElement != null) {
            PsiElement target = refElement.resolve();
            Module module = findModule(refElement);
            PsiDirectory[] directories = target instanceof PsiPackage && module != null
                ? ((PsiPackage)target).getDirectories(GlobalSearchScope.moduleScope(module, false))
                : null;
            String packageName = refText(refElement);
            HighlightInfoType type = statement.getRole() == Role.OPENS ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
            if (directories == null || directories.length == 0) {
                String message = JavaErrorBundle.message("package.not.found", packageName);
                return HighlightInfo.newHighlightInfo(type)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .create();
            }
            if (PsiUtil.isPackageEmpty(directories, packageName)) {
                String message = JavaErrorBundle.message("package.is.empty", packageName);
                return HighlightInfo.newHighlightInfo(type)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .create();
            }
        }

        return null;
    }

    @Nonnull
    public static List<HighlightInfo> checkPackageAccessTargets(@Nonnull PsiPackageAccessibilityStatement statement) {
        List<HighlightInfo> results = new ArrayList<>();

        Set<String> targets = new HashSet<>();
        for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
            String refText = refElement.getReferenceText();
            PsiPolyVariantReference ref = refElement.getReference();
            assert ref != null : statement;
            if (!targets.add(refText)) {
                boolean exports = statement.getRole() == Role.EXPORTS;
                String message =
                    JavaErrorBundle.message(exports ? "module.duplicate.exports.target" : "module.duplicate.opens.target", refText);
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .create();
                QuickFixAction.registerQuickFixAction(
                    info,
                    factory().createDeleteFix(refElement, JavaQuickFixBundle.message("delete.reference.fix.text"))
                );
                results.add(info);
            }
            else if (ref.multiResolve(true).length == 0) {
                String message = JavaErrorBundle.message("module.not.found", refElement.getReferenceText());
                results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(refElement)
                    .descriptionAndTooltip(message)
                    .create());
            }
        }

        return results;
    }

    @Nullable
    public static HighlightInfo checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
        if (refElement != null) {
            PsiElement target = refElement.resolve();
            if (target == null) {
                String message = JavaErrorBundle.message("cannot.resolve.symbol", refElement.getReferenceName());
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(refElement))
                    .descriptionAndTooltip(message)
                    .create();
            }
            else if (target instanceof PsiClass && ((PsiClass)target).isEnum()) {
                String message = JavaErrorBundle.message("module.service.enum", ((PsiClass)target).getName());
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(range(refElement))
                    .descriptionAndTooltip(message)
                    .create();
            }
        }

        return null;
    }

    @Nonnull
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
                String message = JavaErrorBundle.message("module.duplicate.impl", refText);
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(implRef)
                    .descriptionAndTooltip(message)
                    .create();
                QuickFixAction.registerQuickFixAction(
                    info,
                    factory().createDeleteFix(implRef, JavaQuickFixBundle.message("delete.reference.fix.text"))
                );
                results.add(info);
                continue;
            }

            if (!(intTarget instanceof PsiClass)) {
                continue;
            }

            PsiElement implTarget = implRef.resolve();
            if (implTarget instanceof PsiClass) {
                PsiClass implClass = (PsiClass)implTarget;

                if (findModule(statement) != findModule(implClass)) {
                    String message = JavaErrorBundle.message("module.service.alien");
                    results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(range(implRef))
                        .descriptionAndTooltip(message)
                        .create());
                }

                PsiMethod provider = ContainerUtil.find(
                    implClass.findMethodsByName("provider", false),
                    m -> m.hasModifierProperty(PsiModifier.PUBLIC)
                        && m.hasModifierProperty(PsiModifier.STATIC)
                        && m.getParameterList().getParametersCount() == 0
                );
                if (provider != null) {
                    PsiType type = provider.getReturnType();
                    PsiClass typeClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
                    if (!InheritanceUtil.isInheritorOrSelf(typeClass, (PsiClass)intTarget, true)) {
                        String message = JavaErrorBundle.message("module.service.provider.type", implClass.getName());
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(message)
                            .create());
                    }
                }
                else if (InheritanceUtil.isInheritorOrSelf(implClass, (PsiClass)intTarget, true)) {
                    if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        String message = JavaErrorBundle.message("module.service.abstract", implClass.getName());
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(message)
                            .create());
                    }
                    else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.hasModifierProperty(PsiModifier.STATIC))) {
                        String message = JavaErrorBundle.message("module.service.inner", implClass.getName());
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(message)
                            .create());
                    }
                    else if (!PsiUtil.hasDefaultConstructor(implClass)) {
                        String message = JavaErrorBundle.message("module.service.no.ctor", implClass.getName());
                        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range(implRef))
                            .descriptionAndTooltip(message)
                            .create());
                    }
                }
                else {
                    String message = JavaErrorBundle.message("module.service.impl");
                    results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(range(implRef))
                        .descriptionAndTooltip(message)
                        .create());
                }
            }
        }

        return results;
    }

    @Nullable
    public static HighlightInfo checkPackageAccessibility(
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nonnull PsiElement target,
        @Nonnull PsiJavaModule refModule
    ) {
        if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) == null) {
            Module module = findModule(refModule);
            if (module != null) {
                if (target instanceof PsiClass) {
                    PsiElement targetFile = target.getParent();
                    if (targetFile instanceof PsiClassOwner) {
                        PsiJavaModule targetModule = getModuleDescriptor((PsiFileSystemItem)targetFile);
                        String packageName = ((PsiClassOwner)targetFile).getPackageName();
                        return checkPackageAccessibility(ref, refModule, targetModule, packageName);
                    }
                }
                else if (target instanceof PsiPackage) {
                    PsiElement refImport = ref.getParent();
                    if (refImport instanceof PsiImportStatementBase && ((PsiImportStatementBase)refImport).isOnDemand()) {
                        PsiDirectory[] dirs =
                            ((PsiPackage)target).getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false));
                        if (dirs.length == 1) {
                            PsiJavaModule targetModule = getModuleDescriptor(dirs[0]);
                            String packageName = ((PsiPackage)target).getQualifiedName();
                            return checkPackageAccessibility(ref, refModule, targetModule, packageName);
                        }
                    }
                }
            }
        }

        return null;
    }

    private static HighlightInfo checkPackageAccessibility(
        PsiJavaCodeReferenceElement ref,
        PsiJavaModule refModule,
        PsiJavaModule targetModule,
        String packageName
    ) {
        if (!refModule.equals(targetModule)) {
            if (targetModule == null) {
                String message = JavaErrorBundle.message("module.package.on.classpath");
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).descriptionAndTooltip(message).create();
            }

            String refModuleName = refModule.getName();
            String requiredName = targetModule.getName();
            if (!(targetModule instanceof AutomaticJavaModule || JavaModuleGraphUtil.exports(targetModule, packageName, refModule))) {
                String message = JavaErrorBundle.message("module.package.not.exported", requiredName, packageName, refModuleName);
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).descriptionAndTooltip(message).create();
            }

            if (!(PsiJavaModule.JAVA_BASE.equals(requiredName) || JavaModuleGraphUtil.reads(refModule, targetModule))) {
                String message = JavaErrorBundle.message("module.not.in.requirements", refModuleName, requiredName);
                HighlightInfo info =
                    HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).descriptionAndTooltip(message).create();
                QuickFixAction.registerQuickFixAction(info, new AddRequiredModuleFix(refModule, requiredName));
                return info;
            }
        }

        return null;
    }

    @Nullable
    public static HighlightInfo checkClashingReads(@Nonnull PsiJavaModule module) {
        Trinity<String, PsiJavaModule, PsiJavaModule> conflict = JavaModuleGraphUtil.findConflict(module);
        if (conflict != null) {
            String message = JavaErrorBundle.message(
                "module.conflicting.reads",
                module.getName(),
                conflict.first,
                conflict.second.getName(),
                conflict.third.getName()
            );
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(module)).descriptionAndTooltip(message).create();
        }

        return null;
    }

    private static Module findModule(PsiElement element) {
        return Optional.ofNullable(element.getContainingFile())
            .map(PsiFile::getVirtualFile)
            .map(f -> ModuleUtilCore.findModuleForFile(f, element.getProject()))
            .orElse(null);
    }

    private static HighlightInfo moduleResolveError(PsiJavaModuleReferenceElement refElement, PsiPolyVariantReference ref) {
        if (ref.multiResolve(true).length == 0) {
            String message = JavaErrorBundle.message("module.not.found", refElement.getReferenceText());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refElement)
                .descriptionAndTooltip(message)
                .create();
        }
        else if (ref.multiResolve(false).length > 1) {
            String message = JavaErrorBundle.message("module.ambiguous", refElement.getReferenceText());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                .range(refElement)
                .descriptionAndTooltip(message)
                .create();
        }
        else {
            String message = JavaErrorBundle.message("module.not.on.path", refElement.getReferenceText());
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(refElement)
                .descriptionAndTooltip(message)
                .create();
            factory().registerOrderEntryFixes(ref);
            return info;
        }
    }

    private static QuickFixFactory factory() {
        return QuickFixFactory.getInstance();
    }

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