package com.intellij.java.coverage.view;

import com.intellij.java.coverage.JavaCoverageAnnotator;
import com.intellij.java.coverage.JavaCoverageSuite;
import com.intellij.java.coverage.PackageAnnotator;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.util.function.Computable;
import consulo.execution.coverage.CoverageSuite;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.execution.coverage.CoverageViewManager;
import consulo.execution.coverage.view.*;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * User: anna
 * Date: 1/5/12
 */
public class JavaCoverageViewExtension extends CoverageViewExtension {
    private final JavaCoverageAnnotator myAnnotator;

    public JavaCoverageViewExtension(
        JavaCoverageAnnotator annotator,
        Project project,
        CoverageSuitesBundle suitesBundle,
        CoverageViewManager.StateBean stateBean
    ) {
        super(project, suitesBundle, stateBean);
        myAnnotator = annotator;
    }

    @Override
    public String getSummaryForNode(AbstractTreeNode node) {
        final String coverageInformationString = myAnnotator
            .getPackageCoverageInformationString(
                (PsiJavaPackage) node.getValue(),
                null,
                getCoverageDataManager(),
                getStateBean().myFlattenPackages
            );
        return "Coverage Summary for Package \'" + node.toString() + "\': " + getNotCoveredMessage(coverageInformationString);
    }

    @Override
    @RequiredReadAction
    public String getSummaryForRootNode(AbstractTreeNode childNode) {
        final Object value = childNode.getValue();
        String coverageInformationString =
            myAnnotator.getPackageCoverageInformationString((PsiPackage) value, null, getCoverageDataManager());
        if (coverageInformationString == null) {
            if (!getCoverageViewManager().isReady()) {
                return "Loading...";
            }
            PackageAnnotator.PackageCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
            final Collection children = childNode.getChildren();
            for (Object child : children) {
                final Object childValue = ((CoverageListNode) child).getValue();
                if (childValue instanceof PsiPackage psiPackage) {
                    final PackageAnnotator.PackageCoverageInfo coverageInfo =
                        myAnnotator.getPackageCoverageInfo(psiPackage, getStateBean().myFlattenPackages);
                    if (coverageInfo != null) {
                        info = JavaCoverageAnnotator.merge(info, coverageInfo);
                    }
                }
                else {
                    final PackageAnnotator.ClassCoverageInfo classCoverageInfo = getClassCoverageInfo(((PsiClass) childValue));
                    if (classCoverageInfo != null) {
                        info.coveredClassCount += classCoverageInfo.coveredMethodCount > 0 ? 1 : 0;
                        info.totalClassCount++;

                        info.coveredMethodCount += classCoverageInfo.coveredMethodCount;
                        info.totalMethodCount += classCoverageInfo.totalMethodCount;

                        info.coveredLineCount += classCoverageInfo.partiallyCoveredLineCount + classCoverageInfo.fullyCoveredLineCount;
                        info.totalLineCount += classCoverageInfo.totalLineCount;
                    }
                }
            }
            coverageInformationString = JavaCoverageAnnotator.getCoverageInformationString(info, false);
        }
        return "Coverage Summary for \'all classes in scope\': " + getNotCoveredMessage(coverageInformationString);
    }

    private static String getNotCoveredMessage(String coverageInformationString) {
        if (coverageInformationString == null) {
            coverageInformationString = "not covered";
        }
        return coverageInformationString;
    }

    @Override
    public String getPercentage(int columnIndex, AbstractTreeNode node) {
        final Object value = node.getValue();
        if (value instanceof PsiClass psiClass) {

            //no coverage gathered
            if (psiClass.isInterface()) {
                return null;
            }

            final String qualifiedName = psiClass.getQualifiedName();
            if (columnIndex == 1) {
                return myAnnotator.getClassCoveredPercentage(qualifiedName);
            }
            else if (columnIndex == 2) {
                return myAnnotator.getClassMethodPercentage(qualifiedName);
            }

            return myAnnotator.getClassLinePercentage(qualifiedName);
        }
        if (value instanceof PsiPackage psiPackage) {
            final boolean flatten = getStateBean().myFlattenPackages;
            if (columnIndex == 1) {
                return myAnnotator.getPackageClassPercentage(psiPackage, flatten);
            }
            else if (columnIndex == 2) {
                return myAnnotator.getPackageMethodPercentage(psiPackage, flatten);
            }
            return myAnnotator.getPackageLinePercentage(psiPackage, flatten);
        }
        return null;
    }

