# Netcode

This project adds annotation processors and classes that simplify the addition of packet based networking code to a project. The aim of this project is to support easy to use annotation driven packet definition without using reflection or run-time type inference.

This project supports the following connection types:

1. UDP
2. TCP
3. In memory connections

And the following features:

1. Packet priority
2. Mismatched version support
3. Data marshaling
4. Reliable sending
5. Fragmented packets
6. Custom connection types
7. Multi-threaded handling
8. Packet ordering guarantees