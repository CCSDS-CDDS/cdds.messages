# CCSDS Cloud Data Delivery Standards (CDDS)

** Attention, this is work in progress **

The CDDS context is the data transfer between terrestrial ground stations and mission data systems potantially deployed in a cloud.

CDDS covers the follwing domains:

- Telemetry             (data link layer)
- Telecommand            (data link layer)
- Ground Station Monitoring and Control
- Tracking Data

CDDS defines messages in terms of Google Protocol Buffers, which are maintained in this repository. While these messages are primariliy intended to be exchanged
by some implementation technology like message brokers or e.g. gRPC, that is not mandated by CDDS.

To facilitate the definition of file formats, CDDS foresees that the CDDS messages are written to files (Protobuf ‘writeDelimited’ and ‘readDelimited`)

**Building**

Build the project:         mvn install

Generate documentation:    mvn generate-sources
(target/generated-docs)
