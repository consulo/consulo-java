/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayFactory;
import consulo.java.module.util.JavaClassNames;

/**
 * Representation of Java type (primitive type, array or class type).
 */
public abstract class PsiType implements PsiAnnotationOwner, Cloneable, JvmType
{
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType BYTE = new PsiPrimitiveType("byte", "java.lang.Byte");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType CHAR = new PsiPrimitiveType("char", "java.lang.Character");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType("double", "java.lang.Double");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType FLOAT = new PsiPrimitiveType("float", "java.lang.Float");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType INT = new PsiPrimitiveType("int", "java.lang.Integer");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType LONG = new PsiPrimitiveType("long", "java.lang.Long");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType SHORT = new PsiPrimitiveType("short", "java.lang.Short");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType("boolean", "java.lang.Boolean");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType VOID = new PsiPrimitiveType("void", "java.lang.Void");
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final PsiPrimitiveType NULL = new PsiPrimitiveType("null", (String) null);

	public static final PsiType[] EMPTY_ARRAY = new PsiType[0];
	public static final ArrayFactory<PsiType> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiType[count];

	@Nonnull
	public static PsiType[] createArray(int count)
	{
		return ARRAY_FACTORY.create(count);
	}

	private TypeAnnotationProvider myAnnotationProvider;

	/**
	 * Constructs a PsiType with given annotations
	 */
	protected PsiType(@Nonnull final PsiAnnotation[] annotations)
	{
		this(TypeAnnotationProvider.Static.create(annotations));
	}

	/**
	 * Constructs a PsiType that will take its annotations from the given annotation provider.
	 */
	protected PsiType(@Nonnull TypeAnnotationProvider annotations)
	{
		myAnnotationProvider = annotations;
	}

