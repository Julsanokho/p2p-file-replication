package sn.unchk.p2pfilereplication.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sn.unchk.p2pfilereplication.model.FileInfo;
import sn.unchk.p2pfilereplication.service.FileService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/{filename}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<FileInfo> uploadFile(
            @PathVariable String filename,
            @RequestBody byte[] content) {

        log.info("Upload request: [{}] ({} bytes)", filename, content.length);
        FileInfo saved = fileService.storeFile(filename, content);
        return ResponseEntity.accepted().body(saved);
    }

    @PostMapping(value = "/replica/{filename}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> receiveReplica(
            @PathVariable String filename,
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Replica ingestion: [{}] ({} bytes)", filename, file.getSize());
        fileService.storeReplica(filename, file.getBytes());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        log.info("Download request: [{}]", filename);
        byte[] content = fileService.fetchFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping
    public ResponseEntity<List<FileInfo>> listFiles() {
        return ResponseEntity.ok(fileService.listFiles());
    }
}
