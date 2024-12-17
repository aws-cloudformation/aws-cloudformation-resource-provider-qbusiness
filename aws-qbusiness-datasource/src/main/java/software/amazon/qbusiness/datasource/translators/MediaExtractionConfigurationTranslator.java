package software.amazon.qbusiness.datasource.translators;

import software.amazon.qbusiness.datasource.ImageExtractionConfiguration;
import software.amazon.qbusiness.datasource.MediaExtractionConfiguration;

public final class MediaExtractionConfigurationTranslator {

    private MediaExtractionConfigurationTranslator() {
    }

    public static MediaExtractionConfiguration fromServiceMediaExtractionConfiguration(
            software.amazon.awssdk.services.qbusiness.model.MediaExtractionConfiguration mediaExtractionConfiguration
    ) {
        if (mediaExtractionConfiguration == null) {
            return null;
        }

        return MediaExtractionConfiguration.builder()
                .imageExtractionConfiguration(
                        fromServiceImageExtractionConfiguration(mediaExtractionConfiguration.imageExtractionConfiguration()))
                .build();
    }

    public static software.amazon.awssdk.services.qbusiness.model.MediaExtractionConfiguration toServiceMediaExtractionConfiguration(
            MediaExtractionConfiguration mediaExtractionConfiguration
    ) {
        if (mediaExtractionConfiguration == null) {
            return null;
        }
        return software.amazon.awssdk.services.qbusiness.model.MediaExtractionConfiguration.builder()
                .imageExtractionConfiguration(
                        toServiceImageExtractionConfiguration(mediaExtractionConfiguration.getImageExtractionConfiguration()))
                .build();
    }


    static software.amazon.awssdk.services.qbusiness.model.ImageExtractionConfiguration toServiceImageExtractionConfiguration(
            ImageExtractionConfiguration imageExtractionConfiguration
    ) {
        if (imageExtractionConfiguration == null) {
            return null;
        }

        return software.amazon.awssdk.services.qbusiness.model.ImageExtractionConfiguration.builder()
                .imageExtractionStatus(imageExtractionConfiguration.getImageExtractionStatus())
                .build();
    }

    static ImageExtractionConfiguration fromServiceImageExtractionConfiguration(
            software.amazon.awssdk.services.qbusiness.model.ImageExtractionConfiguration imageExtractionConfiguration
    ) {
        if (imageExtractionConfiguration == null) {
            return null;
        }

        return ImageExtractionConfiguration.builder()
                .imageExtractionStatus(imageExtractionConfiguration.imageExtractionStatusAsString())
                .build();
    }

}
