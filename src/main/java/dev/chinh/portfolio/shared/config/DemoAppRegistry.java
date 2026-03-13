package dev.chinh.portfolio.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "")
public class DemoAppRegistry {

    private List<DemoApp> apps = new ArrayList<>();

    public List<DemoApp> getApps() { return apps; }
    public void setApps(List<DemoApp> apps) { this.apps = apps; }
}
