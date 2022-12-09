## Build Camel Java Layer

```
mvn -f aries-backchannels/camel clean install
```

## Build ACA-Py + Camel Agents

```
./manage build -a acapy -a camel
```

## Start/Stop the Test Harness

```
./manage start -d acapy -b camel
...
./manage stop
```

or in one go

```
# Rebuild the Test Harness and the Camel agent
docker rm -f bob_agent && ./manage build -a camel && ./manage start -d acapy -b camel
```

## RFC0025 DIDComm Transports

[Aries RFC 0025: DIDComm Transports](https://github.com/hyperledger/aries-rfcs/tree/main/features/0025-didcomm-transports)

```
# Create DIDExchange connection between two agents with overlapping transports
./manage test -d acapy -b camel -t T001-RFC0025,T002-RFC0025 -t ~@Transport_Ws
```

## RFC0036 Issue Credential Protocol 1.0

[Aries RFC 0036: Issue Credential Protocol 1.0](https://github.com/hyperledger/aries-rfcs/tree/main/features/0036-issue-credential)

```
# Issue a credential with the Issuer beginning with an offer
./manage test -d acapy -b camel -t T001-RFC0036,T002-RFC0036,T003-RFC0036,T004-RFC0036
```

## RFC0037 Present Proof Protocol 1.0

[Aries RFC 0037: Present Proof Protocol 1.0](https://github.com/hyperledger/aries-rfcs/tree/main/features/0037-present-proof)

```
# Present Proof where the prover does not propose a presentation of the proof and is acknowledged
./manage test -d acapy -b camel -t T001-RFC0037
```

## RFC0160 P2P Connection Protocol

[Connection Protocol](https://github.com/hyperledger/aries-rfcs/tree/main/features/0160-connection-protocol)

```
# Establish a connection between two agents
./manage test -d acapy -b camel -t T001-RFC0160
```

## Run all supported tests

```
./manage test -d acapy -b camel -t T001-RFC0025,T002-RFC0025 -t ~@Transport_Ws \
&& ./manage test -d acapy -b camel -t T001-RFC0036,T002-RFC0036,T003-RFC0036,T004-RFC0036 \
&& ./manage test -d acapy -b camel -t T001-RFC0037 \
&& ./manage test -d acapy -b camel -t T001-RFC0160
```

## Next

```
docker rm -f bob_agent && mvn -f aries-backchannels/camel clean install && ./manage build -a camel && ./manage start -d acapy -b camel

./manage test -d acapy -b camel -t T002-RFC0025 -t ~@Transport_Ws
```

## AIP-1.0 Status

| Status | Feature: RFC 0025 DIDComm Transports
|:------:|:-------------------------------------|
|   x    | @T001-RFC0025 Create DIDExchange connection between two agents with overlapping transports
|   x    | @T002-RFC0025 Create 0160 connection between two agents with overlapping transports
|        | @T003-RFC0025 Fail creating a connection between two agents that have mismatching transports configured

| Status | Feature: RFC 0036 Aries agent issue credential
|:------:|:-----------------------------------------------|
|   x    | @T001-RFC0036 @AIP10 Issue a credential with the Holder beginning with a proposal
|   x    | @T002-RFC0036 @AIP10 Issue a credential with the Holder beginning with a proposal with negotiation
|   x    | @T003-RFC0036 @AIP10 Issue a credential with the Issuer beginning with an offer
|   x    | @T004-RFC0036 @AIP10 Issue a credential with the Issuer beginning with an offer with negotiation
|        | @T005-RFC0036 @AIP10 Issue a credential with negotiation beginning from a credential request
|        | @T006-RFC0036 @AIP10 Issue a credential with the Holder beginning with a request and is accepted

| Status | Feature: RFC 0037 Aries agent present proof
|:------:|:--------------------------------------------|
|   x    | @T001-RFC0037 @AIP10 Present Proof where the prover does not propose a presentation of the proof and is acknowledged
|        | @T001.2-RFC0037 @AIP10 Present Proof of specific types and proof is acknowledged with a Drivers License credential type
|        | @T001.3-RFC0037 @AIP10 Present Proof of specific types and proof is acknowledged with a Biological Indicators credential type
|        | @T001.4-RFC0037 @AIP10 Present Proof of specific types and proof is acknowledged with multiple credential types
|        | @T001.5-RFC0037 @AIP10 Present Proof where the prover does not propose a presentation of the proof and is acknowledged
|        | @T003-RFC0037 @AIP10 Present Proof where the prover has proposed the presentation of proof in response to a presentation request and is acknowledged
|        | @T003.1-RFC0037 @AIP10 Present Proof where the prover has proposed the presentation of proof from a different credential in response to a presentation request and is acknowledged
|        | @T004-RFC0037 @AIP10 Present Proof where the verifier and prover are connectionless, prover has proposed the presentation of proof, and is acknowledged
|        | @T005-RFC0037 @AIP10 Present Proof where the verifier rejects the presentation of the proof
|        | @T006-RFC0037 @AIP10 Present Proof where the prover starts with a proposal the presentation of proof and is acknowledged

| Status | Feature: RFC 0160 Aries agent connection functions
|:------:|:---------------------------------------------------|
|   x    | @T001-RFC0160 Establish a connection between two agents
|        | @T002-RFC0160 Connection established between two agents but inviter sends next message to establish full connection state
|        | @T003-RFC0160 Inviter Sends invitation for one agent second agent tries after connection
|        | @T004-RFC0160 Inviter Sends invitation for one agent second agent tries during first share phase
|        | @T005-RFC0160 Inviter Sends invitation for multiple agents
|        | @T006-RFC0160 Establish a connection between two agents who already have a connection initiated from invitee
|        | @T007-RFC0160 Establish a connection between two agents but gets a request not accepted report problem message

## AIP-2.0 Status

| Status | Feature: RFC 0023 Establishing Connections with DID Exchange
|:------:|:-------------------------------------------------------------|
|        | @T001-RFC0023 Establish a connection with DID Exchange between two agents with an explicit invitation
|        | @T003-RFC0023 Establish a connection with DID Exchange between two agents with an explicit invitation with a public DID
|        | @T005-RFC0023 Establish a connection with DID Exchange between two agents with an implicit invitation
|        | @T007-RFC0023 Establish a connection with DID Exchange between two agents with attempt to continue after protocol is completed
|        | @T008-RFC0023 Establish a connection with DID Exchange between two agents with an explicit invitation but invitation is rejected and connection process restarted
|        | @T009-RFC0023 Establish a connection with DID Exchange between two agents with an explicit invitation but invitation is rejected and connection process abandoned
|        | @T010-RFC0023 Establish a connection with DID Exchange and responder rejects the request
|        | @T011-RFC0023 Establish a connection with DID Exchange and requester rejects the response
|        | @T012-RFC0023 Attempt to Establish a connection with DID Exchange between two agents with an explicit invitation with connection reuse

| Status | Feature: RFC 0025 DIDComm Transports
|:------:|:-------------------------------------|
|        | @T001-RFC0025 Create DIDExchange connection between two agents with overlapping transports
|        | @T002-RFC0025 Create 0160 connection between two agents with overlapping transports
|        | @T003-RFC0025 Fail creating a connection between two agents that have mismatching transports configured

| Status | Feature: RFC 0036 Aries agent issue credential
|:------:|:-----------------------------------------------|
|        | @T001.1-RFC0036 @AIP20 Issue a credential with the Holder beginning with a proposal with DID Exchange Connection

| Status | Feature: RFC 0037 Aries agent present proof
|:------:|:--------------------------------------------|
|        | @T001.1-RFC0037 @AIP20 Present Proof where the prover does not propose a presentation of the proof and is acknowledged with a DID Exchange Connection

| Status | Feature: RFC0044 didcomm mime types
|:------:|:------------------------------------|
|        | @T001-RFC0044 Perform DID Exchange between two agents that have the same default envelope media profile
|        | @T002-RFC0044 Perform DID Exchange between two permissive agents that have different default envelope media profiles
|        | @T003-RFC0044 Fail DID Exchange between two agents with mismatching media profiles
|        | @T004-RFC0044 Perform DID Exchange with OOB media type handshake, with one accept parameter
|        | @T005-RFC0044 Perform DID Exchange with OOB media type handshake, with two accept parameters
|        | @T006-RFC0044 Fail DID Exchange between two agents with explicit oob accept parameter, with no matching profiles

| Status | Feature: RFC 0183 Aries agent credential revocation and revocation notification
|:------:|:--------------------------------------------------------------------------------|
|        | @T001-HIPE0011 Credential revoked by Issuer and Holder attempts to prove with a prover that doesn't care if it was revoked
|        | @T001.1-HIPE0011 Credential revoked by Issuer and Holder attempts to prove with a prover that doesn't care if it was revoked with a DID Exchange connection
|        | @T001.2-HIPE0011 Credential revoked by Issuer and Holder attempts to prove with a prover that doesn't care if it was revoked
|        | @T002-HIPE0011 Credential revoked and replaced with a new updated credential, holder proves claims with the updated credential with timesstamp
|        | @T002.1-HIPE0011 Credential revoked and replaced with a new updated credential, holder proves claims with the updated credential with no timestamp
|        | @T003-HIPE0011 Proof in process while Issuer revokes credential before presentation and the verifier doesn't care about revocation status
|        | @T004-HIPE0011 Credential revoked and replaced with a new updated credential, get possible credentials from agent wallet
|        | @T005-HIPE0011 Credential is revoked inside the non-revocation interval
|        | @T006-HIPE0011 Credential is revoked before the non-revocation instant
|        | @T006.1-HIPE0011 Credential is revoked before the non-revocation interval
|        | @T006.2-HIPE0011 Credential is revoked before the non-revocation instant
|        | @T007-HIPE0011 Credential is revoked after the non-revocation instant
|        | @T008-HIPE0011 Credential is revoked during a timeframe with an open ended FROM or TO date
|        | @T009-HIPE0011 Revoke attempt be done by the holder or a verifier
|        | @T010-HIPE0011 Attempt to revoke an unrevokable credential.
|        | @T011-HIPE0011 Issuer revokes multiple credentials in the same transaction
|        | @T012-HIPE0011 Revocable Credential not revoked and Holder attempts to prove without a non-revocation interval
|        | @T013-HIPE0011 Non-revocable Credential, not revoked, and holder proves claims with the credential with timesstamp
|        | @T014-HIPE0011 Revocable Credential, not revoked, and holder proves claims with the credential with timesstamp
|        | @T001-RFC0183 Issuer revokes a credential and sends a v1 revocation notification

| Status | Feature: RFC 0211 Aries Agent Mediator Coordination
|:------:|:----------------------------------------------------|
|        | @T001-RFC0211 Request mediation with the mediator accepting the mediation request
|        | @T002-RFC0211 Request mediation with the mediator accepting the mediation request and creating a connection using the mediator
|        | @T003-RFC0211 Request mediation with the mediator denying the mediation request
|        | @T004-RFC0211 @Transport_Http @Transport_Ws Two agents creating a connection using a mediator without having inbound transports
|        | @T005-RFC0211 @Transport_Http @Transport_Ws Two agents creating a connection using a mediator with overlapping transports

| Status | Feature: RFC 0434 Intiating exchange using the Out of Band protocol
|:------:|:--------------------------------------------------------------------|
|        | @T001-RFC0434 Issue a v1 indy credential using connectionless out of band invitation
|        | @T002-RFC0434 Issue a v2 credential using connectionless out of band invitation
|        | @T003-RFC0434 Present a v1 indy proof using connectionless out of band invitation
|        | @T004-RFC0434 Present a v2 proof using connectionless out of band invitation

| Status | Feature: RFC 0453 Aries Agent Issue Credential v2
|:------:|:--------------------------------------------------|
|        | @T001-RFC0453 Issue a Indy credential with the Holder beginning with a proposal
|        | @T001.1-RFC0453 Issue a JSON-LD credential with the Holder beginning with a proposal
|        | @T002-RFC0453 Issue a credential with the Holder beginning with a proposal with negotiation

| Status | Feature: RFC 0454 Aries agent present proof v2
|:------:|:-----------------------------------------------|
|        | @T001-RFC0454 Present Proof of specific types and proof is acknowledged with a Drivers License credential type with a DID Exchange Connection
|        | @T002-RFC0454 Present Proof of specific types and proof is acknowledged with a Citizenship credential type with a DID Exchange Connection
