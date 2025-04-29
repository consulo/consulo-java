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
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.compiler.impl.classParsing.*;
import com.intellij.java.compiler.impl.util.cls.ClsUtil;
import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.application.Application;
import consulo.compiler.*;
import consulo.component.ProcessCanceledException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Jeka
 * @since 2002-01-07
 */
public class JavaDependencyCache implements DependencyCache {
    private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.DependencyCache");

    private volatile Cache myCache;
    private volatile Cache myNewClassesCache;

    private static final String REMOTE_INTERFACE_NAME = "java.rmi.Remote";
    private final IntSet myToUpdate = IntSets.newHashSet(); // qName strings to be updated.
    private final IntSet myTraverseRoots = IntSets.newHashSet(); // Dependencies are calculated from these clasess
    private final IntSet myClassesWithSourceRemoved = IntSets.newHashSet();
    private final IntSet myPreviouslyRemoteClasses = IntSets.newHashSet();
    // classes that were Remote, but became non-Remote for some reason
    private final IntSet myMarkedInfos = IntSets.newHashSet(); // classes to be recompiled
    private final Set<VirtualFile> myMarkedFiles = new HashSet<>();

    private volatile JavaDependencyCacheNavigator myCacheNavigator;
    private volatile SymbolTable mySymbolTable;
    private final String mySymbolTableFilePath;
    private final String myStoreDirectoryPath;
    private static final String SYMBOLTABLE_FILE_NAME = "java-symboltable.dat";

    public JavaDependencyCache(@Nonnull String cacheDir) {
        myStoreDirectoryPath = cacheDir + File.separator + ".java-dependency-info";
        mySymbolTableFilePath = myStoreDirectoryPath + "/" + SYMBOLTABLE_FILE_NAME;
    }

    public JavaDependencyCacheNavigator getCacheNavigator() throws CacheCorruptedException {
        if (myCacheNavigator == null) {
            myCacheNavigator = new JavaDependencyCacheNavigator(getCache());
        }
        return myCacheNavigator;
    }

    public void wipe() throws CacheCorruptedException {
        getCache().wipe();
        getNewClassesCache().wipe();
    }

    public Cache getCache() throws CacheCorruptedException {
        try {
            if (myCache == null) {
                // base number of cached record views of each type
                myCache = new Cache(myStoreDirectoryPath, 512);
            }

            return myCache;
        }
        catch (IOException e) {
            throw new CacheCorruptedException(e);
        }
    }

    public Cache getNewClassesCache() throws CacheCorruptedException {
        try {
            if (myNewClassesCache == null) {
                myNewClassesCache = new Cache(myStoreDirectoryPath + "/tmp", 16);
            }
            return myNewClassesCache;
        }
        catch (IOException e) {
            throw new CacheCorruptedException(e);
        }
    }

    public void addTraverseRoot(int qName) {
        myTraverseRoots.add(qName);
    }

    @Override
    public void clearTraverseRoots() {
        myTraverseRoots.clear();
    }

    @Override
    public boolean hasUnprocessedTraverseRoots() {
        return !myTraverseRoots.isEmpty();
    }

    public void markSourceRemoved(int qName) {
        myClassesWithSourceRemoved.add(qName);
    }

    public void addClassToUpdate(int qName) {
        myToUpdate.add(qName);
    }

    public int reparseClassFile(@Nonnull File file, @Nullable byte[] fileContent) throws ClsFormatException, CacheCorruptedException {
        SymbolTable symbolTable = getSymbolTable();

        int qName = getNewClassesCache().importClassInfo(new ClassFileReader(file, symbolTable, fileContent), symbolTable);
        addClassToUpdate(qName);
        addTraverseRoot(qName);
        return qName;
    }

    // for profiling purposes
    /*
    private static void pause() {
        System.out.println("PAUSED. ENTER A CHAR.");
        byte[] buf = new byte[1];
        try {
            System.in.read(buf);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    */

