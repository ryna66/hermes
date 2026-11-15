package in.sih.platform.challenge;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LocalAttachmentStorage {
    private final Path root;
    public LocalAttachmentStorage(@Value("${storage.upload-dir:./uploads}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public String store(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) return null;
        String type = file.getContentType();
        if (type == null || !type.startsWith(kind.equals("photo") ? "image/" : "audio/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, kind + " must have an appropriate media type");
        // Never use the client filename to construct a filesystem path.
        Path target = root.resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(root);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(target); }
                        catch (IOException e) { org.slf4j.LoggerFactory.getLogger(LocalAttachmentStorage.class)
                                .error("Could not remove rolled-back upload {}", target, e); }
                    }
                }
            });
            try (InputStream input = file.getInputStream()) { Files.copy(input, target); }
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store " + kind, e);
        }
    }
}
