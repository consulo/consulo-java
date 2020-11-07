package consulo.java.spi;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.spi.SPIFileType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2020-11-07
 */
public class SPIFileTypeFactory extends FileTypeFactory
{
	@Override
	public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer)
	{
		fileTypeConsumer.consume(SPIFileType.INSTANCE);
	}
}