    @Override
    public void update() throws CacheCorruptedException {
        if (myToUpdate.isEmpty()) {
            return; // optimization
        }

        //pause();

        int[] namesToUpdate = myToUpdate.toArray();
        Cache cache = getCache();
        Cache newCache = getNewClassesCache();
        JavaDependencyCacheNavigator navigator = getCacheNavigator();

        // remove unnecesary dependencies
        for (int qName : namesToUpdate) {
            // process use-dependencies
            for (int referencedClassQName : cache.getReferencedClasses(qName)) {
                if (!cache.containsClass(referencedClassQName)) {
                    continue;
                }
                cache.removeClassReferencer(referencedClassQName, qName);
            }
            cache.clearReferencedClasses(qName);
            // process inheritance dependencies
            navigator.walkSuperClasses(
                qName,
                classQName -> {
                    cache.removeSubclass(classQName, qName);
                    return true;
                }
            );
        }

        // do update of classInfos
        for (int qName : namesToUpdate) {
            cache.importClassInfo(newCache, qName);
        }

        // build forward-dependencies for the new infos, all new class infos must be already in the main cache!

        SymbolTable symbolTable = getSymbolTable();

        for (int qName : namesToUpdate) {
            if (!newCache.containsClass(qName)) {
                continue;
            }
            buildForwardDependencies(qName, newCache.getReferences(qName));
            boolean isRemote = false;
            // "remote objects" are classes that _directly_ implement remote interfaces
            int[] superInterfaces = cache.getSuperInterfaces(qName);
            if (superInterfaces.length > 0) {
                int remoteInterfaceName = symbolTable.getId(REMOTE_INTERFACE_NAME);
                for (int superInterface : superInterfaces) {
                    if (isRemoteInterface(cache, superInterface, remoteInterfaceName)) {
                        isRemote = true;
                        break;
                    }
                }
            }
            boolean wasRemote = cache.isRemote(qName);
            if (wasRemote && !isRemote) {
                synchronized (myPreviouslyRemoteClasses) {
                    myPreviouslyRemoteClasses.add(qName);
                }
            }
            cache.setRemote(qName, isRemote);
        }

        // building subclass dependencies
        for (int qName : namesToUpdate) {
            buildSubclassDependencies(getCache(), qName, qName);
        }

        for (int qName : myClassesWithSourceRemoved.toArray()) {
            cache.removeClass(qName);
        }
        myToUpdate.clear();

        //pause();
    }

    @Override
    public String relativePathToQName(@Nonnull String path, char separator) {
        return JavaMakeUtil.relativeClassPathToQName(path, separator);
    }

    @Override
    public void syncOutDir(Trinity<File, String, Boolean> trinity) throws CacheCorruptedException {
        String className = trinity.getSecond();
        if (className != null) {
            int id = getSymbolTable().getId(className);
            addTraverseRoot(id);
            boolean sourcePresent = trinity.getThird();
            if (!sourcePresent) {
                markSourceRemoved(id);
            }
        }
    }

