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

## Annotation System

The annotation system allows the user to define packets by defining a **handler**. A handler is a method that is annotated with the packet definition annotations. This may be a static method, a constructor, or an instance method in a class with a no-argument constructor.

This method will be called by the networking engine when the packet is received. To send a packet a generated object with a name corresponding to the packet name annotation is constructed and then passed to the system.