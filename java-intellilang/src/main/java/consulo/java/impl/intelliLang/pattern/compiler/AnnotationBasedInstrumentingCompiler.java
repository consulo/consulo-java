/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package consulo.java.impl.intelliLang.pattern.compiler;

import com.intellij.java.compiler.impl.PsiClassWriter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.compiler.ClassInstrumentingCompiler;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.compiler.ValidityState;
import consulo.compiler.scope.CompileScope;
import consulo.content.bundle.Sdk;
import consulo.internal.org.objectweb.asm.ClassReader;
import consulo.internal.org.objectweb.asm.ClassWriter;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Based on NotNullVerifyingCompiler, kindly provided by JetBrains for reference.
 */
public abstract class AnnotationBasedInstrumentingCompiler implements ClassInstrumentingCompiler {
    private static final Logger LOG = Logger.getInstance("org.intellij.lang.pattern.compiler.AnnotationBasedInstrumentingCompiler");

    public AnnotationBasedInstrumentingCompiler() {
    }

    @Override
    public ProcessingItem[] getProcessingItems(CompileContext context) {
        if (!isEnabled()) {
            return ProcessingItem.EMPTY_ARRAY;
        }

        Project project = context.getProject();
        Set<InstrumentationItem> result = new HashSet<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(context.getProject());

        DumbService.getInstance(project).waitForSmartMode();

        Application.get().runReadAction(() -> {
            String[] names = getAnnotationNames(project);
            for (String name : names) {
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

                PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
                if (psiClass == null) {
                    context.newError(LocalizeValue.localizeTODO("Cannot find class " + name)).add();
                    continue;
                }

                // wow, this is a sweet trick... ;)
                searchHelper.processAllFilesWithWord(
                    StringUtil.getShortName(name),
                    scope,
                    psiFile -> {
                        if (JavaLanguage.INSTANCE == psiFile.getLanguage()
                            && psiFile.getVirtualFile() != null
                            && psiFile instanceof PsiJavaFile javaFile) {
                            addClassFiles(javaFile, result, project);
                        }
                        return true;
                    },
                    true
                );
            }
        });
        return result.toArray(new ProcessingItem[result.size()]);
    }

    @RequiredReadAction
    private static void addClassFiles(PsiJavaFile srcFile, Set<InstrumentationItem> result, Project project) {
        VirtualFile sourceFile = srcFile.getVirtualFile();
        assert sourceFile != null;

        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        Module module = index.getModuleForFile(sourceFile);
        if (module != null) {
            Sdk jdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
            boolean jdk6 = jdk != null && JavaSdkTypeUtil.isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_6);

            ModuleCompilerPathsManager compilerPathsManager = ModuleCompilerPathsManager.getInstance(module);
            VirtualFile compilerOutputPath = compilerPathsManager.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
            if (compilerOutputPath != null) {
                String packageName = srcFile.getPackageName();
                VirtualFile packageDir = packageName.length() > 0
                    ? compilerOutputPath.findFileByRelativePath(packageName.replace('.', '/'))
                    : compilerOutputPath;

                if (packageDir != null && packageDir.isDirectory()) {
                    PsiClass[] classes = srcFile.getClasses();
                    for (VirtualFile classFile : packageDir.getChildren()) {
                        if (classFile.isDirectory() || !"class".equals(classFile.getExtension())) {
                            // no point in looking at directories or non-class files
                            continue;
                        }
                        String name = classFile.getName();
                        for (PsiClass clazz : classes) {
                            String className = clazz.getName();
                            if (className != null && name.startsWith(className)) {
                                result.add(new InstrumentationItem(classFile, jdk6));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
        ProgressIndicator progressIndicator = context.getProgressIndicator();
        progressIndicator.setText(getProgressMessage());

        Project project = context.getProject();
        List<ProcessingItem> result = new ArrayList<>(items.length);
        Application.get().runReadAction(() -> {
            int filesProcessed = 0;

            for (ProcessingItem pi : items) {
                InstrumentationItem item = ((InstrumentationItem) pi);
                VirtualFile classFile = item.getClassFile();

                try {
                    byte[] bytes = classFile.contentsToByteArray();
                    ClassReader classreader;
                    try {
                        classreader = new ClassReader(bytes, 0, bytes.length);
                    }
                    catch (Exception e) {
                        LOG.debug("ASM failed to read class file <" + classFile.getPresentableUrl() + ">", e);
                        continue;
                    }

                    ClassWriter classwriter = new PsiClassWriter(project, item.isJDK6());
                    Instrumenter instrumenter = createInstrumenter(classwriter);

                    classreader.accept(instrumenter, 0);

                    if (instrumenter.instrumented()) {
                        // only dump the class if it has actually been instrumented
                        FileOutputStream out = new FileOutputStream(classFile.getPath());
                        try {
                            out.write(classwriter.toByteArray());
                        }
                        finally {
                            out.close();
                        }
                    }

                    result.add(item);
                    progressIndicator.setFraction(++filesProcessed / (double) items.length);
                }
                catch (InstrumentationException | IOException e) {
                    context.newError(LocalizeValue.localizeTODO("[" + getDescription() + "]: " + e.getLocalizedMessage())).add();
                }
            }
        });
        return result.toArray(new ProcessingItem[result.size()]);
    }

    protected abstract boolean isEnabled();

    protected abstract String[] getAnnotationNames(Project project);

    protected abstract Instrumenter createInstrumenter(ClassWriter classwriter);

    protected abstract String getProgressMessage();

    @Override
    public boolean validateConfiguration(CompileScope compilescope) {
        return true;
    }

    @Nullable
    @Override
    public ValidityState createValidityState(DataInput dataInputStream) throws IOException {
//        return TimestampValidityState.load(dataInputStream);
        return null;
    }
}