    private void buildForwardDependencies(int classQName, Collection<ReferenceInfo> references) throws CacheCorruptedException {
        Cache cache = getCache();

        int genericSignature = cache.getGenericSignature(classQName);
        if (genericSignature != -1) {
            String genericClassSignature = resolve(genericSignature);
            int[] bounds = findBounds(genericClassSignature);
            for (int boundClassQName : bounds) {
                cache.addClassReferencer(boundClassQName, classQName);
            }
        }

        buildAnnotationDependencies(classQName, cache.getRuntimeVisibleAnnotations(classQName));
        buildAnnotationDependencies(classQName, cache.getRuntimeInvisibleAnnotations(classQName));

        for (ReferenceInfo refInfo : references) {
            int declaringClassName = getActualDeclaringClassForReference(refInfo);
            if (declaringClassName == Cache.UNKNOWN) {
                continue;
            }
            if (refInfo instanceof MemberReferenceInfo memberRefInfo) {
                MemberInfo memberInfo = memberRefInfo.getMemberInfo();
                if (memberInfo instanceof FieldInfo) {
                    cache.addFieldReferencer(declaringClassName, memberInfo.getName(), classQName);
                }
                else if (memberInfo instanceof MethodInfo) {
                    cache.addMethodReferencer(declaringClassName, memberInfo.getName(), memberInfo.getDescriptor(), classQName);
                }
                else {
                    LOG.error("Unknown member info class: " + memberInfo.getClass().getName());
                }
            }
            else { // reference to class
                cache.addClassReferencer(declaringClassName, classQName);
            }
        }
        SymbolTable symbolTable = getSymbolTable();

        for (FieldInfo fieldInfo : cache.getFields(classQName)) {
            buildAnnotationDependencies(classQName, fieldInfo.getRuntimeVisibleAnnotations());
            buildAnnotationDependencies(classQName, fieldInfo.getRuntimeInvisibleAnnotations());

            String className = JavaMakeUtil.parseObjectType(symbolTable.getSymbol(fieldInfo.getDescriptor()), 0);
            if (className == null) {
                continue;
            }
            int cls = symbolTable.getId(className);
            cache.addClassReferencer(cls, classQName);
        }

        for (MethodInfo methodInfo : cache.getMethods(classQName)) {
            buildAnnotationDependencies(classQName, methodInfo.getRuntimeVisibleAnnotations());
            buildAnnotationDependencies(classQName, methodInfo.getRuntimeInvisibleAnnotations());
            buildAnnotationDependencies(classQName, methodInfo.getRuntimeVisibleParameterAnnotations());
            buildAnnotationDependencies(classQName, methodInfo.getRuntimeInvisibleParameterAnnotations());

            if (methodInfo.isConstructor()) {
                continue;
            }

            String returnTypeClassName = JavaMakeUtil.parseObjectType(methodInfo.getReturnTypeDescriptor(symbolTable), 0);
            if (returnTypeClassName != null) {
                int returnTypeClassQName = symbolTable.getId(returnTypeClassName);
                cache.addClassReferencer(returnTypeClassQName, classQName);
            }

            String[] parameterSignatures = JavaCacheUtils.getParameterSignatures(methodInfo, symbolTable);
            for (String parameterSignature : parameterSignatures) {
                String paramClassName = JavaMakeUtil.parseObjectType(parameterSignature, 0);
                if (paramClassName != null) {
                    int paramClassId = symbolTable.getId(paramClassName);
                    cache.addClassReferencer(paramClassId, classQName);
                }
            }
        }
    }

    private static boolean isRemoteInterface(Cache cache, int ifaceName, int remoteInterfaceName) throws CacheCorruptedException {
        if (ifaceName == remoteInterfaceName) {
            return true;
        }
        for (int superInterfaceName : cache.getSuperInterfaces(ifaceName)) {
            if (isRemoteInterface(cache, superInterfaceName, remoteInterfaceName)) {
                return true;
            }
        }
        return false;
    }


    private void buildAnnotationDependencies(int classQName, AnnotationConstantValue[][] annotations) throws CacheCorruptedException {
        if (annotations == null || annotations.length == 0) {
            return;
        }
        for (AnnotationConstantValue[] annotation : annotations) {
            buildAnnotationDependencies(classQName, annotation);
        }
    }

    private void buildAnnotationDependencies(int classQName, AnnotationConstantValue[] annotations) throws CacheCorruptedException {
        if (annotations == null || annotations.length == 0) {
            return;
        }
        Cache cache = getCache();
        for (AnnotationConstantValue annotation : annotations) {
            int annotationQName = annotation.getAnnotationQName();

            cache.addClassReferencer(annotationQName, classQName);

            AnnotationNameValuePair[] memberValues = annotation.getMemberValues();
            for (AnnotationNameValuePair nameValuePair : memberValues) {
                for (MethodInfo annotationMember : cache.findMethodsByName(annotationQName, nameValuePair.getName())) {
                    cache.addMethodReferencer(annotationQName, annotationMember.getName(), annotationMember.getDescriptor(), classQName);
                }
            }
        }
    }

    private int[] findBounds(String genericClassSignature) throws CacheCorruptedException {
        try {
            String[] boundInterfaces = BoundsParser.getBounds(genericClassSignature);
            int[] ids = ArrayUtil.newIntArray(boundInterfaces.length);
            for (int i = 0; i < boundInterfaces.length; i++) {
                ids[i] = getSymbolTable().getId(boundInterfaces[i]);
            }
            return ids;
        }
        catch (SignatureParsingException e) {
            return ArrayUtil.EMPTY_INT_ARRAY;
        }
    }

