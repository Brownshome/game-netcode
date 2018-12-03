# Architecture

The use defines packet handlers. The packet class is then generated and registered to a schema, which is also generated. When the system initially connects the schemas are compared, and an error is raised if the systems are not compatible. The schemas also negotiate a minor version number, which can be used to support out-of-date schemas.

# Connection overloads

Under normal conditions a connection should not overload, that is fill up its send buffer. This would occur if the sends overload the bandwidth of the connection or the processing on the other end of the connection.

In this case we should drop the non-reliable and / or low-priority packets. It is not acceptable to drop reliable packets, if we do have to drop a reliable packet it is likely that the other end of the connection has deadlocked or lost connection. In that case we raise an error state, and throw an exception at the site of sending.