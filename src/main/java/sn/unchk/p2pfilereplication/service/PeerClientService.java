package sn.unchk.p2pfilereplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import sn.unchk.p2pfilereplication.config.NodeConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class PeerClientService {

    private static final Logger log = LoggerFactory.getLogger(PeerClientService.class);

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS    = 5_000;

    private final List<String> peers;
    private final RestClient restClient;

    public PeerClientService(NodeConfig nodeConfig, RestClient.Builder restClientBuilder) {
        this.peers = nodeConfig.getPeers();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public void replicateToPeers(String filename, byte[] content) {
        if (peers.isEmpty()) {
            log.debug("No peers configured — replication skipped for [{}]", filename);
            return;
        }
        peers.forEach(peer -> replicateToPeer(peer, filename, content));
    }

    public Optional<byte[]> fetchFromPeers(String filename) {
        if (peers.isEmpty()) {
            log.debug("No peers configured — remote fetch skipped for [{}]", filename);
            return Optional.empty();
        }
        return peers.stream()
                .map(peer -> fetchFromPeer(peer, filename))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private void replicateToPeer(String peerBaseUrl, String filename, byte[] content) {
        try {
            restClient.post()
                    .uri(peerBaseUrl + "/files/replica/" + encodeSegment(filename))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(buildMultipartBody(filename, content))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Replicated [{}] → [{}]", filename, peerBaseUrl);
        } catch (RestClientException ex) {
            log.warn("Replication of [{}] to [{}] failed — peer may be down: {}",
                    filename, peerBaseUrl, ex.getMessage());
        }
    }

    private Optional<byte[]> fetchFromPeer(String peerBaseUrl, String filename) {
        try {
            byte[] content = restClient.get()
                    .uri(peerBaseUrl + "/files/" + encodeSegment(filename))
                    .retrieve()
                    .body(byte[].class);

            if (content != null && content.length > 0) {
                log.info("Retrieved [{}] from peer [{}]", filename, peerBaseUrl);
                return Optional.of(content);
            }
        } catch (RestClientException ex) {
            log.debug("Peer [{}] cannot serve [{}]: {}", peerBaseUrl, filename, ex.getMessage());
        }
        return Optional.empty();
    }

    private static String encodeSegment(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }

    private MultiValueMap<String, Object> buildMultipartBody(String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return body;
    }
}
