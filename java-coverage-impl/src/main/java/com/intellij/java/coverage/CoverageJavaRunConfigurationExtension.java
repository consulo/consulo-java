/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.java.coverage;

import com.intellij.coverage.listeners.CoverageListener;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.action.Location;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunnerSettings;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.coverage.*;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringElementListenerComposite;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.util.collection.ArrayUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers "Coverage" tab in Java run configurations
 */
@ExtensionImpl
public class CoverageJavaRunConfigurationExtension extends RunConfigurationExtension {
    @Override
    public void attachToProcess(
        @Nonnull RunConfigurationBase configuration,
        @Nonnull ProcessHandler handler,
        RunnerSettings runnerSettings
    ) {
        CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
    }

    @Override
    @Nullable
    public SettingsEditor createEditor(@Nonnull RunConfigurationBase configuration) {
        return new CoverageConfigurable(configuration);
    }

    @Override
    public String getEditorTitle() {
        return CoverageEngine.getEditorTitle();
    }

    @Nonnull
    @Override
    public String getSerializationId() {
        return "coverage";
    }

    @Override
    public void updateJavaParameters(RunConfigurationBase configuration, OwnJavaParameters params, RunnerSettings runnerSettings) {
        if (!isApplicableFor(configuration)) {
            return;
        }

        JavaCoverageEnabledConfiguration coverageConfig = JavaCoverageEnabledConfiguration.getFrom(configuration);
        //noinspection ConstantConditions
        coverageConfig.setCurrentCoverageSuite(null);
        CoverageRunner coverageRunner = coverageConfig.getCoverageRunner();
        if (runnerSettings instanceof CoverageRunnerData && coverageRunner != null) {
            CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
            coverageConfig.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(coverageConfig));
            coverageConfig.appendCoverageArgument(params);

            Sdk jdk = params.getJdk();
            if (jdk != null && JavaSdkTypeUtil.isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_7)
                && coverageRunner instanceof JavaCoverageRunner javaCoverageRunner && !javaCoverageRunner.isJdk7Compatible()) {
                Notifications.Bus.notify(new Notification(
                    NotificationGroup.balloonGroup("Coverage"),
                    "Coverage instrumentation is not fully compatible with JDK 7",
                    coverageRunner.getPresentableName() +
                        " coverage instrumentation can lead to java.lang.VerifyError errors with JDK 7. " +
                        "If so, please try IDEA coverage runner.",
                    NotificationType.WARNING
                ));
            }
        }
    }

    @Override
    public void readExternal(@Nonnull RunConfigurationBase runConfiguration, @Nonnull Element element) throws InvalidDataException {
        if (!isApplicableFor(runConfiguration)) {
            return;
        }

        //noinspection ConstantConditions
        JavaCoverageEnabledConfiguration.getFrom(runConfiguration).readExternal(element);
    }

    @Override
    public void writeExternal(@Nonnull RunConfigurationBase runConfiguration, @Nonnull Element element) throws WriteExternalException {
        if (!isApplicableFor(runConfiguration)) {
            return;
        }
        //noinspection ConstantConditions
        JavaCoverageEnabledConfiguration.getFrom(runConfiguration).writeExternal(element);
    }

    @Override
    public void extendCreatedConfiguration(@Nonnull RunConfigurationBase runJavaConfiguration, @Nonnull Location location) {
        JavaCoverageEnabledConfiguration coverageEnabledConfiguration =
            JavaCoverageEnabledConfiguration.getFrom(runJavaConfiguration);
        assert coverageEnabledConfiguration != null;
        if (runJavaConfiguration instanceof CommonJavaRunConfigurationParameters commonJavaRunConfigurationParameters) {
            coverageEnabledConfiguration.setUpCoverageFilters(
                commonJavaRunConfigurationParameters.getRunClass(),
                commonJavaRunConfigurationParameters.getPackage()
            );
        }
    }

    @Override
    public void cleanUserData(RunConfigurationBase runConfiguration) {
        runConfiguration.putCopyableUserData(CoverageEnabledConfiguration.COVERAGE_KEY, null);
    }

    @Override
    public void validateConfiguration(@Nonnull RunConfigurationBase runJavaConfiguration, boolean isExecution)
        throws RuntimeConfigurationException {
    }

    @Override
    public RefactoringElementListener wrapElementListener(
        PsiElement element,
        RunConfigurationBase configuration,
        RefactoringElementListener listener
    ) {
        if (!isApplicableFor(configuration)) {
            return listener;
        }
        JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(configuration);
        if (coverageEnabledConfiguration != null) {
            Project project = configuration.getProject();
            ClassFilter[] patterns = coverageEnabledConfiguration.getCoveragePatterns();
            String[] filters = getFilters(coverageEnabledConfiguration);
            if (patterns != null) {
                assert filters != null;
                if (element instanceof PsiClass psiClass) {
                    int idx = ArrayUtil.find(filters, psiClass.getQualifiedName());
                    if (idx > -1) {
                        RefactoringListeners.Accessor<PsiClass> accessor = new MyClassAccessor(project, patterns, idx, filters);
                        RefactoringElementListener classListener = RefactoringListeners.getClassOrPackageListener(element, accessor);
                        if (classListener != null) {
                            listener = appendListener(listener, classListener);
                        }
                    }
                }
                else if (element instanceof PsiJavaPackage javaPackage) {
                    String qualifiedName = javaPackage.getQualifiedName();
                    for (int i = 0, filtersLength = filters.length; i < filtersLength; i++) {
                        if (filters[i].startsWith(qualifiedName + ".")) {
                            RefactoringElementListener packageListener;
                            if (filters[i].endsWith("*")) {
                                packageListener = RefactoringListeners.getListener(
                                    (PsiJavaPackage) element,
                                    new MyPackageAccessor(project, patterns, i, filters)
                                );
                            }
                            else {
                                packageListener = RefactoringListeners.getClassOrPackageListener(
                                    element,
                                    new MyClassAccessor(project, patterns, i, filters)
                                );
                            }
                            if (packageListener != null) {
                                listener = appendListener(listener, packageListener);
                            }
                        }
                    }
                }
            }
        }
        return listener;
    }

    @Nullable
    private static String[] getFilters(JavaCoverageEnabledConfiguration coverageEnabledConfiguration) {
        ClassFilter[] patterns = coverageEnabledConfiguration.getCoveragePatterns();
        if (patterns != null) {
            List<String> filters = new ArrayList<>();
            for (ClassFilter classFilter : patterns) {
                filters.add(classFilter.getPattern());
            }
            return ArrayUtil.toStringArray(filters);
        }
        return null;
    }

    private static RefactoringElementListener appendListener(
        RefactoringElementListener listener,
        RefactoringElementListener classOrPackageListener
    ) {
        if (listener == null) {
            listener = new RefactoringElementListenerComposite();
        }
        else if (!(listener instanceof RefactoringElementListenerComposite)) {
            RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
            composite.addListener(listener);
            listener = composite;
        }
        ((RefactoringElementListenerComposite) listener).addListener(classOrPackageListener);
        return listener;
    }

    @Override
    public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
        if (listener instanceof CoverageListener) {
            if (!(runnerSettings instanceof CoverageRunnerData)) {
                return true;
            }
            CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(configuration);
            return !(coverageEnabledConfiguration.getCoverageRunner() instanceof IDEACoverageRunner)
                || !(coverageEnabledConfiguration.isTrackPerTestCoverage() && !coverageEnabledConfiguration.isSampling());
        }
        return false;
    }

    @Override
    protected boolean isApplicableFor(@Nonnull RunConfigurationBase configuration) {
        return CoverageEnabledConfiguration.isApplicableTo(configuration);
    }

    private static class MyPackageAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiJavaPackage> {


        private MyPackageAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
            super(project, patterns, idx, filters);
        }

        @Override
        public void setName(String qualifiedName) {
            super.setName(qualifiedName + ".*");
        }

        @Override
        public PsiJavaPackage getPsiElement() {
            String name = getName();
            return JavaPsiFacade.getInstance(getProject()).findPackage(name.substring(0, name.length() - ".*".length()));
        }

        @Override
        public void setPsiElement(PsiJavaPackage psiElement) {
            setName(psiElement.getQualifiedName());
        }
    }

    private static class MyClassAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiClass> {

        private MyClassAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
            super(project, patterns, idx, filters);
        }

        @Override
        public PsiClass getPsiElement() {
            return JavaPsiFacade.getInstance(getProject()).findClass(getName(), GlobalSearchScope.allScope(getProject()));
        }

        @Override
        public void setPsiElement(PsiClass psiElement) {
            setName(psiElement.getQualifiedName());
        }
    }

    private static class MyAccessor {
        private final Project myProject;
        private final ClassFilter[] myPatterns;
        private final int myIdx;
        private final String[] myFilters;

        private MyAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
            myProject = project;
            myPatterns = patterns;
            myIdx = idx;
            myFilters = filters;
        }

        public void setName(String qName) {
            myPatterns[myIdx] = new ClassFilter(qName);
        }

        public String getName() {
            return myFilters[myIdx];
        }

        public Project getProject() {
            return myProject;
        }
    }
}