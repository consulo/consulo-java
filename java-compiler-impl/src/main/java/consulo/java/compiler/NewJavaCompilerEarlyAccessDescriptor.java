package consulo.java.compiler;

import consulo.ide.eap.EarlyAccessProgramDescriptor;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/03/2021
 */
public class NewJavaCompilerEarlyAccessDescriptor extends EarlyAccessProgramDescriptor
{
	@Nonnull
	@Override
	public String getName()
	{
		return "New Java Compiler (java 8+)";
	}

	@Nonnull
	@Override
	public String getGroupName()
	{
		return "Java";
	}
}
