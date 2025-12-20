// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.impl.refactoring.typeMigration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import jakarta.annotation.Nullable;

import com.intellij.java.language.psi.*;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.*;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import jakarta.annotation.Nonnull;

/**
 * @author db
 */
public class TypeEvaluator
{
	private static final Logger LOG = Logger.getInstance(TypeEvaluator.class);

	private final HashMap<TypeMigrationUsageInfo, LinkedList<PsiType>> myTypeMap;
	private final TypeMigrationRules myRules;
	private final TypeMigrationLabeler myLabeler;
	private final ProjectFileIndex myProjectFileIndex;

	public TypeEvaluator(LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> types, TypeMigrationLabeler labeler, Project project)
	{
		myLabeler = labeler;
		myRules = labeler == null ? new TypeMigrationRules(project) : labeler.getRules();
		myTypeMap = new HashMap<>();

		if(types != null)
		{
			for(Pair<TypeMigrationUsageInfo, PsiType> p : types)
			{
				if(!(p.getFirst().getElement() instanceof PsiExpression))
				{
					LinkedList<PsiType> e = new LinkedList<>();
					e.addFirst(p.getSecond());
					myTypeMap.put(p.getFirst(), e);
				}
			}
		}

		myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
	}

	public boolean setType(TypeMigrationUsageInfo usageInfo, @Nonnull PsiType type)
	{
		LinkedList<PsiType> t = myTypeMap.get(usageInfo);

		PsiElement element = usageInfo.getElement();

		if(type instanceof PsiEllipsisType && !(element instanceof PsiParameter && ((PsiParameter) element).getDeclarationScope() instanceof PsiMethod))
		{
			type = ((PsiEllipsisType) type).toArrayType();
		}

		if(t != null)
		{
			if(!t.getFirst().equals(type))
			{
				if(element instanceof PsiVariable || element instanceof PsiMethod)
				{
					return false;
				}

				t.addFirst(type);

				return true;
			}
		}
		else
		{
			LinkedList<PsiType> e = new LinkedList<>();

			e.addFirst(type);

			usageInfo.setOwnerRoot(myLabeler.getCurrentRoot());
			myTypeMap.put(usageInfo, e);
			return true;
		}

		return false;
	}

	@Nullable
	public PsiType getType(PsiElement element)
	{
		VirtualFile file = element.getContainingFile().getVirtualFile();
		if(file == null || !myProjectFileIndex.isInContent(file))
		{
			return TypeMigrationLabeler.getElementType(element);
		}

		for(Map.Entry<TypeMigrationUsageInfo, LinkedList<PsiType>> entry : myTypeMap.entrySet())
		{
			if(Comparing.equal(element, entry.getKey().getElement()))
			{
				return entry.getValue().getFirst();
			}
		}
		if(element.getTextRange() == null)
		{
			return null;
		}
		return getType(new TypeMigrationUsageInfo(element));
	}

	@Nullable
	public PsiType getType(TypeMigrationUsageInfo usageInfo)
	{
		LinkedList<PsiType> e = myTypeMap.get(usageInfo);

		if(e != null)
		{
			return e.getFirst();
		}

		return TypeMigrationLabeler.getElementType(usageInfo.getElement());
	}