	@Nonnull
	public PsiType annotate(@Nonnull TypeAnnotationProvider provider)
	{
		if(provider == myAnnotationProvider)
		{
			return this;
		}

		try
		{
			PsiType copy = (PsiType) clone();
			copy.myAnnotationProvider = provider;
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates array type with this type as a component.
	 */
	@Nonnull
	public PsiArrayType createArrayType()
	{
		return new PsiArrayType(this);
	}

	/**
	 * @deprecated use {@link #annotate(TypeAnnotationProvider)} (to be removed in IDEA 18)
	 */
	public PsiArrayType createArrayType(@Nonnull PsiAnnotation... annotations)
	{
		return new PsiArrayType(this, annotations);
	}

	/**
	 * Returns text of the type that can be presented to a user (references normally non-qualified).
	 */
	@Nonnull
	public String getPresentableText(boolean annotated)
	{
		return getPresentableText();
	}

	/**
	 * Same as {@code getPresentableText(false)}.
	 */
	@Nonnull
	public abstract String getPresentableText();

	/**
	 * Returns canonical representation of the type (all references fully-qualified).
	 */
	@Nonnull
	public String getCanonicalText(boolean annotated)
	{
		return getCanonicalText();
	}

	/**
	 * Same as {@code getCanonicalText(false)}.
	 */
	@Nonnull
	public abstract String getCanonicalText();

	/**
	 * Return canonical text of the type with some internal details added for presentational purposes. Use with care.
	 * todo[r.sh] merge with getPresentableText()
	 */
	@Nonnull
	public String getInternalCanonicalText()
	{
		return getCanonicalText();
	}

	/**
	 * Checks if the type is currently valid.
	 *
	 * @return true if the type is valid, false otherwise.
	 * @see PsiElement#isValid()
	 */
	public abstract boolean isValid();

	/**
	 * @return true if values of type {@code type} can be assigned to rvalues of this type.
	 */
	public boolean isAssignableFrom(@Nonnull PsiType type)
	{
		return TypeConversionUtil.isAssignable(this, type);
	}

	/**
	 * Checks whether values of type {@code type} can be casted to this type.
	 */
	public boolean isConvertibleFrom(@Nonnull PsiType type)
	{
		return TypeConversionUtil.areTypesConvertible(type, this);
	}

	/**
	 * Checks if the specified string is equivalent to the canonical text of the type.
	 *
	 * @param text the text to compare with.
	 * @return true if the string is equivalent to the type, false otherwise
	 */
	public abstract boolean equalsToText(@Nonnull String text);

	/**
	 * Returns the class type for qualified class name.
	 *
	 * @param qName        qualified class name.
	 * @param project
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	public static PsiClassType getTypeByName(String qName, Project project, GlobalSearchScope resolveScope)
	{
		PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
		return factory.createTypeByFQClassName(qName, resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.Object class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangObject(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_OBJECT, manager.getProject(), resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.Class class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangClass(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_CLASS, manager.getProject(), resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.Throwable class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangThrowable(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_THROWABLE, manager.getProject(), resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.String class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangString(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_STRING, manager.getProject(), resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.Error class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangError(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_ERROR, manager.getProject(), resolveScope);
	}

	/**
	 * Returns the class type for the java.lang.RuntimeException class.
	 *
	 * @param manager      the PSI manager used to create the class type.
	 * @param resolveScope the scope in which the class is searched.
	 * @return the class instance.
	 */
	@Nonnull
	public static PsiClassType getJavaLangRuntimeException(@Nonnull PsiManager manager, @Nonnull GlobalSearchScope resolveScope)
	{
		return getTypeByName(JavaClassNames.JAVA_LANG_RUNTIME_EXCEPTION, manager.getProject(), resolveScope);
	}

	/**
	 * Passes the type to the specified visitor.
	 *
	 * @param visitor the visitor to accept the type.
	 * @return the value returned by the visitor.
	 */
	public abstract <A> A accept(@Nonnull PsiTypeVisitor<A> visitor);

	/**
	 * Returns the number of array dimensions for the type.
	 *
	 * @return the number of dimensions, or 0 if the type is not an array type.
	 */
	public final int getArrayDimensions()
	{
		PsiType type = this;
		int dims = 0;
		while(type instanceof PsiArrayType)
		{
			dims++;
			type = ((PsiArrayType) type).getComponentType();
		}
		return dims;
	}

	/**
	 * Returns the innermost component type for an array type.
	 *
	 * @return the innermost (non-array) component of the type, or {@code this} if the type is not
	 * an array type.
	 */
	@Nonnull
	public final PsiType getDeepComponentType()
	{
		PsiType type = this;
		while(type instanceof PsiArrayType)
		{
			type = ((PsiArrayType) type).getComponentType();
		}
		return type;
	}

	/**
	 * Returns the scope in which the reference to the underlying class of a class type is searched.
	 *
	 * @return the resolve scope instance, or null if the type is a primitive or an array of primitives.
	 */
	@Nullable
	public abstract GlobalSearchScope getResolveScope();

	/**
	 * Returns the list of superclass types for a class type.
	 *
	 * @return the array of superclass types, or an empty array if the type is not a class type.
	 */
	@Nonnull
	public abstract PsiType[] getSuperTypes();

	/**
	 * @return provider for this type's annotations. Can be used to construct other PsiType instances
	 * without actually evaluating the annotation array, which can be computationally expensive sometimes.
	 */
	@Nonnull
	public final TypeAnnotationProvider getAnnotationProvider()
	{
		return myAnnotationProvider;
	}

	/**
	 * @return annotations for this type. Uses {@link #getAnnotationProvider()} to retrieve the annotations.
	 */
	@Override
	@Nonnull
	public PsiAnnotation[] getAnnotations()
	{
		return myAnnotationProvider.getAnnotations();
	}

	@Override
	public PsiAnnotation findAnnotation(@Nonnull String qualifiedName)
	{
		for(PsiAnnotation annotation : getAnnotations())
		{
			if(qualifiedName.equals(annotation.getQualifiedName()))
			{
				return annotation;
			}
		}
		return null;
	}

	@Override
	@Nonnull
	public PsiAnnotation addAnnotation(@Nonnull String qualifiedName)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	@Nonnull
	public PsiAnnotation[] getApplicableAnnotations()
	{
		return getAnnotations();
	}

	@Override
	public String toString()
	{
		return "PsiType:" + getPresentableText();
	}

	protected static abstract class Stub extends PsiType
	{
		protected Stub(@Nonnull PsiAnnotation[] annotations)
		{
			super(annotations);
		}

		protected Stub(@Nonnull TypeAnnotationProvider annotations)
		{
			super(annotations);
		}

		@Nonnull
		@Override
		public final String getPresentableText()
		{
			return getPresentableText(false);
		}

		@Nonnull
		@Override
		public abstract String getPresentableText(boolean annotated);

		@Nonnull
		@Override
		public final String getCanonicalText()
		{
			return getCanonicalText(false);
		}

		@Nonnull
		@Override
		public abstract String getCanonicalText(boolean annotated);
	}
}