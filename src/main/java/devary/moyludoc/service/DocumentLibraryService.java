package devary.moyludoc.service;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Blocking
public class DocumentLibraryService {

    @ConfigProperty(name = "moyludoc.library.root", defaultValue = "docs-library")
    String libraryRoot;

    public DocumentTreeNode loadTree() {
        Path root = resolveRoot();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            return buildDirectoryNode(root, root, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load document library from " + root, e);
        }
    }

    public byte[] loadDocumentById(String id) {
        try {
            Path root = resolveRoot();
            Path relative = Path.of(new String(Base64.getUrlDecoder().decode(id)));
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root)) {
                throw new IllegalArgumentException("Document path escapes library root");
            }
            if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("Document not found");
            }
            if (Files.size(candidate) == 0) {
                throw new IllegalArgumentException("Document is empty");
            }
            return Files.readAllBytes(candidate);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read document", e);
        }
    }

    public String titleById(String id) {
        Path relative = Path.of(new String(Base64.getUrlDecoder().decode(id)));
        return relative.getFileName().toString();
    }

    private DocumentTreeNode buildDirectoryNode(Path root, Path directory, boolean includeEmptyRoot) throws IOException {
        List<DocumentTreeNode> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> {
                        try {
                            if (Files.isDirectory(path)) {
                                DocumentTreeNode child = buildDirectoryNode(root, path, false);
                                if (child != null) {
                                    children.add(child);
                                }
                            } else if (isSupportedDocument(path) && Files.size(path) > 0) {
                                Path relative = root.relativize(path);
                                children.add(new DocumentTreeNode(
                                        path.getFileName().toString(),
                                        relative.toString(),
                                        true,
                                        encode(relative),
                                        List.of(),
                                        Files.size(path)));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }

        if (!includeEmptyRoot && children.isEmpty()) {
            return null;
        }

        Path relative = root.equals(directory) ? Path.of("") : root.relativize(directory);
        return new DocumentTreeNode(
                root.equals(directory) ? root.getFileName().toString() : directory.getFileName().toString(),
                relative.toString(),
                false,
                null,
                children,
                0);
    }

    private boolean isSupportedDocument(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".docx");
    }

    private String encode(Path relative) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(relative.toString().getBytes());
    }

    private Path resolveRoot() {
        return Path.of(libraryRoot).toAbsolutePath().normalize();
    }

    public record DocumentTreeNode(
            String name,
            String relativePath,
            boolean document,
            String id,
            List<DocumentTreeNode> children,
            long sizeInBytes) {

        public DocumentTreeNode {
            children = List.copyOf(Objects.requireNonNullElse(children, List.of()));
        }
    }
}
