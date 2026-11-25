package sn.arlettedebo.platform.ops.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(@NotBlank String dir) {
}
