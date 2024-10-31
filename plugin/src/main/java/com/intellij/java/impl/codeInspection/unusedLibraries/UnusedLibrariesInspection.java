// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection.unusedLibraries;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.impl.codeInspection.AbstractDependencyVisitor;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.ReadAction;
import consulo.application.util.graph.GraphAlgorithms;
import consulo.component.util.graph.Graph;
import consulo.component.util.graph.GraphGenerator;
import consulo.component.util.graph.InboundSemiGraph;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class UnusedLibrariesInspection extends GlobalInspectionTool implements OldStyleInspection {
    private static final Logger LOG = Logger.getInstance(UnusedLibrariesInspection.class);

    public boolean IGNORE_LIBRARY_PARTS = true;

    @Override
    public
    @Nonnull
    JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
            JavaAnalysisBundle.message("don.t.report.unused.jars.inside.used.library"),
            this,
            "IGNORE_LIBRARY_PARTS"
        );
    }

    @Override
    public
    @Nonnull
    RefGraphAnnotator getAnnotator(@Nonnull RefManager refManager) {
        return new UnusedLibraryGraphAnnotator(refManager);
    }

    @Override
    public boolean isReadActionNeeded() {
        return false;
    }

    @Override
    @RequiredReadAction
    public void runInspection(
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor,
        @Nonnull Object state
    ) {
        RefManager refManager = globalContext.getRefManager();
        for (Module module : ModuleManager.getInstance(globalContext.getProject()).getModules()) {
            if (scope.containsModule(module)) {
                RefModule refModule = refManager.getRefModule(module);
                if (refModule != null) {
                    CommonProblemDescriptor[] descriptors = getDescriptors(manager, refModule, module);
                    if (descriptors != null) {
                        problemDescriptionsProcessor.addProblemElement(refModule, descriptors);
                    }
                }
            }
        }
    }

    private CommonProblemDescriptor[] getDescriptors(@Nonnull InspectionManager manager, RefModule refModule, Module module) {
        VirtualFile[] givenRoots = ReadAction.compute(
            () -> OrderEnumerator.orderEntries(module).withoutSdk()
                .withoutModuleSourceEntries()
                .withoutDepModules()
                .classes()
                .getRoots()
        );

        if (givenRoots.length == 0) {
            return null;
        }

        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final Set<VirtualFile> usedRoots = refModule.getUserData(UnusedLibraryGraphAnnotator.USED_LIBRARY_ROOTS);

        if (usedRoots != null) {
            appendUsedRootDependencies(usedRoots, givenRoots);
        }

        return ReadAction.compute(() -> {
            final List<CommonProblemDescriptor> result = new ArrayList<>();
            for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
                if (entry instanceof LibraryOrderEntry libraryOrderEntry &&
                    !libraryOrderEntry.isExported() && libraryOrderEntry.getScope() != DependencyScope.RUNTIME) {
                    final Set<VirtualFile> files = ContainerUtil.set(entry.getFiles(BinariesOrderRootType.getInstance()));
                    boolean allRootsUnused = usedRoots == null || !files.removeAll(usedRoots);
                    if (allRootsUnused) {
                        String message = JavaAnalysisBundle.message("unused.library.problem.descriptor", entry.getPresentableName());
                        result.add(manager.createProblemDescriptor(
                            message,
                            module,
                            new RemoveUnusedLibrary(entry.getPresentableName(), null)
                        ));
                    }
                    else if (!files.isEmpty() && !IGNORE_LIBRARY_PARTS) {
                        final String unusedLibraryRoots = StringUtil.join(files, file -> file.getPresentableName(), ",");
                        String message = JavaAnalysisBundle.message(
                            "unused.library.roots.problem.descriptor",
                            unusedLibraryRoots,
                            entry.getPresentableName()
                        );
                        CommonProblemDescriptor descriptor = ((LibraryOrderEntry)entry).isModuleLevel()
                            ? manager.createProblemDescriptor(
                                message,
                                module,
                                new RemoveUnusedLibrary(entry.getPresentableName(), files)
                            )
                            : manager.createProblemDescriptor(message);
                        result.add(descriptor);
                    }
                }
            }

            return result.isEmpty() ? null : result.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
        });
    }

    private static void appendUsedRootDependencies(
        @Nonnull Set<VirtualFile> usedRoots,
        @Nonnull VirtualFile[] givenRoots
    ) {
        //classes per root
        Map<VirtualFile, Set<String>> fromClasses = new HashMap<>();
        //classes uses in root, ignoring self & jdk
        Map<VirtualFile, Set<String>> toClasses = new HashMap<>();
        collectClassesPerRoots(givenRoots, fromClasses, toClasses);

        Graph<VirtualFile> graph = GraphGenerator.generate(new InboundSemiGraph<>() {
            @Nonnull
            @Override
            public Collection<VirtualFile> getNodes() {
                return Arrays.asList(givenRoots);
            }

            @Nonnull
            @Override
            public Iterator<VirtualFile> getIn(VirtualFile n) {
                Set<String> classesInCurrentRoot = fromClasses.get(n);
                return toClasses.entrySet().stream()
                    .filter(entry -> ContainerUtil.intersects(entry.getValue(), classesInCurrentRoot))
                    .map(entry -> entry.getKey())
                    .collect(Collectors.toSet()).iterator();
            }
        });

        GraphAlgorithms algorithms = GraphAlgorithms.getInstance();
        Set<VirtualFile> dependencies = new HashSet<>();
        for (VirtualFile root : usedRoots) {
            algorithms.collectOutsRecursively(graph, root, dependencies);
        }
        usedRoots.addAll(dependencies);
    }

    private static void collectClassesPerRoots(
        VirtualFile[] givenRoots,
        Map<VirtualFile, Set<String>> fromClasses,
        Map<VirtualFile, Set<String>> toClasses
    ) {
        for (VirtualFile root : givenRoots) {
            Set<String> fromClassNames = new HashSet<>();
            Set<String> toClassNames = new HashSet<>();

            VfsUtilCore.iterateChildrenRecursively(root, null, fileOrDir -> {
                if (!fileOrDir.isDirectory() && fileOrDir.getName().endsWith(".class")) {
                    AbstractDependencyVisitor visitor = new AbstractDependencyVisitor() {
                        @Override
                        protected void addClassName(String name) {
                            if (!name.startsWith("java.") && !name.startsWith("javax.")) { //ignore jdk classes
                                toClassNames.add(name);
                            }
                        }
                    };
                    try {
                        visitor.processStream(fileOrDir.getInputStream());
                        fromClassNames.add(visitor.getCurrentClassName());
                    }
                    catch (IOException e) {
                        LOG.error(e);
                    }
                }
                return true;
            });
            toClassNames.removeAll(fromClassNames);

            fromClasses.put(root, fromClassNames);
            toClasses.put(root, toClassNames);
        }
    }


    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    @Nls
    @Nonnull
    public String getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy().get();
    }

    @Override
    @NonNls
    @Nonnull
    public String getShortName() {
        return "UnusedLibrary";
    }

    @Override
    public
    @Nonnull
    QuickFix<?> getQuickFix(String hint) {
        return new RemoveUnusedLibrary(hint, null);
    }

    @Nullable
    @Override
    public String getHint(@Nonnull QuickFix fix) {
        return fix instanceof RemoveUnusedLibrary removeUnusedLibrary && removeUnusedLibrary.myFiles == null
            ? removeUnusedLibrary.myLibraryName : null;
    }

    private static class RemoveUnusedLibrary implements QuickFix<ModuleProblemDescriptor> {
        private final Set<? extends VirtualFile> myFiles;
        private final String myLibraryName;

        RemoveUnusedLibrary(String libraryName, final Set<? extends VirtualFile> files) {
            myLibraryName = libraryName;
            myFiles = files;
        }

        @Override
        @Nonnull
        public String getFamilyName() {
            return myFiles == null ? JavaAnalysisBundle.message("detach.library.quickfix.name") : JavaAnalysisBundle.message(
                "detach.library.roots.quickfix.name");
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull final Project project, @Nonnull final ModuleProblemDescriptor descriptor) {
            final Module module = descriptor.getModule();

            final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            for (OrderEntry entry : model.getOrderEntries()) {
                if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), myLibraryName)) {
                    if (myFiles == null) {
                        model.removeOrderEntry(entry);
                    }
                    else {
                        final Library library = ((LibraryOrderEntry)entry).getLibrary();
                        if (library != null) {
                            final Library.ModifiableModel modifiableModel = library.getModifiableModel();
                            for (VirtualFile file : myFiles) {
                                modifiableModel.removeRoot(file.getUrl(), BinariesOrderRootType.getInstance());
                            }
                            modifiableModel.commit();
                        }
                    }
                }
            }
            model.commit();
        }
    }

    private static class UnusedLibraryGraphAnnotator extends RefGraphAnnotator {
        public static final Key<Set<VirtualFile>> USED_LIBRARY_ROOTS = Key.create("inspection.dependencies");
        private final ProjectFileIndex myFileIndex;
        private final RefManager myManager;

        UnusedLibraryGraphAnnotator(RefManager manager) {
            myManager = manager;
            myFileIndex = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();
        }

        @Override
        @RequiredReadAction
        public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
            if (what != null && from != null) {
                final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(what);
                final VirtualFile containingDir = virtualFile != null ? virtualFile.getParent() : null;
                if (containingDir != null) {
                    final VirtualFile libraryClassRoot = myFileIndex.getClassRootForFile(containingDir);
                    if (libraryClassRoot != null) {
                        final Module fromModule = ModuleUtilCore.findModuleForPsiElement(from);
                        if (fromModule != null) {
                            final RefModule refModule = myManager.getRefModule(fromModule);
                            if (refModule != null) {
                                Set<VirtualFile> usedRoots = refModule.getUserData(USED_LIBRARY_ROOTS);
                                if (usedRoots == null) {
                                    usedRoots = new HashSet<>();
                                    refModule.putUserData(USED_LIBRARY_ROOTS, usedRoots);
                                }
                                usedRoots.add(libraryClassRoot);
                            }
                        }
                    }
                }
            }
        }
    }
}
