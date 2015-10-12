package com.intellij.codeInsight.completion;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

/**
 * @author peter
 */
public class MemberLookupHelper
{
	private final PsiMember myMember;
	private final boolean myMergedOverloads;
	@Nullable
	private final PsiClass myContainingClass;
	private boolean myShouldImport = false;

	public MemberLookupHelper(List<PsiMethod> overloads, PsiClass containingClass, boolean shouldImport)
	{
		this(overloads.get(0), containingClass, shouldImport, true);
	}

	public MemberLookupHelper(PsiMember member, @Nullable PsiClass containingClass, boolean shouldImport, final boolean mergedOverloads)
	{
		myMember = member;
		myContainingClass = containingClass;
		myShouldImport = shouldImport;
		myMergedOverloads = mergedOverloads;
	}

	public PsiMember getMember()
	{
		return myMember;
	}

	@Nullable
	public PsiClass getContainingClass()
	{
		return myContainingClass;
	}

	public void setShouldBeImported(boolean shouldImportStatic)
	{
		myShouldImport = shouldImportStatic;
	}

	public boolean willBeImported()
	{
		return myShouldImport;
	}

	public void renderElement(LookupElementPresentation presentation, boolean showClass, boolean showPackage, PsiSubstitutor substitutor)
	{
		final String className = myContainingClass == null ? "???" : myContainingClass.getName();

		final String memberName = myMember.getName();
		if(showClass && StringUtil.isNotEmpty(className))
		{
			presentation.setItemText(className + "." + memberName);
		}
		else
		{
			presentation.setItemText(memberName);
		}

		final String qname = myContainingClass == null ? "" : myContainingClass.getQualifiedName();
		String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
		String location = showPackage && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

		final String params = myMergedOverloads ? "(...)" : myMember instanceof PsiMethod ? PsiFormatUtil.formatMethod((PsiMethod) myMember, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS,
				PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE) : "";
		presentation.clearTail();
		presentation.appendTailText(params, false);
		if(myShouldImport && StringUtil.isNotEmpty(className))
		{
			presentation.appendTailText(" in " + className + location, true);
		}
		else
		{
			presentation.appendTailText(location, true);
		}

		final PsiType type = myMember instanceof PsiMethod ? ((PsiMethod) myMember).getReturnType() : ((PsiField) myMember).getType();
		if(type != null)
		{
			presentation.setTypeText(substitutor.substitute(type).getPresentableText());
		}
	}


}
