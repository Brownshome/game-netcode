package brownshome.netcode;

import java.util.List;

public interface NetworkSchema {
	List<PacketDefinition<?>> getPacketTypes();
	String getShortName();
	String getFullName();
	int getMinorVersion();
	int getMajorVersion();
}
