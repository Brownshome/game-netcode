package brownshome.netcode;

public interface Connection {
	void send(Packet packet);
	void flush();
}