	@Nullable
	public PsiType evaluateType(PsiExpression expr)
	{
		if(expr == null)
		{
			return null;
		}
		LinkedList<PsiType> e = myTypeMap.get(new TypeMigrationUsageInfo(expr));

		if(e != null)
		{
			return e.getFirst();
		}

		if(expr instanceof PsiArrayAccessExpression)
		{
			PsiType at = evaluateType(((PsiArrayAccessExpression) expr).getArrayExpression());

			if(at instanceof PsiArrayType)
			{
				return ((PsiArrayType) at).getComponentType();
			}
		}
		else if(expr instanceof PsiAssignmentExpression)
		{
			return evaluateType(((PsiAssignmentExpression) expr).getLExpression());
		}
		else if(expr instanceof PsiMethodCallExpression)
		{
			PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
			JavaResolveResult resolveResult = call.resolveMethodGenerics();
			PsiMethod method = (PsiMethod) resolveResult.getElement();

			if(method != null)
			{
				PsiParameter[] parameters = method.getParameterList().getParameters();
				PsiExpression[] actualParms = call.getArgumentList().getExpressions();
				return PsiUtil.captureToplevelWildcards(createMethodSubstitution(parameters, actualParms, method, call, resolveResult.getSubstitutor(), false).substitute(evaluateType(call
						.getMethodExpression())), expr);
			}
		}
		else if(expr instanceof PsiPolyadicExpression)
		{
			PsiExpression[] operands = ((PsiPolyadicExpression) expr).getOperands();
			IElementType sign = ((PsiPolyadicExpression) expr).getOperationTokenType();
			PsiType lType = operands.length > 0 ? evaluateType(operands[0]) : null;
			for(int i = 1; i < operands.length; i++)
			{
				lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, evaluateType(operands[i]), sign, true);
			}
			return lType;
		}
		else if(expr instanceof PsiUnaryExpression)
		{
			return evaluateType(((PsiUnaryExpression) expr).getOperand());
		}
		else if(expr instanceof PsiParenthesizedExpression)
		{
			return evaluateType(((PsiParenthesizedExpression) expr).getExpression());
		}
		else if(expr instanceof PsiConditionalExpression)
		{
			PsiExpression thenExpression = ((PsiConditionalExpression) expr).getThenExpression();
			PsiExpression elseExpression = ((PsiConditionalExpression) expr).getElseExpression();

			PsiType thenType = evaluateType(thenExpression);
			PsiType elseType = evaluateType(elseExpression);

			switch((thenType == null ? 0 : 1) + (elseType == null ? 0 : 2))
			{
				case 0:
					return expr.getType();

				case 1:
					return thenType;

				case 2:
					return elseType;

				case 3:
					if(TypeConversionUtil.areTypesConvertible(thenType, elseType))
					{
						return thenType;
					}
					else if(TypeConversionUtil.areTypesConvertible(elseType, thenType))
					{
						return elseType;
					}
					else
					{
						switch((thenType.equals(thenExpression.getType()) ? 0 : 1) + (elseType.equals(elseExpression.getType()) ? 0 : 2))
						{
							case 0:
								return expr.getType();

							case 1:
								return thenType;

							case 2:
								return elseType;

							case 3:
								return expr.getType();

							default:
								LOG.error("Must not happen.");
								return null;
						}
					}

				default:
					LOG.error("Must not happen.");
			}

		}
		else if(expr instanceof PsiNewExpression)
		{
			PsiExpression qualifier = ((PsiNewExpression) expr).getQualifier();

			if(qualifier != null)
			{
				PsiClassType.ClassResolveResult qualifierResult = resolveType(evaluateType(qualifier));

				if(qualifierResult.getElement() != null)
				{
					PsiSubstitutor qualifierSubs = qualifierResult.getSubstitutor();
					PsiClassType.ClassResolveResult result = resolveType(expr.getType());

					if(result.getElement() != null)
					{
						PsiClass aClass = result.getElement();

						return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, result.getSubstitutor().putAll(qualifierSubs));
					}
				}
			}
		}
		else if(expr instanceof PsiFunctionalExpression)
		{
			PsiType functionalInterfaceType = ((PsiFunctionalExpression) expr).getFunctionalInterfaceType();
			if(functionalInterfaceType != null)
			{
				return functionalInterfaceType;
			}
		}
		else if(expr instanceof PsiReferenceExpression)
		{
			PsiType type = evaluateReferenceExpressionType(expr);
			if(type != null)
			{
				return PsiImplUtil.normalizeWildcardTypeByPosition(type, expr);
			}
		}
		else if(expr instanceof PsiSuperExpression)
		{
			PsiClass psiClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
			if(psiClass != null)
			{
				PsiClass superClass = psiClass.getSuperClass();
				if(superClass != null)
				{
					return getType(new TypeMigrationUsageInfo(superClass));
				}
			}
		}

		return getType(expr);
	}

	@Nullable
	private PsiType evaluateReferenceExpressionType(PsiExpression expr)
	{
		PsiReferenceExpression ref = (PsiReferenceExpression) expr;
		PsiExpression qualifier = ref.getQualifierExpression();

		if(qualifier == null)
		{
			PsiElement resolvee = ref.resolve();

			if(resolvee == null)
			{
				return null;
			}

			return resolvee instanceof PsiClass ? JavaPsiFacade.getElementFactory(resolvee.getProject()).createType((PsiClass) resolvee, PsiSubstitutor.EMPTY) : getType(resolvee);
		}
		else
		{
			PsiType qualifierType = evaluateType(qualifier);
			if(!(qualifierType instanceof PsiArrayType))
			{
				PsiElement element = ref.resolve();

				PsiClassType.ClassResolveResult result = resolveType(qualifierType);

				PsiClass aClass = result.getElement();
				if(aClass != null)
				{
					PsiSubstitutor aSubst = result.getSubstitutor();
					if(element instanceof PsiField)
					{
						PsiField field = (PsiField) element;
						PsiType aType = field.getType();
						PsiClass superClass = field.getContainingClass();
						if(InheritanceUtil.isInheritorOrSelf(aClass, superClass, true))
						{
							aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
						}
						return aSubst.substitute(aType);
					}
					else if(element instanceof PsiMethod)
					{
						PsiMethod method = (PsiMethod) element;
						PsiType aType = method.getReturnType();
						PsiClass superClass = method.getContainingClass();
						if(InheritanceUtil.isInheritorOrSelf(aClass, superClass, true))
						{
							aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
						}
						else if(InheritanceUtil.isInheritorOrSelf(superClass, aClass, true))
						{
							PsiMethod[] methods = method.findSuperMethods(aClass);
							if(methods.length > 0)
							{
								aType = methods[0].getReturnType();
							}
			/*}
            final Pair<PsiType, PsiType> pair = myRules.bindTypeParameters(qualifier.getType(), qualifierType, method, ref, myLabeler);
            if (pair != null) {
              final PsiClass psiClass = PsiUtil.resolveClassInType(aType);
              if (psiClass instanceof PsiTypeParameter) {
                aSubst = aSubst.put((PsiTypeParameter)psiClass, pair.getSecond());
              }*/
						}
						return aSubst.substitute(aType);
					}
				}
			}
		}
		return null;
	}

	public static PsiClassType.ClassResolveResult resolveType(PsiType type)
	{
		PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
		PsiClass aClass = resolveResult.getElement();
		if(aClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassType = ((PsiAnonymousClass) aClass).getBaseClassType();
			return resolveType(resolveResult.getSubstitutor().substitute(baseClassType));
		}
		return resolveResult;
	}

	public PsiSubstitutor createMethodSubstitution(PsiParameter[] parameters, PsiExpression[] actualParms, PsiMethod method, PsiExpression call)
	{
		return createMethodSubstitution(parameters, actualParms, method, call, PsiSubstitutor.EMPTY, false);
	}

	public PsiSubstitutor createMethodSubstitution(PsiParameter[] parameters,
                                                   PsiExpression[] actualParms,
                                                   PsiMethod method,
                                                   PsiExpression call,
                                                   PsiSubstitutor subst,
                                                   boolean preferSubst)
	{
		SubstitutorBuilder substitutorBuilder = new SubstitutorBuilder(method, call, subst);

		for(int i = 0; i < Math.min(parameters.length, actualParms.length); i++)
		{
			substitutorBuilder.bindTypeParameters(getType(parameters[i]), evaluateType(actualParms[i]));
		}
		return substitutorBuilder.createSubstitutor(preferSubst);
	}

	public String getReport()
	{
		StringBuilder buffer = new StringBuilder();

		String[] t = new String[myTypeMap.size()];
		int k = 0;

		for(TypeMigrationUsageInfo info : myTypeMap.keySet())
		{
			LinkedList<PsiType> types = myTypeMap.get(info);
			StringBuilder b = new StringBuilder();

			if(types != null)
			{
				b.append(info.getElement()).append(" : ");

				b.append(StringUtil.join(types, psiType -> psiType.getCanonicalText(), " "));

				b.append("\n");
			}

			t[k++] = b.toString();
		}

		Arrays.sort(t);

		for(String aT : t)
		{
			buffer.append(aT);
		}

		return buffer.toString();
	}

	public LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> getMigratedDeclarations()
	{
		LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> list = new LinkedList<>();

		for(TypeMigrationUsageInfo usageInfo : myTypeMap.keySet())
		{
			LinkedList<PsiType> types = myTypeMap.get(usageInfo);
			PsiElement element = usageInfo.getElement();
			if(element instanceof PsiVariable || element instanceof PsiMethod)
			{
				list.addLast(Pair.create(usageInfo, types.getFirst()));
			}
		}

		return list;
	}

	@Nullable
	static PsiType substituteType(PsiType migrationType, PsiType originalType, boolean captureWildcard, PsiClass originalClass, PsiType rawTypeToReplace)
	{
		if(originalClass != null)
		{
			if(((PsiClassType) originalType).hasParameters() && ((PsiClassType) migrationType).hasParameters())
			{
				PsiResolveHelper psiResolveHelper = JavaPsiFacade.getInstance(originalClass.getProject()).getResolveHelper();

				PsiType rawOriginalType = JavaPsiFacade.getElementFactory(originalClass.getProject()).createType(originalClass, PsiSubstitutor.EMPTY);

				PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
				for(PsiTypeParameter parameter : originalClass.getTypeParameters())
				{
					PsiType type = psiResolveHelper.getSubstitutionForTypeParameter(parameter, rawOriginalType, migrationType, false, PsiUtil.getLanguageLevel(originalClass));
					if(type != null)
					{
						substitutor = substitutor.put(parameter, captureWildcard && type instanceof PsiWildcardType ? ((PsiWildcardType) type).getExtendsBound() : type);
					}
					else
					{
						return null;
					}
				}

				return substitutor.substitute(rawTypeToReplace);
			}
			else
			{
				return originalType;
			}
		}
		return null;
	}

	public static PsiType substituteType(PsiType migrationTtype, PsiType originalType, boolean isContraVariantPosition)
	{
		if(originalType instanceof PsiClassType && migrationTtype instanceof PsiClassType)
		{
			PsiClass originalClass = ((PsiClassType) originalType).resolve();
			if(originalClass != null)
			{
				if(isContraVariantPosition && TypeConversionUtil.erasure(originalType).isAssignableFrom(TypeConversionUtil.erasure(migrationTtype)))
				{
					PsiClass psiClass = ((PsiClassType) migrationTtype).resolve();
					PsiSubstitutor substitutor = psiClass != null ? TypeConversionUtil.getClassSubstitutor(originalClass, psiClass, PsiSubstitutor.EMPTY) : null;
					if(substitutor != null)
					{
						PsiType psiType = substituteType(migrationTtype, originalType, false, psiClass, JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(originalClass,
								substitutor));
						if(psiType != null)
						{
							return psiType;
						}
					}
				}
				else if(!isContraVariantPosition && TypeConversionUtil.erasure(migrationTtype).isAssignableFrom(TypeConversionUtil.erasure(originalType)))
				{
					PsiType psiType = substituteType(migrationTtype, originalType, false, originalClass, JavaPsiFacade.getElementFactory(originalClass.getProject()).createType(originalClass,
							PsiSubstitutor.EMPTY));
					if(psiType != null)
					{
						return psiType;
					}
				}
			}
		}
		return migrationTtype;
	}

	@Nullable
	public <T> T getSettings(Class<T> aClass)
	{
		return myRules.getConversionSettings(aClass);
	}

	private class SubstitutorBuilder
	{
		private final Map<PsiTypeParameter, PsiType> myMapping;
		private final PsiMethod myMethod;
		private final PsiExpression myCall;
		private final PsiSubstitutor mySubst;


		public SubstitutorBuilder(PsiMethod method, PsiExpression call, PsiSubstitutor subst)
		{
			mySubst = subst;
			myMapping = new HashMap<>();
			myMethod = method;
			myCall = call;
		}

		private void update(PsiTypeParameter p, PsiType t)
		{
			if(t instanceof PsiPrimitiveType)
			{
				t = ((PsiPrimitiveType) t).getBoxedType(myMethod);
			}
			PsiType binding = myMapping.get(p);

			if(binding == null)
			{
				myMapping.put(p, t);
			}
			else if(t != null)
			{
				myMapping.put(p, PsiIntersectionType.createIntersection(binding, t));
			}
		}

		void bindTypeParameters(PsiType formal, PsiType actual)
		{
			if(formal instanceof PsiWildcardType)
			{
				if(actual instanceof PsiCapturedWildcardType && ((PsiWildcardType) formal).isExtends() == ((PsiCapturedWildcardType) actual).getWildcard().isExtends())
				{
					bindTypeParameters(((PsiWildcardType) formal).getBound(), ((PsiCapturedWildcardType) actual).getWildcard().getBound());
					return;
				}
				else
				{
					formal = ((PsiWildcardType) formal).getBound();
				}
			}

			if(formal instanceof PsiArrayType && actual instanceof PsiArrayType)
			{
				bindTypeParameters(((PsiArrayType) formal).getComponentType(), ((PsiArrayType) actual).getComponentType());
				return;
			}

			Pair<PsiType, PsiType> typePair = myRules.bindTypeParameters(formal, actual, myMethod, myCall, myLabeler);
			if(typePair != null)
			{
				bindTypeParameters(typePair.getFirst(), typePair.getSecond());
				return;
			}

			PsiClassType.ClassResolveResult resultF = resolveType(formal);
			PsiClass classF = resultF.getElement();
			if(classF != null)
			{

				if(classF instanceof PsiTypeParameter)
				{
					update((PsiTypeParameter) classF, actual);
					return;
				}

				PsiClassType.ClassResolveResult resultA = resolveType(actual);

				PsiClass classA = resultA.getElement();
				if(classA == null)
				{
					return;
				}


				if(!classA.equals(classF))
				{
					PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getClassSubstitutor(classF, classA, resultA.getSubstitutor());
					if(superClassSubstitutor != null)
					{
						PsiType aligned = JavaPsiFacade.getInstance(classF.getProject()).getElementFactory().createType(classF, superClassSubstitutor);
						bindTypeParameters(formal, aligned);
					}
				}

				PsiTypeParameter[] typeParms = classA.getTypeParameters();
				PsiSubstitutor substA = resultA.getSubstitutor();
				PsiSubstitutor substF = resultF.getSubstitutor();

				for(PsiTypeParameter typeParm : typeParms)
				{
					bindTypeParameters(substF.substitute(typeParm), substA.substitute(typeParm));
				}
			}
		}

		public PsiSubstitutor createSubstitutor(boolean preferSubst)
		{
			PsiSubstitutor theSubst = mySubst;
			if(preferSubst)
			{
				myMapping.keySet().removeAll(mySubst.getSubstitutionMap().keySet());
			}
			for(PsiTypeParameter parm : myMapping.keySet())
			{
				theSubst = theSubst.put(parm, myMapping.get(parm));
			}
			return theSubst;
		}
	}
}
