# UDP connection

Most of the systems in the UDP code has been taken from [here](https://gafferongames.com/categories/building-a-game-network-protocol/).

The system sends all packets that are in the `UDPPackets` directly as a single datagram, all other packets are aggregated into a udpData packet. This packet contains an arbitrary number of packets encoded within it, which will be called messages. This packet also contains a packet-level ack system, which will be used for conjestion detection, reliability and large file fragmentation.

## Connecting

The connecting process goes through two phases, UDP connection negotiation, and protocol negotiation.

The UDP negotiation phase followings the following system:
1. A connect packet is sent from the client to the server, which is padded, and contains the client salt.
2. The response is either a `ChallengePacket` which indicates a successful connection, and contains the server salt, or a `ConnectionDeniedPacket` which indicates that the connection was rejected for some reason.
3. Once this has been established, all sent packets must be accompanied by a hash, which equals `hash(SENDER_SALT ... RECEIVER_SALT ... DATA)` this hash is used to ensure that the sender is who it says it is, and that it has performed the correct negotiation process. In both cases the actual hashes are not sent. This ensures that it is computationally difficult to produce the salts from the messages.

Once this part of the negotiation is complete then the protocol negotiation proceeds as normal, with each individual packet treated as a message.

## Sending System

The sending system keeps a queue of packets that are currently being sent. The system accumulates bandwidth at the estimated congestion speed. When there is enough bandwidth then the next packet of messages is sent. If the packet is reliable then it is resent multiple times until the ack for that packet arrives back.

When large amounts of data need to be sent they are fragmented into fragment packets.