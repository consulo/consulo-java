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
import consulo.execution.coverage.CoverageDataManager;
import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.execution.coverage.CoverageRunner;
import consulo.execution.coverage.CoverageRunnerData;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringElementListenerComposite;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationService;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers "Coverage" tab in Java run configurations
 */
@ExtensionImpl
public class CoverageJavaRunConfigurationExtension extends RunConfigurationExtension {
    @Override
    public void attachToProcess(
        RunConfigurationBase configuration,
        ProcessHandler handler,
        RunnerSettings runnerSettings
    ) {
        CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
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
                NotificationService.getInstance()
                    .newWarn(NotificationGroup.balloonGroup("Coverage"))
                    .title(LocalizeValue.localizeTODO("Coverage instrumentation is not fully compatible with JDK 7"))
                    .content(LocalizeValue.localizeTODO(coverageRunner.getPresentableName() +
                        " coverage instrumentation can lead to java.lang.VerifyError errors with JDK 7. " +
                        "If so, please try IDEA coverage runner."))
                    .notify(configuration.getProject());
            }
        }
    }

    @Override
    public void extendCreatedConfiguration(RunConfigurationBase runJavaConfiguration, Location location) {
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
    public void validateConfiguration(RunConfigurationBase runJavaConfiguration, boolean isExecution)
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
                        RefactoringListeners.Accessor<PsiClass> accessor = new MyClassAccessor(project, coverageEnabledConfiguration, patterns, idx, filters);
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
                                    new MyPackageAccessor(project, coverageEnabledConfiguration, patterns, i, filters)
                                );
                            }
                            else {
                                packageListener = RefactoringListeners.getClassOrPackageListener(
                                    element,
                                    new MyClassAccessor(project, coverageEnabledConfiguration, patterns, i, filters)
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
    protected boolean isApplicableFor(RunConfigurationBase configuration) {
        return CoverageEnabledConfiguration.isApplicableTo(configuration);
    }

    private static class MyPackageAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiJavaPackage> {


        private MyPackageAccessor(Project project, JavaCoverageEnabledConfiguration configuration, ClassFilter[] patterns, int idx, String[] filters) {
            super(project, configuration, patterns, idx, filters);
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

        private MyClassAccessor(Project project, JavaCoverageEnabledConfiguration configuration, ClassFilter[] patterns, int idx, String[] filters) {
            super(project, configuration, patterns, idx, filters);
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
        private final JavaCoverageEnabledConfiguration myConfiguration;
        private final ClassFilter[] myPatterns;
        private final int myIdx;
        private final String[] myFilters;

        private MyAccessor(Project project, JavaCoverageEnabledConfiguration configuration, ClassFilter[] patterns, int idx, String[] filters) {
            myProject = project;
            myConfiguration = configuration;
            myPatterns = patterns;
            myIdx = idx;
            myFilters = filters;
        }

        public void setName(String qName) {
            myPatterns[myIdx] = new ClassFilter(qName);
            myConfiguration.setCoveragePatterns(myPatterns);
        }

        public String getName() {
            return myFilters[myIdx];
        }

        public Project getProject() {
            return myProject;
        }
    }
}