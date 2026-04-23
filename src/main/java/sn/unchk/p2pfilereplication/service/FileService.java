package sn.unchk.p2pfilereplication.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sn.unchk.p2pfilereplication.config.NodeConfig;
import sn.unchk.p2pfilereplication.exception.FileNotFoundException;
import sn.unchk.p2pfilereplication.exception.InvalidFilenameException;
import sn.unchk.p2pfilereplication.model.FileInfo;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final Path storageRoot;
    private final PeerClientService peerClientService;

    public FileService(NodeConfig nodeConfig, PeerClientService peerClientService) {
        this.storageRoot = Paths.get(nodeConfig.getStorageDir()).toAbsolutePath().normalize();
        this.peerClientService = peerClientService;
    }

    @PostConstruct
    public void initStorage() {
        try {
            Files.createDirectories(storageRoot);
            log.info("Storage initialized at: {}", storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot initialize storage directory: " + storageRoot, ex);
        }
    }

    public FileInfo storeFile(String filename, byte[] content) {
        validateContent(content);
        Path target = resolveSecurePath(filename);
        writeToDisk(filename, content, target);
        scheduleReplication(filename, content);
        log.info("File [{}] stored and queued for replication ({} bytes)", filename, content.length);
        return new FileInfo(filename, content.length);
    }

    public void storeReplica(String filename, byte[] content) {
        validateContent(content);
        Path target = resolveSecurePath(filename);
        writeToDisk(filename, content, target);
        log.info("Replica [{}] stored locally — replication suppressed (loop guard)", filename);
    }

    public byte[] fetchFile(String filename) {
        Path target = resolveSecurePath(filename);

        if (Files.exists(target)) {
            log.debug("Serving [{}] from local storage", filename);
            return readFromDisk(filename, target);
        }

        log.info("File [{}] absent locally — initiating peer discovery", filename);
        byte[] content = peerClientService.fetchFromPeers(filename)
                .orElseThrow(() -> new FileNotFoundException(
                        "File [" + filename + "] not found locally or on any reachable peer."));

        scheduleLocalCache(filename, content);
        return content;
    }

    public List<FileInfo> listFiles() {
        try (Stream<Path> entries = Files.walk(storageRoot, 1)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(this::toFileInfo)
                    .toList();
        } catch (IOException ex) {
            log.error("Failed to enumerate storage directory: {}", storageRoot, ex);
            throw new IllegalStateException("Failed to list local files", ex);
        }
    }

    private void writeToDisk(String filename, byte[] content, Path target) {
        try {
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            log.error("Disk write failed for [{}]", filename, ex);
            throw new IllegalStateException("Failed to persist file: " + filename, ex);
        }
    }

    private byte[] readFromDisk(String filename, Path target) {
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            log.error("Disk read failed for [{}]", filename, ex);
            throw new IllegalStateException("Failed to read file: " + filename, ex);
        }
    }

    private void scheduleReplication(String filename, byte[] content) {
        CompletableFuture.runAsync(() -> peerClientService.replicateToPeers(filename, content));
    }

    private void scheduleLocalCache(String filename, byte[] content) {
        CompletableFuture.runAsync(() -> {
            Path target = resolveSecurePath(filename);
            try {
                Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Background cache complete: [{}]", filename);
            } catch (IOException ex) {
                log.warn("Background cache failed for [{}]: {}", filename, ex.getMessage());
            }
        });
    }

    private void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File content must not be empty.");
        }
    }

    private Path resolveSecurePath(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidFilenameException("Filename must not be blank.");
        }
        Path resolved = storageRoot.resolve(filename).normalize();
        if (!resolved.startsWith(storageRoot)) {
            log.warn("Path traversal attempt blocked — input: [{}]", filename);
            throw new InvalidFilenameException("Illegal filename: path traversal is not permitted.");
        }
        return resolved;
    }

    private FileInfo toFileInfo(Path path) {
        try {
            return new FileInfo(path.getFileName().toString(), Files.size(path));
        } catch (IOException ex) {
            log.warn("Could not read size for [{}] — reporting -1", path.getFileName());
            return new FileInfo(path.getFileName().toString(), -1L);
        }
    }
}
