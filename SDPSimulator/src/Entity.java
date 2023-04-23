import java.util.ArrayList;

abstract public class Entity {
    private final String ip;
    private final int port;
    private final String name;

    public Entity(String ip, int port, String name) {
        this.ip = ip;
        this.port = port;
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    abstract public void addDPDiscovered(DomainParticipant domainParticipant);
    abstract public void addDPDiscovered(String ip, int port, String name);

    abstract public DomainParticipant getDPById(int id);
    abstract public ArrayList<DomainParticipant> getDomainParticipantsDiscovered();

}