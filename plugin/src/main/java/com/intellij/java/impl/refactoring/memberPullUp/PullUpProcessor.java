/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 22:35:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.impl.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.java.impl.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.application.util.query.Query;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import consulo.language.Language;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.*;

public class PullUpProcessor extends BaseRefactoringProcessor implements PullUpData {
  private static final Logger LOG = Logger.getInstance(PullUpProcessor.class);

  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final MemberInfo[] myMembersToMove;
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove = null;
  private Set<PsiMember> myMovedMembers = null;
  private final Map<Language, PullUpHelper<MemberInfo>> myProcessors = new HashMap<>();

  public PullUpProcessor(PsiClass sourceClass,
                         PsiClass targetSuperClass,
                         MemberInfo[] membersToMove,
                         DocCommentPolicy javaDocPolicy) {
    super(sourceClass.getProject());
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;
  }

  @Override
  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
    return new PullUpUsageViewDescriptor();
  }

  @Override
  @Nonnull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (MemberInfo memberInfo : myMembersToMove) {
      final PsiMember member = memberInfo.getMember();
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        for (PsiReference reference : ReferencesSearch.search(member)) {
          result.add(new UsageInfo(reference));
        }
      }
    }
    return result.isEmpty() ? UsageInfo.EMPTY_ARRAY : result.toArray(new UsageInfo[result.size()]);
  }

	/*@Nullable
  @Override
	protected String getRefactoringId()
	{
		return "refactoring.pull.up";
	}

	@Nullable
	@Override
	protected RefactoringEventData getBeforeData()
	{
		RefactoringEventData data = new RefactoringEventData();
		data.addElement(mySourceClass);
		data.addMembers(myMembersToMove, new Function<MemberInfo, PsiElement>()
		{
			@Override
			public PsiElement fun(MemberInfo info)
			{
				return info.getMember();
			}
		});
		return data;
	}

	@Nullable
	@Override
	protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages)
	{
		final RefactoringEventData data = new RefactoringEventData();
		data.addElement(myTargetSuperClass);
		return data;
	}  */

  @Override
  protected void performRefactoring(@Nonnull UsageInfo[] usages) {
    moveMembersToBase();
    moveFieldInitializations();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) {
        continue;
      }

      PullUpHelper<MemberInfo> processor = getProcessor(element);
      processor.updateUsage(element);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        processMethodsDuplicates();
      }
    }, Application.get().getNoneModalityState(), myProject.getDisposed());
  }

  private void processMethodsDuplicates() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            if (!myTargetSuperClass.isValid()) {
              return;
            }
            final Query<PsiClass> search = ClassInheritorsSearch.search(myTargetSuperClass);
            final Set<VirtualFile> hierarchyFiles = new HashSet<VirtualFile>();
            for (PsiClass aClass : search) {
              final PsiFile containingFile = aClass.getContainingFile();
              if (containingFile != null) {
                final VirtualFile virtualFile = containingFile.getVirtualFile();
                if (virtualFile != null) {
                  hierarchyFiles.add(virtualFile);
                }
              }
            }
            final Set<PsiMember> methodsToSearchDuplicates = new HashSet<PsiMember>();
            for (PsiMember psiMember : myMembersAfterMove) {
              if (psiMember instanceof PsiMethod && psiMember.isValid() && ((PsiMethod) psiMember)
                  .getBody() != null) {
                methodsToSearchDuplicates.add(psiMember);
              }
            }

            MethodDuplicatesHandler.invokeOnScope(myProject, methodsToSearchDuplicates,
                new AnalysisScope(myProject, hierarchyFiles), true);
          }
        });
      }
    }, MethodDuplicatesHandler.REFACTORING_NAME, true, myProject);
  }

  @Override
  protected String getCommandName() {
    return RefactoringLocalize.pullupCommand(DescriptiveNameUtil.getDescriptiveName(mySourceClass)).get();
  }

  public void moveMembersToBase() throws IncorrectOperationException {
    myMovedMembers = new HashSet<>();
    myMembersAfterMove = new HashSet<>();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      myMovedMembers.add(info.getMember());
    }

    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();

    for (MemberInfo info : myMembersToMove) {
      PullUpHelper<MemberInfo> processor = getProcessor(info);

      if (!(info.getMember() instanceof PsiClass) || info.getOverrides() == null) {
        processor.setCorrectVisibility(info);
        processor.encodeContextInfo(info);
      }

      processor.move(info, substitutor);
    }

    for (PsiMember member : myMembersAfterMove) {
      getProcessor(member).postProcessMember(member);

      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance
          (myProject);
      ((JavaRefactoringListenerManagerImpl) listenerManager).fireMemberMoved(mySourceClass, member);
    }
  }

  private PullUpHelper<MemberInfo> getProcessor(@Nonnull PsiElement element) {
    Language language = element.getLanguage();
    return getProcessor(language);
  }

  private PullUpHelper<MemberInfo> getProcessor(Language language) {
    PullUpHelper<MemberInfo> helper = myProcessors.get(language);
    if (helper == null) {
      helper = PullUpHelperFactory.forLanguage(language).createPullUpHelper(this);
      myProcessors.put(language, helper);
    }
    return helper;
  }

  private PullUpHelper<MemberInfo> getProcessor(@Nonnull MemberInfo info) {
    PsiReferenceList refList = info.getSourceReferenceList();
    if (refList != null) {
      return getProcessor(refList.getLanguage());
    }
    return getProcessor(info.getMember());
  }

  private PsiSubstitutor upDownSuperClassSubstitutor() {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(mySourceClass)) {
      substitutor = substitutor.put(parameter, null);
    }
    final Map<PsiTypeParameter, PsiType> substitutionMap = TypeConversionUtil.getSuperClassSubstitutor
        (myTargetSuperClass, mySourceClass, PsiSubstitutor.EMPTY).getSubstitutionMap();
    for (PsiTypeParameter parameter : substitutionMap.keySet()) {
      final PsiType type = substitutionMap.get(parameter);
      final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
      if (resolvedClass instanceof PsiTypeParameter) {
        substitutor = substitutor.put((PsiTypeParameter) resolvedClass, JavaPsiFacade.getElementFactory
            (myProject).createType(parameter));
      }
    }
    return substitutor;
  }

  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField) member);
      }
    }

    if (movedFields.isEmpty()) {
      return;
    }

    getProcessor(myTargetSuperClass).moveFieldInitializations(movedFields);
  }

  public static boolean checkedInterfacesContain(Collection<? extends MemberInfoBase<? extends PsiMember>>
                                                     memberInfos,
                                                 PsiMethod psiMethod) {
    for (MemberInfoBase<? extends PsiMember> memberInfo : memberInfos) {
      if (memberInfo.isChecked() &&
          memberInfo.getMember() instanceof PsiClass &&
          Boolean.FALSE.equals(memberInfo.getOverrides())) {
        if (((PsiClass) memberInfo.getMember()).findMethodBySignature(psiMethod, true) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiClass getSourceClass() {
    return mySourceClass;
  }

  @Override
  public PsiClass getTargetClass() {
    return myTargetSuperClass;
  }

  @Override
  public DocCommentPolicy getDocCommentPolicy() {
    return myJavaDocPolicy;
  }

  @Override
  public Set<PsiMember> getMembersToMove() {
    return myMovedMembers;
  }

  @Override
  public Set<PsiMember> getMovedMembers() {
    return myMembersAfterMove;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  private class PullUpUsageViewDescriptor implements UsageViewDescriptor {
    @Override
    public String getProcessedElementsHeader() {
      return "Pull up members from";
    }

    @Override
    @Nonnull
    public PsiElement[] getElements() {
      return new PsiElement[]{mySourceClass};
    }

    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
      return "Class to pull up members to \"" + RefactoringUIUtil.getDescription(myTargetSuperClass, true) + "\"";
    }

    @Override
    public String getCommentReferencesText(int usagesCount, int filesCount) {
      return null;
    }
  }
}