    @Override
    public PsiElement getElementToSelect(Object object) {
        PsiElement psiElement = super.getElementToSelect(object);
        if (psiElement != null) {
            final PsiFile containingFile = psiElement.getContainingFile();
            if (containingFile instanceof PsiClassOwner classOwner) {
                final PsiClass[] classes = classOwner.getClasses();
                if (classes.length == 1) {
                    return classes[0];
                }
                for (PsiClass aClass : classes) {
                    if (PsiTreeUtil.isAncestor(aClass, psiElement, false)) {
                        return aClass;
                    }
                }
            }
        }
        return psiElement;
    }

    @Override
    public VirtualFile getVirtualFile(Object object) {
        if (object instanceof PsiPackage psiPackage) {
            final PsiDirectory[] directories = psiPackage.getDirectories();
            return directories.length > 0 ? directories[0].getVirtualFile() : null;
        }
        return super.getVirtualFile(object);
    }

    @Nullable
    @Override
    public PsiElement getParentElement(PsiElement element) {
        if (element instanceof PsiClass) {
            final PsiDirectory containingDirectory = element.getContainingFile().getContainingDirectory();
            return containingDirectory != null ? JavaDirectoryService.getInstance().getPackage(containingDirectory) : null;
        }
        return ((PsiPackage) element).getParentPackage();
    }

    @Override
    public AbstractTreeNode createRootNode() {
        return new CoverageListRootNode(
            getProject(),
            JavaPsiFacade.getInstance(getProject()).findPackage(""),
            getSuitesBundle(),
            getStateBean()
        );
    }

    @Override
    public List<AbstractTreeNode> createTopLevelNodes() {
        final List<AbstractTreeNode> topLevelNodes = new ArrayList<>();
        final LinkedHashSet<PsiJavaPackage> packages = new LinkedHashSet<>();
        final LinkedHashSet<PsiClass> classes = new LinkedHashSet<>();
        for (CoverageSuite suite : getSuitesBundle().getSuites()) {
            packages.addAll(((JavaCoverageSuite) suite).getCurrentSuitePackages(getProject()));
            classes.addAll(((JavaCoverageSuite) suite).getCurrentSuiteClasses(getProject()));
        }

        final Set<PsiPackage> packs = new HashSet<>();
        for (PsiPackage aPackage : packages) {
            final String qualifiedName = aPackage.getQualifiedName();
            for (PsiPackage psiPackage : packages) {
                if (psiPackage.getQualifiedName().startsWith(qualifiedName + ".")) {
                    packs.add(psiPackage);
                    break;
                }
            }
        }
        packages.removeAll(packs);

        for (PsiJavaPackage aPackage : packages) {
            final GlobalSearchScope searchScope = getSuitesBundle().getSearchScope(getProject());
            if (aPackage.getDirectories(searchScope).length == 0) {
                continue;
            }
            if (aPackage.getClasses(searchScope).length != 0) {
                final CoverageListNode node = new CoverageListNode(getProject(), aPackage, getSuitesBundle(), getStateBean());
                topLevelNodes.add(node);
            }
            collectSubPackages(topLevelNodes, aPackage, getSuitesBundle(), getStateBean());
        }

        for (PsiClass aClass : classes) {
            if (getClassCoverageInfo(aClass) == null) {
                continue;
            }
            topLevelNodes.add(new CoverageListNode(getProject(), aClass, getSuitesBundle(), getStateBean()));
        }
        return topLevelNodes;
    }

