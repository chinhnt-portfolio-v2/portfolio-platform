package dev.chinh.portfolio.shared.config;

public class DemoApp {

    private String id;
    private String name;
    private String healthEndpoint;
    private int pollIntervalSeconds = 60;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHealthEndpoint() { return healthEndpoint; }
    public void setHealthEndpoint(String healthEndpoint) { this.healthEndpoint = healthEndpoint; }

    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }
}
