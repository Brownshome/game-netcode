package brownshome.netcode.udp;

/** This is a struct that holds the ack data for a packet. */
final class UDPAck {
	/** This array holds all of the packets that were acked in the last packet. */
	final int[] ackedPackets;
	
	/** This constructs the ack from the RAW data in the incoming packet. */
	UDPAck(int mostRecentAck, int bitfield) {
		int size = Integer.bitCount(bitfield) + 1;
		int i = 0;
		ackedPackets = new int[size];
		
		ackedPackets[i++] = mostRecentAck;
		
		//Bit n is mostRecentAck - n, where n starts from 1
		int n = 0;
		for(; bitfield != 0; bitfield >>>= 1) {
			n++;
			if((bitfield & 1) == 1) {
				ackedPackets[i++] = mostRecentAck - n;
			}
		}
		
		assert i == size;
	}
}
