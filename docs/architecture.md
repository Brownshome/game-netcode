# Architecture

The use defines packet handlers. The packet class is then generated and registered to a schema, which is also generated. When the system initially connects the schemas are compared, and an error is raised if the systems are not compatible. The schemas also negotiate a minor version number, which can be used to support out-of-date schemas.