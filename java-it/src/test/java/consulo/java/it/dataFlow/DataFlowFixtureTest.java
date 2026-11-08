/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.it.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowInspectionStateBase;
import com.intellij.java.impl.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.application.ReadAction;
import consulo.application.WriteAction;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.document.util.TextRange;
import consulo.it.AllowLogError;
import consulo.it.HeadlessModules;
import consulo.it.HeadlessProjectExtension;
import consulo.it.HeadlessProjects;
import consulo.java.impl.module.extension.JavaMutableModuleExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.impl.internal.inspection.ProblemsHolderImpl;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link DataFlowInspection} over the fixtures of {@code plugin/src/test/resources/inspection/dataFlow/fixture} and compares
 * what it reports with the golden files of {@code dataFlow/} (see {@link #check}).
 * <p>
 * Each fixture is a project of its own: one module with the JDK running the test as its SDK and the nullability annotations
 * ({@code javax.annotation.Nonnull/Nullable}, {@code org.jetbrains.annotations.Nullable/NotNull/Contract}) as sources next to the fixture.
 *
 * @author VISTALL
 */
@ExtendWith(HeadlessProjectExtension.class)
// the JDK of a headless application has no plugin directory with jdk-annotations.jar
@AllowLogError("com.intellij.java.impl.openapi.projectRoots.impl.DefaultJavaSdkTypeImpl")
public class DataFlowFixtureTest {
    private static final Pattern MARKUP = Pattern.compile("<(warning|weak_warning|error)(?: descr=\"([^\"]*)\")?>|</(warning|weak_warning|error)>");

    private static final String[][] ANNOTATIONS = {
        {"org/jetbrains/annotations/NotNull.java",
            "package org.jetbrains.annotations; import java.lang.annotation.*; @Documented @Retention(RetentionPolicy.CLASS) "
                + "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) "
                + "public @interface NotNull { String value() default \"\"; }"},
        {"org/jetbrains/annotations/Nullable.java",
            "package org.jetbrains.annotations; import java.lang.annotation.*; @Documented @Retention(RetentionPolicy.CLASS) "
                + "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) "
                + "public @interface Nullable { String value() default \"\"; }"},
        // JSR-305, which the fixtures are annotated with
        {"javax/annotation/Nonnull.java",
            "package javax.annotation; import java.lang.annotation.*; @Documented @Retention(RetentionPolicy.RUNTIME) "
                + "public @interface Nonnull { }"},
        {"javax/annotation/Nullable.java",
            "package javax.annotation; import java.lang.annotation.*; @Documented @Retention(RetentionPolicy.RUNTIME) "
                + "public @interface Nullable { }"},
        {"org/jetbrains/annotations/Contract.java",
            "package org.jetbrains.annotations; import java.lang.annotation.*; @Documented @Retention(RetentionPolicy.CLASS) "
                + "@Target({ElementType.METHOD, ElementType.CONSTRUCTOR}) "
                + "public @interface Contract { String value() default \"\"; boolean pure() default false; String mutates() default \"\"; }"},
    };

    static Stream<String> fixtures() throws Exception {
        try (Stream<Path> list = Files.list(fixtureDirectory())) {
            return list.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".java")).sorted().toList().stream();
        }
    }

    /**
     * The fixtures carry the markup of the JetBrains tests from 2013, which also depended on per-test setup not kept with them
     * (custom annotations, helper classes): the expected result is therefore what the engine reports today, in
     * {@code dataFlow/<fixture>.txt} - one {@code start:end message} line per problem. A missing file is written from the
     * current result, as is every file with {@code -Dgolden.update=true}.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    public void check(String fileName, HeadlessProjects projects) throws Exception {
        String marked = Files.readString(fixtureDirectory().resolve(fileName), StandardCharsets.UTF_8).replace("\r\n", "\n");

        String text = stripMarkup(marked, new ArrayList<>());

        PsiFile file = openSource(projects, fileName, text);

        List<String> actual = ReadAction.compute(() -> runInspection(file));
        actual.sort(null);

        Path golden = goldenDirectory().resolve(fileName.substring(0, fileName.length() - ".java".length()) + ".txt");
        if (!Files.exists(golden) || Boolean.getBoolean("golden.update")) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, String.join("\n", actual) + (actual.isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
            return;
        }
        List<String> expected = Files.readAllLines(golden, StandardCharsets.UTF_8);
        assertThat(actual).as(fileName).isEqualTo(expected);
    }

    private static Path goldenDirectory() {
        return Path.of("").toAbsolutePath().resolve("src/test/resources/dataFlow");
    }

    /**
     * @return the text without the markup; {@code expected} gets {@code start:end descr} of every marked range
     */
    private static String stripMarkup(String marked, List<String> expected) {
        StringBuilder text = new StringBuilder();
        List<int[]> starts = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        Matcher matcher = MARKUP.matcher(marked);
        int last = 0;
        while (matcher.find()) {
            text.append(marked, last, matcher.start());
            last = matcher.end();
            if (matcher.group(1) != null) {
                starts.add(new int[]{text.length()});
                descriptions.add(matcher.group(2) == null ? "" : unescape(matcher.group(2)));
            }
            else {
                int[] start = starts.removeLast();
                String description = descriptions.removeLast();
                expected.add(start[0] + ":" + text.length() + " " + description);
            }
        }
        text.append(marked.substring(last));
        return text.toString();
    }

    private static String unescape(String description) {
        return description.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    private static List<String> runInspection(PsiFile file) {
        DataFlowInspection inspection = new DataFlowInspection();
        DataFlowInspectionStateBase state = (DataFlowInspectionStateBase) inspection.createStateProvider().getState();
        state.SUGGEST_NULLABLE_ANNOTATIONS = true;

        InspectionManager manager = InspectionManager.getInstance(file.getProject());
        ProblemsHolder holder = new ProblemsHolderImpl(manager, file, false);
        LocalInspectionToolSession session = new LocalInspectionToolSession(file, 0, file.getTextLength());
        PsiElementVisitor visitor = inspection.buildVisitor(holder, false, session, state);
        file.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                element.accept(visitor);
                super.visitElement(element);
            }
        });

        List<String> actual = new ArrayList<>();
        for (ProblemDescriptor descriptor : holder.getResults()) {
            PsiElement element = descriptor.getPsiElement();
            TextRange inElement = descriptor.getTextRangeInElement();
            TextRange range = inElement == null ? element.getTextRange() : inElement.shiftRight(element.getTextRange().getStartOffset());
            actual.add(range.getStartOffset() + ":" + range.getEndOffset() + " " + render(String.valueOf(descriptor.getDescriptionTemplate()), range.substring(file.getText())));
        }
        return actual;
    }

    /**
     * The message as shown: {@code #ref} is the reported code, {@code #loc} the location (empty here), without HTML
     */
    private static String render(String template, String reference) {
        return template.replace("#ref", reference).replace(" #loc", "").replace("#loc", "").replaceAll("<[^>]+>", "").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&amp;", "&");
    }

    private static PsiFile openSource(HeadlessProjects projects, String fileName, String text) throws Exception {
        Path directory = projects.newDirectory();
        Path srcDirectory = Files.createDirectories(directory.resolve("src"));
        Files.writeString(srcDirectory.resolve(fileName), text, StandardCharsets.UTF_8);
        for (String[] annotation : ANNOTATIONS) {
            Path path = srcDirectory.resolve(annotation[0]);
            Files.createDirectories(path.getParent());
            Files.writeString(path, annotation[1], StandardCharsets.UTF_8);
        }

        Project project = projects.open(directory);

        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertThat(root).isNotNull();
        VirtualFile srcRoot = root.findChild("src");
        assertThat(srcRoot).isNotNull();

        Module module = HeadlessModules.createModule(project, "main", root);
        HeadlessModules.addSourceRoot(module, srcRoot);

        WriteAction.run(() -> {
            Sdk jdk = JavaSdkType.getDefaultJavaSdkType().createJdk("jdk", System.getProperty("java.home"));
            SdkTable.getInstance().addSdk(jdk);

            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            JavaMutableModuleExtensionImpl java = rootModel.getExtensionWithoutCheck("java");
            assertThat(java).as("java extension").isNotNull();
            java.setEnabled(true);
            java.getInheritableSdk().set(null, jdk);
            rootModel.commit();
        });

        HeadlessProjects.awaitIdle(project);

        VirtualFile file = srcRoot.findChild(fileName);
        assertThat(file).isNotNull();
        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
        assertThat(psiFile).isNotNull();
        return psiFile;
    }

    private static Path fixtureDirectory() {
        // surefire runs with the module directory as the working directory
        Path directory = Path.of("").toAbsolutePath().getParent().resolve("plugin/src/test/resources/inspection/dataFlow/fixture");
        assertThat(directory).isDirectory();
        return directory;
    }
}
