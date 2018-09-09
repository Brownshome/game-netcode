package brownshome.netcode;

import java.util.function.Supplier;

import brownshome.netcode.annotation.PacketDeclaration;

public final class PacketDefinition<PACKET extends Packet> {
	public final PacketDeclaration declaration;
	public final Supplier<PACKET> constructor;
	
	public PacketDefinition(PacketDeclaration declaration, Supplier<PACKET> constructor) {
		this.declaration = declaration;
		this.constructor = constructor;
	}
}