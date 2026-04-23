package sn.unchk.p2pfilereplication.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "node")
public class NodeConfig {

    @NotBlank(message = "node.storage-dir must be configured")
    private String storageDir;

    @NotNull(message = "node.peers must be configured (use empty list [] if no peers)")
    private List<String> peers = new ArrayList<>();

    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }

    public List<String> getPeers() { return peers; }
    public void setPeers(List<String> peers) { this.peers = peers; }
}
