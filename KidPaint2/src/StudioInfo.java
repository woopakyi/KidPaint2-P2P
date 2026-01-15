public class StudioInfo {
    public final String name;
    public final String ip;
    public final int port;

    public StudioInfo(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    @Override
    public String toString() {
        return name + " (" + ip + ":" + port + ")";
    }
}
