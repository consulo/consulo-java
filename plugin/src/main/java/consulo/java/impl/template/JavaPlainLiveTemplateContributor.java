package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.*;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaPlainLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "javaplain";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Java Plain");
    }

    @Override
    public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder("javaplainSt", "St", "String ", CodeInsightLocalize.livetemplateDescriptionSt())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
            builder.withContextsOf(JavaLikeDeclarationContextType.class, true);
            builder.withContextsOf(JavaLikeExpressionContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaplainPsf", "psf", "public static final ", CodeInsightLocalize.livetemplateDescriptionPsf())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaplainPsfi", "psfi", "public static final int ", CodeInsightLocalize.livetemplateDescriptionPsfi())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaplainPsfs", "psfs", "public static final String ", CodeInsightLocalize.livetemplateDescriptionPsfs())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaplainThr", "thr", "throw new ", CodeInsightLocalize.livetemplateDescriptionThr())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }
    }
}
