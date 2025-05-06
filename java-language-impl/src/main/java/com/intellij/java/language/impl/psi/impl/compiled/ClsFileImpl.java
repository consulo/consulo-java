// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.ClassFileViewProvider;
import com.intellij.java.language.impl.psi.impl.JavaPsiImplementationHelper;
import com.intellij.java.language.impl.psi.impl.compiled.ClsElementImpl.InvalidMirrorException;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.compiled.ClassFileDecompiler;
import com.intellij.java.language.psi.compiled.ClassFileDecompilers;
import com.intellij.java.language.psi.stubs.PsiClassHolderFileStub;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.Queryable;
import consulo.component.ProcessCanceledException;
import consulo.component.util.ModificationTracker;
import consulo.container.PluginException;
import consulo.container.plugin.PluginId;
import consulo.container.plugin.PluginManager;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.internal.org.objectweb.asm.Attribute;
import consulo.internal.org.objectweb.asm.ClassReader;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.language.Language;
import consulo.language.content.FileIndexFacade;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.PsiBinaryFileImpl;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.*;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.ProjectManager;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.BitUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ClsFileImpl extends PsiBinaryFileImpl
    implements PsiJavaFile, PsiFileWithStubSupport, PsiFileEx, Queryable, PsiClassOwnerEx, PsiCompiledFile {
    private static final Logger LOG = Logger.getInstance(ClsFileImpl.class);

    private static final String BANNER = "\n" +
        "  // Consulo API Decompiler stub source generated from a class file\n" +
        "  // Implementation of methods is not available\n" +
        "\n";

    private static final Key<Document> CLS_DOCUMENT_LINK_KEY = Key.create("cls.document.link");

    /**
     * NOTE: you absolutely MUST NOT hold PsiLock under the mirror lock
     */
    private final Object myMirrorLock = new Object();
    private final Object myStubLock = new Object();

    private final boolean myIsForDecompiling;
    private volatile SoftReference<StubTree> myStub;
    private volatile Reference<TreeElement> myMirrorFileElement;
    private volatile ClsPackageStatementImpl myPackageStatement;

    public ClsFileImpl(@Nonnull FileViewProvider viewProvider) {
        this(viewProvider, false);
    }

    private ClsFileImpl(@Nonnull FileViewProvider viewProvider, boolean forDecompiling) {
        super(viewProvider.getManager(), viewProvider);
        myIsForDecompiling = forDecompiling;
        //noinspection ResultOfMethodCallIgnored
        JavaElementType.CLASS.getIndex();  // initialize Java stubs
    }

    @Override
    public PsiFile getContainingFile() {
        if (!isValid()) {
            throw new PsiInvalidElementAccessException(this);
        }
        return this;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && (myIsForDecompiling || getVirtualFile().isValid());
    }

    boolean isForDecompiling() {
        return myIsForDecompiling;
    }

    @RequiredReadAction
    @Override
    @Nonnull
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @RequiredReadAction
    @Override
    @Nonnull
    public PsiElement[] getChildren() {
        PsiJavaModule module = getModuleDeclaration();
        return module != null ? new PsiElement[]{module} : getClasses();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiClass[] getClasses() {
        return getStub().getClasses();
    }

    @Override
    @RequiredReadAction
    public PsiPackageStatement getPackageStatement() {
        ClsPackageStatementImpl statement = myPackageStatement;
        if (statement == null) {
            statement = ClsPackageStatementImpl.NULL_PACKAGE;
            PsiClassHolderFileStub<?> stub = getStub();
            if (!(stub instanceof PsiJavaFileStub) || stub.findChildStubByType(JavaStubElementTypes.MODULE) == null) {
                String packageName = findPackageName(stub);
                if (packageName != null) {
                    statement = new ClsPackageStatementImpl(this, packageName);
                }
            }
            myPackageStatement = statement;
        }
        return statement != ClsPackageStatementImpl.NULL_PACKAGE ? statement : null;
    }

    private static String findPackageName(PsiClassHolderFileStub<?> stub) {
        String packageName = null;

        if (stub instanceof PsiJavaFileStub javaFileStub) {
            packageName = javaFileStub.getPackageName();
        }
        else {
            PsiClass[] psiClasses = stub.getClasses();
            if (psiClasses.length > 0) {
                String className = psiClasses[0].getQualifiedName();
                if (className != null) {
                    int index = className.lastIndexOf('.');
                    if (index >= 0) {
                        packageName = className.substring(0, index);
                    }
                }
            }
        }

        return !StringUtil.isEmpty(packageName) ? packageName : null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getPackageName() {
        PsiPackageStatement statement = getPackageStatement();
        return statement == null ? "" : statement.getPackageName();
    }

    @Override
    public void setPackageName(String packageName) throws IncorrectOperationException {
        throw new IncorrectOperationException("Cannot set package name for compiled files");
    }

    @RequiredReadAction
    @Override
    public PsiImportList getImportList() {
        return null;
    }

    @Override
    public boolean importClass(@Nonnull PsiClass aClass) {
        throw new UnsupportedOperationException("Cannot add imports to compiled classes");
    }

    @Override
    @Nonnull
    public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
        return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    public PsiClass[] getSingleClassImports(boolean checkIncludes) {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    public String[] getImplicitlyImportedPackages() {
        return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public Set<String> getClassNames() {
        return Collections.singleton(getVirtualFile().getNameWithoutExtension());
    }

    @Override
    @Nonnull
    public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
        return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }

    @Override
    public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LanguageLevel getLanguageLevel() {
        PsiClassHolderFileStub<?> stub = getStub();
        if (stub instanceof PsiJavaFileStub javaFileStub) {
            LanguageLevel level = javaFileStub.getLanguageLevel();
            if (level != null) {
                return level;
            }
        }
        return LanguageLevel.HIGHEST;
    }

    @RequiredReadAction
    @Nullable
    @Override
    public PsiJavaModule getModuleDeclaration() {
        PsiClassHolderFileStub<?> stub = getStub();
        return stub instanceof PsiJavaFileStub javaFileStub ? javaFileStub.getModule() : null;
    }

    @RequiredWriteAction
    @Override
    public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
        throw ClsElementImpl.cannotModifyException(this);
    }

    @Override
    public void checkSetName(String name) throws IncorrectOperationException {
        throw ClsElementImpl.cannotModifyException(this);
    }

    /**
     * Shouldn't be called from outside or overridden
     */
    @RequiredReadAction
    @Deprecated
    public void appendMirrorText(@SuppressWarnings("unused") int indentLevel, @Nonnull StringBuilder buffer) {
        appendMirrorText(buffer);
    }

    @RequiredReadAction
    private void appendMirrorText(@Nonnull StringBuilder buffer) {
        buffer.append(BANNER);

        PsiJavaModule module = getModuleDeclaration();
        if (module != null) {
            ClsElementImpl.appendText(module, 0, buffer);
        }
        else {
            ClsElementImpl.appendText(getPackageStatement(), 0, buffer, "\n\n");

            PsiClass[] classes = getClasses();
            if (classes.length > 0) {
                ClsElementImpl.appendText(classes[0], 0, buffer);
            }
        }
    }

    /**
     * Shouldn't be called from outside or overridden
     */
    @RequiredReadAction
    @Deprecated
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
        setFileMirror(element);
    }

    @RequiredReadAction
    private void setFileMirror(@Nonnull TreeElement element) {
        PsiElement mirrorElement = SourceTreeToPsiMap.treeToPsiNotNull(element);
        if (!(mirrorElement instanceof PsiJavaFile mirrorFile)) {
            throw new InvalidMirrorException("Unexpected mirror file: " + mirrorElement);
        }

        PsiJavaModule module = getModuleDeclaration();
        if (module != null) {
            ClsElementImpl.setMirror(module, mirrorFile.getModuleDeclaration());
        }
        else {
            ClsElementImpl.setMirrorIfPresent(getPackageStatement(), mirrorFile.getPackageStatement());
            ClsElementImpl.setMirrors(getClasses(), mirrorFile.getClasses());
        }
    }

    @Override
    @Nonnull
    public PsiElement getNavigationElement() {
        for (ClsCustomNavigationPolicy navigationPolicy : ClsCustomNavigationPolicy.EP_NAME.getExtensionList()) {
            try {
                PsiElement navigationElement = navigationPolicy instanceof ClsCustomNavigationPolicyEx customNavigationPolicy
                    ? customNavigationPolicy.getFileNavigationElement(this)
                    : navigationPolicy.getNavigationElement(this);
                if (navigationElement != null) {
                    return navigationElement;
                }
            }
            catch (IndexNotReadyException ignore) {
            }
        }

        return LanguageCachedValueUtil.getCachedValue(this, () -> {
            PsiElement target = JavaPsiImplementationHelper.getInstance(getProject()).getClsFileNavigationElement(this);
            ModificationTracker tracker = FileIndexFacade.getInstance(getProject()).getRootModificationTracker();
            return CachedValueProvider.Result.create(target, this, target.getContainingFile(), tracker);
        });
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public PsiElement getMirror() {
        TreeElement mirrorTreeElement = SoftReference.dereference(myMirrorFileElement);
        if (mirrorTreeElement == null) {
            synchronized (myMirrorLock) {
                mirrorTreeElement = SoftReference.dereference(myMirrorFileElement);
                if (mirrorTreeElement == null) {
                    VirtualFile file = getVirtualFile();
                    PsiClass[] classes = getClasses();
                    String fileName = (classes.length > 0 ? classes[0].getName() : file.getNameWithoutExtension()) +
                        JavaFileType.DOT_DEFAULT_EXTENSION;

                    Document document = FileDocumentManager.getInstance().getDocument(file);
                    assert document != null : file.getUrl();

                    CharSequence mirrorText = document.getImmutableCharSequence();
                    boolean internalDecompiler = StringUtil.startsWith(mirrorText, BANNER);
                    PsiFileFactory factory = PsiFileFactory.getInstance(getManager().getProject());
                    PsiFile mirror = factory.createFileFromText(fileName, JavaLanguage.INSTANCE, mirrorText, false, false);
                    mirror.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, getLanguageLevel());

                    mirrorTreeElement = SourceTreeToPsiMap.psiToTreeNotNull(mirror);
                    try {
                        TreeElement finalMirrorTreeElement = mirrorTreeElement;
                        ProgressManager.getInstance().executeNonCancelableSection(() -> {
                            setFileMirror(finalMirrorTreeElement);
                            putUserData(CLS_DOCUMENT_LINK_KEY, document);
                        });
                    }
                    catch (InvalidMirrorException e) {
                        //noinspection ThrowableResultOfMethodCallIgnored
                        LOG.error(file.getUrl(), internalDecompiler ? e : wrapException(e, file));
                    }

                    ((PsiFileImpl)mirror).setOriginalFile(this);
                    myMirrorFileElement = new SoftReference<>(mirrorTreeElement);
                }
            }
        }
        return mirrorTreeElement.getPsi();
    }

    @RequiredReadAction
    @Override
    public String getText() {
        VirtualFile file = getVirtualFile();
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assert document != null : file.getUrl();
        return document.getText();
    }

    @RequiredReadAction
    @Override
    public int getTextLength() {
        VirtualFile file = getVirtualFile();
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assert document != null : file.getUrl();
        return document.getTextLength();
    }

    private static Exception wrapException(InvalidMirrorException e, VirtualFile file) {
        ClassFileDecompiler decompiler = ClassFileDecompilers.find(file);
        if (decompiler instanceof ClassFileDecompiler.Light) {
            PluginId pluginId = PluginManager.getPluginId(decompiler.getClass());
            if (pluginId != null) {
                return new PluginException(e, pluginId);
            }
        }

        return e;
    }

    @RequiredReadAction
    @Override
    public PsiFile getDecompiledPsiFile() {
        return (PsiFile)getMirror();
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor javaElementVisitor) {
            javaElementVisitor.visitJavaFile(this);
        }
        else {
            visitor.visitFile(this);
        }
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiFile:" + getName();
    }

    @Override
    @RequiredReadAction
    public final TextRange getTextRange() {
        return TextRange.create(0, getTextLength());
    }

    @Override
    @RequiredReadAction
    public final int getStartOffsetInParent() {
        return 0;
    }

    @Override
    @RequiredReadAction
    public final PsiElement findElementAt(int offset) {
        return getMirror().findElementAt(offset);
    }

    @Override
    @RequiredReadAction
    public PsiReference findReferenceAt(int offset) {
        return getMirror().findReferenceAt(offset);
    }

    @Override
    public final int getTextOffset() {
        return 0;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public char[] textToCharArray() {
        return getMirror().textToCharArray();
    }

    @Nonnull
    @RequiredReadAction
    public PsiClassHolderFileStub<?> getStub() {
        return (PsiClassHolderFileStub)getStubTree().getRoot();
    }

    @Override
    @RequiredReadAction
    public boolean processDeclarations(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
        ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
        if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
            PsiClass[] classes = getClasses();
            for (PsiClass aClass : classes) {
                if (!processor.execute(aClass, state)) {
                    return false;
                }
            }
        }
        return true;
    }

    @RequiredReadAction
    @Override
    @Nonnull
    public StubTree getStubTree() {
        getApplication().assertReadAccessAllowed();

        StubTree stubTree = SoftReference.dereference(myStub);
        if (stubTree != null) {
            return stubTree;
        }

        // build newStub out of lock to avoid deadlock
        StubTree newStubTree = (StubTree)StubTreeLoader.getInstance().readOrBuild(getProject(), getVirtualFile(), this);
        if (newStubTree == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No stub for class file in index: " + getVirtualFile().getPresentableUrl());
            }
            newStubTree = new StubTree(new PsiJavaFileStubImpl("corrupted_class_files", true));
        }

        synchronized (myStubLock) {
            stubTree = SoftReference.dereference(myStub);
            if (stubTree != null) {
                return stubTree;
            }

            stubTree = newStubTree;

            @SuppressWarnings("unchecked") PsiFileStubImpl<PsiFile> fileStub = (PsiFileStubImpl)stubTree.getRoot();
            fileStub.setPsi(this);

            myStub = new SoftReference<>(stubTree);
        }

        return stubTree;
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public StubbedSpine getStubbedSpine() {
        return getStubTree().getSpine();
    }

    @Override
    public boolean isContentsLoaded() {
        return myStub != null;
    }

    @Override
    @RequiredWriteAction
    public void onContentReload() {
        getApplication().assertWriteAccessAllowed();

        synchronized (myStubLock) {
            StubTree stubTree = SoftReference.dereference(myStub);
            myStub = null;
            if (stubTree != null) {
                //noinspection unchecked
                ((PsiFileStubImpl)stubTree.getRoot()).clearPsi("cls onContentReload");
            }
        }

        synchronized (myMirrorLock) {
            putUserData(CLS_DOCUMENT_LINK_KEY, null);
            myMirrorFileElement = null;
            myPackageStatement = null;
        }
    }

    @Override
    public void putInfo(@Nonnull Map<String, String> info) {
        PsiFileImpl.putInfo(this, info);
    }

    // default decompiler implementation

    @Nonnull
    public static CharSequence decompile(@Nonnull VirtualFile file) {
        PsiManager manager = PsiManager.getInstance(ProjectManager.getInstance().getDefaultProject());
        ClsFileImpl clsFile = new ClsFileImpl(new ClassFileViewProvider(manager, file), true);
        StringBuilder buffer = new StringBuilder();
        Application.get().runReadAction(() -> clsFile.appendMirrorText(buffer));
        return buffer;
    }

    @Nullable
    public static PsiJavaFileStub buildFileStub(@Nonnull VirtualFile file, @Nonnull byte[] bytes) throws ClsFormatException {
        try {
            if (ClassFileViewProvider.isInnerClass(file, bytes)) {
                return null;
            }

            ClassReader reader = new ClassReader(bytes);
            String className = file.getNameWithoutExtension();
            String internalName = reader.getClassName();
            boolean module = internalName.equals("module-info") && BitUtil.isSet(reader.getAccess(), Opcodes.ACC_MODULE);
            JavaSdkVersion jdkVersion = ClsParsingUtil.getJdkVersionByBytecode(reader.readUnsignedShort(6));
            LanguageLevel level = jdkVersion != null ? jdkVersion.getMaxLanguageLevel() : null;
            if (level != null && level.isAtLeast(LanguageLevel.JDK_11) && ClsParsingUtil.isPreviewLevel(reader.readUnsignedShort(4))) {
                level = ObjectUtil.notNull(level.getPreviewLevel(), LanguageLevel.HIGHEST);
            }

            if (module) {
                PsiJavaFileStub stub = new PsiJavaFileStubImpl(null, "", level, true);
                ModuleStubBuildingVisitor visitor = new ModuleStubBuildingVisitor(stub);
                reader.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES);
                if (visitor.getResult() != null) {
                    return stub;
                }
            }
            else {
                PsiJavaFileStub stub = new PsiJavaFileStubImpl(null, getPackageName(internalName), level, true);
                try {
                    FileContentPair source = new FileContentPair(file, reader);
                    StubBuildingVisitor<FileContentPair> visitor = new StubBuildingVisitor<>(source, STRATEGY, stub, 0, className);
                    reader.accept(
                        visitor,
                        EMPTY_ATTRIBUTES,
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.VISIT_LOCAL_VARIABLES
                    );
                    if (visitor.getResult() != null) {
                        return stub;
                    }
                }
                catch (OutOfOrderInnerClassException e) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(file.getPath());
                    }
                }
            }

            return null;
        }
        catch (ProcessCanceledException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new ClsFormatException(file.getPath() + ": " + e.getMessage(), e);
        }
    }

    private static String getPackageName(String internalName) {
        int p = internalName.lastIndexOf('/');
        return p > 0 ? internalName.substring(0, p).replace('/', '.') : "";
    }

    static class FileContentPair extends Pair<VirtualFile, ClassReader> {
        FileContentPair(@Nonnull VirtualFile file, @Nonnull ClassReader content) {
            super(file, content);
        }

        public @Nonnull ClassReader getContent() {
            return second;
        }

        @Override
        public String toString() {
            return first.toString();
        }
    }

    private static final InnerClassSourceStrategy<FileContentPair> STRATEGY = new InnerClassSourceStrategy<>() {
        @Override
        public @Nullable FileContentPair findInnerClass(String innerName, FileContentPair outerClass) {
            String baseName = outerClass.first.getNameWithoutExtension();
            VirtualFile dir = outerClass.first.getParent();
            assert dir != null : outerClass;
            VirtualFile innerClass = dir.findChild(baseName + '$' + innerName + ".class");
            if (innerClass != null) {
                try {
                    byte[] bytes = innerClass.contentsToByteArray(false);
                    return new FileContentPair(innerClass, new ClassReader(bytes));
                }
                catch (IOException ignored) {
                }
            }
            return null;
        }

        @Override
        public void accept(FileContentPair innerClass, StubBuildingVisitor<FileContentPair> visitor) {
            try {
                innerClass.second.accept(
                    visitor,
                    EMPTY_ATTRIBUTES,
                    ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.VISIT_LOCAL_VARIABLES
                );
            }
            catch (
                Exception e) {  // workaround for bug in skipping annotations when a first parameter of inner class is dropped (IDEA-204145)
                VirtualFile file = innerClass.first;
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.valueOf(file), e);
                }
                else {
                    LOG.info(file + ": " + e.getMessage());
                }
            }
        }
    };

    public static final Attribute[] EMPTY_ATTRIBUTES = new Attribute[0];
}