    // fixes JDK 1.4 javac bug that generates references in the constant pool
    // to the subclass even if the field was declared in a superclass
    private int getActualDeclaringClassForReference(ReferenceInfo refInfo) throws CacheCorruptedException {
        if (!(refInfo instanceof MemberReferenceInfo memberRefInfo)) {
            return refInfo.getClassName();
        }
        int declaringClassName = refInfo.getClassName();
        Cache cache = getCache();
        MemberInfo memberInfo = memberRefInfo.getMemberInfo();
        if (memberInfo instanceof FieldInfo) {
            if (cache.findFieldByName(declaringClassName, memberInfo.getName()) != null) {
                return declaringClassName;
            }
        }
        else if (memberInfo instanceof MethodInfo) {
            if (cache.findMethod(declaringClassName, memberInfo.getName(), memberInfo.getDescriptor()) != null) {
                return declaringClassName;
            }
        }
        DeclaringClassFinder finder = new DeclaringClassFinder(memberInfo);
        getCacheNavigator().walkSuperClasses(declaringClassName, finder);
        return finder.getDeclaringClassName();
    }

    /**
     * @return qualified names of the classes that should be additionally recompiled
     */
    public Pair<int[], Set<VirtualFile>> findDependentClasses(CompileContext context, Project project, Set<VirtualFile> compiledWithErrors)
        throws CacheCorruptedException, ExitException {

        markDependencies(context, project, compiledWithErrors);
        return Pair.create(myMarkedInfos.toArray(), Collections.unmodifiableSet(myMarkedFiles));
    }

