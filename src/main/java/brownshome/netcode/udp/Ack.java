package brownshome.netcode.udp;

/** This is a struct that holds the ack data for a packet.
 *
 * This is an immutable object
 **/
final class Ack {
	/** This array holds all of the packets that were acked in the last packet. */
	final int[] ackedPackets;
	
	/** This constructs the ack from the RAW data in the incoming packet. */
	Ack(int lsbSequenceNumber, int bitfield) {
		int size = Integer.bitCount(bitfield);
		int i = 0;
		ackedPackets = new int[size];
		
		//Bit n is lsbSequenceNumber - n + 1, where n starts from 1
		int n = 0;
		for (; bitfield != 0; bitfield >>>= 1) {
			n++;
			if((bitfield & 1) == 1) {
				ackedPackets[i++] = lsbSequenceNumber - n + 1;
			}
		}
		
		assert i == size;
	}
}
