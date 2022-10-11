// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider.Result;
import consulo.application.util.CachedValuesManager;
import consulo.component.util.graph.DFSTBuilder;
import consulo.component.util.graph.Graph;
import consulo.component.util.graph.GraphGenerator;
import consulo.content.ContentFolderTypeProvider;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.jar.JarFile;

import static consulo.util.lang.ObjectUtil.tryCast;

public final class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() {
  }

  @Nullable
  public static PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    if (element != null) {
      PsiFileSystemItem fsItem = element instanceof PsiFileSystemItem ? (PsiFileSystemItem) element : element.getContainingFile();
      if (fsItem != null) {
        return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
      }
    }

    return null;
  }

  @Nullable
  public static PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @Nonnull Project project) {
    if (file == null) {
      return null;
    }

    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file)
        ? findDescriptorInLibrary(project, index, file)
        : findDescriptorByModule(index.getModuleForFile(file), index.isInTestSourceContent(file));
  }

  private static PsiJavaModule findDescriptorInLibrary(Project project, ProjectFileIndex index, VirtualFile file) {
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile = JavaModuleNameIndex.descriptorFile(root);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile) psiFile).getModuleDeclaration();
        }
      } else if (root.getFileSystem() instanceof ArchiveFileSystem && "jar".equalsIgnoreCase(root.getExtension())) {
        return LightJavaModule.findModule(PsiManager.getInstance(project), root);
      }
    }
    return null;
  }

  @Nullable
  public static PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module != null) {
      Predicate<ContentFolderTypeProvider> rootType = inTests ? LanguageContentFolderScopes.test() : LanguageContentFolderScopes.production();
      List<VirtualFile> files = ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getContentFolderFiles(rootType),
          root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE));
      if (files.size() == 1) {
        PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(files.get(0));
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile) psiFile).getModuleDeclaration();
        }
      } else if (files.isEmpty()) {
        files = ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getContentFolderFiles(rootType),
            root -> root.findFileByRelativePath(JarFile.MANIFEST_NAME));
        if (files.size() == 1) {
          VirtualFile manifest = files.get(0);
          String name = LightJavaModule.claimedModuleName(manifest);
          if (name != null) {
            return LightJavaModule.findModule(PsiManager.getInstance(module.getProject()), manifest.getParent().getParent());
          }
        }
      }
    }

    return null;
  }

  @Nonnull
  public static Collection<PsiJavaModule> findCycle(@Nonnull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
        Result.create(findCycles(project), cacheDependency()));
    return ObjectUtil.notNull(ContainerUtil.find(cycles, set -> set.contains(module)), Collections.emptyList());
  }

  public static boolean exports(@Nonnull PsiJavaModule source, @Nonnull String packageName, @Nullable PsiJavaModule target) {
    Map<String, Set<String>> exports = LanguageCachedValueUtil.getCachedValue(source, () ->
        Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || target != null && targets.contains(target.getName()));
  }

  public static boolean reads(@Nonnull PsiJavaModule source, @Nonnull PsiJavaModule destination) {
    return getRequiresGraph(source).reads(source, destination);
  }

  @Nonnull
  public static Set<PsiJavaModule> getAllDependencies(PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source);
  }

  @Nullable
  public static Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(@Nonnull PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  public static
  @Nullable
  PsiJavaModule findOrigin(@Nonnull PsiJavaModule module, @Nonnull String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  @SuppressWarnings("deprecation")
  private static Object cacheDependency() {
    return PsiModificationTracker.MODIFICATION_COUNT;
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
  @Nonnull
  private static List<Set<PsiJavaModule>> findCycles(Project project) {
    Set<PsiJavaModule> projectModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      List<PsiJavaModule> descriptors = ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getSourceRoots(true),
          root -> findDescriptorByFile(root, project));
      if (descriptors.size() > 1) {
        return Collections.emptyList();  // aborts the process when there are incorrect modules in the project
      }
      if (descriptors.size() == 1) {
        projectModules.add(descriptors.get(0));
      }
    }

    if (!projectModules.isEmpty()) {
      MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
      for (PsiJavaModule module : projectModules) {
        for (PsiRequiresStatement statement : module.getRequires()) {
          PsiJavaModuleReference ref = statement.getModuleReference();
          if (ref != null) {
            ResolveResult[] results = ref.multiResolve(true);
            if (results.length == 1) {
              PsiJavaModule dependency = (PsiJavaModule) results[0].getElement();
              if (dependency != null && projectModules.contains(dependency)) {
                relations.putValue(module, dependency);
              }
            }
          }
        }
      }

      if (!relations.isEmpty()) {
        Graph<PsiJavaModule> graph = new ChameleonGraph<>(relations, false);
        DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(graph);
        Collection<Collection<PsiJavaModule>> components = builder.getComponents();
        if (!components.isEmpty()) {
          return ContainerUtil.map(components, elements -> new LinkedHashSet<>(elements));
        }
      }
    }

    return Collections.emptyList();
  }

  private static Map<String, Set<String>> exportsMap(@Nonnull PsiJavaModule source) {
    Map<String, Set<String>> map = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : new HashSet<>(targets));
    }
    return map;
  }

  private static RequiresGraph getRequiresGraph(PsiJavaModule module) {
    Project project = module.getProject();
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
        Result.create(buildRequiresGraph(project), cacheDependency()));
  }

  /*
   * Collects all module dependencies in the project.
   * The resulting graph is used for tracing readability and checking package conflicts.
   */
  private static RequiresGraph buildRequiresGraph(Project project) {
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> transitiveEdges = new HashSet<>();

    JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
    ProjectAwareSearchScope scope = ProjectScopes.getAllScope(project);
    for (String key : index.getAllKeys(project)) {
      for (PsiJavaModule module : index.get(key, project, scope)) {
        visit(module, relations, transitiveEdges);
      }
    }

    Graph<PsiJavaModule> graph = GraphGenerator.generate(new ChameleonGraph<>(relations, true));
    return new RequiresGraph(graph, transitiveEdges);
  }

  private static void visit(PsiJavaModule module, MultiMap<PsiJavaModule, PsiJavaModule> relations, Set<String> transitiveEdges) {
    if (!(module instanceof LightJavaModule) && !relations.containsKey(module)) {
      relations.putValues(module, Collections.emptyList());
      boolean explicitJavaBase = false;
      for (PsiRequiresStatement statement : module.getRequires()) {
        PsiJavaModuleReference ref = statement.getModuleReference();
        if (ref != null) {
          if (PsiJavaModule.JAVA_BASE.equals(ref.getCanonicalText())) {
            explicitJavaBase = true;
          }
          for (ResolveResult result : ref.multiResolve(false)) {
            PsiJavaModule dependency = (PsiJavaModule) result.getElement();
            assert dependency != null : result;
            relations.putValue(module, dependency);
            if (statement.hasModifierProperty(PsiModifier.TRANSITIVE)) {
              transitiveEdges.add(RequiresGraph.key(dependency, module));
            }
            visit(dependency, relations, transitiveEdges);
          }
        }
      }
      if (!explicitJavaBase) {
        PsiJavaModule javaBase = JavaPsiFacade.getInstance(module.getProject()).findModule(PsiJavaModule.JAVA_BASE, module.getResolveScope());
        if (javaBase != null) {
          relations.putValue(module, javaBase);
        }
      }
    }
  }

  private static final class RequiresGraph {
    private final Graph<PsiJavaModule> myGraph;
    private final Set<String> myTransitiveEdges;

    private RequiresGraph(Graph<PsiJavaModule> graph, Set<String> transitiveEdges) {
      myGraph = graph;
      myTransitiveEdges = transitiveEdges;
    }

    public boolean reads(PsiJavaModule source, PsiJavaModule destination) {
      Collection<PsiJavaModule> nodes = myGraph.getNodes();
      if (nodes.contains(destination) && nodes.contains(source)) {
        Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
        while (directReaders.hasNext()) {
          PsiJavaModule next = directReaders.next();
          if (source.equals(next) || myTransitiveEdges.contains(key(destination, next)) && reads(source, next)) {
            return true;
          }
        }
      }
      return false;
    }

    public Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule source) {
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule existing = exports.put(pkg, m);
        return existing != null ? new Trinity<>(pkg, existing, m) : null;
      });
    }

    public PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
      return processExports(module, (pkg, m) -> packageName.equals(pkg) ? m : null);
    }

    private <T> T processExports(PsiJavaModule start, BiFunction<String, PsiJavaModule, T> processor) {
      return myGraph.getNodes().contains(start) ? processExports(start.getName(), start, 0, new HashSet<>(), processor) : null;
    }

    private <T> T processExports(String name, PsiJavaModule module, int layer, Set<PsiJavaModule> visited, BiFunction<String, PsiJavaModule, T> processor) {
      if (visited.add(module)) {
        if (layer == 1) {
          for (PsiPackageAccessibilityStatement statement : module.getExports()) {
            List<String> exportTargets = statement.getModuleNames();
            if (exportTargets.isEmpty() || exportTargets.contains(name)) {
              T result = processor.apply(statement.getPackageName(), module);
              if (result != null) {
                return result;
              }
            }
          }
        }
        if (layer < 2) {
          for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
            PsiJavaModule dependency = iterator.next();
            if (layer == 0 || myTransitiveEdges.contains(key(dependency, module))) {
              T result = processExports(name, dependency, 1, visited, processor);
              if (result != null) {
                return result;
              }
            }
          }
        }
      }

      return null;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getName() + '/' + exporter.getName();
    }

    @Nonnull
    public Set<PsiJavaModule> getAllDependencies(PsiJavaModule module) {
      Set<PsiJavaModule> requires = new HashSet<>();
      collectDependencies(module, requires);
      return requires;
    }

    private void collectDependencies(PsiJavaModule module, Set<PsiJavaModule> dependencies) {
      for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
        PsiJavaModule dependency = iterator.next();
        if (!dependencies.contains(dependency)) {
          dependencies.add(dependency);
          collectDependencies(dependency, dependencies);
        }
      }
    }
  }

  private static final class ChameleonGraph<N> implements Graph<N> {
    private final Set<N> myNodes;
    private final MultiMap<N, N> myEdges;
    private final boolean myInbound;

    private ChameleonGraph(MultiMap<N, N> edges, boolean inbound) {
      myNodes = new HashSet<>();
      edges.entrySet().forEach(e -> {
        myNodes.add(e.getKey());
        myNodes.addAll(e.getValue());
      });
      myEdges = edges;
      myInbound = inbound;
    }

    @Override
    @Nonnull
    public Collection<N> getNodes() {
      return myNodes;
    }

    @Override
    @Nonnull
    public Iterator<N> getIn(N n) {
      return myInbound ? myEdges.get(n).iterator() : Collections.emptyIterator();
    }

    @Override
    @Nonnull
    public Iterator<N> getOut(N n) {
      return myInbound ? Collections.emptyIterator() : myEdges.get(n).iterator();
    }
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final PsiJavaModule myModule;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(@Nonnull Project project, @Nonnull PsiJavaModule module, @Nonnull VirtualFile moduleFile) {
      super(project);
      myModule = module;
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = fileIndex.isInLibrary(moduleFile);
      myIsInTests = !myIncludeLibraries && fileIndex.isInTestSourceContent(moduleFile);
    }

    @Override
    public boolean isSearchInModuleContent(@Nonnull Module aModule) {
      return findDescriptorByModule(aModule, myIsInTests) == myModule;
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(@Nonnull VirtualFile file) {
      Project project = getProject();
      if (project == null) {
        return false;
      }
      if (!isJvmLanguageFile(file)) {
        return false;
      }
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
      if (index.isInLibrary(file)) {
        return myIncludeLibraries && myModule.equals(findDescriptorInLibrary(project, index, file));
      }
      Module module = index.getModuleForFile(file);
      return myModule.equals(findDescriptorByModule(module, myIsInTests));
    }

    private static boolean isJvmLanguageFile(@Nonnull VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      LanguageFileType languageFileType = tryCast(fileTypeRegistry.getFileTypeByFileName(file.getName()), LanguageFileType.class);
      return languageFileType != null && languageFileType.getLanguage() instanceof JavaLanguage;
    }

    public static
    @Nullable
    JavaModuleScope moduleScope(@Nonnull PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) {
        return null;
      }
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      return new JavaModuleScope(module.getProject(), module, virtualFile);
    }
  }
}
