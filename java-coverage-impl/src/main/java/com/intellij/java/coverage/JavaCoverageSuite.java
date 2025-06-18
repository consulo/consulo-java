package com.intellij.java.coverage;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.execution.coverage.*;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author ven
 */
public class JavaCoverageSuite extends BaseCoverageSuite {
    private static final Logger LOG = Logger.getInstance(JavaCoverageSuite.class);

    private String[] myFilters;
    private String mySuiteToMerge;

    private static final String FILTER = "FILTER";
    private static final String MERGE_SUITE = "MERGE_SUITE";
    private static final String COVERAGE_RUNNER = "RUNNER";
    private final CoverageEngine myCoverageEngine;

    //read external only
    public JavaCoverageSuite(@Nonnull JavaCoverageEngine coverageSupportProvider) {
        super();
        myCoverageEngine = coverageSupportProvider;
    }

    public JavaCoverageSuite(
        String name,
        CoverageFileProvider coverageDataFileProvider,
        String[] filters,
        long lastCoverageTimeStamp,
        boolean coverageByTestEnabled,
        boolean tracingEnabled,
        boolean trackTestFolders,
        CoverageRunner coverageRunner,
        @Nonnull JavaCoverageEngine coverageSupportProvider,
        Project project
    ) {
        super(name, coverageDataFileProvider, lastCoverageTimeStamp, coverageByTestEnabled,
            tracingEnabled, trackTestFolders,
            coverageRunner != null ? coverageRunner : CoverageRunner.getInstance(IDEACoverageRunner.class), project
        );

        myFilters = filters;
        myCoverageEngine = coverageSupportProvider;
    }

    @Nonnull
    public String[] getFilteredPackageNames() {
        if (myFilters == null || myFilters.length == 0) {
            return ArrayUtil.EMPTY_STRING_ARRAY;
        }
        List<String> result = new ArrayList<>();
        for (String filter : myFilters) {
            if (filter.equals("*")) {
                result.add(""); //default package
            }
            else if (filter.endsWith(".*")) {
                result.add(filter.substring(0, filter.length() - 2));
            }
        }
        return ArrayUtil.toStringArray(result);
    }

    @Nonnull
    public String[] getFilteredClassNames() {
        if (myFilters == null) {
            return ArrayUtil.EMPTY_STRING_ARRAY;
        }
        List<String> result = new ArrayList<>();
        for (String filter : myFilters) {
            if (!filter.equals("*") && !filter.endsWith(".*")) {
                result.add(filter);
            }
        }
        return ArrayUtil.toStringArray(result);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);

        // filters
        List children = element.getChildren(FILTER);
        List<String> filters = new ArrayList<>();
        //noinspection unchecked
        for (Element child : ((Iterable<Element>) children)) {
            filters.add(child.getValue());
        }
        myFilters = filters.isEmpty() ? null : ArrayUtil.toStringArray(filters);

        // suite to merge
        mySuiteToMerge = element.getAttributeValue(MERGE_SUITE);

        if (getRunner() == null) {
            setRunner(CoverageRunner.getInstance(IDEACoverageRunner.class)); //default
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        if (mySuiteToMerge != null) {
            element.setAttribute(MERGE_SUITE, mySuiteToMerge);
        }
        if (myFilters != null) {
            for (String filter : myFilters) {
                Element filterElement = new Element(FILTER);
                filterElement.setText(filter);
                element.addContent(filterElement);
            }
        }
        CoverageRunner coverageRunner = getRunner();
        element.setAttribute(COVERAGE_RUNNER, coverageRunner != null ? coverageRunner.getId() : "emma");
    }

    @Nullable
    @Override
    public ProjectData getCoverageData(CoverageDataManager coverageDataManager) {
        ProjectData data = getCoverageData();
        if (data != null) {
            return data;
        }
        ProjectData map = loadProjectInfo();
        if (mySuiteToMerge != null) {
            JavaCoverageSuite toMerge = null;
            CoverageSuite[] suites = coverageDataManager.getSuites();
            for (CoverageSuite suite : suites) {
                if (Comparing.strEqual(suite.getPresentableName(), mySuiteToMerge)) {
                    if (!Comparing.strEqual(((JavaCoverageSuite) suite).getSuiteToMerge(), getPresentableName())) {
                        toMerge = (JavaCoverageSuite) suite;
                    }
                    break;
                }
            }
            if (toMerge != null) {
                ProjectData projectInfo = toMerge.getCoverageData(coverageDataManager);
                if (map != null) {
                    map.merge(projectInfo);
                }
                else {
                    map = projectInfo;
                }
            }
        }
        setCoverageData(map);
        return map;
    }

    @Nonnull
    @Override
    public CoverageEngine getCoverageEngine() {
        return myCoverageEngine;
    }

    @Nullable
    public String getSuiteToMerge() {
        return mySuiteToMerge;
    }

    public boolean isClassFiltered(String classFQName) {
        for (String className : getFilteredClassNames()) {
            if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
                return true;
            }
        }
        return false;
    }

    public boolean isPackageFiltered(String packageFQName) {
        String[] filteredPackageNames = getFilteredPackageNames();
        for (String packName : filteredPackageNames) {
            if (packName.equals(packageFQName) || packageFQName.startsWith(packName) && packageFQName.charAt(packName.length()) == '.') {
                return true;
            }
        }
        return filteredPackageNames.length == 0 && getFilteredClassNames().length == 0;
    }

    public @Nonnull
    List<PsiJavaPackage> getCurrentSuitePackages(Project project) {
        List<PsiJavaPackage> packages = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        String[] filters = getFilteredPackageNames();
        if (filters.length == 0) {
            if (getFilteredClassNames().length > 0) {
                return Collections.emptyList();
            }

            PsiJavaPackage defaultPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage("");
            if (defaultPackage != null) {
                packages.add(defaultPackage);
            }
        }
        else {
            List<String> nonInherited = new ArrayList<>();
            for (String filter : filters) {
                if (!isSubPackage(filters, filter)) {
                    nonInherited.add(filter);
                }
            }

            for (String filter : nonInherited) {
                PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(filter);
                if (psiPackage != null) {
                    packages.add(psiPackage);
                }
            }
        }

        return packages;
    }

    private static boolean isSubPackage(String[] filters, String filter) {
        for (String supPackageFilter : filters) {
            if (filter.startsWith(supPackageFilter + ".")) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public List<PsiClass> getCurrentSuiteClasses(Project project) {
        List<PsiClass> classes = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        String[] classNames = getFilteredClassNames();
        if (classNames.length > 0) {
            for (String className : classNames) {
                PsiClass aClass =
                    project.getApplication().runReadAction((Supplier<PsiClass>) () -> JavaPsiFacade.getInstance(psiManager.getProject())
                        .findClass(className.replace("$", "."), GlobalSearchScope.allScope(project)));
                if (aClass != null) {
                    classes.add(aClass);
                }
            }
        }

        return classes;
    }
}
