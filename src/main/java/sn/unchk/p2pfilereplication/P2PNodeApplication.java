package sn.unchk.p2pfilereplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import sn.unchk.p2pfilereplication.config.NodeConfig;

@SpringBootApplication
@EnableConfigurationProperties(NodeConfig.class)
public class P2PNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(P2PNodeApplication.class, args);
    }
}