    private void markDependencies(CompileContext context, Project project, Set<VirtualFile> compiledWithErrors)
        throws CacheCorruptedException, ExitException {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("====================Marking dependent files=====================");
            }
            // myToUpdate can be modified during the mark procedure, so use toArray() to iterate it
            int[] traverseRoots = myTraverseRoots.toArray();
            SourceFileFinder sourceFileFinder = new SourceFileFinder(project, context);
            CachingSearcher searcher = new CachingSearcher(project);
            ChangedRetentionPolicyDependencyProcessor changedRetentionPolicyDependencyProcessor =
                new ChangedRetentionPolicyDependencyProcessor(project, searcher, this);
            for (int qName : traverseRoots) {
                if (!getCache().containsClass(qName)) {
                    continue;
                }
                if (getNewClassesCache().containsClass(qName)) { // there is a new class file created
                    new JavaDependencyProcessor(project, this, qName).run();
                    List<ChangedConstantsDependencyProcessor.FieldChangeInfo> changed = new ArrayList<>();
                    List<ChangedConstantsDependencyProcessor.FieldChangeInfo> removed = new ArrayList<>();
                    findModifiedConstants(qName, changed, removed);
                    if (!changed.isEmpty() || !removed.isEmpty()) {
                        new ChangedConstantsDependencyProcessor(project, searcher, this, qName, context, changed
                            .toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[changed.size()]), removed
                            .toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[removed.size()]))
                            .run();
                    }
                    changedRetentionPolicyDependencyProcessor.checkAnnotationRetentionPolicyChanges(qName);
                    for (DependencyProcessor additionalProcessor : project.getApplication().getExtensionList(DependencyProcessor.class)) {
                        additionalProcessor.processDependencies(context, qName, searcher);
                    }
                }
                else {
                    boolean isSourceDeleted = false;
                    if (myClassesWithSourceRemoved.contains(qName)) { // no recompiled class file, check whether the classfile exists
                        isSourceDeleted = true;
                    }
                    else if (!new File(getCache().getPath(qName)).exists()) {
                        String qualifiedName = resolve(qName);
                        String sourceFileName = getCache().getSourceFileName(qName);
                        boolean markAsRemovedSource = project.getApplication().runReadAction((Supplier<Boolean>)() -> {
                            VirtualFile sourceFile = sourceFileFinder.findSourceFile(qualifiedName, sourceFileName, false);
                            return sourceFile == null || !compiledWithErrors.contains(sourceFile) ? Boolean.TRUE : Boolean.FALSE;
                        });
                        if (markAsRemovedSource) {
                            // for Inner classes: sourceFile may exist, but the inner class declaration inside it may not,
                            // thus the source for the class info should be considered removed
                            isSourceDeleted = true;
                            markSourceRemoved(qName);
                            myMarkedInfos.remove(qName); // if the info has been marked already, the mark should be removed
                        }
                    }
                    if (isSourceDeleted) {
                        Dependency[] backDependencies = getCache().getBackDependencies(qName);
                        for (Dependency backDependency : backDependencies) {
                            if (markTargetClassInfo(backDependency) && LOG.isDebugEnabled()) {
                                LOG.debug(
                                    "Mark dependent class " + backDependency.getClassQualifiedName() + "; " +
                                        "reason: no class file found for " + qName
                                );
                            }
                        }
                    }
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("================================================================");
            }
        }
        catch (ProcessCanceledException ignored) {
            // deliberately suppressed
        }
    }

    private void findModifiedConstants(
        int qName,
        Collection<ChangedConstantsDependencyProcessor.FieldChangeInfo> changedConstants,
        Collection<ChangedConstantsDependencyProcessor.FieldChangeInfo> removedConstants
    ) throws CacheCorruptedException {
        Cache cache = getCache();
        for (FieldInfo field : cache.getFields(qName)) {
            int oldFlags = field.getFlags();
            if (ClsUtil.isStatic(oldFlags) && ClsUtil.isFinal(oldFlags)) {
                Cache newClassesCache = getNewClassesCache();
                FieldInfo newField = newClassesCache.findFieldByName(qName, field.getName());
                if (newField == null) {
                    if (!ConstantValue.EMPTY_CONSTANT_VALUE.equals(field.getConstantValue())) {
                        // if the field was really compile time constant
                        removedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(field));
                    }
                }
                else {
                    boolean visibilityRestricted = JavaMakeUtil.isMoreAccessible(oldFlags, newField.getFlags());
                    if (!field.getConstantValue().equals(newField.getConstantValue()) || visibilityRestricted) {
                        changedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(field, visibilityRestricted));
                    }
                }
            }
        }
    }

    private static void buildSubclassDependencies(Cache cache, int qName, int targetClassId) throws CacheCorruptedException {
        int superQName = cache.getSuperQualifiedName(targetClassId);
        if (superQName != Cache.UNKNOWN) {
            cache.addSubclass(superQName, qName);
            buildSubclassDependencies(cache, qName, superQName);
        }

        int[] interfaces = cache.getSuperInterfaces(targetClassId);
        for (int interfaceName : interfaces) {
            cache.addSubclass(interfaceName, qName);
            buildSubclassDependencies(cache, qName, interfaceName);
        }
    }

    /**
     * Marks ClassInfo targeted by the dependency
     *
     * @return true if really added, false otherwise
     */
    public boolean markTargetClassInfo(Dependency dependency) throws CacheCorruptedException {
        return markClassInfo(dependency.getClassQualifiedName(), false);
    }

    /**
     * Marks ClassInfo that corresponds to the specified qualified name
     * If class info is already recompiled, it is not marked
     *
     * @return true if really added, false otherwise
     */
    public boolean markClass(int qualifiedName) throws CacheCorruptedException {
        return markClass(qualifiedName, false);
    }

    /**
     * Marks ClassInfo that corresponds to the specified qualified name
     * If class info is already recompiled, it is not marked unless force parameter is true
     *
     * @return true if really added, false otherwise
     */
    public boolean markClass(int qualifiedName, boolean force) throws CacheCorruptedException {
        return markClassInfo(qualifiedName, force);
    }

    public boolean isTargetClassInfoMarked(Dependency dependency) {
        return isClassInfoMarked(dependency.getClassQualifiedName());
    }

    public boolean isClassInfoMarked(int qName) {
        return myMarkedInfos.contains(qName);
    }

    public void markFile(VirtualFile file) {
        myMarkedFiles.add(file);
    }

    /**
     * @return true if really marked, false otherwise
     */
    private boolean markClassInfo(int qName, boolean force) throws CacheCorruptedException {
        if (!getCache().containsClass(qName)) {
            return false;
        }
        if (myClassesWithSourceRemoved.contains(qName)) {
            return false; // no need to recompile since source has been removed
        }
        if (!force) {
            if (getNewClassesCache().containsClass(qName)) { // already recompiled
                return false;
            }
        }
        return myMarkedInfos.add(qName);
    }

    @Override
    public void resetState() {
        myClassesWithSourceRemoved.clear();
        myMarkedFiles.clear();
        myMarkedInfos.clear();
        myToUpdate.clear();
        myTraverseRoots.clear();
        if (myNewClassesCache != null) {
            myNewClassesCache.wipe();
            myNewClassesCache = null;
        }
        myCacheNavigator = null;
        try {
            if (myCache != null) {
                myCache.dispose();
                myCache = null;
            }
        }
        catch (CacheCorruptedException e) {
            LOG.info(e);
        }
        try {
            if (mySymbolTable != null) {
                mySymbolTable.dispose();
                mySymbolTable = null;
            }
        }
        catch (CacheCorruptedException e) {
            LOG.info(e);
        }
    }

    public SymbolTable getSymbolTable() throws CacheCorruptedException {
        if (mySymbolTable == null) {
            mySymbolTable = new SymbolTable(new File(mySymbolTableFilePath));
        }
        return mySymbolTable;
    }

    public String resolve(int id) throws CacheCorruptedException {
        return getSymbolTable().getSymbol(id);
    }

    public boolean wasRemote(int qName) {
        return myPreviouslyRemoteClasses.contains(qName);
    }

    @Override
    public void findDependentFiles(
        CompileContext context,
        SimpleReference<CacheCorruptedException> exceptionRef,
        Function<Pair<int[], Set<VirtualFile>>, Pair<int[], Set<VirtualFile>>> filter,
        Set<VirtualFile> dependentFiles,
        Set<VirtualFile> compiledWithErrors
    ) throws CacheCorruptedException, ExitException {
        Pair<int[], Set<VirtualFile>> deps = findDependentClasses(context, context.getProject(), compiledWithErrors);
        Pair<int[], Set<VirtualFile>> filteredDeps = filter != null ? filter.apply(deps) : deps;

        CacheCorruptedException[] _ex = {null};
        Application.get().runReadAction(() -> {
            try {
                CompilerManager compilerConfiguration = CompilerManager.getInstance(context.getProject());
                SourceFileFinder sourceFileFinder = new SourceFileFinder(context.getProject(), context);
                Cache cache = getCache();
                for (int infoQName : filteredDeps.getFirst()) {
                    String qualifiedName = resolve(infoQName);
                    String sourceFileName = cache.getSourceFileName(infoQName);
                    VirtualFile file = sourceFileFinder.findSourceFile(qualifiedName, sourceFileName, true);
                    if (file != null) {
                        dependentFiles.add(file);
                        /*if (Application.get().isUnitTestMode()) {
                            LOG.assertTrue(file.isValid());
                            CompilerManagerImpl.addRecompiledPath(file.getPath());
                        }*/
                    }
                    else {
                        LOG.info("No source file for " + resolve(infoQName) + " found; source file name=" + sourceFileName);
                    }
                }
                for (VirtualFile file : filteredDeps.getSecond()) {
                    if (!compilerConfiguration.isExcludedFromCompilation(file)) {
                        dependentFiles.add(file);
                        /*if (Application.get().isUnitTestMode()) {
                            LOG.assertTrue(file.isValid());
                            CompilerManagerImpl.addRecompiledPath(file.getPath());
                        }*/
                    }
                }
            }
            catch (CacheCorruptedException e) {
                _ex[0] = e;
            }
        });
    }

    private class DeclaringClassFinder implements ClassInfoProcessor {
        private final int myMemberName;
        private final int myMemberDescriptor;
        private int myDeclaringClass = Cache.UNKNOWN;
        private final boolean myIsField;

        private DeclaringClassFinder(MemberInfo memberInfo) {
            myMemberName = memberInfo.getName();
            myMemberDescriptor = memberInfo.getDescriptor();
            myIsField = memberInfo instanceof FieldInfo;
        }

        public int getDeclaringClassName() {
            return myDeclaringClass;
        }

        @Override
        public boolean process(int classQName) throws CacheCorruptedException {
            Cache cache = getCache();
            if (myIsField) {
                FieldInfo fieldId = cache.findField(classQName, myMemberName, myMemberDescriptor);
                if (fieldId != null) {
                    myDeclaringClass = classQName;
                    return false;
                }
            }
            else {
                MethodInfo methodId = cache.findMethod(classQName, myMemberName, myMemberDescriptor);
                if (methodId != null) {
                    myDeclaringClass = classQName;
                    return false;
                }
            }
            return true;
        }
    }
}
