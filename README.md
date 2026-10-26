# CCSDS Cloud Data Delivery Standards (CDDS)

This repository is associated with The Consultative Committee for Space Data Systems (CCSDS). 
CCSDS is an international organization comprised of 11 Member Agencies and multiple Observer Agencies. This repository is an official technical output of the 
**Cloud Data Delivery Standards (CDDS) Working Group** under the **Cross Support Services (CSS) Area** of CCSDS. 
This repository contains Protobuf CDDS message definitions corresponding to CCSDS 931.1 (Recommended Practice) and gRPC CDDS service definitions CCSDS 931.10 (Experimental Specification).
The content is developed and maintained by the Consultative Committee for Space Data Systems (CCSDS).

The CDDS context is the data transfer between terrestrial ground stations or CDDS service providers and mission data systems potentially deployed in a cloud.

CDDS covers the following domains:

- Telemetry             (data link layer)
- Telecommand            (data link layer)
- Ground Station Monitoring and Control
- Tracking Data

This repository contains CDDS messages defined in Google Protocol Buffers and corresponding unit tests. CDDS 931.1 does not mandate a specific technology to exchange CDDS messages, although a gRPC based approach is documented in 931.10 and used for unit test in this repository.

To facilitate CDDS file formats, CDDS foresees that the CDDS messages are written to files (Protobuf ‘writeDelimited’ and ‘readDelimited`)

**Building**
---
**Build Prerequisites**. The following executables must be installed and in the PATH:
 - protoc             
 - protoc-gen-doc     
 - protoc-gen-grpc-java

**Build the project**:         mvn install

**Generate documentation**:    mvn generate-sources or generate-doc.sh
(target/generated-docs)

**Debug mTLS**:
mvn test -Djavax.net.debug=ssl,handshake,trustmanager -Dtest=TcServiceTest#testSecureTcService

**Status**: DRAFT
---
These definitions are part of ongoing working group development and/or agency reviews. They are subject to change and should not be used for operational missions without an understanding of the risks associated with evolving specifications.

**Intellectual Property & Licensing**
---
These artifacts are provided to the international space community to promote interoperability. This repository is licensed under the Apache License, Version 2.0 

**Contributing**
---
As an international consensus-based body, CCSDS cannot accept changes to a standard without formal review. 
Bug Reports and Requested Technical Changes (dealt with on best effort basis):
*Please contact the CCSDS Working Group via secretariat@mailman.ccsds.org and
*Please open an Issue via GitHub Issues. These will be reviewed and answered by CCSDS CDDS Working Group.
*In addition feel free proposing changes via creating GitHub Pull Request. The Pull Request is subject to review by the CDDS CCSDS Working Group. Note: depending on the nature of the change(s) it may be subject to formal CCSDS review process.

**Disclaimer**
Use of these definitions is at the user's risk. Neither CCSDS nor the contributing Member Agencies assume any liability from the use of these files.

**Copyright Notice**

Copyright © 2026 CCSDS. These artifacts are derived from CCSDS standards which are the collective intellectual property of the CCSDS Member Agencies.

