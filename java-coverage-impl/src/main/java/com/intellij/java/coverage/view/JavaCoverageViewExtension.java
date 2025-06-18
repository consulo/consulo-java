package com.intellij.java.coverage.view;

import com.intellij.java.coverage.JavaCoverageAnnotator;
import com.intellij.java.coverage.JavaCoverageSuite;
import com.intellij.java.coverage.PackageAnnotator;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.execution.coverage.CoverageSuite;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.execution.coverage.CoverageViewManager;
import consulo.execution.coverage.view.*;
import consulo.java.coverage.localize.JavaCoverageLocalize;
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
 * @author anna
 * @since 2012-01-05
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
        String coverageInformationString = myAnnotator.getPackageCoverageInformationString(
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
        Object value = childNode.getValue();
        String coverageInformationString =
            myAnnotator.getPackageCoverageInformationString((PsiPackage) value, null, getCoverageDataManager());
        if (coverageInformationString == null) {
            if (!getCoverageViewManager().isReady()) {
                return "Loading...";
            }
            PackageAnnotator.PackageCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
            Collection children = childNode.getChildren();
            for (Object child : children) {
                Object childValue = ((CoverageListNode) child).getValue();
                if (childValue instanceof PsiPackage psiPackage) {
                    PackageAnnotator.PackageCoverageInfo coverageInfo =
                        myAnnotator.getPackageCoverageInfo(psiPackage, getStateBean().myFlattenPackages);
                    if (coverageInfo != null) {
                        info = JavaCoverageAnnotator.merge(info, coverageInfo);
                    }
                }
                else {
                    PackageAnnotator.ClassCoverageInfo classCoverageInfo = getClassCoverageInfo(((PsiClass) childValue));
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
        Object value = node.getValue();
        if (value instanceof PsiClass psiClass) {

            //no coverage gathered
            if (psiClass.isInterface()) {
                return null;
            }

            String qualifiedName = psiClass.getQualifiedName();
            if (columnIndex == 1) {
                return myAnnotator.getClassCoveredPercentage(qualifiedName);
            }
            else if (columnIndex == 2) {
                return myAnnotator.getClassMethodPercentage(qualifiedName);
            }

            return myAnnotator.getClassLinePercentage(qualifiedName);
        }
        if (value instanceof PsiPackage psiPackage) {
            boolean flatten = getStateBean().myFlattenPackages;
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
    @RequiredReadAction
    public PsiElement getElementToSelect(Object object) {
        PsiElement psiElement = super.getElementToSelect(object);
        if (psiElement != null) {
            PsiFile containingFile = psiElement.getContainingFile();
            if (containingFile instanceof PsiClassOwner classOwner) {
                PsiClass[] classes = classOwner.getClasses();
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
            PsiDirectory[] directories = psiPackage.getDirectories();
            return directories.length > 0 ? directories[0].getVirtualFile() : null;
        }
        return super.getVirtualFile(object);
    }

    @Nullable
    @Override
    public PsiElement getParentElement(PsiElement element) {
        if (element instanceof PsiClass) {
            PsiDirectory containingDirectory = element.getContainingFile().getContainingDirectory();
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
        List<AbstractTreeNode> topLevelNodes = new ArrayList<>();
        Set<PsiJavaPackage> packages = new LinkedHashSet<>();
        Set<PsiClass> classes = new LinkedHashSet<>();
        for (CoverageSuite suite : getSuitesBundle().getSuites()) {
            JavaCoverageSuite javaCoverageSuite = (JavaCoverageSuite) suite;
            packages.addAll(javaCoverageSuite.getCurrentSuitePackages(getProject()));
            classes.addAll(javaCoverageSuite.getCurrentSuiteClasses(getProject()));
        }

        Set<PsiPackage> packs = new HashSet<>();
        for (PsiPackage aPackage : packages) {
            String qualifiedName = aPackage.getQualifiedName();
            for (PsiPackage psiPackage : packages) {
                if (psiPackage.getQualifiedName().startsWith(qualifiedName + ".")) {
                    packs.add(psiPackage);
                    break;
                }
            }
        }
        packages.removeAll(packs);

        for (PsiJavaPackage aPackage : packages) {
            GlobalSearchScope searchScope = getSuitesBundle().getSearchScope(getProject());
            if (aPackage.getDirectories(searchScope).length == 0) {
                continue;
            }
            if (aPackage.getClasses(searchScope).length != 0) {
                CoverageListNode node = new CoverageListNode(getProject(), aPackage, getSuitesBundle(), getStateBean());
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
        PsiPackage rootPackage,
        CoverageSuitesBundle data,
        CoverageViewManager.StateBean stateBean
    ) {
        GlobalSearchScope searchScope = data.getSearchScope(rootPackage.getProject());
        Application application = rootPackage.getApplication();
        PsiPackage[] subPackages = application.runReadAction((Supplier<PsiPackage[]>) () -> rootPackage.getSubPackages(searchScope));
        for (PsiPackage aPackage : subPackages) {
            PsiDirectory[] directories = application.runReadAction((Supplier<PsiDirectory[]>) () -> aPackage.getDirectories(searchScope));
            if (directories.length == 0 && !application.runReadAction(
                (Supplier<Boolean>) () -> JavaPsiFacade.getInstance(aPackage.getProject())
                    .isPartOfPackagePrefix(aPackage.getQualifiedName())
            )) {
                continue;
            }
            if (application.runReadAction((Supplier<Boolean>) () -> isInCoverageScope(aPackage, data))) {
                CoverageListNode node = new CoverageListNode(rootPackage.getProject(), aPackage, data, stateBean);
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
    public List<AbstractTreeNode> getChildrenNodes(AbstractTreeNode node) {
        List<AbstractTreeNode> children = new ArrayList<>();
        if (node instanceof CoverageListNode) {
            Object val = node.getValue();
            if (val instanceof PsiClass) {
                return Collections.emptyList();
            }

            //append package classes
            if (val instanceof PsiJavaPackage javaPackage) {
                if (!getStateBean().myFlattenPackages) {
                    collectSubPackages(children, javaPackage, getSuitesBundle(), getStateBean());
                }
                Application application = javaPackage.getApplication();
                if (application.runReadAction((Supplier<Boolean>) () -> isInCoverageScope(javaPackage, getSuitesBundle()))) {
                    PsiClass[] classes = application.runReadAction(
                        (Supplier<PsiClass[]>) () -> javaPackage.getClasses(getSuitesBundle().getSearchScope(node.getProject()))
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
                    List<PsiClass> classes = ((JavaCoverageSuite) suite).getCurrentSuiteClasses(getProject());
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
            @Override
            public String get() {
                return aClass.getQualifiedName();
            }
        }));
    }

    @Override
    public ColumnInfo[] createColumnInfos() {
        return new ColumnInfo[]{
            new ElementColumnInfo(),
            new PercentageCoverageColumnInfo(1, JavaCoverageLocalize.coverageViewColumnClass(), getSuitesBundle(), getStateBean()),
            new PercentageCoverageColumnInfo(2, JavaCoverageLocalize.coverageViewColumnMethod(), getSuitesBundle(), getStateBean()),
            new PercentageCoverageColumnInfo(3, JavaCoverageLocalize.coverageViewColumnLine(), getSuitesBundle(), getStateBean())
        };
    }

    private static boolean isInCoverageScope(PsiElement element, CoverageSuitesBundle suitesBundle) {
        if (element instanceof PsiPackage psiPackage) {
            String qualifiedName = psiPackage.getQualifiedName();
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
        PsiFile psiFile = object instanceof VirtualFile virtualFile ? PsiManager.getInstance(getProject()).findFile(virtualFile) : null;
        if (psiFile instanceof PsiClassOwner classOwner) {
            String packageName = classOwner.getPackageName();
            return isInCoverageScope(JavaPsiFacade.getInstance(getProject()).findPackage(packageName), getSuitesBundle());
        }
        return object instanceof PsiPackage psiPackage && isInCoverageScope(psiPackage, getSuitesBundle());
    }

    @Override
    public boolean supportFlattenPackages() {
        return true;
    }
}
