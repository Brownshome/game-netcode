package brownshome.netcode;

import java.util.List;

public interface NetworkSchema {
	List<PacketDefinition<?>> getPacketTypes();
}
