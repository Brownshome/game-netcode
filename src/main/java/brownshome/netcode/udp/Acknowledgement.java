package brownshome.netcode.udp;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

import brownshome.netcode.annotation.converter.Networkable;

/**
 * The acknowledgement field of a packet. Bit n of acknowledgement is oldestAcknowledgement + n - 1
 */
public record Acknowledgement(int oldestAcknowledgement, int acknowledgement) implements Networkable, Iterable<Integer> {
	private static final Acknowledgement EMPTY = new Acknowledgement(0, 0);

	public Acknowledgement(ByteBuffer buffer) {
		this(buffer.getInt(), buffer.getInt());
	}

	public static Acknowledgement emptyAcknowledgement() {
		return EMPTY;
	}

	@Override
	public void write(ByteBuffer buffer) {
		buffer.putInt(oldestAcknowledgement);
		buffer.putInt(acknowledgement);
	}

	@Override
	public int size() {
		return Integer.BYTES * 2;
	}

	/**
	 * Returns an iterator for the acknowledgements in this field, from the oldest to the newest
	 * @return an iterator
	 */
	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			int next = oldestAcknowledgement;
			int bits = acknowledgement;

			@Override
			public int nextInt() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				int result = next;

				// Count the number of zeros in the field, and skip them all
				int n = Integer.numberOfTrailingZeros(bits & ~1);
				bits >>>= n;
				next += n;

				return result;
			}

			@Override
			public boolean hasNext() {
				return bits != 1;
			}
		};
	}
}