    private static void collectSubPackages(
        List<AbstractTreeNode> children,
        final PsiPackage rootPackage,
        final CoverageSuitesBundle data,
        final CoverageViewManager.StateBean stateBean
    ) {
        final GlobalSearchScope searchScope = data.getSearchScope(rootPackage.getProject());
        final Application application = Application.get();
        final PsiPackage[] subPackages =
            application.runReadAction((Computable<PsiPackage[]>) () -> rootPackage.getSubPackages(searchScope));
        for (final PsiPackage aPackage : subPackages) {
            final PsiDirectory[] directories =
                application.runReadAction((Computable<PsiDirectory[]>) () -> aPackage.getDirectories(searchScope));
            if (directories.length == 0 && !application.runReadAction(
                (Computable<Boolean>) () -> JavaPsiFacade.getInstance(aPackage.getProject())
                    .isPartOfPackagePrefix(aPackage.getQualifiedName())
            )) {
                continue;
            }
            if (application.runReadAction((Computable<Boolean>) () -> isInCoverageScope(aPackage, data))) {
                final CoverageListNode node = new CoverageListNode(rootPackage.getProject(), aPackage, data, stateBean);
                children.add(node);
            }
            else if (!stateBean.myFlattenPackages) {
                collectSubPackages(children, aPackage, data, stateBean);
            }
            if (stateBean.myFlattenPackages) {
                collectSubPackages(children, aPackage, data, stateBean);
            }
        }
    }


    @Override
    public List<AbstractTreeNode> getChildrenNodes(final AbstractTreeNode node) {
        List<AbstractTreeNode> children = new ArrayList<>();
        if (node instanceof CoverageListNode) {
            final Object val = node.getValue();
            if (val instanceof PsiClass) {
                return Collections.emptyList();
            }

            //append package classes
            if (val instanceof PsiJavaPackage javaPackage) {
                if (!getStateBean().myFlattenPackages) {
                    collectSubPackages(children, javaPackage, getSuitesBundle(), getStateBean());
                }
                Application application = Application.get();
                if (application.runReadAction((Computable<Boolean>) () -> isInCoverageScope(javaPackage, getSuitesBundle()))) {
                    final PsiClass[] classes = application.runReadAction(
                        (Computable<PsiClass[]>) () -> javaPackage.getClasses(getSuitesBundle().getSearchScope(node.getProject()))
                    );
                    for (PsiClass aClass : classes) {
                        if (!(node instanceof CoverageListRootNode) && getClassCoverageInfo(aClass) == null) {
                            continue;
                        }
                        children.add(new CoverageListNode(getProject(), aClass, getSuitesBundle(), getStateBean()));
                    }
                }
            }
            if (node instanceof CoverageListRootNode) {
                for (CoverageSuite suite : getSuitesBundle().getSuites()) {
                    final List<PsiClass> classes = ((JavaCoverageSuite) suite).getCurrentSuiteClasses(getProject());
                    for (PsiClass aClass : classes) {
                        children.add(new CoverageListNode(getProject(), aClass, getSuitesBundle(), getStateBean()));
                    }
                }
            }
            for (AbstractTreeNode childNode : children) {
                childNode.setParent(node);
            }
        }
        return children;
    }

    @Nullable
    private PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(final PsiClass aClass) {
        return myAnnotator.getClassCoverageInfo(aClass.getApplication().runReadAction(new Supplier<>() {
            public String get() {
                return aClass.getQualifiedName();
            }
        }));
    }

    @Override
    public ColumnInfo[] createColumnInfos() {
        return new ColumnInfo[]{
            new ElementColumnInfo(),
            new PercentageCoverageColumnInfo(1, "Class, %", getSuitesBundle(), getStateBean()),
            new PercentageCoverageColumnInfo(2, "Method, %", getSuitesBundle(), getStateBean()),
            new PercentageCoverageColumnInfo(3, "Line, %", getSuitesBundle(), getStateBean())
        };
    }

    private static boolean isInCoverageScope(PsiElement element, CoverageSuitesBundle suitesBundle) {
        if (element instanceof PsiPackage psiPackage) {
            final String qualifiedName = psiPackage.getQualifiedName();
            for (CoverageSuite suite : suitesBundle.getSuites()) {
                if (((JavaCoverageSuite) suite).isPackageFiltered(qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @RequiredReadAction
    public boolean canSelectInCoverageView(Object object) {
        final PsiFile psiFile = object instanceof VirtualFile ? PsiManager.getInstance(getProject()).findFile((VirtualFile) object) : null;
        if (psiFile instanceof PsiClassOwner classOwner) {
            final String packageName = classOwner.getPackageName();
            return isInCoverageScope(JavaPsiFacade.getInstance(getProject()).findPackage(packageName), getSuitesBundle());
        }
        return object instanceof PsiPackage psiPackage && isInCoverageScope(psiPackage, getSuitesBundle());
    }

    @Override
    public boolean supportFlattenPackages() {
        return true;
    }
}
