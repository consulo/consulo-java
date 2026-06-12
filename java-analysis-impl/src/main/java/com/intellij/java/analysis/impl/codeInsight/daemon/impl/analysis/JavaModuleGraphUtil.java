// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.impl.psi.impl.search.JavaModuleFinderImpl;
import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiJavaModuleModificationTracker;
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
import consulo.project.content.ProjectRootModificationTracker;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;

import org.jspecify.annotations.Nullable;

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
    if (element == null) {
      return null;
    }
    if (element instanceof PsiJavaModule module) {
      return module;
    }
    if (element.getContainingFile() instanceof PsiJavaFile file) {
      PsiJavaModule module = file.getModuleDeclaration();
      if (module != null) {
        return module;
      }
    }

    if (element instanceof PsiFileSystemItem fsItem) {
      return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
    }

    PsiFile file = element.getContainingFile();
    if (file != null) {
      return findDescriptorByFile(file.getVirtualFile(), file.getProject());
    }

    if (element instanceof PsiJavaPackage psiPackage) {
      PsiDirectory[] directories = psiPackage.getDirectories((GlobalSearchScope) ProjectScopes.getLibrariesScope(psiPackage.getProject()));
      for (PsiDirectory directory : directories) {
        PsiJavaModule descriptor = findDescriptorByFile(directory.getVirtualFile(), directory.getProject());
        if (descriptor != null) {
          return descriptor;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, Project project) {
    if (file == null) {
      return null;
    }
    return JavaPsiFacade.getInstance(project).findModule(file);
  }

  @Nullable
  public static PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) {
      return null;
    }
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
        ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
        : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  private static Result<PsiJavaModule> createModuleCacheResult(Module module, boolean inTests) {
    Project project = module.getProject();
    return Result.create(findDescriptionByModuleInner(module, inTests),
        ProjectRootModificationTracker.getInstance(project),
        PsiJavaModuleModificationTracker.getInstance(project));
  }

  @Nullable
  private static PsiJavaModule findDescriptionByModuleInner(Module module, boolean inTests) {
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

    return null;
  }

  public static Collection<PsiJavaModule> findCycle(PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
        Result.create(findCycles(project),
                      PsiJavaModuleModificationTracker.getInstance(project),
                      ProjectRootModificationTracker.getInstance(project)));
    return ObjectUtil.notNull(ContainerUtil.find(cycles, set -> set.contains(module)), Collections.emptyList());
  }

  public static boolean exports(PsiJavaModule source, String packageName, @Nullable PsiJavaModule target) {
    Map<String, Set<String>> exports = LanguageCachedValueUtil.getCachedValue(source, () ->
        Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || target != null && targets.contains(target.getName()));
  }

  public static boolean reads(PsiJavaModule source, PsiJavaModule destination) {
    return getRequiresGraph(source).reads(source, destination);
  }

  public static Set<PsiJavaModule> getAllDependencies(PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source);
  }

  /**
   * Retrieves a list of package accessibility statements for a given Java module that
   * are accessible to the specified place.
   *
   * @param place the place from which accessibility is being checked.
   * @param module the module whose exported packages are to be retrieved.
   * @return a list of `PsiPackageAccessibilityStatement` elements that represent the exported packages accessible
   *         to the specified place.
   */
  public static List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module) {
    List<PsiPackageAccessibilityStatement> results = new ArrayList<>();
    PsiJavaModule currentModule = findDescriptorByElement(place);
    List<PsiPackageAccessibilityStatement> exports = getAllDeclaredExports(module);
    for (PsiPackageAccessibilityStatement export : exports) {
      PsiJavaCodeReferenceElement aPackage = export.getPackageReference();
      if (aPackage == null) continue;
      List<String> accessibleModules = export.getModuleNames();
      if (!accessibleModules.isEmpty()) {
        if (currentModule == null || !accessibleModules.contains(currentModule.getName())) continue;
      }
      results.add(export);
    }
    return results;
  }

  /**
   * Retrieves all transitive modules required by the given module, including the module itself.
   *
   * @param module the module for which transitive dependencies are being collected; must not be null
   * @return a set of transitive modules required by the given module, including the module itself
   */
  public static Set<PsiJavaModule> getAllTransitiveModulesIncludeCurrent(PsiJavaModule module) {
    return LanguageCachedValueUtil.getCachedValue(module, () -> {
      Project project = module.getProject();
      Set<PsiJavaModule> collected = new HashSet<>();
      collected.addAll(getAllDependencies(module));
      collected.add(module);
      return Result.create(collected,
                           PsiJavaModuleModificationTracker.getInstance(project),
                           ProjectRootModificationTracker.getInstance(project));
    });
  }

  private static List<PsiPackageAccessibilityStatement> getAllDeclaredExports(PsiJavaModule module) {
    Project project = module.getProject();
    return LanguageCachedValueUtil.getCachedValue(module, () -> {
      List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();
      for (PsiJavaModule javaModule : getAllTransitiveModulesIncludeCurrent(module)) {
        for (PsiPackageAccessibilityStatement export : javaModule.getExports()) {
          exports.add(export);
        }
      }
      return Result.create(exports,
                           PsiJavaModuleModificationTracker.getInstance(project),
                           ProjectRootModificationTracker.getInstance(project));
    });
  }

  @Nullable
  public static Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  public static
  @Nullable
  PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
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

  private static Map<String, Set<String>> exportsMap(PsiJavaModule source) {
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
        Result.create(buildRequiresGraph(project),
                      PsiJavaModuleModificationTracker.getInstance(project),
                      ProjectRootModificationTracker.getInstance(project)));
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
      source = getPhysicalModule(source);
      destination = getPhysicalModule(destination);
      Collection<PsiJavaModule> nodes = myGraph.getNodes();
      if (!nodes.contains(destination) || !nodes.contains(source)) {
        return false;
      }

      UniqueBuffer<PsiJavaModule> buffer = new UniqueBuffer<>();
      buffer.add(destination);
      while (!buffer.isEmpty()) {
        destination = buffer.poll();
        Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
        while (directReaders.hasNext()) {
          PsiJavaModule next = directReaders.next();
          if (source.equals(next)) {
            return true;
          }
          if (myTransitiveEdges.contains(key(destination, next)) && !next.equals(destination)) {
            buffer.add(next);
          }
        }
      }
      return false;
    }

    public Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule source) {
      source = getPhysicalModule(source);
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule found = exports.put(pkg, m);
        return found == null ||
               found instanceof LightJavaModule && m instanceof LightJavaModule ||
               found.getName().equals(m.getName())
               ? null : new Trinity<>(pkg, found, m);
      });
    }

    public PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
      return processExports(getPhysicalModule(module), (pkg, m) -> packageName.equals(pkg) ? m : null);
    }

    private <T> T processExports(PsiJavaModule start, BiFunction<String, PsiJavaModule, T> processor) {
      start = getPhysicalModule(start);
      return myGraph.getNodes().contains(start) ? processExports(start.getName(), start, true, new HashSet<>(), processor) : null;
    }

    private <T> T processExports(String name, PsiJavaModule module, boolean direct, Set<PsiJavaModule> visited, BiFunction<String, PsiJavaModule, T> processor) {
      module = getPhysicalModule(module);
      if (visited.add(module)) {
        if (!direct) {
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
        for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
          PsiJavaModule dependency = iterator.next();
          if (direct || myTransitiveEdges.contains(key(dependency, module))) {
            T result = processExports(name, dependency, false, visited, processor);
            if (result != null) {
              return result;
            }
          }
        }
      }

      return null;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getName() + '/' + exporter.getName();
    }

    public Set<PsiJavaModule> getAllDependencies(PsiJavaModule module) {
      Set<PsiJavaModule> requires = new HashSet<>();
      collectDependencies(getPhysicalModule(module), requires);
      return requires;
    }

    private void collectDependencies(PsiJavaModule module, Set<PsiJavaModule> dependencies) {
      module = getPhysicalModule(module);
      for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
        PsiJavaModule dependency = iterator.next();
        if (!dependencies.contains(dependency)) {
          dependencies.add(dependency);
          collectDependencies(dependency, dependencies);
        }
      }
    }

    private static PsiJavaModule getPhysicalModule(PsiJavaModule from) {
      if (from.isPhysical()) {
        return from;
      }
      if (!(from.getContainingFile() instanceof PsiJavaFile file)) {
        return from;
      }
      if (!(file.getOriginalFile() instanceof PsiJavaFile origin)) {
        return from;
      }
      if (origin.getModuleDeclaration() instanceof PsiJavaModule result) {
        return result;
      }
      return from;
    }

    /**
     * FIFO queue that prevents duplicate additions.
     * Once added, an element cannot be added again even after being polled.
     */
    private static class UniqueBuffer<T> {
      private final Set<T> myUnique = new HashSet<>();
      private final Queue<T> myBuffer = new ArrayDeque<>();

      public void add(T value) {
        if (myUnique.add(value)) {
          myBuffer.add(value);
        }
      }

      public T poll() {
        return myBuffer.poll();
      }

      public boolean isEmpty() {
        return myBuffer.isEmpty();
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
    public Collection<N> getNodes() {
      return myNodes;
    }

    @Override
    public Iterator<N> getIn(N n) {
      return myInbound ? myEdges.get(n).iterator() : Collections.emptyIterator();
    }

    @Override
    public Iterator<N> getOut(N n) {
      return myInbound ? Collections.emptyIterator() : myEdges.get(n).iterator();
    }
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final PsiJavaModule myModule;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(Project project, PsiJavaModule module, VirtualFile moduleFile) {
      super(project);
      myModule = module;
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = fileIndex.isInLibrary(moduleFile);
      myIsInTests = !myIncludeLibraries && fileIndex.isInTestSourceContent(moduleFile);
    }

    @Override
    public boolean isSearchInModuleContent(Module aModule) {
      return findDescriptorByModule(aModule, myIsInTests) == myModule;
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(VirtualFile file) {
      Project project = getProject();
      if (project == null) {
        return false;
      }
      if (!isJvmLanguageFile(file)) {
        return false;
      }
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) {
        return myIncludeLibraries && myModule.equals(JavaModuleFinderImpl.findDescriptorInLibrary(project, index, file));
      }
      Module module = index.getModuleForFile(file);
      return myModule.equals(findDescriptorByModule(module, myIsInTests));
    }

    private static boolean isJvmLanguageFile(VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      LanguageFileType languageFileType = tryCast(fileTypeRegistry.getFileTypeByFileName(file.getName()), LanguageFileType.class);
      return languageFileType != null && languageFileType.getLanguage() instanceof JavaLanguage;
    }

    public static
    @Nullable
    JavaModuleScope moduleScope(PsiJavaModule module) {
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
