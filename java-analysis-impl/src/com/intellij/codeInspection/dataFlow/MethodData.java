/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.codeInspection.dataFlow;

import java.util.List;
import java.util.function.Supplier;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

/**
 * from kotlin
 */
class MethodData
{
	private NullityInferenceResult nullity;
	private PurityInferenceResult purity;
	private List<PreContract> contracts;
	private int bodyStart;
	private int bodyEnd;

	MethodData(NullityInferenceResult nullity, PurityInferenceResult purity, List<PreContract> contracts, int bodyStart, int bodyEnd)
	{
		this.nullity = nullity;
		this.purity = purity;
		this.contracts = contracts;
		this.bodyStart = bodyStart;
		this.bodyEnd = bodyEnd;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		MethodData that = (MethodData) o;

		if(bodyStart != that.bodyStart)
		{
			return false;
		}
		if(bodyEnd != that.bodyEnd)
		{
			return false;
		}
		if(nullity != null ? !nullity.equals(that.nullity) : that.nullity != null)
		{
			return false;
		}
		if(purity != null ? !purity.equals(that.purity) : that.purity != null)
		{
			return false;
		}
		if(contracts != null ? !contracts.equals(that.contracts) : that.contracts != null)
		{
			return false;
		}

		return true;
	}

	public NullityInferenceResult getNullity()
	{
		return nullity;
	}

	public PurityInferenceResult getPurity()
	{
		return purity;
	}

	public List<PreContract> getContracts()
	{
		return contracts;
	}

	public int getBodyStart()
	{
		return bodyStart;
	}

	public int getBodyEnd()
	{
		return bodyEnd;
	}

	@Override
	public int hashCode()
	{
		int result = nullity != null ? nullity.hashCode() : 0;
		result = 31 * result + (purity != null ? purity.hashCode() : 0);
		result = 31 * result + (contracts != null ? contracts.hashCode() : 0);
		result = 31 * result + bodyStart;
		result = 31 * result + bodyEnd;
		return result;
	}

	public Supplier<PsiCodeBlock> methodBody(PsiMethodImpl method)
	{
		return () ->
		{
			PsiMethodStub stub = method.getStub();
			if(stub != null)
			{
				return CachedValuesManager.getCachedValue(method, () -> new CachedValueProvider.Result<>(getDetachedBody(method), method));
			}
			else
			{
				return method.getBody();
			}
		};
	}

	private PsiCodeBlock getDetachedBody(PsiMethod method)
	{
		Document document = method.getContainingFile().getViewProvider().getDocument();
		if(document == null)
		{
			return method.getBody();
		}
		CharSequence bodyText = PsiDocumentManager.getInstance(method.getProject()).getLastCommittedText(document).subSequence(bodyStart, bodyEnd);
		return JavaPsiFacade.getElementFactory(method.getProject()).createCodeBlockFromText(bodyText, method);
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder("MethodData{");
		sb.append("nullity=").append(nullity);
		sb.append(", putity=").append(purity);
		sb.append(", contracts=").append(contracts);
		sb.append(", bodyStart=").append(bodyStart);
		sb.append(", bodyEnd=").append(bodyEnd);
		sb.append('}');
		return sb.toString();
	}
}
