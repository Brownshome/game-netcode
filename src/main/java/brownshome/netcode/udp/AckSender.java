package brownshome.netcode.udp;

/** This class records what packets have been received and produces the requested Ack fields for sending. */
final class AckSender {
	//Bit n is mostRecentAck - n + 1, where n starts from 1
	private int ackField;

	//This is the sequence number of the most recent ack
	private int mostRecentAck;

	void receivedPacket(int sequenceNumber) {
		if(ackField == 0) {
			mostRecentAck = sequenceNumber;
			ackField = 1;
			return;
		}

		if(sequenceNumber - mostRecentAck + (Integer.SIZE - 1) < 0) {
			//The sequenceNumber is out of date, nothing we can do.
			return;
		}

		//Rotate forward until mostRecentAck is the sequenceNumber
		while(sequenceNumber != mostRecentAck) {
			ackField <<= 1;
			mostRecentAck++;

			if(ackField == 0) {
				mostRecentAck = sequenceNumber;
				ackField = 1;
				return;
			}
		}

		//Set the bit for the most recent ack
		ackField |= 1;
	}

	/** Gets the sequence number of the most recent packet to be received */
	int mostRecentAck() {
		return mostRecentAck;
	}

	/** Creates a bitfield for a specific sequence number. If this number is smaller than the most recent sequence number then more
	 * recent acks will not be sent. */
	int createAckField(int sequenceNumber) {
		if(sequenceNumber - mostRecentAck <= 0) {
			return ackField >>> (mostRecentAck - sequenceNumber + 1);
		} else {
			return ackField << (sequenceNumber - mostRecentAck - 1);
		}
	}
}
